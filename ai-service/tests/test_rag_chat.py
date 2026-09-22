import hashlib, hmac, json, time
import httpx
from fastapi.testclient import TestClient
from app.main import app
from app.config import settings

def signed(body: bytes, path: str):
    ts=str(int(time.time()*1000)); nonce=str(time.time_ns()); canonical=f"{ts}\n{nonce}\nPOST\n{path}\n{hashlib.sha256(body).hexdigest()}"; sig=hmac.new(settings.ai_internal_secret.encode(),canonical.encode(),hashlib.sha256).hexdigest()
    return {"X-Internal-Service":"aftersales-server","X-Internal-Timestamp":ts,"X-Internal-Nonce":nonce,"X-Internal-Signature":sig}

def test_chat_stream_without_qdrant_returns_transport_error():
    body=json.dumps({"ticketId":1,"message":"退款政策是什么"}).encode()
    response=TestClient(app).post("/internal/v1/chat/stream",content=body,headers=signed(body,"/internal/v1/chat/stream"))
    assert response.status_code == 200
    assert "event: done" in response.text
