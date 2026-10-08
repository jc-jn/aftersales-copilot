import contextvars
import json
import logging
import re
import time
import uuid
from datetime import datetime, timezone

from prometheus_client import CollectorRegistry, Counter, Histogram, generate_latest

trace_id = contextvars.ContextVar("trace_id", default="")
registry = CollectorRegistry()
http_requests = Counter("aftersales_http_requests_total", "HTTP requests", ["method", "route", "status"], registry=registry)
http_duration = Histogram("aftersales_http_duration_seconds", "HTTP duration including stream lifetime", ["method", "route"], registry=registry)
ai_calls = Counter("aftersales_ai_calls_total", "Model invocations", ["operation", "provider", "status"], registry=registry)
ai_duration = Histogram("aftersales_ai_duration_seconds", "Model invocation latency", ["operation", "provider"], registry=registry)
log = logging.getLogger("aftersales")


def normalize_trace(value):
    return value if isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_-]{1,64}", value) else str(uuid.uuid4())


class JsonFormatter(logging.Formatter):
    def format(self, record):
        value = {"timestamp": datetime.now(timezone.utc).isoformat(), "level": record.levelname,
                 "service": "ai-service", "event": record.getMessage(), "traceId": trace_id.get()}
        value.update(getattr(record, "fields", {}))
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def configure_logging():
    if not log.handlers:
        handler = logging.StreamHandler()
        handler.setFormatter(JsonFormatter())
        log.addHandler(handler)
        log.setLevel(logging.INFO)
        log.propagate = False
    # Third-party request logs can contain query strings or signed object URLs.
    for name in ("httpx", "httpcore", "uvicorn.access"):
        logging.getLogger(name).disabled = True


configure_logging()


class ObservabilityMiddleware:
    def __init__(self, app):
        self.app = app

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        header = next((value.decode("ascii", errors="ignore") for key, value in scope.get("headers", []) if key == b"x-trace-id"), None)
        current = normalize_trace(header)
        token = trace_id.set(current)
        started = time.perf_counter()
        status = 500

        async def wrapped_send(message):
            nonlocal status
            if message["type"] == "http.response.start":
                status = message["status"]
                message["headers"] = [(key, value) for key, value in message.get("headers", []) if key != b"x-trace-id"]
                message["headers"].append((b"x-trace-id", current.encode()))
            await send(message)

        try:
            await self.app(scope, receive, wrapped_send)
        finally:
            route = getattr(scope.get("route"), "path", "UNMATCHED")
            method = scope["method"] if scope["method"] in {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"} else "OTHER"
            elapsed = time.perf_counter() - started
            http_requests.labels(method, route, str(status)).inc()
            http_duration.labels(method, route).observe(elapsed)
            log.info("http_request", extra={"fields": {"method": method, "route": route, "status": status, "durationMs": round(elapsed * 1000)}})
            trace_id.reset(token)


def metrics():
    return generate_latest(registry)
