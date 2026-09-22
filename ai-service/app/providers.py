from typing import Any, Protocol

class ChatProvider(Protocol):
    async def structured(self, prompt: str, schema: dict[str, Any] | None = None) -> dict[str, Any]: ...

class FakeProvider:
    async def structured(self, prompt: str, schema: dict[str, Any] | None = None) -> dict[str, Any]:
        intent = "RETURN_REFUND" if "退货" in prompt else "EXCHANGE" if "换" in prompt else "REPAIR" if "维修" in prompt else "REFUND_ONLY"
        return {"intent":intent,"confidence":0.72,"prioritySuggestion":"MEDIUM","sentiment":"NEUTRAL","extracted":{"issue":"UNKNOWN"},"missingFields":[],"needsHuman":False,"riskFlags":[],"replySuggestion":"您好，我们已收到您的售后问题，将由客服继续处理。","proposalSuggestion":None,"citations":[]}

def provider() -> ChatProvider: return FakeProvider()
