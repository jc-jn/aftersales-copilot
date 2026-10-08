import hashlib
import hmac
import json
import time

import httpx
import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.config import Settings, settings
from app.indexer import index_document
from app.main import app
from app.security import sign


def signed(body, path, secret=None):
    timestamp = str(int(time.time() * 1000))
    nonce = f"security-{time.time_ns()}"
    signature = sign(timestamp, nonce, "POST", path, body)
    if secret:
        canonical = f"{timestamp}\n{nonce}\nPOST\n{path}\n{hashlib.sha256(body).hexdigest()}"
        signature = hmac.new(secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
    return {"X-Internal-Service": "aftersales-server", "X-Internal-Timestamp": timestamp,
            "X-Internal-Nonce": nonce, "X-Internal-Signature": signature}


def test_python_rejects_ai_outbound_key_for_java_identity():
    path = "/internal/v1/tickets/analyze"
    body = b'{}'
    response = TestClient(app).post(path, content=body, headers=signed(body, path, settings.ai_internal_secret))
    assert response.status_code == 401


def test_invalid_signature_does_not_consume_valid_nonce():
    path = "/internal/v1/tickets/analyze"
    body = json.dumps({"taskId": 1, "ticket": {"id": 2, "version": 1}}).encode()
    headers = signed(body, path)
    client = TestClient(app)
    assert client.post(path, content=body, headers={**headers, "X-Internal-Signature": "0" * 64}).status_code == 401
    assert client.post(path, content=body, headers=headers).status_code == 200


def test_signed_malformed_json_is_validation_error():
    path = "/internal/v1/tickets/analyze"
    body = b'{bad json'
    assert TestClient(app).post(path, content=body, headers=signed(body, path)).status_code == 422


def test_internal_request_size_limit():
    path = "/internal/v1/tickets/analyze"
    body = b'x' * (1024 * 1024 + 1)
    assert TestClient(app).post(path, content=body, headers=signed(body, path)).status_code == 413


def test_production_requires_separate_non_default_secrets():
    with pytest.raises(ValidationError):
        Settings(_env_file=None, app_env="prod")
    with pytest.raises(ValidationError):
        Settings(_env_file=None, app_env="prod", ai_internal_secret="a" * 32, java_internal_secret="a" * 32)


@pytest.mark.asyncio
async def test_indexer_rejects_untrusted_download_host():
    async with httpx.AsyncClient() as client:
        with pytest.raises(ValueError, match="DOCUMENT_URL_NOT_ALLOWED"):
            await index_document({"objectUrl": "http://169.254.169.254/latest/meta-data/"}, client)
