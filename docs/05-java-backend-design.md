# 05. Java 后端设计

## 1. 工程配置

建议使用 Maven Wrapper，根 `pom.xml` 固定：

- `maven.compiler.release=21`
- Spring Boot `3.5.x` 的最新补丁版本；开始编码时从 Spring Initializr/官方仓库核对具体补丁，不在文档中虚构不存在的版本号。
- MyBatis-Plus 使用与 Spring Boot 3 兼容的 starter。
- Flyway 管理数据库迁移。
- springdoc-openapi/Knife4j 二选一；契约以 OpenAPI 为准。
- Testcontainers 用于 MySQL、Redis、RabbitMQ 集成测试。
- Resilience4j 用于 Python 服务调用的超时、并发隔离和熔断。
- Micrometer + Actuator 暴露指标。

开发机虽然安装 JDK 24，但项目应安装并选择 JDK 21；若使用 IntelliJ IDEA，则将 Project SDK、Maven Runner 和 Language Level 设置为 21。使用其他 IDE 时完成等效配置，或配置 Maven Toolchains：

```xml
<toolchain>
  <type>jdk</type>
  <provides><version>21</version></provides>
  <configuration><jdkHome>C:\Program Files\Java\jdk-21</jdkHome></configuration>
</toolchain>
```

路径按真实安装位置修改，不提交个人绝对路径到仓库。

## 2. 分层规则

每个业务模块采用以下结构：

```text
ticket/
├─ api/              # Controller、request/response DTO
├─ application/      # 用例服务、Command/Query、事务边界
├─ domain/           # 聚合、枚举、领域服务、仓储接口
└─ infrastructure/   # Mapper、PO、仓储实现、外部适配
```

规则：

- Controller 只做协议解析、Bean Validation 和调用应用服务。
- 应用服务控制事务、权限和用例编排。
- 聚合/状态机负责业务不变量，不在 Controller 中用大量 `if` 实现。
- Mapper 不跨模块公开；跨模块通过 Facade/Application Service。
- API DTO、领域对象和数据库 PO 不混用。
- `common` 不得变成任意代码堆放区，只放真正跨模块稳定抽象。

## 3. 核心类设计

### 3.1 Ticket 模块

```text
TicketController
TicketQueryController
TicketApplicationService
TicketQueryService
TicketDomainService
TicketStateMachine
TicketAssignmentService
TicketRepository
TicketMessageRepository
TicketTimelineRepository
```

`TicketStateMachine` 接口示意：

```java
public interface TicketStateMachine {
    TransitionResult transition(
        Ticket ticket,
        TicketAction action,
        Actor actor,
        TransitionContext context
    );
}
```

`transition` 只验证并产生目标状态/领域事件，不直接发 MQ。应用服务在事务内持久化工单、时间线和 Outbox。

关键命令：

- `CreateTicketCommand`
- `AssignTicketCommand`
- `RequestCustomerInfoCommand`
- `SupplementTicketCommand`
- `RejectTicketCommand`
- `CancelTicketCommand`
- `CloseTicketCommand`

### 3.2 Proposal 模块

```text
ProposalController
ProposalApplicationService
ProposalValidationService
AfterSalesEligibilityService
ProposalExecutionService
RefundExecutor
ReturnRefundExecutor
ExchangeExecutor
RepairExecutor
ProposalRepository
ExecutionRepository
```

`AfterSalesEligibilityService` 是 Java 权威规则引擎，输入订单项、规则和当前时间，输出四类资格、截止日期、最大金额和理由。AI 不重写此规则。

执行器统一接口：

```java
public interface ProposalExecutor {
    AfterSalesType supports();
    ExecutionResult execute(ExecutionContext context);
}
```

`ProposalExecutionService.confirmAndExecute()` 顺序：

1. 校验请求 Idempotency-Key 格式并计算 request hash。
2. 查询已有执行记录；相同 Key 同请求直接返回，不同请求报冲突。
3. 开启事务，锁定提案行并读取工单当前版本。
4. 校验操作者、状态、有效期、提案与工单关系。
5. 重新运行资格规则和最大可退金额计算。
6. 插入 `proposal_execution(PROCESSING)`，依赖唯一键形成最终幂等屏障。
7. 调用对应执行器：仅退款生成模拟退款；其他类型创建对应流程记录并推进到 `WAITING_RETURN`。
8. 更新提案、工单、订单项金额，写时间线与审计。
9. 更新执行记录为 `SUCCEEDED`；这里表示确认命令应用成功，不表示退货/换货/维修已经结案；提交事务。
10. 返回确定结果。异常时事务回滚；可预期业务错误不写半成品。

MVP 中外部系统都是模拟的，因此可以在单个本地事务完成。未来接真实支付时必须改成 Saga/Outbox 回调，不能持有数据库事务等待支付接口。

### 3.3 AI Adapter 模块

```text
AiTaskApplicationService
AiInternalCallbackController
AiChatController
AiServiceClient
SseProxyService
InternalSignatureService
AiAnalysisRepository
AiCallLogRepository
```

- `AiServiceClient` 使用 Spring `WebClient`，不能使用阻塞 RestTemplate 代理 SSE。
- 建连超时建议 3 秒，普通分析请求总超时 30 秒；流式请求 idle timeout 建议 60 秒，总时长上限 3 分钟。
- 熔断时返回可识别降级错误，不回滚已经创建的工单。
- Java 转发 SSE 时只允许白名单事件类型，解析并验证 JSON，不能盲目透传任意内容。
- 完整 AI 对话在 `done/error/cancel` 时至多持久化一次，可用 `AtomicBoolean finalized` 防止多个终止回调重复保存。

