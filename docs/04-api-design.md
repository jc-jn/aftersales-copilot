# 04. API 与跨服务契约

## 1. 通用规范

- 公共前缀：`/api/v1`；内部前缀：`/internal/v1`。
- Content-Type：普通接口 `application/json`；上传 `multipart/form-data`；流式 `text/event-stream`。
- JWT：`Authorization: Bearer <accessToken>`。
- 时间：ISO-8601 UTC，例如 `2026-09-21T08:00:00.000Z`。
- ID 在 JSON 中使用字符串，例如 `"9007199254740993"`。
- 分页：`page` 从 1 开始，`size` 默认 20、最大 100。
- 状态修改请求必须传 `version`；幂等命令传 `Idempotency-Key`。
- 响应不得返回密码哈希、内部 Token、模型 API Key、MinIO 永久地址。

成功响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "01K5..."
}
```

失败响应：

```json
{
  "code": "TICKET_INVALID_TRANSITION",
  "message": "当前状态不允许执行该操作",
  "details": {"currentStatus": "CLOSED", "action": "REQUEST_INFO"},
  "traceId": "01K5..."
}
```

主要 HTTP 映射：参数错误 400、未认证 401、无权限 403、不存在 404、版本/幂等/状态冲突 409、规则拒绝 422、限流 429、依赖不可用 503。

## 2. 认证 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | 用户名/密码登录 |
| POST | `/auth/refresh` | Refresh Token 轮换，旧 Token 立即撤销 |
| POST | `/auth/logout` | 撤销当前 Refresh Token |
| GET | `/auth/me` | 当前用户信息 |

登录请求：

```json
{"username":"customer01","password":"Demo@123456"}
```

响应 `accessToken`（建议 15 分钟）、`refreshToken`（建议 7 天）、`expiresIn`、用户与角色。演示密码只进入 seed 和 README 的本地章节，部署时必须覆盖。

## 3. 消费者 API

### 3.1 订单

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/customer/orders` | 本人订单分页 |
| GET | `/customer/orders/{orderId}` | 本人订单详情、订单项、物流 |
| GET | `/customer/order-items/{itemId}/after-sales-eligibility` | 四类售后资格的 Java 规则计算结果 |

资格响应示例：

```json
{
  "code": "OK",
  "data": {
    "orderItemId": "10001",
    "remainingRefundableCent": 129900,
    "options": [
      {"type":"REFUND_ONLY","eligible":true,"deadline":"2026-09-25T00:00:00Z","reason":null},
      {"type":"EXCHANGE","eligible":false,"deadline":null,"reason":"EXCHANGE_WINDOW_EXPIRED"}
    ]
  },
  "traceId": "..."
}
```

### 3.2 工单

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/tickets` | 创建工单 |
| GET | `/tickets` | 当前用户的工单列表；客服按权限看到队列 |
| GET | `/tickets/{ticketId}` | 工单详情，字段按角色裁剪 |
| GET | `/tickets/{ticketId}/timeline` | 时间线 |
| POST | `/tickets/{ticketId}/messages` | 发送公开消息/客服内部备注 |
| POST | `/tickets/{ticketId}/attachments` | 上传附件，先校验再存储 |
| POST | `/tickets/{ticketId}/supplement` | 用户补充结构化信息并恢复客服处理 |
| POST | `/tickets/{ticketId}/cancel` | 取消允许取消的工单 |
| POST | `/tickets/{ticketId}/rating` | 对 `RESOLVED/CLOSED` 工单评价一次 |

创建请求：

```json
{
  "orderItemId": "10001",
  "requestedType": "RETURN_REFUND",
  "title": "耳机右侧无声",
  "description": "收货第 3 天开始右侧无声，已尝试重新配对。",
  "attachmentIds": ["88001"],
  "clientRequestId": "01K5CLIENTULID"
}
```

`clientRequestId` 对用户创建工单幂等，重复提交返回第一次创建的工单。

工单详情返回 `version` 和 `availableActions`。`availableActions` 由 Java 根据角色、归属和当前状态计算，例如 `SEND_MESSAGE`、`CANCEL`、`CONFIRM_PROPOSAL`、`REQUEST_INFO`；前端不得自行推断权限。

发送消息：

```json
{
  "content": "请补充耳机序列号和故障视频。",
  "visibility": "PUBLIC",
  "messageType": "REQUEST_INFO",
  "version": 3
}
```

用户只能发送 `PUBLIC/TEXT`；`INTERNAL` 仅客服和管理员。

## 4. 客服 API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/agent/workbench/summary` | 待处理、待用户、超时等计数 |
| GET | `/agent/tickets/queue` | 未分配和本人队列 |
| POST | `/tickets/{id}/claim` | 领取未分配工单 |
| POST | `/tickets/{id}/assign` | 管理员/有权限者分配 |
| POST | `/tickets/{id}/transfer` | 转派并记录原因 |
| POST | `/tickets/{id}/request-info` | 请求用户补充，进入 `PENDING_CUSTOMER` |
| POST | `/tickets/{id}/reject` | 拒绝并记录规则依据 |
| POST | `/tickets/{id}/resolve` | 对无需执行提案的特殊结案；MVP 常规四类由执行服务推进 |
| POST | `/tickets/{id}/close` | 关闭终结工单 |
| GET | `/tickets/{id}/ai-analysis/latest` | 最新 AI 分析和引用 |
| POST | `/tickets/{id}/ai-analysis/retry` | 管理员/客服重试失败分析 |

