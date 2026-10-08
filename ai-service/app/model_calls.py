import time

from .observability import ai_calls, ai_duration, log
from .providers import FakeProvider, provider
from .safety import guard_analysis


async def measured_call(prompt, operation, prompt_version):
    model = provider()
    fake = isinstance(model, FakeProvider)
    provider_name = "fake" if fake else getattr(model, "provider_name", "unknown")
    model_name = "fake-v1" if fake else getattr(model, "model_name", "unknown")
    start = time.perf_counter()
    status = "SUCCEEDED"
    result = None
    error_code = None
    try:
        result = await model.structured(prompt)
        if operation == "TICKET_ANALYSIS":
            result = guard_analysis(result, prompt)
    except Exception:
        status = "FAILED"
        result = None
        error_code = "AI_PROVIDER_FAILED"
    elapsed = time.perf_counter() - start
    raw_usage = getattr(model, "usage", {})
    def valid_tokens(name):
        value = raw_usage.get(name)
        return value if type(value) is int and value >= 0 else None
    usage = {"provider": provider_name, "model": model_name, "promptVersion": prompt_version,
             "inputTokens": valid_tokens("prompt_tokens"), "outputTokens": valid_tokens("completion_tokens"), "estimatedCostMicros": 0 if fake else None,
             "latencyMs": round(elapsed * 1000)}
    ai_calls.labels(operation, provider_name, status).inc()
    ai_duration.labels(operation, provider_name).observe(elapsed)
    log.info("ai_call_completed", extra={"fields": {"operation": operation, "provider": provider_name, "status": status, "latencyMs": usage["latencyMs"]}})
    return result, usage, error_code
