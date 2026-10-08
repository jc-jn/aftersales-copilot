import json
from fastapi import Depends, FastAPI, Request, HTTPException
from pydantic import ValidationError
from fastapi.responses import StreamingResponse
from .config import settings
from .schemas import AnalyzeRequest
from .schemas import DocumentIndexRequest, ChatRequest
from .security import verify_internal
from .indexer import index_document
from .agent import stream_answer
from .observability import ObservabilityMiddleware, metrics
from .model_calls import measured_call
from .safety import prompt_for
from fastapi.responses import Response

app = FastAPI(title="AfterSales AI Service", version="0.2.0", docs_url=None if settings.app_env == "prod" else "/docs", redoc_url=None if settings.app_env == "prod" else "/redoc", openapi_url=None if settings.app_env == "prod" else "/openapi.json")
app.add_middleware(ObservabilityMiddleware)

@app.get("/internal/v1/metrics")
async def prometheus(body: bytes = Depends(verify_internal)):
    return Response(metrics(), media_type="text/plain; version=0.0.4; charset=utf-8")

def parse_payload(schema, body: bytes):
    try:
        return schema.model_validate_json(body)
    except ValidationError as exc:
        raise HTTPException(status_code=422, detail="invalid request payload") from exc


@app.get("/health/live")
async def live() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/health/ready")
async def ready() -> dict[str, str]:
    return {"status": "ok", "provider": settings.llm_provider}

@app.post("/internal/v1/tickets/analyze")
async def analyze(request: Request, body: bytes = Depends(verify_internal)):
    payload = parse_payload(AnalyzeRequest, body)
    result, usage, error = await measured_call(prompt_for("ticket_analysis_v2", payload.model_dump()), "TICKET_ANALYSIS", "ticket-analysis-v2")
    return {"taskId":payload.task_id,"ticketId":int(payload.ticket.get("id",0)),"ticketVersion":int(payload.ticket.get("version",0)),"status":"FAILED" if error else "SUCCEEDED","result":result,"usage":usage,"errorCode":error}

@app.post("/internal/v1/documents/index")
async def index(request: Request, body: bytes = Depends(verify_internal)):
    payload = parse_payload(DocumentIndexRequest, body)
    result = await index_document(payload.model_dump(by_alias=True))
    return {"taskId":payload.task_id, **result}

@app.post("/internal/v1/chat/stream")
async def chat_stream(request: Request, body: bytes = Depends(verify_internal)):
    payload = parse_payload(ChatRequest, body)
    async def events():
        import json
        async for event in stream_answer(payload.ticket_id, payload.message, payload.context, payload.call_id):
            yield f"event: {event['event']}\ndata: {json.dumps(event['data'], ensure_ascii=False)}\n\n"
    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control":"no-cache","X-Accel-Buffering":"no"})
