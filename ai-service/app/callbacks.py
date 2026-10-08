import hashlib
import hmac
import json
import time
import uuid

import httpx

from .config import settings
from .observability import trace_id, normalize_trace


async def post_callback(path, payload, client=None):
    body = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode()
    timestamp = str(int(time.time() * 1000))
    nonce = str(uuid.uuid4())
    canonical = f"{timestamp}\n{nonce}\nPOST\n{path}\n{hashlib.sha256(body).hexdigest()}"
    signature = hmac.new(settings.ai_internal_secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
    headers = {"Content-Type": "application/json", "X-Internal-Service": "ai-service", "X-Internal-Timestamp": timestamp,
               "X-Internal-Nonce": nonce, "X-Internal-Signature": signature, "X-Trace-Id": normalize_trace(trace_id.get())}
    if client is not None:
        response = await client.post(settings.java_internal_base_url + path, content=body, headers=headers)
        response.raise_for_status()
        return response
    async with httpx.AsyncClient(timeout=10) as http:
        response = await http.post(settings.java_internal_base_url + path, content=body, headers=headers)
        response.raise_for_status()
        return response
