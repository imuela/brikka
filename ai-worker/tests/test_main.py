import base64
import json
import os
import sys
import threading
import unittest
import unittest.mock
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import main as worker  # noqa: E402


def start_server(handler_class):
    server = ThreadingHTTPServer(("127.0.0.1", 0), handler_class)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, thread


def post(url, body_dict):
    data = json.dumps(body_dict).encode("utf-8") if body_dict is not None else b""
    request = urllib.request.Request(
        url, data=data, method="POST", headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read().decode("utf-8"))


class CapturingCallbackHandler(BaseHTTPRequestHandler):
    received = []

    def log_message(self, format, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length) if length else b""
        CapturingCallbackHandler.received.append(
            {
                "path": self.path,
                "secret_header": self.headers.get("X-Ai-Worker-Secret"),
                "body": json.loads(body) if body else None,
            }
        )
        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()


class WorkerExtractEndpointTests(unittest.TestCase):
    def setUp(self):
        self.worker_server, self.worker_thread = start_server(worker.Handler)
        self.worker_port = self.worker_server.server_address[1]

        CapturingCallbackHandler.received = []
        self.callback_server, self.callback_thread = start_server(CapturingCallbackHandler)
        self.callback_port = self.callback_server.server_address[1]

    def tearDown(self):
        self.worker_server.shutdown()
        self.worker_server.server_close()
        self.callback_server.shutdown()
        self.callback_server.server_close()

    def worker_url(self, path):
        return f"http://127.0.0.1:{self.worker_port}{path}"

    def callback_url(self):
        return f"http://127.0.0.1:{self.callback_port}/internal/ai/document-extractions/abc/callback"

    def valid_envelope(self, callback_url=None):
        return {
            "eventId": "11111111-1111-1111-1111-111111111111",
            "eventType": "ai.document.analysis.requested",
            "occurredAt": "2026-08-17T00:00:00Z",
            "companyId": "22222222-2222-2222-2222-222222222222",
            "aggregateType": "DOCUMENT_EXTRACTION",
            "aggregateId": "33333333-3333-3333-3333-333333333333",
            "payload": {
                "documentVersionId": "44444444-4444-4444-4444-444444444444",
                "callbackUrl": callback_url or self.callback_url(),
                "callbackSecret": "s3cr3t",
            },
        }

    def test_valid_request_calls_back_with_honest_empty_result(self):
        status, body = post(self.worker_url("/extract"), self.valid_envelope())

        self.assertEqual(202, status)
        self.assertEqual("ACCEPTED", body["status"])

        self.assertEqual(1, len(CapturingCallbackHandler.received))
        call = CapturingCallbackHandler.received[0]
        self.assertEqual("s3cr3t", call["secret_header"])
        self.assertEqual({"extractedFields": [], "confidence": {}}, call["body"])

    def test_missing_payload_field_returns_400(self):
        envelope = self.valid_envelope()
        del envelope["payload"]["documentVersionId"]

        status, body = post(self.worker_url("/extract"), envelope)

        self.assertEqual(400, status)
        self.assertEqual("INVALID_REQUEST", body["error"])
        self.assertEqual(0, len(CapturingCallbackHandler.received))

    def test_unsupported_event_type_returns_400(self):
        envelope = self.valid_envelope()
        envelope["eventType"] = "something.else"

        status, body = post(self.worker_url("/extract"), envelope)

        self.assertEqual(400, status)
        self.assertEqual("INVALID_REQUEST", body["error"])

    def test_invalid_json_returns_400(self):
        request = urllib.request.Request(
            self.worker_url("/extract"),
            data=b"not json",
            method="POST",
            headers={"Content-Type": "application/json"},
        )
        try:
            urllib.request.urlopen(request, timeout=5)
            self.fail("expected HTTPError")
        except urllib.error.HTTPError as exc:
            self.assertEqual(400, exc.code)

    def test_unknown_path_returns_404(self):
        status, body = post(self.worker_url("/not-extract"), self.valid_envelope())

        self.assertEqual(404, status)
        self.assertEqual("NOT_FOUND", body["error"])

    def test_unreachable_callback_returns_502(self):
        status, body = post(
            self.worker_url("/extract"),
            self.valid_envelope(callback_url="http://127.0.0.1:1/callback"),
        )

        self.assertEqual(502, status)
        self.assertEqual("CALLBACK_FAILED", body["error"])


