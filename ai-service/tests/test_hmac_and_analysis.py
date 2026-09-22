import hashlib, hmac, json, time
from fastapi.testclient import TestClient
from app.main import app
from app.config import settings

def headers(body: bytes, path: str, nonce: str | None = None):
    ts=str(int(time.time()*1000)); nonce=nonce or f"n-{time.time_ns()}"; canonical=f"{ts}\n{nonce}\nPOST\n{path}\n{hashlib.sha256(body).hexdigest()}"; sig=hmac.new(settings.ai_internal_secret.encode(),canonical.encode(),hashlib.sha256).hexdigest()
    return {"X-Internal-Service":"aftersales-server","X-Internal-Timestamp":ts,"X-Internal-Nonce":nonce,"X-Internal-Signature":sig}

def test_analysis_fake_provider():
    body=json.dumps({"taskId":1,"ticket":{"id":2,"version":1,"description":"耳机右侧无声"},"orderContext":{},"allowedTools":[]}).encode()
    response=TestClient(app).post("/internal/v1/tickets/analyze",content=body,headers=headers(body,"/internal/v1/tickets/analyze"))
    assert response.status_code==200; assert response.json()["status"]=="SUCCEEDED"

def test_analysis_rejects_bad_signature():
    response=TestClient(app).post("/internal/v1/tickets/analyze",content=b"{}",headers={"X-Internal-Service":"aftersales-server"})
    assert response.status_code==401

def test_analysis_rejects_replayed_nonce():
    body=json.dumps({"taskId":2,"ticket":{"id":3,"version":1,"description":"退款"},"orderContext":{},"allowedTools":[]}).encode()
    request_headers=headers(body,"/internal/v1/tickets/analyze")
    client=TestClient(app)
    assert client.post("/internal/v1/tickets/analyze",content=body,headers=request_headers).status_code==200
    assert client.post("/internal/v1/tickets/analyze",content=body,headers=request_headers).status_code==401
