# 11. 部署手册

## 1. 开发机准备（Windows 11）

已知开发环境是 Windows 11、Docker 29.7.2、16GB 内存和某款 IDE（具体 IDE 未指定）。还需要：

- 安装 JDK 21 LTS；保留 JDK 24 作为其他项目使用。
- Maven 可使用项目 Wrapper，无需全局固定版本。
- Python 3.11。
- Node.js 当前 LTS。
- Git。

16GB 内存下建议 Docker Desktop 分配 6～8GB，不在本地部署大参数 Ollama 模型；使用云 API 控制成本。一次启动 MySQL、Redis、RabbitMQ、Qdrant、MinIO 通常可行，Prometheus/Grafana 可按需启动。

## 2. 目录和 Compose

```text
deploy/
├─ compose.yml
├─ compose.observability.yml
├─ nginx/
├─ mysql/
├─ rabbitmq/
├─ prometheus/
└─ .env.example
```

基础服务：

- `mysql:8.0`（固定可验证的小版本/镜像 digest）。
- `redis:7`。
- `rabbitmq:3-management`。
- `qdrant/qdrant`。
- `minio/minio`。

编码时应固定经过本地验证的具体 tag，不使用 `latest`；本文不猜测未来可用 tag。

## 3. 环境变量

```env
MYSQL_DATABASE=aftersales
MYSQL_USER=aftersales
MYSQL_PASSWORD=<strong-random>
MYSQL_ROOT_PASSWORD=<strong-random>

REDIS_PASSWORD=<strong-random>
RABBITMQ_USER=aftersales
RABBITMQ_PASSWORD=<strong-random>

MINIO_ROOT_USER=<strong-random>
MINIO_ROOT_PASSWORD=<strong-random>
MINIO_BUCKET=aftersales-private

JWT_SECRET=<at-least-32-random-bytes>
AI_INTERNAL_SECRET=<different-random-secret>

LLM_PROVIDER=dashscope
LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_API_KEY=<secret>
LLM_CHAT_MODEL=<verified-model-name>
EMBEDDING_MODEL=<verified-model-name>

PUBLIC_BASE_URL=https://support.example.com
CORS_ALLOWED_ORIGINS=https://support.example.com
```

`.env` 加入 `.gitignore`；只提交 `.env.example`。模型名和价格必须在申请账号后依据供应商控制台/官方文档填写。

## 4. 本地开发启动顺序

```powershell
## 1. 基础设施
docker compose -f deploy/compose.yml up -d mysql redis rabbitmq qdrant minio

## 2. Java（IDEA 或）
.\mvnw.cmd -pl bootstrap -am spring-boot:run

## 3. Python
cd ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --reload --port 8000

## 4. Vue
cd web
npm ci
npm run dev
```

实际 Maven module 命令在创建代码后按根工程确认。不要在脚本中假设个人 JDK 绝对路径。

## 5. Profile

| Profile | AI | 数据 | 用途 |
|---|---|---|---|
| `test` | Fake Provider | Testcontainers | 自动测试 |
| `local` | 云模型或 Fake 可切换 | local seed | 开发 |
| `demo` | 云模型 + 预算限制 | 脱敏演示数据 | 录屏/在线演示 |
| `prod` | 云模型 | 不自动 seed | 云部署 |

必须实现 Fake Provider：固定输出分类、引用和 token 流，保证离线演示核心业务、E2E 和 CI 不依赖付费 API。

## 6. 容器化

### Java

- 多阶段构建：Maven 构建 + JRE 21 运行。
- 使用非 root 用户。
- JVM 在容器内设置合理 MaxRAMPercentage。
- `/actuator/health/liveness`、`readiness` 健康检查。

### Python

- `python:3.11-slim` 的固定补丁 tag。
- 非 root 用户，安装依赖后清除缓存。
- 生产不使用 `--reload`。
- 单 worker 起步；流式和 I/O 使用 async。增加 worker 前确认 RabbitMQ consumer 和内存行为。

### Web/Nginx

- Node 构建，Nginx 静态托管。
- `/api/` 代理 Java。
- 禁止将 `/internal/` 代理公网。
- SSE location 关闭代理缓冲，增加读取超时。

## 7. 云服务器建议

完整中间件在 2C4G 上可能紧张；建议：

- 最低演示：2C4G，关闭 Grafana/Prometheus，严格内存限制，仅少量访问。
- 推荐演示：4C8G。
- 生产思路：托管 MySQL/Redis/对象存储，但会增加费用，不属于 50 元模型预算。

如果服务器预算不足，可以只本地 Docker 演示 + 录屏，不为了“在线地址”牺牲稳定性。

## 8. 域名与 HTTPS

1. 域名解析到服务器。
2. 防火墙只开放 22（限制来源）、80、443。
3. Nginx/Caddy 获取 Let's Encrypt 证书。
4. HTTP 重定向 HTTPS。
5. Refresh Cookie 设置 Secure。
6. MinIO 控制台、RabbitMQ 管理台、Qdrant 不开放公网。

## 9. 初始化

- Flyway 自动创建结构。
- `demo` profile 显式运行 seed，生产不加载默认账号。
- 管理员首次密码通过环境变量/一次性初始化命令创建，首次登录强制修改是增强项。
- 知识文档 seed 上传后触发索引，等待 `INDEXED` 再运行 RAG 测试。

## 10. 发布与回滚

- 镜像 tag 使用 Git SHA，不用 `latest`。
- 发布前备份 MySQL，检查 Flyway 迁移是否向后兼容。
- 先更新 Python（保持旧契约兼容），再 Java，再 Web；破坏性契约使用 v2。
- 回滚应用不能盲目回滚已执行数据库迁移。迁移优先采用扩展→双读/迁移→收缩。

## 11. 运维命令

```powershell
docker compose -f deploy/compose.yml ps
docker compose -f deploy/compose.yml logs -f aftersales-server
docker compose -f deploy/compose.yml logs -f ai-service
docker compose -f deploy/compose.yml restart ai-service
```

删除 volume 会丢失数据，不把 `down -v` 写入日常停止脚本。需要清理演示数据时必须明确备份和目标目录。
