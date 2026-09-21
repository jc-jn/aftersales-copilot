# 06. Python AI 服务设计

## 1. 服务定位

Python 服务是无业务写权限的 AI 编排服务，负责：

- 工单结构化分析。
- 文档解析、切片、Embedding 和 Qdrant 索引。
- RAG 检索、可选 Rerank 和引用生成。
- AI 对话 SSE。
- 结案摘要。
- 调用 Java 暴露的白名单只读工具。

它不连接业务 MySQL，不签发用户 Token，不计算最终退款金额，不执行退款/换货/维修。

## 2. Python 基线

- Python 3.11。
- FastAPI + Uvicorn。
- Pydantic v2 + pydantic-settings。
- httpx 异步客户端。
- LangChain/LangGraph 使用编码时的稳定兼容版本并锁定精确依赖；由于 Python AI 生态变化快，文档不编造未来版本号。
- Qdrant Python Client。
- RabbitMQ 建议 aio-pika。
- 文档解析：pypdf、python-docx、Markdown/纯文本解析；不对扫描 PDF 承诺 OCR。
- 测试：pytest、pytest-asyncio、respx。
- 代码质量：ruff + mypy（至少核心 schemas/workflows）。

`requirements.lock` 或 `uv.lock` 必须提交，以确保可复现。

## 3. 配置模型

```env
APP_ENV=local
AI_INTERNAL_SECRET=change-me
LLM_PROVIDER=dashscope
LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_API_KEY=...
LLM_CHAT_MODEL=qwen-plus
LLM_FALLBACK_PROVIDER=deepseek
LLM_FALLBACK_BASE_URL=https://api.deepseek.com
LLM_FALLBACK_API_KEY=...
LLM_FALLBACK_MODEL=deepseek-chat
EMBEDDING_PROVIDER=dashscope
EMBEDDING_MODEL=text-embedding-v3
EMBEDDING_DIMENSION=<以所选模型官方文档和实测返回为准>
QDRANT_URL=http://localhost:6333
QDRANT_COLLECTION=aftersales_kb_v1
JAVA_INTERNAL_BASE_URL=http://aftersales-server:8080
RABBITMQ_URL=amqp://...
MINIO_ENDPOINT=http://minio:9000
```

Embedding 维度不能凭经验硬编码。首次初始化 collection 前调用模型或查官方文档确定维度，将结果写入配置；已有 collection 时启动检查维度一致，否则拒绝就绪。

## 4. Provider 抽象

```python
class ChatProvider(Protocol):
    async def structured(self, messages, schema, settings) -> ProviderResult: ...
    async def stream(self, messages, tools, settings) -> AsyncIterator[ProviderEvent]: ...

class EmbeddingProvider(Protocol):
    async def embed_documents(self, texts: list[str]) -> list[list[float]]: ...
    async def embed_query(self, text: str) -> list[float]: ...
```

通义千问和 DeepSeek 均通过 OpenAI-compatible Chat Completions 适配，但能力并不假定完全一致：

- 启动时/管理测试时检测流式、JSON 输出、tool calling 是否可用。
- 结构化输出优先使用 provider 支持的 JSON schema；否则严格 Prompt + Pydantic 校验 + 最多一次修复请求。
- Fallback 只允许在尚未向客户端输出有效 token 前发生；流已开始后不能切模型拼接答案。
- Embedding 与聊天模型分开配置。DeepSeek 是否提供满足项目要求的 Embedding 必须以实际 API 为准，不作假设；默认使用通义 Embedding。

## 5. 工单分析工作流

LangGraph 节点建议：

```text
validate_input
  → detect_injection_and_sensitive_content
  → retrieve_policy
  → extract_and_classify
  → validate_result
  → build_reply_suggestion
  → finalize
```

不需要让 Agent 自主循环完成所有步骤。分类和信息抽取是确定性 DAG，更易测试。

Pydantic 输出模型：

```python
class TicketAnalysisResult(BaseModel):
    intent: Literal["REFUND_ONLY", "RETURN_REFUND", "EXCHANGE", "REPAIR", "OTHER"]
    confidence: float = Field(ge=0, le=1)
    priority_suggestion: Literal["LOW", "MEDIUM", "HIGH", "URGENT"]
    sentiment: Literal["NEUTRAL", "NEGATIVE", "VERY_NEGATIVE"]
    extracted: ExtractedTicketInfo
    missing_fields: list[str]
    needs_human: bool
    risk_flags: list[str]
    reply_suggestion: str
    proposal_suggestion: ProposalSuggestion | None
    citations: list[Citation]
```

