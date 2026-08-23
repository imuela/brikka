"""Brika AI Worker (Sprint 10, D10-4/D10-5; real provider calls added Sprint 33/34).

Stateless HTTP service that stands in for the RabbitMQ-based Worker described in
ADR-AI-001 / 21_AI_V1_SCOPE.md §4, activated only when the Spring Boot Gateway is
configured with `brika.ai.worker-transport=http` (HttpAiTaskDispatcher). The default
Gateway transport (LocalAiTaskDispatcher) never calls this process.

Hard constraints (D10-4, disclosed and enforced structurally, not just by convention):
  - No PostgreSQL access and no database credentials anywhere in this module.
  - Never writes to Brika's database directly — the only way a result reaches
    `document_extractions` is via the internal Spring Boot callback endpoint.
  - No storage (MinIO/S3) credentials either: the Gateway hands this worker a
    short-lived presigned download URL per request (`payload.documentDownloadUrl`,
    Sprint 33) instead — the worker fetches bytes over plain HTTPS, never touches a
    storage SDK or secret key.
  - Never fabricates an extraction result (D10-2): if no provider is configured
    (`AI_PROVIDER` unset/`none` and no legacy `ANTHROPIC_API_KEY`), every request is
    answered honestly with an empty result and no provider/model reported, mirroring
    NoOpAiProvider on the Java side. This is the behavior in every environment that
    has not explicitly opted in.

Sprint 33 added a real call to the Anthropic Messages API. Sprint 34 generalizes
provider selection (`_resolve_provider`, `AI_PROVIDER=none|anthropic|ollama`) and adds
a second, local/free provider — Ollama (http://localhost:11434 by default) — so a
developer can run real document analysis with no API key and no per-call cost. Both
providers are synchronous, single-turn, no tools/agents/streaming/RAG. On any failure
(network, timeout, unparseable response, unreachable local Ollama, unsupported format
for the selected provider) the worker still reports provider/model (so the Gateway can
honestly record FAILED rather than NO_PROVIDER — "we tried and it broke" is a
different, more useful fact than "nothing was configured") plus a warning describing
what went wrong; it never crashes and never fabricates field values it doesn't
actually have.

Sprint 34 D34-AI-1 — Ollama is deliberately text-only here: no vision-capable model is
bundled by default (multimodal Ollama models run several GB, explicitly ruled out by
the sprint's "no instalar un modelo enorme"). PDF/image analysis therefore stays
Anthropic-only until a documented decision adds a local vision model; requesting those
formats through Ollama is an honest, structured "unsupported for this provider"
outcome, never a best-effort guess.

Uses only the Python standard library — no framework, no SDK dependency to justify
for a two-provider, single-endpoint service (both HTTP APIs need nothing but a POST).
"""

import base64
import json
import os
import re
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DEFAULT_PORT = 8100
ANTHROPIC_API_VERSION = "2023-06-01"
DEFAULT_ANTHROPIC_MODEL = "claude-3-5-sonnet-20241022"
REQUEST_TIMEOUT_SECONDS = 30
MAX_DOCUMENT_BYTES = 15 * 1024 * 1024  # matches brika.storage.max-file-size-bytes order of magnitude

# Sprint 34: a small instruction-tuned model, chosen specifically so `ollama pull` stays under
# ~1.3GB and CPU inference on a dev laptop stays reasonably fast — not the most capable model
# Ollama can run, the most reasonable default for a local dev machine (docs/09_AI.md §Ollama).
DEFAULT_OLLAMA_MODEL = "llama3.2:1b"
DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"
# Local CPU inference is slower and more variable than a cloud API — generous but bounded.
OLLAMA_REQUEST_TIMEOUT_SECONDS = 60

# 21_AI_V1_SCOPE.md §2.A: field/value/confidence/source/page shape. Only formats Claude's
# Messages API actually accepts as a document/image/text content block — anything else is a
# structured, honest "unsupported format" outcome, never a best-effort guess.
SUPPORTED_MIME_TYPES = {"application/pdf", "image/jpeg", "image/png", "text/plain", "text/html"}
# Ollama subset (D34-AI-1 above): text only, no document/image content blocks.
OLLAMA_SUPPORTED_MIME_TYPES = {"text/plain", "text/html"}

EXTRACTION_SYSTEM_PROMPT = (
    "You are a document-analysis assistant for a mortgage broker platform. Analyze the"
    " attached document and respond with ONLY a single JSON object (no prose, no markdown"
    " fences) with this exact shape:\n"
    '{"summary": "<one or two sentence summary>",'
    ' "fields": [{"name": "<snake_case field name>", "value": "<string value>",'
    ' "confidence": <0.0-1.0>, "page": <int or null>}],'
    ' "warnings": ["<any caveat about the extraction>"]}\n'
    "If the document contains a monthly income figure, name that field exactly"
    ' "monthly_income" with a plain numeric string value (no currency symbol, no thousands'
    " separators). Never invent a field you cannot actually see in the document — omit it"
    " instead. If you cannot read the document at all, return empty fields and explain why in"
    " warnings."
)


