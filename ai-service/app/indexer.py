import hashlib, uuid
from typing import Any
import httpx
from .config import settings
from .documents import chunk_document, clean_text, decode_document, parse_document, sha256_bytes
from .providers import embedding_provider

class QdrantClient:
    def __init__(self, url: str, collection: str, dimension: int): self.url=url.rstrip("/"); self.collection=collection; self.dimension=dimension
    async def ensure_collection(self, client: httpx.AsyncClient) -> None:
        response=await client.get(f"{self.url}/collections/{self.collection}")
        if response.status_code == 404:
            response=await client.put(f"{self.url}/collections/{self.collection}",json={"vectors":{"size":self.dimension,"distance":"Cosine"}})
        response.raise_for_status()
    async def upsert(self, points: list[dict[str,Any]], client: httpx.AsyncClient) -> None:
        response=await client.put(f"{self.url}/collections/{self.collection}/points?wait=true",json={"points":points}); response.raise_for_status()
    async def delete_version(self, document_id: int, index_version: int, client: httpx.AsyncClient) -> None:
        await client.post(f"{self.url}/collections/{self.collection}/points/delete?wait=true",json={"filter":{"must":[{"key":"documentId","match":{"value":str(document_id)}},{"key":"indexVersion","match":{"value":index_version}}]}})

async def index_document(request: dict[str,Any], client: httpx.AsyncClient | None = None) -> dict[str,Any]:
    if request.get("objectUrl"):
        own_download = client is None
        download_client = client or httpx.AsyncClient(timeout=20)
        response = await download_client.get(request["objectUrl"]); response.raise_for_status(); data = response.content
        if own_download: await download_client.aclose()
    else:
        data=decode_document(request.get("contentBase64"),request["fileName"])
    actual_sha=sha256_bytes(data)
    expected=request.get("contentSha256")
    if expected and expected != actual_sha: raise ValueError("DOCUMENT_SHA256_MISMATCH")
    text=clean_text(parse_document(data,request["fileName"],request.get("contentType","text/plain")))
    chunks=chunk_document(text); dimension=int(getattr(settings,"embedding_dimension",8) or 8); embeddings=await embedding_provider(dimension).embed_documents([c.content for c in chunks])
    http=client or httpx.AsyncClient(timeout=20); qdrant=QdrantClient(settings.qdrant_url,settings.qdrant_collection,dimension); await qdrant.ensure_collection(http)
    points=[]
    for chunk,vector in zip(chunks,embeddings):
        chunk_id=f"doc_{request['documentId']}_v{request['indexVersion']}_{chunk.sequence_no:04d}"; point_id=str(uuid.uuid5(uuid.NAMESPACE_URL,chunk_id)); payload={"chunkId":chunk_id,"documentId":str(request["documentId"]),"indexVersion":request["indexVersion"],"sectionTitle":chunk.section_title,"content":chunk.content,"title":request.get("title",request.get("fileName","")),**request.get("metadata",{})}; points.append({"id":point_id,"vector":vector,"payload":payload})
    if points: await qdrant.upsert(points,http)
    if client is None: await http.aclose()
    return {"documentId":request["documentId"],"indexVersion":request["indexVersion"],"status":"SUCCEEDED","chunkCount":len(points),"sha256":actual_sha,"chunks":[{"chunkId":p["payload"]["chunkId"],"sequenceNo":i,"sectionTitle":p["payload"]["sectionTitle"],"contentHash":hashlib.sha256(p["payload"]["content"].encode()).hexdigest(),"qdrantPointId":p["id"]} for i,p in enumerate(points)]}