`proposal_suggestion.refund_amount_cent` 只能是建议值或 `null`，Java 不信任它。没有有效政策引用时，不生成确定性政策结论，`needs_human=true`。

## 6. 对话 Agent

### 6.1 工具白名单

| 工具 | 输入 | 输出 | 权限 |
|---|---|---|---|
| `get_order_summary` | orderNo | 状态、商品、金额的脱敏摘要 | 只读 |
| `get_logistics` | orderNo | 物流节点 | 只读 |
| `get_warranty_status` | orderItemId | 质保与四类资格 | 只读 |
| `get_ticket_history` | ticketId | 当前工单必要历史 | 只读 |
| `search_policy` | query + filters | 知识片段与引用 | 只读，本地 RAG |

不提供 `refund()`、`update_ticket()` 之类写工具。AI 若判断需要业务处理，只输出 `proposal_suggestion`，由 Java 生成草稿。

### 6.2 工具调用防护

- 工具参数必须通过 Pydantic 校验。
- orderNo/ticketId 不由模型自由指定：优先使用 Java 请求上下文中的 ID；若参数不匹配立即拒绝。
- 每轮最多 4 次工具调用，总对话最多 8 轮内部循环。
- 单个 Java 工具调用超时 3 秒，可重试一次只读请求。
- 工具返回数据长度受限，地址、电话和序列号脱敏。
- 工具异常转换为结构化 `TOOL_UNAVAILABLE`，不得把堆栈发给模型/用户。

## 7. 文档索引

### 7.1 解析流程

1. 验证 Java 签名、taskId、documentId、indexVersion。
2. 使用短期签名 URL 或内部 MinIO 凭据下载文件。
3. 校验 SHA-256 与 Java 记录一致。
4. 按文件类型解析；加密/损坏/扫描图片 PDF 返回明确错误。
5. 清理重复空白、页眉页脚（仅可解释规则），保留页码和标题。
6. 分块、Embedding、批量 upsert Qdrant。
7. 向 Java 回调 chunk 元数据和索引状态。

### 7.2 分块默认值

- 优先按 Markdown/文档标题切分，再按段落。
- 目标 500～800 中文字符或根据 tokenizer 约 400～600 tokens。
- overlap 80～120 字符；禁止跨越明显章节强行 overlap。
- 每个 chunk 保留 `documentId/indexVersion/page/section/scope/effective time`。
- 参数可配置，并通过评测集调整；不声称该默认值对所有文档最优。

### 7.3 一致性

- point ID 使用稳定 UUID5 或 `documentId:indexVersion:sequence` 的哈希。
- 批量 upsert 成功后再回调 Java 成功。
- 若部分失败，本次 indexVersion 标记失败并删除该版本已写 point，避免混合半成品。
- 查询只过滤 Java 标记为当前有效的 indexVersion。

## 8. RabbitMQ 消费

消费者处理步骤：

1. 解析 envelope 和 schemaVersion。
2. 验证必填字段；未知版本送 DLQ。
3. 调 Java 查询 task 状态（或依赖回调幂等）；已成功则 ACK。
4. 标记/回调 RUNNING（可选），执行任务。
5. 回调结果成功后 ACK。
6. Provider 429/5xx、网络超时等可重试；输入损坏、文件不支持等不可重试。

Python 不自行无限重试，遵循队列重试次数和预算。

## 9. 错误分类

```text
ValidationError          -> AI_INPUT_INVALID，不重试
UnsupportedDocument     -> DOCUMENT_UNSUPPORTED，不重试
PromptInjectionDetected -> AI_SAFETY_REVIEW，转人工
NoEvidenceError         -> KNOWLEDGE_NO_EVIDENCE，正常降级
ProviderRateLimit       -> AI_PROVIDER_RATE_LIMIT，可重试
ProviderTimeout         -> AI_PROVIDER_TIMEOUT，可重试
ProviderAuthError       -> AI_PROVIDER_AUTH_FAILED，不自动重试并报警
OutputSchemaError       -> AI_OUTPUT_INVALID，修复一次后失败
```

## 10. 健康检查

- `/health/live`：进程存活，不访问外部依赖。
- `/health/ready`：检查配置、Qdrant collection、RabbitMQ 连接；不强制每次调用付费 LLM。
- `/health/provider`：管理员主动测试模型，可产生少量费用并记录日志，不用于容器健康检查。

## 11. 测试

- Provider 使用 fake adapter，单测不调用真实收费 API。
- httpx 工具调用用 respx mock。
- Qdrant 集成测试可用容器或独立测试 collection。
- Prompt golden tests 比较结构化字段和事实，不要求自然语言逐字相等。
- 每次修改 Prompt 都更新 `prompt_version` 和评测结果，不覆盖历史版本。