class DocumentServingHandler(BaseHTTPRequestHandler):
    """Stands in for a presigned MinIO download URL."""

    body = b"payslip content"

    def log_message(self, format, *args):
        pass

    def do_GET(self):
        data = DocumentServingHandler.body
        self.send_response(200)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class FakeAnthropicHandler(BaseHTTPRequestHandler):
    """Stands in for https://api.anthropic.com/v1/messages."""

    response_text = json.dumps(
        {
            "summary": "Payslip for Javier Ruiz, March 2026.",
            "fields": [
                {"name": "monthly_income", "value": "1900", "confidence": 0.9, "page": 1}
            ],
            "warnings": [],
        }
    )
    status_code = 200
    received_requests = []

    def log_message(self, format, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) if length else b"{}")
        FakeAnthropicHandler.received_requests.append(
            {"headers": dict(self.headers), "body": body}
        )
        if FakeAnthropicHandler.status_code != 200:
            self.send_response(FakeAnthropicHandler.status_code)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        response_body = json.dumps(
            {"content": [{"type": "text", "text": FakeAnthropicHandler.response_text}]}
        ).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_body)))
        self.end_headers()
        self.wfile.write(response_body)


class RealProviderExtractionTests(unittest.TestCase):
    """Sprint 33: the worker's real Anthropic call, exercised against fake HTTP servers — never
    the real api.anthropic.com, and never a real API key."""

    def setUp(self):
        self.worker_server, self.worker_thread = start_server(worker.Handler)
        self.worker_port = self.worker_server.server_address[1]

        CapturingCallbackHandler.received = []
        self.callback_server, self.callback_thread = start_server(CapturingCallbackHandler)
        self.callback_port = self.callback_server.server_address[1]

        FakeAnthropicHandler.received_requests = []
        FakeAnthropicHandler.status_code = 200
        FakeAnthropicHandler.response_text = json.dumps(
            {
                "summary": "Payslip for Javier Ruiz, March 2026.",
                "fields": [
                    {"name": "monthly_income", "value": "1900", "confidence": 0.9, "page": 1}
                ],
                "warnings": [],
            }
        )
        self.anthropic_server, self.anthropic_thread = start_server(FakeAnthropicHandler)
        self.anthropic_port = self.anthropic_server.server_address[1]

        DocumentServingHandler.body = b"payslip content"
        self.document_server, self.document_thread = start_server(DocumentServingHandler)
        self.document_port = self.document_server.server_address[1]

        self.env_patch = unittest.mock.patch.dict(
            os.environ,
            {
                "ANTHROPIC_API_KEY": "fake-test-key-never-real",
                "ANTHROPIC_API_BASE_URL": f"http://127.0.0.1:{self.anthropic_port}",
            },
        )
        self.env_patch.start()

    def tearDown(self):
        self.env_patch.stop()
        for server in (self.worker_server, self.callback_server, self.anthropic_server, self.document_server):
            server.shutdown()
            server.server_close()

    def worker_url(self, path):
        return f"http://127.0.0.1:{self.worker_port}{path}"

    def document_url(self):
        return f"http://127.0.0.1:{self.document_port}/doc.pdf"

    def callback_url(self):
        return f"http://127.0.0.1:{self.callback_port}/internal/ai/document-extractions/abc/callback"

    def envelope(self, mime_type="application/pdf", download_url=None):
        return {
            "eventId": "11111111-1111-1111-1111-111111111111",
            "eventType": "ai.document.analysis.requested",
            "occurredAt": "2026-08-17T00:00:00Z",
            "companyId": "22222222-2222-2222-2222-222222222222",
            "aggregateType": "DOCUMENT_EXTRACTION",
            "aggregateId": "33333333-3333-3333-3333-333333333333",
            "payload": {
                "documentVersionId": "44444444-4444-4444-4444-444444444444",
                "callbackUrl": self.callback_url(),
                "callbackSecret": "s3cr3t",
                "documentDownloadUrl": (
                    download_url if download_url is not None else self.document_url()
                ),
                "documentFilename": "payslip.pdf",
                "documentMimeType": mime_type,
            },
        }

    def test_real_call_reports_extracted_fields_provider_and_model(self):
        status, _ = post(self.worker_url("/extract"), self.envelope())

        self.assertEqual(202, status)
        self.assertEqual(1, len(CapturingCallbackHandler.received))
        body = CapturingCallbackHandler.received[0]["body"]
        self.assertEqual("anthropic", body["provider"])
        self.assertTrue(body["model"])
        self.assertEqual(
            [{"name": "monthly_income", "value": "1900", "confidence": 0.9, "page": 1}],
            body["extractedFields"],
        )
        self.assertIn("Javier Ruiz", body["summary"])
        self.assertEqual([], body["warnings"])

        # The worker actually sent the document bytes to "Anthropic" as a base64 document block.
        sent = FakeAnthropicHandler.received_requests[0]["body"]
        content_blocks = sent["messages"][0]["content"]
        document_block = next(b for b in content_blocks if b["type"] == "document")
        self.assertEqual(
            base64.b64encode(b"payslip content").decode("ascii"), document_block["source"]["data"]
        )
        # The real API key must never be logged/echoed anywhere reachable — not in the request
        # sent onward besides the x-api-key header, and never in the callback body.
        self.assertNotIn("fake-test-key-never-real", json.dumps(body))

    def test_unsupported_mime_type_is_an_honest_failure_not_a_guess(self):
        status, _ = post(self.worker_url("/extract"), self.envelope(mime_type="application/zip"))

        self.assertEqual(202, status)
        body = CapturingCallbackHandler.received[0]["body"]
        self.assertEqual([], body["extractedFields"])
        self.assertEqual("anthropic", body["provider"])  # attempted, so not NO_PROVIDER
        self.assertTrue(any("Unsupported" in w for w in body["warnings"]))

    def test_unreachable_document_url_is_reported_as_a_warning_not_a_crash(self):
        status, _ = post(
            self.worker_url("/extract"), self.envelope(download_url="http://127.0.0.1:1/doc.pdf")
        )

        self.assertEqual(202, status)
        body = CapturingCallbackHandler.received[0]["body"]
        self.assertEqual([], body["extractedFields"])
        self.assertEqual("anthropic", body["provider"])
        self.assertTrue(any("fetch" in w.lower() for w in body["warnings"]))

    def test_provider_error_response_is_reported_as_a_warning_not_a_crash(self):
        FakeAnthropicHandler.status_code = 500

        status, _ = post(self.worker_url("/extract"), self.envelope())

        self.assertEqual(202, status)
        body = CapturingCallbackHandler.received[0]["body"]
        self.assertEqual([], body["extractedFields"])
        self.assertEqual("anthropic", body["provider"])
        self.assertTrue(body["warnings"])

    def test_non_json_provider_response_is_reported_as_a_warning_not_a_crash(self):
        FakeAnthropicHandler.response_text = "Sure, here are the fields you asked for: none really."

        status, _ = post(self.worker_url("/extract"), self.envelope())

        self.assertEqual(202, status)
        body = CapturingCallbackHandler.received[0]["body"]
        self.assertEqual([], body["extractedFields"])
        self.assertTrue(any("JSON" in w or "valid" in w for w in body["warnings"]))

    def test_no_api_key_still_behaves_exactly_like_the_no_provider_path(self):
        with unittest.mock.patch.dict(os.environ, {"ANTHROPIC_API_KEY": ""}):
            status, _ = post(self.worker_url("/extract"), self.envelope())

        self.assertEqual(202, status)
        body = CapturingCallbackHandler.received[0]["body"]
        self.assertEqual({"extractedFields": [], "confidence": {}}, body)


class WorkerIsolationTests(unittest.TestCase):
    """D10-4: the worker must be stateless and have no PostgreSQL access or credentials."""

    def test_no_database_driver_or_credential_imports(self):
        source_path = os.path.join(os.path.dirname(__file__), "..", "main.py")
        with open(source_path, "r", encoding="utf-8") as handle:
            source = handle.read()

        forbidden_tokens = [
            "psycopg2",
            "sqlalchemy",
            "jdbc",
            "POSTGRES",
            "DATABASE_URL",
            "import socket",
        ]
        for token in forbidden_tokens:
            self.assertNotIn(token, source, f"main.py must not reference {token!r}")


if __name__ == "__main__":
    unittest.main()
