import json
import os
import sys
import threading
import unittest
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
