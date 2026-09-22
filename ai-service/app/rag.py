from typing import Any
import httpx
from .config import settings
from .providers import embedding_provider

async def search_policy(query: str, filters: dict[str, Any] | None = None, top_k: int = 5, client: httpx.AsyncClient | None = None) -> list[dict[str, Any]]:
    own = client is None; http = client or httpx.AsyncClient(timeout=10)
    vector = await embedding_provider(settings.embedding_dimension).embed_query(query)
    must = []
    for key, value in (filters or {}).items():
        if value is not None: must.append({"key": key, "match": {"value": value}})
    body = {"vector": vector, "limit": top_k, "with_payload": True, "score_threshold": 0.0}
    if must: body["filter"] = {"must": must}
    response = await http.post(f"{settings.qdrant_url.rstrip('/')}/collections/{settings.qdrant_collection}/points/search", json=body)
    response.raise_for_status()
    result = response.json().get("result", [])
    hits = []
    for item in result:
        payload = item.get("payload", {})
        hits.append({"chunkId": payload.get("chunkId"), "documentId": payload.get("documentId"), "indexVersion": payload.get("indexVersion"), "title": payload.get("title", ""), "section": payload.get("sectionTitle", ""), "quote": payload.get("content", ""), "score": item.get("score", 0)})
    if own: await http.aclose()
    return hits
