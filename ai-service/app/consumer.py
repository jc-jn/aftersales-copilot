import asyncio, hashlib, hmac, json, time, uuid
from typing import Any
import httpx
from .config import settings
from .indexer import index_document
from .callbacks import post_callback
from .model_calls import measured_call
from .observability import trace_id, normalize_trace
from .safety import prompt_for

async def process_envelope(envelope: dict[str,Any], client: httpx.AsyncClient | None = None) -> dict[str,Any]:
    if type(envelope.get("schemaVersion")) is not int or envelope.get("schemaVersion") != 1 or envelope.get("eventType") != "ticket.ai.analyze.requested.v1": raise ValueError("unsupported event")
    data=envelope["data"]
    token = trace_id.set(normalize_trace(envelope.get("traceId")))
    try:
        result, usage, error_code = await measured_call(prompt_for("ticket_analysis_v2", data), "TICKET_ANALYSIS", "ticket-analysis-v2")
        callback={"callId":data.get("callId") or f"legacy-{data['taskId']}","taskId":data["taskId"],"ticketId":data["ticketId"],"ticketVersion":data["ticketVersion"],"status":"FAILED" if error_code else "SUCCEEDED","result":result,"errorCode":error_code,"usage":usage}
        await post_callback("/internal/v1/ai-results/ticket-analysis", callback, client)
    finally:
        trace_id.reset(token)
    return callback

async def process_document_envelope(envelope: dict[str,Any]) -> dict[str,Any]:
    if type(envelope.get("schemaVersion")) is not int or envelope.get("schemaVersion") != 1 or envelope.get("eventType") != "knowledge.document.index.requested.v1": raise ValueError("unsupported event")
    data = dict(envelope["data"])
    path = f"/internal/v1/knowledge/documents/{data['documentId']}/download"
    ts = str(int(time.time()*1000)); nonce = str(uuid.uuid4())
    canonical = f"{ts}\n{nonce}\nPOST\n{path}\n{hashlib.sha256(b'').hexdigest()}"
    signature = hmac.new(settings.ai_internal_secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
    headers = {"X-Internal-Service":"ai-service", "X-Internal-Timestamp":ts, "X-Internal-Nonce":nonce, "X-Internal-Signature":signature, "X-Trace-Id":normalize_trace(envelope.get("traceId"))}
    async with httpx.AsyncClient(timeout=10) as http:
        response = await http.post(settings.java_internal_base_url+path, content=b"", headers=headers)
        response.raise_for_status()
        data["objectUrl"] = response.json()["url"]
    result = await index_document(data)
    data=envelope["data"]; callback={"taskId":data["taskId"],"documentId":data["documentId"],"indexVersion":data["indexVersion"],**result}
    token = trace_id.set(normalize_trace(envelope.get("traceId")))
    try:
        await post_callback("/internal/v1/ai-results/document-index", callback)
    finally:
        trace_id.reset(token)
    return callback

async def consume_forever() -> None:
    import aio_pika
    connection=await aio_pika.connect_robust(settings.rabbitmq_url)
    channel=await connection.channel(); await channel.set_qos(prefetch_count=4); exchange=await channel.declare_exchange("aftersales.topic",aio_pika.ExchangeType.TOPIC,durable=True); queue=await channel.declare_queue("ai.ticket.analysis.q",durable=True,arguments={"x-dead-letter-exchange":"aftersales.dlx"}); await queue.bind(exchange,"ticket.ai.analyze.requested.v1"); await queue.bind(exchange,"knowledge.document.index.requested.v1")
    async with queue.iterator() as messages:
        async for message in messages:
            async with message.process(requeue=False):
                envelope=json.loads(message.body)
                if envelope.get("eventType")=="knowledge.document.index.requested.v1": await process_document_envelope(envelope)
                else: await process_envelope(envelope)

if __name__ == "__main__": asyncio.run(consume_forever())
