# AfterSales Copilot（智售后）开发文档

面向 3C 电商售后场景的 Java + Python AI 全栈项目。Java 服务负责用户、订单、工单、状态机、权限、事务和审计；Python 服务负责分类、信息抽取、RAG、回复建议、摘要和处理提案。任何退款、换货或维修状态变更都必须由 Java 服务校验并执行，AI 不直接访问或修改业务数据库。

本仓库当前交付物是项目开发规范，后续代码必须以 `docs/` 中的契约为准。发生设计变更时，应先修改文档，再修改代码。

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

工单创建事务会同时写入 `ai_task` 和 `outbox_event`；Java 定时发布器投递 `ticket.ai.analyze.requested.v1`，Python 消费后通过 HMAC 回调 Java，结果幂等写入 `ai_analysis`。真实 OpenAI-compatible Provider 需启用 `real-ai` profile 并配置 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_CHAT_MODEL`。

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
