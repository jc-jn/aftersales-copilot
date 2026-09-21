# 13. Vibe Coding 执行指南

## 1. 使用原则

不要把整套系统一次性交给 AI 生成。每次只实现一个可验证的垂直切片，并向编码助手提供：

1. 本文档中的相关章节。
2. 当前仓库真实文件和已有约定。
3. 明确的输入、输出、非目标和验收命令。
4. “不得修改无关代码、不得虚构依赖/API、遇到歧义先报告”的约束。

每轮生成后必须人工检查数据库迁移、权限、状态机、金额和并发逻辑。

## 2. 推荐实施顺序

```text
工程脚手架
→ Flyway + 数据模型
→ 登录/权限
→ 订单查询
→ 工单创建/状态机
→ 客服工作台
→ 提案与四类执行器
→ Outbox/RabbitMQ
→ Python 分析
→ 文档索引/RAG
→ SSE 对话
→ 管理/统计
→ 部署与测试
```

## 3. 通用提示词模板

```text
你正在实现 AfterSales Copilot。

事实来源：
- docs/XX-....md
- docs/03-database-design.md
- docs/04-api-design.md

本次只实现：[一个具体用例]。

要求：
1. 先阅读仓库现有代码和上述文档，列出将修改的文件和关键约束。
2. 不修改无关模块，不重命名已有公共契约。
3. 不虚构依赖版本、外部 API 或数据库字段；不确定时使用接口/配置占位并说明。
4. Controller 不直接调用 Mapper；遵守 api/application/domain/infrastructure 分层。
5. 金额使用 long 分，ID JSON 使用字符串，时间使用 UTC。
6. 所有状态修改校验 version；危险命令实现幂等。
7. 同步补充单元/集成测试和 OpenAPI。
8. 完成后运行相关测试，报告实际结果和未完成项。

验收标准：
- [列出可执行标准]

禁止：
- AI 服务写业务数据库
- 硬编码密钥
- 用 TODO 伪装已完成
- 为通过测试删除安全或业务校验
```

## 4. 阶段提示词示例

### 4.1 初始化 Java 工程

```text
根据 README、docs/02-architecture.md 和 docs/05-java-backend-design.md，创建 Java 21 Maven 多模块骨架，只包含 bootstrap/common/auth/catalog/order/ticket/proposal/knowledge/aiadapter/statistics/audit。使用 Spring Boot 3.5 系列当前可获得的稳定补丁版本，并在执行前通过项目生成器或 Maven Central 元数据确认版本，不要猜测。添加 Maven Wrapper、基础 profile、Actuator、Flyway 和测试配置。暂不写业务逻辑。运行 mvnw test 验证。
```

### 4.2 生成数据库迁移

```text
严格依据 docs/03-database-design.md 创建 V1～V4 Flyway SQL 和对应 PO/Mapper。先指出文档中任何无法落实的约束；不得自行新增不同命名。MySQL 使用 utf8mb4，金额 BIGINT，时间 DATETIME(3)，枚举 VARCHAR。为所有查询路径创建文档规定的索引。加入 Testcontainers migration 测试。
```

### 4.3 工单状态机

```text
实现 docs/01-requirements.md 的 TicketStateMachine。状态转换必须集中定义，非法转换返回 TICKET_INVALID_TRANSITION；状态变更增加 version 并写 timeline/outbox。不要实现 AI。为每条合法转换和代表性非法转换写参数化测试。
```

### 4.4 提案幂等执行

```text
实现 docs/04-api-design.md 的 POST /proposals/{id}/confirm 和 docs/05-java-backend-design.md 的 confirmAndExecute 顺序。本轮只支持 REFUND_ONLY。要求数据库唯一键、请求 hash、行锁/乐观锁、金额重新计算、审计和并发集成测试。不要调用真实支付。
```

### 4.5 Python 工单分析

```text
依据 docs/06-python-ai-design.md 和 docs/07-rag-and-agent-design.md 创建 Python 3.11 FastAPI 服务的 ticket analysis 垂直切片。先实现 FakeProvider 和 Pydantic Schema，再实现一个 OpenAI-compatible provider。严格 JSON 校验，失败最多修复一次。不要连接 MySQL，不实现写工具。使用 pytest 和 respx 测试。
```

### 4.6 SSE 代理

```text
实现 Python /internal/v1/chat/stream、Java /api/v1/tickets/{id}/ai-chat/stream 和 Vue useSseChat。事件必须严格匹配 docs/04-api-design.md。Java 校验内部流事件并持久化一次；浏览器用 POST fetch 流，处理拆包、取消、error/done。禁止输出模型思维链。
```

## 5. 每轮审查清单

- 是否真实运行过测试，而不是只声称通过？
- 是否出现文档之外的状态或字段？
- 是否把 AI 建议当成 Java 业务事实？
- 是否存在 Controller→Mapper、跨模块 Mapper 等捷径？
- 是否存在金额浮点、ID 精度、时区错误？
- 是否在日志/响应中泄露密钥或隐私？
- 是否对网络调用设置超时、重试边界和降级？
- 是否对重复请求、MQ 重投、并发确认进行了处理？
- 是否提交锁文件、迁移和测试？

## 6. 处理设计变更

当编码中发现文档不合理：

1. 不让 AI 静默自行决定。
2. 写 ADR 或先修改对应 Markdown，说明原设计、问题、新设计和影响。
3. 同步修改数据库/API/测试章节。
4. 通过迁移兼容已有数据，不重置数据库掩盖问题。

## 7. Git 规范

分支：`main` 保持可演示；功能分支 `feat/ticket-state-machine`、`feat/rag-indexing`。

提交建议：

```text
feat(ticket): implement ticket creation and active-ticket guard
feat(proposal): add idempotent refund-only execution
feat(ai): add ticket analysis schema and fake provider
test(ticket): cover invalid state transitions
docs(api): clarify proposal confirmation contract
fix(security): bind tool order id to ticket context
```

每个提交聚焦一个逻辑变化；不要提交 `.env`、IDE 用户配置、生成日志、模型缓存和上传文件。

