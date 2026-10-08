import hashlib
import hmac
import json
import logging
import time

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import settings
from app.consumer import process_envelope
from app.main import app
from app.model_calls import measured_call
from app.observability import JsonFormatter, trace_id
from app.security import sign


def headers(body, path, method="POST", trace="trace-day23"):
    timestamp = str(int(time.time() * 1000)); nonce = f"day23-{time.time_ns()}"
    return {"X-Internal-Service": "aftersales-server", "X-Internal-Timestamp": timestamp,
            "X-Internal-Nonce": nonce, "X-Internal-Signature": sign(timestamp, nonce, method, path, body), "X-Trace-Id": trace}


def test_metrics_are_signed_and_use_route_templates():
    client = TestClient(app)
    assert client.get("/internal/v1/metrics").status_code == 401
    client.get("/health/live", headers={"X-Trace-Id": "x" * 100})
    response = client.get("/internal/v1/metrics", headers=headers(b"", "/internal/v1/metrics", "GET"))
    assert response.status_code == 200
    assert "aftersales_http_requests_total" in response.text
    assert 'route="/health/live"' in response.text
    assert "trace-day23" not in response.text
    assert response.headers["x-trace-id"] == "trace-day23"


def test_json_logs_include_context_without_request_content():
    record = logging.LogRecord("aftersales", logging.INFO, __file__, 1, "http_request", (), None)
    record.fields = {"route": "/health/live", "status": 200}
    token = trace_id.set("trace-json")
    try:
        value = json.loads(JsonFormatter().format(record))
        assert value["traceId"] == "trace-json"
        assert value["route"] == "/health/live"
        assert "body" not in value
    finally:
        trace_id.reset(token)


@pytest.mark.asyncio
async def test_fake_usage_is_free_without_fabricated_token_counts():
    result, usage, error = await measured_call("private customer text", "TICKET_ANALYSIS", "test-v1")
    assert result and error is None
    assert usage["inputTokens"] is None and usage["outputTokens"] is None
    assert usage["estimatedCostMicros"] == 0
    assert usage["latencyMs"] >= 0


@pytest.mark.asyncio
async def test_provider_failure_has_unknown_cost_and_no_sensitive_error(monkeypatch):
    class FailingProvider:
        async def structured(self, prompt):
            raise RuntimeError("secret api-key content")
    monkeypatch.setattr("app.model_calls.provider", lambda: FailingProvider())
    result, usage, error = await measured_call("private prompt", "TICKET_ANALYSIS", "test-v1")
    assert result is None and error == "AI_PROVIDER_FAILED"
    assert usage["estimatedCostMicros"] is None
    assert "secret" not in json.dumps(usage)


@pytest.mark.asyncio
async def test_consumer_preserves_trace_and_legacy_call_id():
    def handler(request):
        body = json.loads(request.content)
        assert body["callId"] == "legacy-1"
        assert request.headers["x-trace-id"] == "trace-mq"
        canonical = f"{request.headers['x-internal-timestamp']}\n{request.headers['x-internal-nonce']}\nPOST\n{request.url.path}\n{hashlib.sha256(request.content).hexdigest()}"
        expected = hmac.new(settings.ai_internal_secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
        assert request.headers["x-internal-signature"] == expected
        return httpx.Response(200, json={"accepted": True})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        result = await process_envelope({"schemaVersion": 1, "eventType": "ticket.ai.analyze.requested.v1", "traceId": "trace-mq",
            "data": {"taskId": 1, "ticketId": 2, "ticketVersion": 1}}, client)
    assert result["usage"]["inputTokens"] is None
    assert trace_id.get() == ""
