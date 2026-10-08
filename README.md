# AfterSales Copilot（智售后）开发文档

面向 3C 电商售后场景的 Java + Python AI 全栈项目。Java 服务负责用户、订单、工单、状态机、权限、事务和审计；Python 服务负责分类、信息抽取、RAG、回复建议、摘要和处理提案。任何退款、换货或维修状态变更都必须由 Java 服务校验并执行，AI 不直接访问或修改业务数据库。

当前已完成前三周功能及第 4 周 Day 22–25，代码以 `docs/` 中的契约为准。发生设计变更时，应先修改文档，再修改代码。Day 25 的真实评测已执行；候选阈值是否可采用以评测报告为准。

## 固定技术基线

- Java 21 LTS（本机 JDK 24 可保留，但项目编译、CI 和镜像统一使用 21）
- Spring Boot 3.5.x、Spring Security、MyBatis-Plus
- Python 3.11、FastAPI、LangGraph/LangChain
- Vue 3、TypeScript、Vite、Element Plus、Pinia
- MySQL 8、Redis 7、RabbitMQ 3、Qdrant
- Docker Compose；生产演示支持 Nginx、HTTPS、MinIO
- 通义千问/DeepSeek，统一通过 OpenAI-compatible 配置切换

## 文档索引

1. [项目总览](docs/00-project-overview.md)
2. [需求规格](docs/01-requirements.md)
3. [系统架构](docs/02-architecture.md)
4. [数据库设计](docs/03-database-design.md)
5. [API 与跨服务契约](docs/04-api-design.md)
6. [Java 后端设计](docs/05-java-backend-design.md)
7. [Python AI 服务设计](docs/06-python-ai-design.md)
8. [RAG、Agent 与 Prompt 设计](docs/07-rag-and-agent-design.md)
9. [前端设计](docs/08-frontend-design.md)
10. [安全、可观测性与成本](docs/09-security-and-observability.md)
11. [测试方案](docs/10-testing.md)
12. [部署手册](docs/11-deployment.md)
13. [四周开发路线](docs/12-development-roadmap.md)
14. [Vibe Coding 执行指南](docs/13-vibe-coding-guide.md)
15. [简历与面试说明](docs/14-resume-and-interview.md)

## 关键约束

- 项目不实现购物车、下单、真实支付、真实退款和物流平台对接；订单、支付和物流使用可复现的模拟数据。
- 售后类型固定为：仅退款、退货退款、换货、维修。
- 第一版采用“模块化单体 Java 服务 + 独立 Python AI 服务”，不拆 Spring Cloud 微服务。
- 浏览器只访问 Java API；Java 代理 AI 流式输出，Python AI 服务不直接暴露给公网。
- RabbitMQ 用于文档处理、工单 AI 分析和结案摘要；用户对话使用同步 SSE，不经过 MQ。

### 第三周 AI 异步链路

本地默认使用 Fake Provider，不调用收费模型。启动 Java 服务和基础设施后，启动 Python API：

```powershell
cd ai-service
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000
```

另开进程启动工单分析消费者：

```powershell
.\.venv\Scripts\python.exe -m app.consumer
```

工单创建事务会同时写入 `ai_task` 和 `outbox_event`；Java 定时发布器投递 `ticket.ai.analyze.requested.v1`，Python 消费后通过 HMAC 回调 Java，结果幂等写入 `ai_analysis`。Java 兼容 Provider 可通过 `real-ai` profile 配置；当前异步分析/对话主链路使用 Python Fake Provider，不能仅启用 Java profile 就切换该链路。

知识库管理员上传接口为 `POST /api/v1/admin/knowledge/documents`（multipart 字段：`file`、`title`、`documentType`、`scopeType`、`scopeId`、`versionLabel`）。Java 会先写入 MinIO，再创建 `DOCUMENT_INDEX` AI task 和 Outbox 事件；Python 解析 Markdown/TXT、PDF 或 DOCX，按标题/段落切片，使用 Fake Embedding 写入 Qdrant，成功后回调 `/internal/v1/ai-results/document-index`。

Day 19/20 已增加 RAG 检索和对话链路：`ai-service/evals/rag_cases.jsonl` 是初始评测集；Python `POST /internal/v1/chat/stream` 返回 `meta/token/done` SSE，Java `POST /api/v1/tickets/{ticketId}/ai-chat/stream` 做鉴权和代理，前端 [useSseChat.ts](/F:/AfterSales/web/src/useSseChat.ts) 负责浏览器端拆包。Python 只通过 `/internal/v1/tools/*` 读取订单、物流和工单历史，不提供退款或业务写工具。

Day 21：成功分析且通过安全条件时，Java 自动保存 `source=AI,status=DRAFT` 的草稿提案；客服/管理员必须通过已有 `POST /proposals/{id}/publish` 审核发布，用户才能确认。AI 失败会回写失败任务并保留人工处理路径；客服/管理员可调用 `POST /tickets/{id}/ai-analysis/retry` 重试，最多 3 次，Outbox 发布器也限制失败事件最多重投 3 次。
- Qdrant 只存向量和检索元数据；知识库、权限及业务状态的权威数据保存在 MySQL。
- 所有金额以“分”为单位使用 `BIGINT`，所有时间使用 UTC 存储、前端按 Asia/Shanghai 展示。

## 本地演示账号

