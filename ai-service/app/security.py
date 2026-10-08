import hashlib
import hmac
import re
import threading
import time
from collections import OrderedDict

from fastapi import Header, HTTPException, Request
from redis import Redis
from redis.exceptions import RedisError
from starlette.concurrency import run_in_threadpool

from .config import settings

_seen_nonces: OrderedDict[str, float] = OrderedDict()
_nonce_lock = threading.Lock()
_redis = Redis.from_url(settings.redis_url, socket_connect_timeout=0.5, socket_timeout=0.5)


def sign(timestamp: str, nonce: str, method: str, path: str, body: bytes) -> str:
    canonical = f"{timestamp}\n{nonce}\n{method.upper()}\n{path}\n{hashlib.sha256(body).hexdigest()}"
    return hmac.new(settings.java_internal_secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()


def claim_nonce(nonce: str) -> bool:
    try:
        return bool(_redis.set(f"aftersales:nonce:aftersales-server:{nonce}", "1", nx=True, ex=300))
    except RedisError as exc:
        if settings.app_env != "local":
            raise HTTPException(status_code=503, detail="security state unavailable") from exc
        with _nonce_lock:
            now = time.time()
            while _seen_nonces and now - next(iter(_seen_nonces.values())) > 300:
                _seen_nonces.popitem(last=False)
            if nonce in _seen_nonces:
                return False
            if len(_seen_nonces) >= 10000:
                raise HTTPException(status_code=503, detail="security state capacity exceeded")
            _seen_nonces[nonce] = now
            return True


async def verify_internal(request: Request, x_internal_service: str | None = Header(default=None),
                          x_internal_timestamp: str | None = Header(default=None),
                          x_internal_nonce: str | None = Header(default=None),
                          x_internal_signature: str | None = Header(default=None)) -> bytes:
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > 1024 * 1024:
            raise HTTPException(status_code=413, detail="internal body too large")
    try:
        timestamp = int(x_internal_timestamp or "0")
    except ValueError:
        timestamp = 0
    valid = (x_internal_service == "aftersales-server"
             and abs(int(time.time() * 1000) - timestamp) <= 300000
             and bool(re.fullmatch(r"[A-Za-z0-9_-]{1,128}", x_internal_nonce or ""))
             and bool(re.fullmatch(r"[a-f0-9]{64}", x_internal_signature or "")))
    if not valid or not hmac.compare_digest(
            sign(str(timestamp), x_internal_nonce or "", request.method, request.url.path, bytes(body)),
            x_internal_signature or ""):
        raise HTTPException(status_code=401, detail="invalid internal signature")
    if not await run_in_threadpool(claim_nonce, x_internal_nonce):
        raise HTTPException(status_code=401, detail="invalid internal signature")
    return bytes(body)