所有客服命令检查：角色、工单归属（管理员除外）、当前状态和 `version`。

## 5. 提案 API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/tickets/{id}/proposals` | 客服创建提案或基于 AI 草稿创建 |
| GET | `/tickets/{id}/proposals` | 查看提案历史 |
| POST | `/proposals/{id}/publish` | 客服复核并发给用户 |
| POST | `/proposals/{id}/confirm` | 有权用户确认；执行或进入寄回流程 |
| POST | `/proposals/{id}/reject` | 用户拒绝提案 |
| POST | `/return-orders/{id}/shipment` | 用户填写退货物流 |
| POST | `/return-orders/{id}/receive` | 客服模拟确认收货 |
| POST | `/return-orders/{id}/inspect` | 客服填写检查结果并触发后续执行 |

创建提案请求：

```json
{
  "type": "REFUND_ONLY",
  "aiAnalysisId": "70001",
  "refundAmountCent": 129900,
  "reasonCode": "QUALITY_DEFECT",
  "description": "商品存在质量问题，为您办理仅退款。",
  "conditions": {"returnRequired": false},
  "expiresInHours": 24,
  "version": 5
}
```

Java 忽略/校正客户端不可信的最终可退金额，响应同时返回 `requestedAmountCent` 和 `validatedAmountCent`。若不同，必须要求客服重新确认，不能静默扩大金额。

确认请求头：

```text
Idempotency-Key: 01K5CONFIRMULID
```

```json
{"ticketVersion": 7, "proposalVersion": 1}
```

相同 Key + 相同请求返回原结果；相同 Key + 不同请求返回 `409 IDEMPOTENCY_KEY_REUSED`。

## 6. AI 对话 SSE

### 6.1 入口

`POST /api/v1/tickets/{ticketId}/ai-chat/stream`

```json
{
  "message": "这个耳机还在保修期吗？我需要寄回吗？",
  "conversationId": "68001",
  "mode": "CUSTOMER_ASSISTANT"
}
```

模式：消费者使用 `CUSTOMER_ASSISTANT`，客服使用 `AGENT_COPILOT`。两种模式拥有不同 Prompt 和工具权限。

### 6.2 SSE 事件

每个事件包含 `event:` 和单行 JSON `data:`：

```text
event: meta
data: {"requestId":"...","assistantMessageId":"...","model":"qwen-plus"}

event: tool_status
data: {"tool":"get_warranty_status","status":"COMPLETED","display":"已查询质保状态"}

event: citation
data: {"citationId":"c1","documentId":"3001","title":"耳机售后政策","section":"换货与维修","quote":"..."}

event: delta
data: {"content":"根据您的订单信息，"}

event: done
data: {"finishReason":"STOP","usage":{"inputTokens":800,"outputTokens":120},"needsHuman":false}
```

