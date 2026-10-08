import json

import httpx
import pytest

from app.config import settings
from app.providers import OpenAIChatProvider, OpenAIEmbeddingProvider
from app.model_calls import measured_call
from app.safety import prompt_for


@pytest.mark.asyncio
async def test_deepseek_roles_json_mode_token_cap_and_usage(monkeypatch):
    monkeypatch.setattr(settings, "llm_api_key", "test-chat-key")
    def handler(request):
        body = json.loads(request.content)
        assert request.url == "https://api.deepseek.com/chat/completions"
        assert request.headers["authorization"] == "Bearer test-chat-key"
        assert body["model"] == "deepseek-v4-pro"
        assert body["max_tokens"] == 512
        assert body["thinking"] == {"type": "disabled"}
        assert body["response_format"] == {"type": "json_object"}
        assert "tools" not in body
        assert body["messages"][0]["role"] == "system"
        assert body["messages"][1]["role"] == "user"
        return httpx.Response(200, json={"choices": [{"finish_reason": "stop", "message": {
            "content": '{"replySuggestion":"请联系人工客服","citationIds":[],"needsHuman":true}'}}],
            "usage": {"prompt_tokens": 101, "completion_tokens": 25}})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        model = OpenAIChatProvider(client)
        monkeypatch.setattr("app.model_calls.provider", lambda: model)
        result, usage, error = await measured_call(prompt_for("chat_v2", {"message": "政策"}), "CHAT", "chat-v2")
    assert error is None and result["needsHuman"]
    assert usage["provider"] == "deepseek" and usage["model"] == "deepseek-v4-pro"
    assert usage["inputTokens"] == 101 and usage["outputTokens"] == 25
    assert usage["estimatedCostMicros"] is None


@pytest.mark.asyncio
@pytest.mark.parametrize("content,reason", [('not json', 'stop'), ('[]', 'stop'), ('{}', 'length')])
async def test_invalid_output_does_not_retry_or_expose_secrets(monkeypatch, content, reason):
    monkeypatch.setattr(settings, "llm_api_key", "private-test-key")
    requests = []
    def handler(request):
        requests.append(request)
        return httpx.Response(200, json={"choices": [{"finish_reason": reason, "message": {"content": content}}]})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        monkeypatch.setattr("app.model_calls.provider", lambda: OpenAIChatProvider(client))
        result, usage, error = await measured_call("private prompt", "CHAT", "chat-v2")
    assert result is None and error == "AI_PROVIDER_FAILED" and len(requests) == 1
    assert "private" not in json.dumps(usage)


@pytest.mark.asyncio
async def test_siliconflow_embedding_order_and_dimension(monkeypatch):
    monkeypatch.setattr(settings, "embedding_api_key", "test-embedding-key")
    def handler(request):
        assert request.url == "https://api.siliconflow.cn/v1/embeddings"
        assert request.headers["authorization"] == "Bearer test-embedding-key"
        assert json.loads(request.content)["model"] == "BAAI/bge-m3"
        return httpx.Response(200, json={"data": [
            {"index": 1, "embedding": [0.0, 1.0]}, {"index": 0, "embedding": [1.0, 0.0]}],
            "usage": {"prompt_tokens": 10}})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        provider = OpenAIEmbeddingProvider(2, client)
        assert await provider.embed_documents(["甲", "乙"]) == [[1.0, 0.0], [0.0, 1.0]]
        assert provider.input_tokens == 10


@pytest.mark.asyncio
@pytest.mark.parametrize("data", [[{"index": 0, "embedding": [1]}],
    [{"index": 1, "embedding": [1, 2]}], [{"index": 0, "embedding": [0, 0]}]])
async def test_embedding_rejects_wrong_dimension_missing_index_and_zero_vector(monkeypatch, data):
    monkeypatch.setattr(settings, "embedding_api_key", "test-key")
    async with httpx.AsyncClient(transport=httpx.MockTransport(
            lambda request: httpx.Response(200, json={"data": data}))) as client:
        with pytest.raises(ValueError):
            await OpenAIEmbeddingProvider(2, client).embed_documents(["甲"])
