import json
from fastapi import Depends, FastAPI, Request
from fastapi.responses import StreamingResponse
from .config import settings
from .providers import provider
from .schemas import AnalyzeRequest
from .schemas import DocumentIndexRequest, ChatRequest
from .security import verify_internal
from .indexer import index_document
from .agent import stream_answer

app = FastAPI(title="AfterSales AI Service", version="0.2.0")


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/ready")
async def ready() -> dict[str, str]:
    return {"status": "ok", "provider": settings.llm_provider}

@app.post("/internal/v1/tickets/analyze")
async def analyze(request: Request, body: bytes = Depends(verify_internal)):
    payload = AnalyzeRequest.model_validate_json(body)
    result = await provider().structured(json.dumps(payload.model_dump(), ensure_ascii=False))
    return {"taskId":payload.task_id,"ticketId":int(payload.ticket.get("id",0)),"ticketVersion":int(payload.ticket.get("version",0)),"status":"SUCCEEDED","result":result,"usage":{"provider":"fake","model":"fake-v1","promptVersion":"ticket-analysis-v1","inputTokens":len(body),"outputTokens":40,"estimatedCostMicros":0,"latencyMs":0}}

@app.post("/internal/v1/documents/index")
async def index(request: Request, body: bytes = Depends(verify_internal)):
    payload = DocumentIndexRequest.model_validate_json(body)
    result = await index_document(payload.model_dump(by_alias=True))
    return {"taskId":payload.task_id, **result}

@app.post("/internal/v1/chat/stream")
async def chat_stream(request: Request, body: bytes = Depends(verify_internal)):
    payload = ChatRequest.model_validate_json(body)
    async def events():
        import json
        async for event in stream_answer(payload.ticket_id, payload.message, payload.context):
            yield f"event: {event['event']}\ndata: {json.dumps(event['data'], ensure_ascii=False)}\n\n"
    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control":"no-cache","X-Accel-Buffering":"no"})
