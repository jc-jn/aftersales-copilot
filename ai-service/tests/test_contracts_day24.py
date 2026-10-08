import copy
import json
from pathlib import Path

import httpx
import pytest
from jsonschema import Draft202012Validator

from app.consumer import process_envelope, process_document_envelope
from app.schemas import TicketAnalysisResult
from app.agent import stream_answer

CONTRACTS = Path(__file__).resolve().parents[2] / "contracts"


def load(name):
    return json.loads((CONTRACTS / name).read_text(encoding="utf-8"))


@pytest.mark.parametrize("name", ["ticket-analysis-request", "ticket-analysis-callback", "chat-sse"])
def test_shared_fixtures_match_schemas(name):
    schema = load(name + ".schema.json")
    Draft202012Validator.check_schema(schema)
    Draft202012Validator(schema).validate(load(name + ".json"))


def test_callback_matches_application_schema():
    fixture = load("ticket-analysis-callback.json")
    TicketAnalysisResult.model_validate(fixture["result"])
    assert fixture["taskId"] == 9007199254740993


def test_callback_schema_rejects_invalid_application_output():
    fixture = load("ticket-analysis-callback.json")
    fixture["result"]["confidence"] = 1.1
    assert not Draft202012Validator(load("ticket-analysis-callback.schema.json")).is_valid(fixture)


@pytest.mark.asyncio
async def test_actual_python_sse_matches_shared_schema(monkeypatch):
    async def search(*args, **kwargs):
        return []
    monkeypatch.setattr("app.agent.search_policy", search)
    events = [event async for event in stream_answer(60001, "政策")]
    Draft202012Validator(load("chat-sse.schema.json")).validate(events)
    assert [event["event"] for event in events][0] == "meta"
    assert events[-1]["event"] == "done" and events[-1]["data"]["needsHuman"]


@pytest.mark.asyncio
async def test_java_fixture_consumed_and_callback_matches_contract():
    envelope = load("ticket-analysis-request.json")
    def handler(request):
        assert request.url.path == "/internal/v1/ai-results/ticket-analysis"
        callback = json.loads(request.content)
        Draft202012Validator(load("ticket-analysis-callback.schema.json")).validate(callback)
        assert callback["taskId"] == envelope["data"]["taskId"]
        assert callback["callId"] == envelope["data"]["callId"]
        assert request.headers["x-trace-id"] == envelope["traceId"]
        return httpx.Response(200, json={"accepted": True})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        await process_envelope(envelope, client)


@pytest.mark.asyncio
@pytest.mark.parametrize("version", [0, 2, "1", True, None])
async def test_unknown_schema_rejected_before_any_provider_or_callback(version, monkeypatch):
    async def forbidden(*args, **kwargs):
        pytest.fail("unsupported event reached external dependency")
    monkeypatch.setattr("app.consumer.measured_call", forbidden)
    monkeypatch.setattr("app.consumer.post_callback", forbidden)
    envelope = copy.deepcopy(load("ticket-analysis-request.json"))
    envelope["schemaVersion"] = version
    with pytest.raises(ValueError, match="unsupported event"):
        await process_envelope(envelope)
    envelope["eventType"] = "knowledge.document.index.requested.v1"
    with pytest.raises(ValueError, match="unsupported event"):
        await process_document_envelope(envelope)
