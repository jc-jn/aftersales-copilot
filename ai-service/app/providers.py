from typing import Any, Protocol
import json
import math
import httpx
from .config import settings
from .schemas import TicketAnalysisResult

class ChatProvider(Protocol):
    async def structured(self, prompt: str, schema: dict[str, Any] | None = None) -> dict[str, Any]: ...

class EmbeddingProvider(Protocol):
    async def embed_documents(self, texts: list[str]) -> list[list[float]]: ...
    async def embed_query(self, text: str) -> list[float]: ...

class FakeProvider:
    async def structured(self, prompt: str, schema: dict[str, Any] | None = None) -> dict[str, Any]:
        intent = "RETURN_REFUND" if "退货" in prompt else "EXCHANGE" if "换" in prompt else "REPAIR" if "维修" in prompt else "REFUND_ONLY"
        result = {"intent":intent,"confidence":0.72,"prioritySuggestion":"MEDIUM","sentiment":"NEUTRAL","extracted":{"issue":"UNKNOWN","attemptedActions":[]},"missingFields":[],"needsHuman":False,"riskFlags":[],"replySuggestion":"您好，我们已收到您的售后问题，将由客服继续处理。","proposalSuggestion":None,"citations":[]}
        return TicketAnalysisResult.model_validate(result).model_dump(by_alias=True)

class FakeEmbeddingProvider:
    def __init__(self, dimension: int = 8): self.dimension = dimension
    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self._embed(t) for t in texts]
    async def embed_query(self, text: str) -> list[float]: return self._embed(text)
    def _embed(self, text: str) -> list[float]:
        import hashlib
        digest = hashlib.sha256(text.encode("utf-8")).digest()
        return [((digest[i % len(digest)] / 255.0) * 2) - 1 for i in range(self.dimension)]

class OpenAIChatProvider:
    provider_name = "deepseek"

    def __init__(self, client: httpx.AsyncClient | None = None):
        self.client = client
        self.model_name = settings.llm_chat_model
        self.usage: dict[str, Any] = {}

    async def structured(self, prompt: str, schema: dict[str, Any] | None = None) -> dict[str, Any]:
        if not settings.llm_api_key:
            raise ValueError("LLM_API_KEY_REQUIRED")
        # Instructions and data remain separate API roles; no model tools are exposed.
        parts = prompt.split("\n<untrusted_data>\n", 1)
        messages = [{"role": "system", "content": parts[0]}]
        if len(parts) == 2:
            messages.append({"role": "user", "content": parts[1]})
        body = {"model": self.model_name, "messages": messages, "stream": False,
                "response_format": {"type": "json_object"}, "temperature": 0,
                "max_tokens": settings.llm_max_tokens, "thinking": {"type": "disabled"}}
        own = self.client is None
        client = self.client or httpx.AsyncClient(timeout=90)
        try:
            response = await client.post(settings.llm_base_url.rstrip("/") + "/chat/completions",
                                         headers={"Authorization": "Bearer " + settings.llm_api_key}, json=body)
            response.raise_for_status()
            payload = response.json()
            self.usage = payload.get("usage") or {}
            choice = payload["choices"][0]
            if choice.get("finish_reason") != "stop":
                raise ValueError("AI_OUTPUT_INCOMPLETE")
            result = json.loads(choice["message"]["content"])
            if not isinstance(result, dict):
                raise ValueError("AI_OUTPUT_INVALID")
            return result
        finally:
            if own:
                await client.aclose()


class OpenAIEmbeddingProvider:
    def __init__(self, dimension: int, client: httpx.AsyncClient | None = None):
        self.dimension = dimension
        self.client = client
        self.input_tokens: int | None = None

    async def embed_documents(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        if not settings.embedding_api_key:
            raise ValueError("EMBEDDING_API_KEY_REQUIRED")
        own = self.client is None
        client = self.client or httpx.AsyncClient(timeout=60)
        try:
            response = await client.post(settings.embedding_base_url.rstrip("/") + "/embeddings",
                headers={"Authorization": "Bearer " + settings.embedding_api_key},
                json={"model": settings.embedding_model, "input": texts, "encoding_format": "float"})
            response.raise_for_status()
            payload = response.json()
            self.input_tokens = (payload.get("usage") or {}).get("prompt_tokens")
            data = sorted(payload["data"], key=lambda item: item["index"])
            if [item["index"] for item in data] != list(range(len(texts))):
                raise ValueError("EMBEDDING_COUNT_MISMATCH")
            vectors = [item["embedding"] for item in data]
            if any(len(v) != self.dimension or any(not isinstance(x, (int, float))
                    or not math.isfinite(x) for x in v) or not any(v) for v in vectors):
                raise ValueError("EMBEDDING_DIMENSION_OR_VALUE_MISMATCH")
            return vectors
        finally:
            if own:
                await client.aclose()

    async def embed_query(self, text: str) -> list[float]:
        return (await self.embed_documents([text]))[0]


def provider() -> ChatProvider:
    return FakeProvider() if settings.llm_provider == "fake" else OpenAIChatProvider()

def embedding_provider(dimension: int = 8) -> EmbeddingProvider:
    return (FakeEmbeddingProvider(dimension) if settings.embedding_provider == "fake"
            else OpenAIEmbeddingProvider(dimension))