### 3.4 Knowledge 模块

```text
KnowledgeAdminController
KnowledgeDocumentService
ObjectStorageService
DocumentIndexTaskService
KnowledgeDocumentRepository
```

上传流程：

1. 校验管理员、扩展名、MIME、文件头、大小和 SHA-256。
2. 先写 MinIO 临时对象；扫描/校验通过后移动到正式 key。
3. 事务保存 `knowledge_document=UPLOADED`、`ai_task`、Outbox。
4. 发布索引任务后状态改为 `PARSING`。
5. Python 回调成功时，Java 校验 indexVersion，保存 chunk meta 并置 `INDEXED`。
6. 旧版本向量由异步清理任务删除。

允许格式和默认大小：PDF 20MB、DOCX 10MB、MD/TXT 5MB。第一版拒绝宏文档、压缩包和加密 PDF。

## 4. 权限模型

使用 URL 角色 + 资源级权限双重校验：

- `CUSTOMER`：只能访问自己的订单和工单。
- `AGENT`：可看公共待领取队列及自己的工单；不能修改其他客服工单。
- `ADMIN`：拥有管理功能和全工单查看权限，但提案执行仍需业务校验。
- 内部 API：不接受用户 JWT，只接受服务 HMAC；路径不暴露给 Nginx 公网 location。

资源权限封装为 `TicketAccessPolicy`、`OrderAccessPolicy`，不要在每个 Controller 重复比较 ID。

## 5. 自动分配实现

推荐数据库原子方式：

1. 查询 `ACTIVE + agent_online=1` 客服及活动工单计数，按计数、`last_assigned_at` 排序取候选。
2. 获取 `lock:ticket:assign:{ticketId}` 短锁。
3. 使用 `UPDATE service_ticket SET assigned_agent_id=?, status='PENDING_AGENT', version=version+1 WHERE id=? AND status='PENDING_ASSIGNMENT' AND assigned_agent_id IS NULL`。
4. 更新行数为 1 才写分配日志；否则读取现状返回。

Redis 锁只是减少冲突，数据库条件更新是正确性的最终保障。

创建工单的防重也采用同样原则：在创建事务中先插入 `active_ticket_guard(order_item_id,ticket_id)`，唯一键是最终保障；Redis `lock:ticket:create:*` 仅用于降低重复请求竞争。工单进入 `RESOLVED/REJECTED/CANCELLED` 时同事务释放 guard。

## 6. Outbox 与消息发布

`OutboxPublisher` 每 1 秒领取最多 50 条 `NEW/FAILED` 且到达重试时间的记录：

- 事务 A：`FOR UPDATE SKIP LOCKED` 领取并标记 `SENDING`（表状态可增加该值）。
- 发送 RabbitMQ Publisher Confirm。
- 事务 B：确认后标记 `PUBLISHED`；失败增加 retry，计算指数退避。
- 达到阈值标记 `FAILED` 并报警，管理员可重投。

消费者回调幂等：`ai_task.taskId` + 回调状态更新条件，重复成功回调返回 200，不重复插入分析结果。

## 7. 统一异常和校验

- Bean Validation 处理格式与长度。
- 领域异常包含稳定错误码、HTTP 状态和安全消息。
- 未知异常只对客户端返回 `INTERNAL_ERROR + traceId`，详细堆栈仅服务器日志。
- 售后类型、状态、角色等反序列化遇到未知值返回 400。
- 文本字段做长度限制；富文本第一版不支持，前端按纯文本渲染。

## 8. 配置

```yaml
app:
  security:
    jwt-access-ttl: PT15M
    jwt-refresh-ttl: P7D
  ai:
    base-url: ${AI_SERVICE_BASE_URL:http://localhost:8000}
    internal-secret: ${AI_INTERNAL_SECRET}
    connect-timeout: 3s
    analysis-timeout: 30s
  storage:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    bucket: ${MINIO_BUCKET:aftersales-private}
```

生产配置不得为密钥提供可用默认值。`application-local.yml` 可引用本机环境变量，不能提交真实密钥。

## 9. 日志与指标

结构化日志字段：`timestamp`、`level`、`service`、`traceId`、`userId`、`ticketId`、`action`、`durationMs`、`result`。手机号、地址、Token、密码、API Key、原始附件不得进入日志。

自定义指标：

- `ticket_created_total{type}`
- `ticket_transition_total{from,to,result}`
- `ticket_assignment_duration_seconds`
- `proposal_execution_total{type,result}`
- `ai_proxy_request_total{operation,result}`
- `ai_proxy_first_token_seconds`
- `outbox_pending_count`

## 10. 数据库迁移顺序

```text
V1__auth_and_user.sql
V2__catalog_and_warranty.sql
V3__orders_and_logistics.sql
V4__tickets.sql
V5__ai_tasks_and_analysis.sql
V6__knowledge_base.sql
V7__proposals_and_executions.sql
V8__outbox_audit_idempotency.sql
V9__demo_seed.sql  # 仅 local/demo profile 执行，生产不自动加载
```

不要使用 Hibernate 自动建表；Flyway 是权威结构来源。