def _resolve_provider():
    """Sprint 34: explicit selection via `AI_PROVIDER` (none|anthropic|ollama). When unset, falls
    back to the Sprint 33 behavior (ANTHROPIC_API_KEY alone activates Anthropic) so existing
    deployments/tests that only ever set that one variable keep working unchanged. Nothing ever
    activates implicitly for Ollama — it requires the explicit opt-in, matching D10-2's "no
    provider is on by default" precedent (NoOpAiProvider/NoOpEmailSender).
    """
    explicit = os.environ.get("AI_PROVIDER", "").strip().lower()
    if explicit in ("none", "anthropic", "ollama"):
        return explicit
    if os.environ.get("ANTHROPIC_API_KEY", "").strip():
        return "anthropic"
    return "none"


def build_callback_body(payload):
    """Never fabricates data — same disclosed "no provider" outcome as NoOpAiProvider, unless a
    real provider is selected and configured, in which case it attempts a real call.
    """
    provider = _resolve_provider()
    if provider == "anthropic":
        api_key = os.environ.get("ANTHROPIC_API_KEY", "").strip()
        if not api_key:
            return {"extractedFields": [], "confidence": {}}
        return run_anthropic_extraction(payload, api_key)
    if provider == "ollama":
        return run_ollama_extraction(payload)
    return {"extractedFields": [], "confidence": {}}


def _validate_download_payload(payload, supported_mime_types):
    download_url = payload.get("documentDownloadUrl")
    mime_type = payload.get("documentMimeType")
    if not download_url:
        return None, None, "No document download URL was provided by the Gateway."
    if mime_type not in supported_mime_types:
        return None, None, f"Unsupported document type for this AI provider: {mime_type!r}."
    return download_url, mime_type, None


def run_anthropic_extraction(payload, api_key):
    model = os.environ.get("ANTHROPIC_MODEL", DEFAULT_ANTHROPIC_MODEL)
    download_url, mime_type, error = _validate_download_payload(payload, SUPPORTED_MIME_TYPES)
    if error:
        return _failed_result("anthropic", model, error)

    try:
        content_bytes = _fetch_document(download_url)
    except (urllib.error.URLError, OSError, ValueError) as exc:
        return _failed_result("anthropic", model, f"Could not fetch document content: {exc}")

    try:
        response_text = _call_anthropic(api_key, model, content_bytes, mime_type)
        parsed = _parse_extraction_response(response_text)
    except (urllib.error.URLError, OSError, TimeoutError) as exc:
        return _failed_result("anthropic", model, f"AI provider call failed: {exc}")
    except ValueError as exc:
        return _failed_result("anthropic", model, f"AI provider returned an unusable response: {exc}")

    return _completed_result("anthropic", model, parsed)


def run_ollama_extraction(payload):
    model = os.environ.get("OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL)
    download_url, mime_type, error = _validate_download_payload(
        payload, OLLAMA_SUPPORTED_MIME_TYPES
    )
    if error:
        return _failed_result("ollama", model, error)

    try:
        content_bytes = _fetch_document(download_url)
    except (urllib.error.URLError, OSError, ValueError) as exc:
        return _failed_result("ollama", model, f"Could not fetch document content: {exc}")

    try:
        response_text = _call_ollama(model, content_bytes)
        parsed = _parse_extraction_response(response_text)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:200] if exc.fp else str(exc)
        return _failed_result("ollama", model, f"Local Ollama call failed: HTTP {exc.code} {detail}")
    except (urllib.error.URLError, OSError, TimeoutError) as exc:
        return _failed_result("ollama", model, f"Local Ollama call failed: {exc}")
    except ValueError as exc:
        return _failed_result("ollama", model, f"Ollama returned an unusable response: {exc}")

    return _completed_result("ollama", model, parsed)


def _completed_result(provider, model, parsed):
    fields = parsed.get("fields", [])
    return {
        "extractedFields": fields,
        "confidence": {"overall": _average_confidence(fields)},
        "provider": provider,
        "model": model,
        "summary": parsed.get("summary"),
        "warnings": parsed.get("warnings", []),
    }


def _failed_result(provider, model, warning):
    return {
        "extractedFields": [],
        "confidence": {},
        "provider": provider,
        "model": model,
        "summary": None,
        "warnings": [warning],
    }


def _fetch_document(url):
    request = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        content = response.read(MAX_DOCUMENT_BYTES + 1)
    if len(content) > MAX_DOCUMENT_BYTES:
        raise ValueError("document exceeds the maximum size this worker will analyze")
    return content


