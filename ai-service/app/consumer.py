import asyncio, hashlib, hmac, json, time, uuid
from typing import Any
import httpx
from .config import settings
from .providers import provider

async def process_envelope(envelope: dict[str,Any], client: httpx.AsyncClient | None = None) -> dict[str,Any]:
    if envelope.get("schemaVersion") != 1 or envelope.get("eventType") != "ticket.ai.analyze.requested.v1": raise ValueError("unsupported event")
    data=envelope["data"]; result=await provider().structured(json.dumps(data,ensure_ascii=False)); callback={"taskId":data["taskId"],"ticketId":data["ticketId"],"ticketVersion":data["ticketVersion"],"status":"SUCCEEDED","result":result,"usage":{"provider":"fake","model":"fake-v1","promptVersion":"ticket-analysis-v1","inputTokens":0,"outputTokens":40,"estimatedCostMicros":0,"latencyMs":0}}
    body=json.dumps(callback,separators=(",",":"),ensure_ascii=False).encode(); path="/internal/v1/ai-results/ticket-analysis"; ts=str(int(time.time()*1000)); nonce=str(uuid.uuid4()); canonical=f"{ts}\n{nonce}\nPOST\n{path}\n{hashlib.sha256(body).hexdigest()}"; signature=hmac.new(settings.ai_internal_secret.encode(),canonical.encode(),hashlib.sha256).hexdigest(); headers={"Content-Type":"application/json","X-Internal-Service":"ai-service","X-Internal-Timestamp":ts,"X-Internal-Nonce":nonce,"X-Internal-Signature":signature,"X-Trace-Id":envelope.get("traceId",str(uuid.uuid4()))}
    owns=client is None; client=client or httpx.AsyncClient(timeout=10); response=await client.post(settings.java_internal_base_url+path,content=body,headers=headers); response.raise_for_status()
    if owns: await client.aclose()
    return callback

async def consume_forever() -> None:
    import aio_pika
    connection=await aio_pika.connect_robust(settings.rabbitmq_url)
    channel=await connection.channel(); await channel.set_qos(prefetch_count=4); exchange=await channel.declare_exchange("aftersales.topic",aio_pika.ExchangeType.TOPIC,durable=True); queue=await channel.declare_queue("ai.ticket.analysis.q",durable=True,arguments={"x-dead-letter-exchange":"aftersales.dlx"}); await queue.bind(exchange,"ticket.ai.analyze.requested.v1")
    async with queue.iterator() as messages:
        async for message in messages:
            async with message.process(requeue=False): await process_envelope(json.loads(message.body))

if __name__ == "__main__": asyncio.run(consume_forever())
