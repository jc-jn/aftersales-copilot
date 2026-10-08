import httpx
import pytest

from app.indexer import QdrantClient


@pytest.mark.asyncio
@pytest.mark.parametrize("size,distance", [(8, "Cosine"), (1024, "Dot")])
async def test_existing_collection_rejects_fake_dimension_or_wrong_metric(size, distance):
    def handler(request):
        return httpx.Response(200, json={"result": {"config": {"params": {
            "vectors": {"size": size, "distance": distance}}}}})
    async with httpx.AsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(ValueError, match="QDRANT_VECTOR_CONFIG_MISMATCH"):
            await QdrantClient("http://localhost:6333", "bge", 1024).ensure_collection(client)
