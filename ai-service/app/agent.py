import json
from typing import Any
from .providers import provider
from .rag import search_policy

async def stream_answer(ticket_id: int, message: str, context: dict[str, Any] | None = None):
    try:
        citations = await search_policy(message, {"ticketId": ticket_id} if context and context.get("ticketId") else {}, top_k=3)
    except Exception:
        citations = []
    prompt = json.dumps({"ticketId": ticket_id, "message": message, "evidence": citations}, ensure_ascii=False)
    result = await provider().structured(prompt)
    text = result.get("replySuggestion", "当前资料不足，已转人工客服。")
    if not citations: text = "当前资料不足，已转人工客服。"
    yield {"event": "meta", "data": {"ticketId": ticket_id, "citations": citations}}
    for token in [text[i:i+20] for i in range(0, len(text), 20)]: yield {"event": "token", "data": {"text": token}}
    yield {"event": "done", "data": {"citations": citations}}
