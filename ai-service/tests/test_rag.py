import httpx
import pytest
from app.rag import search_policy

@pytest.mark.asyncio
async def test_search_policy_maps_citations_and_filters():
    class MockTransport(httpx.AsyncBaseTransport):
        async def handle_async_request(self, request):
            assert request.url.path.endswith("/points/search")
            body = request.content.decode()
            assert "POLICY" in body
            return httpx.Response(200, json={"result":[{"score":0.91,"payload":{"chunkId":"doc_1_v1_0000","documentId":"1","indexVersion":1,"title":"退货政策","sectionTitle":"质量问题","content":"质量问题支持退货"}}]})
    async with httpx.AsyncClient(transport=MockTransport()) as client:
        hits=await search_policy("耳机无声", {"documentType":"POLICY"}, client=client)
    assert hits[0]["chunkId"]=="doc_1_v1_0000" and hits[0]["score"]==0.91