错误事件：

```text
event: error
data: {"code":"AI_PROVIDER_UNAVAILABLE","message":"AI 服务暂不可用，请联系人工客服","retryable":true}
```

约束：

- 不向前端输出模型隐藏思维链；只输出工具状态、引用和最终回答。
- `done` 或 `error` 后立即关闭流。
- 心跳可使用 SSE 注释 `: ping`，每 15 秒一次。
- 客户端中断不自动重试写操作；对话请求本身只读。

## 7. 管理 API

### 7.1 商品与模拟订单

- `/admin/products`、`/admin/skus`：CRUD。
- `/admin/orders`：创建/查看模拟订单，不提供真实支付。
- `/admin/orders/{id}/logistics-events`：增加模拟物流节点。
- `/admin/warranty-rules`：规则 CRUD、启停和有效期校验。

### 7.2 知识库

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/knowledge-bases` | 创建知识库 |
| POST | `/admin/knowledge-documents` | 上传文档及范围元数据 |
| GET | `/admin/knowledge-documents` | 列表与状态 |
| GET | `/admin/knowledge-documents/{id}` | 详情和索引结果 |
| POST | `/admin/knowledge-documents/{id}/reindex` | 递增 indexVersion 并重建 |
| POST | `/admin/knowledge-documents/{id}/archive` | 归档并异步清理向量 |

上传元数据包括：`knowledgeBaseId`、`title`、`documentType`、`scopeType`、`scopeId`、`versionLabel`、有效期和文件。

### 7.3 统计、配置和审计

- `GET /admin/dashboard/overview?from=&to=`
- `GET /admin/statistics/ai-usage?groupBy=day`
- `GET /admin/audit-logs`
- `GET/PUT /admin/ai-settings`：管理 provider、model、endpoint、Fallback 开关和预算限额。MVP 的 API Key 只从服务器环境变量注入，接口仅返回“是否已配置”，不允许读取或更新密钥。
- `POST /admin/ai-settings/test`：使用服务器环境中的密钥发起最小测试请求并记录成本。

## 8. Java → Python 内部 API

内部请求必须包含：

```text
X-Internal-Service: aftersales-server
X-Internal-Timestamp: 1758441600000
X-Internal-Nonce: 01K5...
X-Internal-Signature: hex(hmac_sha256(secret, timestamp + "\n" + nonce + "\n" + method + "\n" + path + "\n" + sha256(body)))
X-Trace-Id: ...
```

Python 校验时间偏差（建议 5 分钟）、HMAC 和 Nonce 短期去重。生产部署再叠加内部网络限制。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/internal/v1/chat/stream` | 返回 SSE |
| POST | `/internal/v1/tickets/analyze` | 可用于同步调试，正式任务优先 MQ |
| POST | `/internal/v1/documents/index` | 可用于调试，正式任务优先 MQ |
| GET | `/internal/v1/health/ready` | 就绪检查，不包含密钥信息 |

分析请求必须由 Java 组装事实快照：

```json
{
  "taskId": "50001",
  "ticket": {
    "id": "60001",
    "version": 1,
    "requestedType": "RETURN_REFUND",
    "description": "耳机右侧无声",
    "createdAt": "2026-09-21T08:00:00Z"
  },
  "orderContext": {
    "orderNo": "O20260901001",
    "orderStatus": "DELIVERED",
    "deliveredAt": "2026-09-18T03:00:00Z",
    "productId": "201",
    "skuId": "301",
    "productName": "示例降噪耳机",
    "paidAmountCent": 129900,
    "remainingRefundableCent": 129900,
    "warrantyExpireAt": "2027-09-18T03:00:00Z"
  },
  "allowedTools": ["get_order_summary","get_logistics","get_warranty_status","search_policy"]
}
```

## 9. Python → Java 内部 API

