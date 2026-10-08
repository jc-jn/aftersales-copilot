import json
from typing import Any
from .rag import search_policy
from .model_calls import measured_call
from .callbacks import post_callback
from .observability import log

async def stream_answer(ticket_id: int, message: str, context: dict[str, Any] | None = None, call_id: str | None = None):
    try:
        citations = await search_policy(message, {"scopeType": "GLOBAL"}, top_k=3)
    except Exception:
        citations = []
    prompt = json.dumps({"ticketId": ticket_id, "message": message, "evidence": citations}, ensure_ascii=False)
    result, usage, error = await measured_call(prompt, "CHAT", "chat-v1")
    if call_id:
        try:
            await post_callback("/internal/v1/ai-results/chat-usage", {"callId": call_id, "ticketId": ticket_id,
                "status": "FAILED" if error else "SUCCEEDED", "usage": usage, "errorCode": error})
        except Exception:
            log.error("ai_usage_callback_failed", extra={"fields": {"callId": call_id, "errorCode": "AI_USAGE_CALLBACK_FAILED"}})
            yield {"event": "error", "data": {"code": "AI_USAGE_CALLBACK_FAILED", "retryable": True}}
            return
    if error:
        yield {"event": "error", "data": {"code": error, "retryable": True}}
        return
    text = result.get("replySuggestion", "当前资料不足，已转人工客服。")
    if not citations: text = "当前资料不足，已转人工客服。"
    yield {"event": "meta", "data": {"ticketId": ticket_id, "citations": citations}}
    for token in [text[i:i+20] for i in range(0, len(text), 20)]: yield {"event": "token", "data": {"text": token}}
    yield {"event": "done", "data": {"citations": citations, "usage": usage}}