`local` 和 `demo` profile 会加载脱敏演示数据，`prod` 不加载默认账号。以下账号密码均为 `Demo@123456`：

- 消费者：`customer01`
- 客服：`agent01`
- 管理员：`admin01`

登录接口为 `POST /api/v1/auth/login`，也支持使用邮箱登录。部署前必须通过 `JWT_SECRET` 提供至少 32 字节的随机密钥，并移除或覆盖演示账号。

## Day 22 安全配置与附件

`/api/v1/admin/**` 仅管理员可访问。CORS 使用 `CORS_ALLOWED_ORIGINS` 精确域名白名单，默认本地前端 `http://localhost:5173`；浏览器继续使用 Bearer JWT。

- 登录/刷新/登出按 IP 每分钟 20 次；AI 按用户 30 次；上传与提案确认各 10 次。配置为 `RATE_LIMIT_AUTH/AI/UPLOAD/CONFIRM`，超限返回 429 和 `Retry-After`。
- `POST/GET /api/v1/tickets/{ticketId}/attachments` 上传/列表；`GET /api/v1/tickets/{ticketId}/attachments/{attachmentId}/download` 获取 300 秒下载地址。消费者仅本人工单，客服仅分配给本人的工单，管理员可访问。附件限 PNG/JPEG/PDF，10 MiB；知识文档限 TXT/MD/PDF/DOCX，20 MiB。
- bucket 保持私有，校验文件 MIME 与内容，随机对象 key，强制下载。**尚未集成完整恶意文件扫描能力**；格式校验不替代杀毒扫描。
- HMAC 分离 `JAVA_INTERNAL_SECRET`（Java 发往 Python）与 `AI_INTERNAL_SECRET`（Python 发往 Java）。Redis 存储防重放 nonce 和限流计数；仅本地开发可回退到进程内存，生产不可回退。
- 浏览器对话目前仅检索 GLOBAL 文档，商品范围检索需后续加入 Java 可信上下文；客户端不能自行指定检索权限。
- `prod` 要求三种签名密钥至少 32 字节且互不相同，拒绝默认/占位密钥、开发基础设施密码以及与 `local/demo` 混用。配置参考 [.env.example](.env.example) 与 [部署手册](docs/11-deployment.md)；Java 需通过环境变量导入，Python 读取 `ai-service/.env`。
- 演示 seed 改为 `V1000__demo_seed.sql`。已有旧 V9 seed 数据库先按部署手册检查历史并备份；不要直接修改迁移历史。

验证：`.\mvnw.cmd test`；Python 在 `ai-service` 下执行 `.\.venv\Scripts\python.exe -m pytest -q`。Docker 29 与旧 docker-java API 不兼容时，可用 `.\mvnw.cmd test '-Dapi.version=1.44'`，Testcontainers 仍需要能拉取测试镜像。

## Day 23 可观测性与管理看板

管理员 `admin01` 登录前端后进入运营看板：上海自然日筛选、工单状态、AI 任务/文档状态、Outbox backlog、调用成功率、每日调用/成本趋势、模型用量和 CSV 导出。普通用户看不到管理员统计，后端也校验角色。

- Java 输出 ECS JSON、Python 输出 JSON，HTTP/MQ/回调/SSE 传递 `X-Trace-Id`；不记录 Prompt、Token、签名 URL 和原始异常消息。
- Java 指标 `/actuator/prometheus` 仅管理员；Python `GET /internal/v1/metrics` 需 HMAC。指标名和鉴权采集方式见 [部署手册](docs/11-deployment.md)。Python consumer 为独立进程，持久用量以 MySQL 为准。
- `ai_call_log` 按 callId 去重记录成功/失败调用。Fake 免费且不伪造 Token；真实用量或价格未核实时成本为未知。`AI_PRICES_JSON=[]` 默认不计未经核实的模型费用，价格按已核实 CNY 来源配置。
- V11 将历史分析 usage 标为未知；旧消息兼容为 `legacy-{taskId}`。预算默认 2/40/50 元，**仅监测预警，未自动阻断 AI**；存在未知成本时实际费用仍需核实。
- Day 23 时 Python 仅支持 Fake；Day 25 已增加 DeepSeek/SiliconFlow 适配。业务预算自动关闭策略和完整监控部署尚未实现。Java 旧 `real-ai` Provider 不在 Python 主调用链中。

## Day 24–25 测试与 RAG 评测

Java/Python/前端复用 `contracts/` 的 MQ、分析回调、SSE 样例；前端新增 `npm test`。SSE 支持字节拆包、Unicode、CRLF、心跳，并识别错误与不完整结束。

真实模型配置：DeepSeek `deepseek-v4-pro`，SiliconFlow `BAAI/bge-m3`（独立密钥，实测1024维）。普通测试使用 Fake，不产生模型费用。评测包含47条虚构标注问题、阈值扫描、独立验证、注入与无答案、引用和费用记录。说明和命令见 [评测手册](ai-service/evals/README.md)。Prompt 注入检测、引用来源验证、Java 风险提案拦截和无证据转人工均已接入。

阈值未配置时关闭证据回答；候选未通过验证时保留报告，不自动写入阈值。切换 Embedding 后使用新 Qdrant collection 并重新索引，不能复用原 Fake 向量。当前MQ分析消息只包含ID，因此缺少事实和证据时保守转人工。