Python 使用独立服务身份和同样的 HMAC 规则。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/internal/v1/ai-results/ticket-analysis` | 回写分析 |
| POST | `/internal/v1/ai-results/document-index` | 回写索引状态和 chunk 元数据 |
| POST | `/internal/v1/ai-results/closure-summary` | 回写摘要 |
| GET | `/internal/v1/tools/orders/{orderNo}` | 受控订单摘要 |
| GET | `/internal/v1/tools/orders/{orderNo}/logistics` | 物流摘要 |
| GET | `/internal/v1/tools/order-items/{id}/warranty` | 质保及 Java 资格计算 |
| GET | `/internal/v1/tools/tickets/{id}/history` | 当前用户/商品的必要历史，限制字段 |

分析回调：

```json
{
  "taskId": "50001",
  "ticketId": "60001",
  "ticketVersion": 1,
  "status": "SUCCEEDED",
  "result": {
    "intent": "RETURN_REFUND",
    "confidence": 0.91,
    "prioritySuggestion": "MEDIUM",
    "sentiment": "NEGATIVE",
    "extracted": {"issue":"RIGHT_EARBUD_NO_SOUND","attemptedActions":["REPAIR_PAIRING"]},
    "missingFields": ["serialNumber","faultVideo"],
    "needsHuman": false,
    "riskFlags": [],
    "replySuggestion": "您好，已了解右侧耳机无声的问题……",
    "proposalSuggestion": null,
    "citations": [
      {"chunkId":"doc_1_v1_003","documentId":"1","title":"耳机售后政策","section":"质量问题","quote":"...","score":0.82}
    ]
  },
  "usage": {"provider":"dashscope","model":"qwen-plus","promptVersion":"ticket-analysis-v1","inputTokens":1200,"outputTokens":260,"estimatedCostMicros":25000,"latencyMs":2100}
}
```

Java 校验 `ticketId/taskId` 关系。若工单版本已变化，仍保存历史分析，但标记为 `stale=true`，不得自动覆盖当前优先级或产生待确认提案。

## 10. RabbitMQ 契约

Exchange：`aftersales.topic`（durable topic）。统一 envelope：

```json
{
  "eventId": "uuid",
  "eventType": "ticket.ai.analyze.requested.v1",
  "occurredAt": "2026-09-21T08:00:00Z",
  "traceId": "...",
  "producer": "aftersales-server",
  "schemaVersion": 1,
  "data": {}
}
```

| Routing key | Queue | 消费者 | 重试 |
|---|---|---|---|
| `ticket.ai.analyze.requested.v1` | `ai.ticket.analysis.q` | Python | 延迟 30s/2m/10m，3 次后 DLQ |
| `knowledge.document.index.requested.v1` | `ai.document.index.q` | Python | 同上 |
| `ticket.closure.summary.requested.v1` | `ai.closure.summary.q` | Python | 同上 |
| `ai.task.failed.v1` | `java.ai.failure.q` | Java | 记录失败并通知工作台 |

队列配置 DLX，例如 `aftersales.dlx`。消费者必须以 `eventId` 或 `taskId` 幂等；处理成功后 ACK，临时错误 NACK 进入延迟重试，校验错误直接送 DLQ。

## 11. 错误码

| 错误码 | 含义 |
|---|---|
| `AUTH_INVALID_CREDENTIALS` | 登录失败，消息不区分用户不存在/密码错误 |
| `ORDER_NOT_OWNED` | 订单不属于当前用户 |
| `AFTER_SALES_NOT_ELIGIBLE` | 不符合售后规则 |
| `ACTIVE_TICKET_EXISTS` | 已有活动工单 |
| `TICKET_VERSION_CONFLICT` | 乐观锁冲突，需刷新 |
| `TICKET_INVALID_TRANSITION` | 非法状态跳转 |
| `TICKET_NOT_ASSIGNED_TO_AGENT` | 非当前负责客服 |
| `PROPOSAL_EXPIRED` | 提案过期 |
| `PROPOSAL_AMOUNT_INVALID` | 金额超限或与规则不一致 |
| `IDEMPOTENCY_KEY_REUSED` | 相同 Key 请求体不一致 |
| `AI_PROVIDER_UNAVAILABLE` | 模型不可用，可人工降级 |
| `AI_OUTPUT_INVALID` | 结构化输出校验失败 |
| `KNOWLEDGE_NO_EVIDENCE` | 没有足够证据，必须转人工/保守回复 |
| `RATE_LIMITED` | 超过限流 |
