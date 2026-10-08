# 09. 安全、可观测性与成本

## 1. 威胁边界

需要重点保护：

- 用户订单、地址、联系方式和附件。
- 工单处理权限和退款金额。
- 模型 API Key、内部服务密钥和对象存储凭据。
- 知识文档及其权限。
- AI 工具调用和提示注入边界。

MVP 不以通过正式等保或支付合规为目标，但实现方式不得明显违背基本安全原则。

## 2. Web 安全

- Spring Security 默认拒绝，按路径/角色授权。
- 密码 BCrypt，生产环境强度参数结合硬件测试设置，不在文档硬编码绝对最优值。
- Refresh Token 轮换与撤销；登录错误不暴露用户是否存在。
- CORS 只允许配置的前端域名；生产禁用 `*` + credentials。
- 若 Refresh Token 使用 Cookie：`HttpOnly`、`Secure`、`SameSite=Lax/Strict`，状态变更增加 CSRF 防护。
- CSP 禁止非必要 inline script；前端不渲染未经清理的 HTML。
- 登录、AI 对话、上传、确认提案等接口分别限流。
- OpenAPI、Actuator 生产只暴露必要端点或限制管理员/内网。

## 3. 文件安全

- 白名单格式、大小、文件头；文件名不用于对象 key。
- MinIO bucket 私有，不返回永久 URL。
- 下载前校验工单/管理员权限，签名 URL 建议 5 分钟。
- 文档/附件使用随机 key，防路径穿越。
- 可集成 ClamAV 作为增强项；未集成时在 README 明确“不具备完整恶意文件扫描能力”。
- PDF/DOCX 解析放在 Python 容器，限制 CPU、内存、处理时长和页数。

## 4. AI 安全

- Python 无业务数据库账号。
- Java 内部工具只读且资源 ID 与当前工单绑定。
- 不向模型发送不必要的姓名、电话、完整地址、密码、Token。
- 文档和用户文本视为不可信数据。
- 不输出隐藏思维链；仅提供可审计的简要原因、工具状态和引用。
- 高风险操作必须经客服审核、用户确认和 Java 校验。
- 管理员上传知识文件也不能获得工具权限。
- 对 Prompt Injection、越权查询、伪造订单号建立评测用例。

## 5. 内部服务安全

- Java/Python 使用不同 HMAC 服务身份和密钥。
- 签名覆盖 timestamp、nonce、method、path、body hash。
- Nonce 放 Redis 5 分钟防重放；无 Redis 时仍校验 timestamp，但生产应保证 Redis 可用。
- Docker 网络不映射 Python、MySQL、Redis、RabbitMQ、Qdrant、MinIO API 到公网。
- 密钥由 `.env`/服务器 secret 注入；`.env.example` 只有占位符。

## 6. 审计

必须审计：

- 登录成功/失败、账号启停。
- 工单分配、转派、状态变更、拒绝、关闭。
- 提案创建、审核、确认、拒绝和执行。
- 售后规则、知识文档、AI 配置变更。
- 管理员查看敏感审计数据。

审计日志记录前后差异但脱敏。查询需分页并限制管理员权限。日志保留策略演示环境可 90 天，实际生产由合规要求决定。

## 7. 可观测性

### 7.1 Trace

- 入口生成/接受合规 `X-Trace-Id`，Java、RabbitMQ envelope、Python、回调全链路传递。
- 不信任任意超长/非法外部 traceId；不合法时重新生成。
- 可选 OpenTelemetry；4 周 MVP 至少实现结构化日志关联。

### 7.2 Metrics

Java 和 Python 分别暴露 Prometheus metrics。建议告警：

- AI 5 分钟错误率 > 20%。
- DLQ 消息 > 0。
- Outbox 未发布 > 100 或最老超过 5 分钟。
- 提案执行失败率异常。
- 单日估算 AI 费用超过 2 元、累计超过 40 元。
- 磁盘/MinIO 容量过高。

### 7.3 日志

本地输出控制台；云端可先使用 Docker JSON logs + 日志轮转。Prometheus/Grafana 是推荐展示项，Loki 可作为加分项，不阻塞 MVP。

## 8. 模型成本治理

- 配置 `daily_soft_limit_micros`、`project_hard_limit_micros`。
- Day 23 在管理看板展示 40 元预警和 50 元限额状态，仅监测，不自动阻断。关闭非管理员实时 AI 的策略留到完整计费后实现，工单始终可走人工流程。
- 价格配置格式：provider、model、input/output 每百万 Token 单价、币种、生效日期、来源 URL/备注。
- 只有确认过官方价格才计算金额；未知时 `estimated_cost_micros=null`，不假装精确。
- 调用前估算上下文长度，超过上限做摘要/截断，不发送无限历史。

## 9. 备份和恢复

