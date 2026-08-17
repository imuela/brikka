"""Brika AI Worker (Sprint 10, D10-4/D10-5).

Stateless HTTP service that stands in for the RabbitMQ-based Worker described in
ADR-AI-001 / 21_AI_V1_SCOPE.md §4, activated only when the Spring Boot Gateway is
configured with `brika.ai.worker-transport=http` (HttpAiTaskDispatcher). The default
Gateway transport (LocalAiTaskDispatcher) never calls this process.

Hard constraints (D10-4, disclosed and enforced structurally, not just by convention):
  - No PostgreSQL access and no database credentials anywhere in this module.
  - Never writes to Brika's database directly — the only way a result reaches
    `document_extractions` is via the internal Spring Boot callback endpoint.
  - Never fabricates an extraction result (D10-2): no AI provider is approved for V1,
    so every request is answered honestly with an empty result and NO_PROVIDER intent,
    mirroring NoOpAiProvider on the Java side.

Uses only the Python standard library — no framework dependency to justify for a
single-endpoint stub service.
"""

import json
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DEFAULT_PORT = 8100


def build_callback_body(payload):
    """Never fabricates data — same disclosed "no provider" outcome as NoOpAiProvider."""
    return {"extractedFields": [], "confidence": {}}


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
