import math
from typing import Any
import httpx
from .config import settings
from .providers import embedding_provider

def select_evidence(hits: list[dict], threshold: float | None, top_k: int = 5) -> list[dict]:
    if threshold is None:
        return []
    return sorted(
        [hit for hit in hits if isinstance(hit.get("score"), (int, float))
         and math.isfinite(hit["score"]) and hit["score"] >= threshold
         and hit.get("chunkId") and hit.get("documentId") and hit.get("quote")],
        key=lambda hit: hit["score"], reverse=True,
    )[:top_k]


async def search_policy(query: str, filters: dict[str, Any] | None = None,
                        top_k: int = 5, client: httpx.AsyncClient | None = None) -> list[dict]:
    if settings.rag_score_threshold is None:
        return []
    own = client is None
    http = client or httpx.AsyncClient(timeout=10)
    try:
        vector = await embedding_provider(settings.embedding_dimension).embed_query(query)
        must = [{"key": key, "match": {"value": value}}
                for key, value in (filters or {}).items() if value is not None]
        body = {"vector": vector, "limit": top_k, "with_payload": True,
                "score_threshold": settings.rag_score_threshold}
        if must:
            body["filter"] = {"must": must}
        response = await http.post(
            f"{settings.qdrant_url.rstrip('/')}/collections/{settings.qdrant_collection}/points/search",
            json=body)
        response.raise_for_status()
        hits = []
        for item in response.json().get("result", []):
            payload = item.get("payload", {})
            hits.append({"chunkId": payload.get("chunkId"), "documentId": payload.get("documentId"),
                         "indexVersion": payload.get("indexVersion"), "title": payload.get("title", ""),
                         "section": payload.get("sectionTitle", ""), "quote": payload.get("content", ""),
                         "score": item.get("score", 0)})
        return select_evidence(hits, settings.rag_score_threshold, top_k)
    finally:
        if own:
            await http.aclose()
