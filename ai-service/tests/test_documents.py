import base64, hashlib, json
import httpx
import pytest
from app.documents import chunk_document, parse_document
from app.indexer import index_document

def test_markdown_chunking():
    chunks=chunk_document("# 退货政策\n\n"+"质量问题可退货。"*100)
    assert chunks and chunks[0].section_title=="退货政策"

@pytest.mark.asyncio
async def test_index_document_with_fake_qdrant():
    class MockTransport(httpx.AsyncBaseTransport):
        async def handle_async_request(self, request):
            if request.method=="GET": return httpx.Response(404)
            return httpx.Response(200,json={"result":True})
    raw="# FAQ\n耳机无声请重新配对。".encode(); payload={"taskId":1,"documentId":2,"indexVersion":1,"fileName":"faq.md","contentType":"text/markdown","contentSha256":hashlib.sha256(raw).hexdigest(),"contentBase64":base64.b64encode(raw).decode(),"metadata":{"documentType":"FAQ"}}
    async with httpx.AsyncClient(transport=MockTransport()) as client:
        result=await index_document(payload,client)
    assert result["status"]=="SUCCEEDED" and result["chunkCount"]==1
