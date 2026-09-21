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
- 40 元触发管理员告警；50 元时关闭非管理员实时 AI，工单仍走人工流程。
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

