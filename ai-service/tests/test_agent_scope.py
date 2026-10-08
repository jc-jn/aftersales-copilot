import pytest

from app.agent import stream_answer


@pytest.mark.asyncio
async def test_chat_cannot_expand_document_scope_with_client_context(monkeypatch):
    async def search(query, filters, top_k):
        assert filters == {"scopeType": "GLOBAL"}
        return []

    monkeypatch.setattr("app.agent.search_policy", search)
    events = [event async for event in stream_answer(1, "policy", {"scopeType": "PRODUCT", "scopeId": 999})]
    assert events[-1]["event"] == "done"
