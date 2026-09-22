from typing import Any, Literal
from pydantic import BaseModel

class AnalyzeRequest(BaseModel):
    taskId: int; ticket: dict[str, Any] = {}; orderContext: dict[str, Any] = {}; allowedTools: list[str] = []

class AnalyzeResponse(BaseModel):
    taskId: int; ticketId: int; ticketVersion: int; status: Literal["SUCCEEDED","FAILED"]; result: dict[str,Any] | None = None; usage: dict[str,Any] = {}; errorCode: str | None = None; errorMessage: str | None = None
