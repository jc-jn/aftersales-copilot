from typing import Any, Protocol
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

def provider() -> ChatProvider: return FakeProvider()

def embedding_provider(dimension: int = 8) -> EmbeddingProvider: return FakeEmbeddingProvider(dimension)