def _content_block(content_bytes, mime_type):
    if mime_type == "text/plain" or mime_type == "text/html":
        return {"type": "text", "text": content_bytes.decode("utf-8", errors="replace")}
    encoded = base64.b64encode(content_bytes).decode("ascii")
    block_type = "document" if mime_type == "application/pdf" else "image"
    return {"type": block_type, "source": {"type": "base64", "media_type": mime_type, "data": encoded}}


def _call_anthropic(api_key, model, content_bytes, mime_type):
    body = {
        "model": model,
        "max_tokens": 1024,
        "system": EXTRACTION_SYSTEM_PROMPT,
        "messages": [
            {
                "role": "user",
                "content": [
                    _content_block(content_bytes, mime_type),
                    {"type": "text", "text": "Analyze this document as instructed."},
                ],
            }
        ],
    }
    api_url = os.environ.get(
        "ANTHROPIC_API_BASE_URL", "https://api.anthropic.com"
    ).rstrip("/") + "/v1/messages"
    request = urllib.request.Request(
        api_url,
        data=json.dumps(body).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "x-api-key": api_key,
            "anthropic-version": ANTHROPIC_API_VERSION,
        },
    )
    with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
        response_body = json.loads(response.read().decode("utf-8"))
    blocks = response_body.get("content", [])
    text_blocks = [b.get("text", "") for b in blocks if b.get("type") == "text"]
    if not text_blocks:
        raise ValueError("provider response contained no text content")
    return "".join(text_blocks)


def _call_ollama(model, content_bytes):
    # Ollama has no vision content-block API here (D34-AI-1) — only ever called for the
    # OLLAMA_SUPPORTED_MIME_TYPES text formats, so decoding as UTF-8 text is always correct.
    text = content_bytes.decode("utf-8", errors="replace")
    prompt = EXTRACTION_SYSTEM_PROMPT + "\n\nDocument content:\n" + text
    body = {"model": model, "prompt": prompt, "stream": False, "format": "json"}
    base_url = os.environ.get("OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL).rstrip("/")
    request = urllib.request.Request(
        base_url + "/api/generate",
        data=json.dumps(body).encode("utf-8"),
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=OLLAMA_REQUEST_TIMEOUT_SECONDS) as response:
        response_body = json.loads(response.read().decode("utf-8"))
    text_response = response_body.get("response")
    if not text_response:
        raise ValueError("Ollama response contained no 'response' text")
    return text_response


def _parse_extraction_response(response_text):
    # The prompt asks for bare JSON, but never trust a provider to obey formatting requests
    # perfectly — strip a markdown fence if one shows up anyway, rather than failing outright.
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", response_text.strip())
    try:
        parsed = json.loads(cleaned)
    except json.JSONDecodeError as exc:
        raise ValueError(f"response was not valid JSON: {exc}") from exc
    if not isinstance(parsed, dict):
        raise ValueError("response JSON was not an object")
    return parsed


def _average_confidence(fields):
    scores = [f.get("confidence") for f in fields if isinstance(f.get("confidence"), (int, float))]
    return round(sum(scores) / len(scores), 2) if scores else None


def send_callback(callback_url, callback_secret, body):
    data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        callback_url,
        data=data,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Ai-Worker-Secret": callback_secret or "",
        },
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.status


class ExtractionRequestError(ValueError):
    pass


def validate_envelope(envelope):
    if not isinstance(envelope, dict):
        raise ExtractionRequestError("Request body must be a JSON object.")
    if envelope.get("eventType") != "ai.document.analysis.requested":
        raise ExtractionRequestError("Unsupported eventType.")
    payload = envelope.get("payload")
    if not isinstance(payload, dict):
        raise ExtractionRequestError("Missing payload.")
    for field in ("documentVersionId", "callbackUrl", "callbackSecret"):
        if not payload.get(field):
            raise ExtractionRequestError(f"Missing payload.{field}.")
    return payload


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):  # noqa: A002 - stdlib signature
        pass  # keep test/CI output quiet; no sensitive data is ever logged either way

    def do_POST(self):
        if self.path != "/extract":
            self._respond(404, {"error": "NOT_FOUND"})
            return

        length = int(self.headers.get("Content-Length", 0))
        raw_body = self.rfile.read(length) if length else b""

        try:
            envelope = json.loads(raw_body or b"{}")
            payload = validate_envelope(envelope)
        except (json.JSONDecodeError, ExtractionRequestError) as exc:
            self._respond(400, {"error": "INVALID_REQUEST", "message": str(exc)})
            return

        callback_body = build_callback_body(payload)
        try:
            send_callback(payload["callbackUrl"], payload["callbackSecret"], callback_body)
        except (urllib.error.URLError, OSError) as exc:
            self._respond(502, {"error": "CALLBACK_FAILED", "message": str(exc)})
            return

        self._respond(202, {"status": "ACCEPTED"})

    def _respond(self, status, body):
        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def run(port=DEFAULT_PORT):
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    server.serve_forever()


if __name__ == "__main__":
    run()