- MySQL 每日逻辑备份；演示环境保留 7 天。
- MinIO 文档与附件随 volume/对象存储备份。
- Qdrant 可从文档重建，但重建耗时；可选 snapshot。
- Redis、RabbitMQ 不作为长期业务事实来源。
- 恢复演练至少执行一次：新环境恢复 MySQL/MinIO，再重建 Qdrant 索引。

## 10. 已知限制

- 模拟退款不能证明真实支付系统集成能力。
- 未实现 OCR 时，扫描 PDF 无法可靠解析。
- 提示注入检测不能保证完全阻止攻击，真正安全来自最小权限和 Java 执行边界。
- 单体部署不提供跨区域高可用；这是项目范围选择，不包装为生产级无限扩展。

## 11. Day 22 实施契约

- `/api/v1/admin/**` 仅 ADMIN；未声明的路径默认拒绝，内部接口仍必须经过 HMAC。CORS 来源由 `CORS_ALLOWED_ORIGINS` 精确配置。
- 登录/刷新按客户端地址限流，AI 对话/分析、上传、提案确认按用户限流，固定 60 秒窗口默认分别为 20/30/10/10 次。返回 HTTP 429、`RATE_LIMITED` 和 `Retry-After`；不信任客户端转发地址。Redis 原子计数，生产 Redis 故障返回 503；本地可回退到有容量上限的进程内计数。
- 工单附件支持 PNG/JPEG/PDF，最大 10 MiB；知识文档支持 UTF-8 TXT/Markdown、PDF、DOCX，最大 20 MiB。校验扩展名、声明 MIME 和文件内容，拒绝路径文件名与危险压缩包。
- 附件上传/列表/下载校验工单归属；消费者仅本人、客服仅分配给本人、管理员可访问。知识文档上传/下载仅管理员。对象 key 随机，私有 bucket；下载地址 300 秒失效，并强制 attachment 下载。
- AI 对话先校验工单访问权限，只接收 message，浏览器不能注入工具或检索上下文。内部订单工具必须传 ticketId，并验证订单与工单绑定。
- 当前浏览器对话仅检索 `scopeType=GLOBAL`，商品/SKU/CATEGORY 范围文档需后续接入 Java 可信检索上下文，客户端不能自行扩大范围。
- Java 出站签名使用 `JAVA_INTERNAL_SECRET`，Python 出站签名使用 `AI_INTERNAL_SECRET`；生产禁止默认/占位密钥，要求至少 32 字节且两者不同。生产只加载结构迁移。
- 本地基础设施端口仅绑定 127.0.0.1。未集成恶意文件扫描；PDF/DOCX 容器资源隔离仍在后续范围。

## 12. Day 23 实施契约

- Java 控制台输出 ECS JSON；Python 输出 JSON。应用自有请求和调用日志仅记录方法、路由模板、状态、耗时、调用 ID/业务 ID、错误分类与合规 traceId，不记录正文、查询字符串、Token、密钥、签名 URL 或完整异常消息。框架诊断日志仍需按部署环境设置级别和访问控制。
- traceId 允许 `[A-Za-z0-9_-]{1,64}`；不合法时重新生成。HTTP、Outbox envelope、Python consumer、HMAC 回调和 SSE 代理传递同一 traceId。
- Java `/actuator/prometheus` 仅管理员；Python `GET /internal/v1/metrics` 必须 HMAC。HTTP/AI 指标仅使用有限路由、operation/status/provider 标签，不以用户/工单/traceId 作标签。
- `ai_call_log` 保存每次模型调用（含失败）的元数据，callId 唯一，重复回调不重复计数。Token 缺失为 NULL，不能把字符数或字节数当作 Token；Fake 成本为 0，Fake Token 为未知。真实模型没有供应商 usage 或未配置已核实 CNY 价格时成本为 NULL。
- 价格从服务器配置注入，包含 provider/model、每百万 Token 输入/输出人民币单价、生效日期和来源。Java 统一使用十进制定价，按人民币微元向上取整；不接受回调自报费用，不自动做汇率转换。
- 管理看板以 Asia/Shanghai 自然日筛选，from/to 均包含，最多 366 天；趋势补齐零调用日期。已知成本小计和未知调用数量分开显示，预算显示已知成本是否达到 2/40/50 元阈值，存在未知成本时不宣称实际费用未超标。
- 本次提供只读概览/用量/预算状态，不引入自动阻断策略、价格在线编辑、Grafana/Loki 部署或真实 Python Provider。
- 已有分析统一迁为历史未知，旧 RabbitMQ 分析消息以 `legacy-{taskId}` 幂等兼容、费用保持未知。Java Mapper 扫描仅限 `@Mapper` 接口，业务调度接口不作为 Mapper 注册。
- JWT 鉴权结果保存在当前请求属性中，供 SSE 的 ASYNC/ERROR 二次分派恢复身份；无 HTTP Session，初始请求仍执行路径授权和工单资源权限校验。
