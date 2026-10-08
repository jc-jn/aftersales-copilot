from typing import Any
from .rag import search_policy
from .model_calls import measured_call
from .callbacks import post_callback
from .observability import log
from .safety import NO_EVIDENCE, UNSAFE_INPUT, injection_risk, prompt_for


def grounded_reply(result, citations, unsafe=False):
    if unsafe or injection_risk(result):
        return UNSAFE_INPUT, [], True, ["PROMPT_INJECTION"]
    ids = result.get("citationIds", []) if isinstance(result, dict) else []
    known = {c["chunkId"]: c for c in citations}
    if not citations or not isinstance(ids, list) or not ids or any(
            not isinstance(i, str) or i not in known for i in ids):
        return NO_EVIDENCE, [], True, ["KNOWLEDGE_NO_EVIDENCE"]
    if result.get("needsHuman") is not False or not isinstance(result.get("replySuggestion"), str):
        return NO_EVIDENCE, [], True, ["KNOWLEDGE_NO_EVIDENCE"]
    return result["replySuggestion"], [known[i] for i in dict.fromkeys(ids)], False, []

async def stream_answer(ticket_id: int, message: str, context: dict[str, Any] | None = None, call_id: str | None = None):
    unsafe = injection_risk(message)
    citations = []
    if not unsafe:
        try:
            citations = await search_policy(message, {"scopeType": "GLOBAL"}, top_k=3)
        except Exception:
            pass
    unsafe = unsafe or injection_risk(citations)
    if unsafe:
        citations = []
    prompt = prompt_for("chat_v3", {"ticketId": ticket_id,
                        "message": "请转人工" if unsafe else message, "evidence": citations})
    result, usage, error = await measured_call(prompt, "CHAT", "chat-v3")
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
    text, citations, needs_human, flags = grounded_reply(result, citations, unsafe)
    yield {"event": "meta", "data": {"ticketId": ticket_id, "citations": citations}}
    for token in [text[i:i+20] for i in range(0, len(text), 20)]: yield {"event": "token", "data": {"text": token}}
    yield {"event": "done", "data": {"citations": citations, "usage": usage, "needsHuman": needs_human, "riskFlags": flags}}
