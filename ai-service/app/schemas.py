from typing import Any, Literal
from pydantic import BaseModel, ConfigDict, Field

class ExtractedTicketInfo(BaseModel):
    model_config = ConfigDict(extra="allow")
    issue: str | None = None
    attempted_actions: list[str] = Field(default_factory=list, alias="attemptedActions")

class Citation(BaseModel):
    chunk_id: str = Field(alias="chunkId")
    document_id: str = Field(alias="documentId")
    title: str = ""
    section: str = ""
    quote: str = ""
    score: float = 0

class ProposalSuggestion(BaseModel):
    type: str
    reason_code: str | None = Field(default=None, alias="reasonCode")
    suggested_refund_amount_cent: int | None = Field(default=None, alias="suggestedRefundAmountCent")
    description: str = ""
    conditions: dict[str, Any] = Field(default_factory=dict)
    evidence_citation_ids: list[str] = Field(default_factory=list, alias="evidenceCitationIds")
    confidence: float = 0

class TicketAnalysisResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")
    intent: Literal["REFUND_ONLY", "RETURN_REFUND", "EXCHANGE", "REPAIR", "OTHER"]
    confidence: float = Field(ge=0, le=1)
    priority_suggestion: Literal["LOW", "MEDIUM", "HIGH", "URGENT"] = Field(alias="prioritySuggestion")
    sentiment: Literal["NEUTRAL", "NEGATIVE", "VERY_NEGATIVE"]
    extracted: ExtractedTicketInfo
    missing_fields: list[str] = Field(default_factory=list, alias="missingFields")
    needs_human: bool = Field(alias="needsHuman")
    risk_flags: list[str] = Field(default_factory=list, alias="riskFlags")
    reply_suggestion: str = Field(alias="replySuggestion")
    proposal_suggestion: ProposalSuggestion | None = Field(default=None, alias="proposalSuggestion")
    citations: list[Citation] = Field(default_factory=list)

class AnalyzeRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    task_id: int = Field(alias="taskId"); ticket: dict[str, Any] = {}; order_context: dict[str, Any] = Field(default_factory=dict, alias="orderContext"); allowed_tools: list[str] = Field(default_factory=list, alias="allowedTools")

class DocumentIndexRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    task_id: int = Field(alias="taskId")
    document_id: int = Field(alias="documentId")
    index_version: int = Field(alias="indexVersion")
    file_name: str = Field(alias="fileName")
    content_type: str = Field(default="text/plain", alias="contentType")
    content_sha256: str | None = Field(default=None, alias="contentSha256")
    content_base64: str | None = Field(default=None, alias="contentBase64")
    object_url: str | None = Field(default=None, alias="objectUrl")
    metadata: dict[str, Any] = Field(default_factory=dict)

class ChatRequest(BaseModel):
    ticket_id: int = Field(alias="ticketId")
    message: str = Field(min_length=1, max_length=4000)
    context: dict[str, Any] = Field(default_factory=dict)

class AnalyzeResponse(BaseModel):
    taskId: int; ticketId: int; ticketVersion: int; status: Literal["SUCCEEDED","FAILED"]; result: dict[str,Any] | None = None; usage: dict[str,Any] = {}; errorCode: str | None = None; errorMessage: str | None = None
