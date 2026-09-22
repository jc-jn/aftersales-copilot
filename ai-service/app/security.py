import hashlib, hmac, time
from collections import OrderedDict
from fastapi import Header, HTTPException, Request
from .config import settings

_seen_nonces: OrderedDict[str, float] = OrderedDict()

def sign(timestamp: str, nonce: str, method: str, path: str, body: bytes) -> str:
    canonical = f"{timestamp}\n{nonce}\n{method.upper()}\n{path}\n{hashlib.sha256(body).hexdigest()}"
    return hmac.new(settings.ai_internal_secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()

async def verify_internal(request: Request, x_internal_service: str|None=Header(default=None), x_internal_timestamp: str|None=Header(default=None), x_internal_nonce: str|None=Header(default=None), x_internal_signature: str|None=Header(default=None)) -> bytes:
    body = await request.body()
    if request.url.path == "/internal/v1/health/ready": return body
    try: timestamp=int(x_internal_timestamp or "0")
    except ValueError: timestamp=0
    now = time.time()
    for nonce, seen_at in list(_seen_nonces.items()):
        if now - seen_at > 300: _seen_nonces.pop(nonce, None)
    replay = bool(x_internal_nonce) and x_internal_nonce in _seen_nonces
    valid = x_internal_service == "aftersales-server" and abs(int(time.time()*1000)-timestamp)<=300000 and bool(x_internal_nonce) and not replay and bool(x_internal_signature) and hmac.compare_digest(sign(str(timestamp),x_internal_nonce or "",request.method,request.url.path,body),x_internal_signature or "")
    if not valid: raise HTTPException(status_code=401, detail="invalid internal signature")
    _seen_nonces[x_internal_nonce] = now
    return body
