import json
from fastapi import Depends, FastAPI, Request
from .config import settings
from .providers import provider
from .schemas import AnalyzeRequest
from .security import verify_internal

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
    return {"taskId":payload.taskId,"ticketId":int(payload.ticket.get("id",0)),"ticketVersion":int(payload.ticket.get("version",0)),"status":"SUCCEEDED","result":result,"usage":{"provider":"fake","model":"fake-v1","promptVersion":"ticket-analysis-v1","inputTokens":len(body),"outputTokens":40,"estimatedCostMicros":0,"latencyMs":0}}
