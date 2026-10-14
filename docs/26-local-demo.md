# Day 26：Docker 本地 demo 运行指南

部署方式为本地 Docker Compose，默认 Fake AI，不调用收费模型。Windows 只需要 Git、Docker Desktop（Linux containers）和 PowerShell；构建工具在镜像中运行，无需本机安装 Java、Python 或 Node。

## 1. 准备

启动 Docker Desktop，建议分配 6～8 GB 内存，至少准备 10 GB 可用磁盘。Docker Compose 要求 2.24.4 或更高版本（使用 `!reset` / `!override`）。首次构建需要访问 Docker 镜像仓库、Maven Central、npm 和 PyPI。

```powershell
cd F:\AfterSales
docker version
docker compose version
```

项目路径可以换成自己的克隆路径，后续命令均在项目根目录运行。

## 2. 一条命令启动

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 up
```

脚本首次创建被 Git 忽略的 `deploy/.env.demo`，生成独立 JWT/HMAC/数据库/中间件随机密钥；之后保留原配置。执行 Compose 配置检查、多阶段构建和 `up --wait`，所有带健康检查的服务就绪后才成功返回。Java 自动执行结构迁移和 demo seed；Python consumer 独立进程消费 MQ。首次构建可能需要数分钟。

浏览器打开 [http://localhost:8088](http://localhost:8088)。账号密码均为 `Demo@123456`：消费者 `customer01`、客服 `agent01`、管理员 `admin01`。这是本地演示数据，不应直接用于公网生产。

若 8088 或 9000 被占用，在首次启动前先生成不同端口的配置：

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\init-demo.ps1 -WebPort 18088 -MinioPort 19000
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 up
```

此时访问 `http://localhost:18088`。已有配置时脚本不会覆盖；可编辑 `deploy/.env.demo` 的 `WEB_PORT`、`MINIO_PORT`、`CORS_ALLOWED_ORIGINS` 和 `MINIO_PUBLIC_ENDPOINT`，保持端口与 URL 一致后重新 `up`。已有数据卷时不要修改基础设施账号密码：初始化密码只对空数据卷生效。

## 3. 运行验证

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 status
powershell -ExecutionPolicy Bypass -File .\deploy\smoke-demo.ps1
```

smoke 脚本验证页面、同源 API、演示登录、管理员权限和内部路径隔离。演示 seed 不自动上传知识文档，未标定 RAG 阈值时 AI 保守转人工属于预期行为。

### 3.1 页面演示流程

1. 打开 `http://localhost:8088`，用 `customer01 / Demo@123456` 登录。
2. 在“创建售后工单”中选择手机订单和“维修”，填写标题（例如“手机无法正常充电”）与问题描述，点击“提交工单”。成功后显示“待分配”，同时打开详情，可查看工单号和提交、入队时间线。
3. 同一订单项只能有一个活动工单。如果已有 `Day26 Docker smoke` 等未结束工单，新提交会提示“该订单项已有活动工单”；点击“我的工单”中的原工单继续演示。需要重新创建时，先打开原工单并点击 `CANCEL` 取消，再提交。
4. 关闭详情，点击右上角退出。用 `agent01 / Demo@123456` 登录，点击“客服队列”的“刷新”，找到相同工单号。点击工单行打开详情，查看问题描述、提案和时间线。刷新为手动操作，消费者提交后已打开的客服页面不会自动更新。
5. 退出后用 `admin01 / Demo@123456` 登录，查看运营统计。

JSON 中的工单、消息和时间线 ID 使用字符串，浏览器保留完整雪花 ID。工单详情抽屉关闭后可以再次打开；接口加载失败会显示错误提示。

### 3.2 旧版异常重复记录修复

旧版在同一订单项重复提交时，可能错误显示成功并留下“已提交”记录，实际未入队。修复代码后，新请求返回明确的 409 冲突且整体回滚；原有异常记录可用以下一次性脚本标记为“已取消”，保留内容和修复时间线。脚本仅处理无 guard、无时间线/消息/AI 任务、未分配且 version=0 的旧记录，可重复执行；不会取消正常的待分配工单。

```powershell
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
Get-Content -Raw -Encoding UTF8 .\deploy\repair-demo-tickets.sql |
    docker exec -i aftersales-demo-mysql-1 sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"'
```

## 4. 查看日志与排错

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 logs -Service aftersales-server
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 logs -Service ai-service
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 logs -Service ai-consumer
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 logs -Service web
```

日志命令输出最近 100 行并持续跟踪，Ctrl+C 退出日志查看。

| 现象 | 处理 |
|---|---|
| 无法连接 Docker daemon | 启动 Docker Desktop，切换 Linux containers，等待引擎就绪 |
| 镜像、依赖下载超时 | 检查 Docker Desktop 代理和网络；恢复后再次 `up` |
| 端口冲突 | 按第二节修改四项端口/URL 配置；不要停止其他项目来抢端口 |
| `up --wait` 超时 | 查看 status 和对应日志；首次数据库初始化完成后再次 `up` |
| 页面 502 | 查看 Java health/logs；构建或重建后再 `up`，Nginx 用 Docker DNS 重新解析服务地址 |
| 登录失败/数据库密码错误 | 检查是否改过已有数据卷的密码，恢复原配置；不要删除数据卷排错 |
| AI 任务不结束 | 查看 ai-consumer 和 RabbitMQ 日志；consumer 必须独立启动 |
| 附件 URL 包含 `minio:9000` | 浏览器签名应使用 `MINIO_PUBLIC_ENDPOINT`；修改配置后重新 `up` |

如果当前网络无法直连 Docker Hub，可在自己信任的镜像源拉取相同 tag 后，重新标记为 Dockerfile 中的原始镜像名；无需修改 Docker Desktop 全局设置。使用镜像源前应确认其可信性。例如本次验证使用以下方式预拉取（也适用于其他四个构建基础镜像）：

```powershell
docker pull --platform linux/amd64 docker.m.daocloud.io/library/python:3.11.11-slim-bookworm
docker tag docker.m.daocloud.io/library/python:3.11.11-slim-bookworm python:3.11.11-slim-bookworm
```

其余构建镜像是 `maven:3.9.9-eclipse-temurin-21`、`eclipse-temurin:21.0.6_7-jre-jammy`、`node:22.14.0-alpine`、`nginx:1.27.4-alpine`；镜像源偶尔返回 502 时需重试。项目默认仍使用官方镜像名，Dockerfile 不固定第三方镜像源。Windows x64 使用 `linux/amd64`；其他架构需改为对应平台。

## 5. 停止、再次启动与更新

```powershell
# 停止容器，保留容器和数据
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 stop
# 再次启动；代码修改后也执行此命令，自动重新构建
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 up
# 删除本 demo 的容器/网络，保留数据卷
powershell -ExecutionPolicy Bypass -File .\deploy\demo.ps1 down
```

不执行 `down -v`，避免丢失 MySQL、MinIO 和向量数据。项目名固定为 `aftersales-demo`，卷名以 `aftersales-demo_` 开头，与原开发 Compose 的 `aftersales_` 卷分离。

## 6. 不使用脚本时的等价命令

先用 `init-demo.ps1` 生成配置，然后执行：

```powershell
docker compose --project-name aftersales-demo --env-file deploy/.env.demo -f deploy/compose.yml -f deploy/compose.demo.yml config --quiet
docker compose --project-name aftersales-demo --env-file deploy/.env.demo -f deploy/compose.yml -f deploy/compose.demo.yml up -d --build --wait --wait-timeout 300
docker compose --project-name aftersales-demo --env-file deploy/.env.demo -f deploy/compose.yml -f deploy/compose.demo.yml ps
```

不要打印完整 `docker compose config`，输出会包含展开后的密码。

## 7. 配置和部署边界

- Web/Nginx：本机 8088；MinIO S3 签名下载：本机 9000；端口都绑定 `127.0.0.1`。MinIO bucket 私有，控制台和其他中间件没有宿主机入口。
- Java：demo profile；API 只通过 Nginx 访问，健康检查在容器内访问 readiness。Python API 的健康检查只表示进程可响应；依赖由 Compose 健康检查/启动顺序保证。consumer 的业务状态以日志和 Java 看板为准。
- Java 和 Python 为非 root；Python 单 worker，无 `--reload`；Java JRE 21 设置容器内存比例。数据服务自动重启，日志轮转。
- 下载分别使用内部和外部 MinIO client，签名区域固定 `us-east-1`（MinIO 默认）；AI 的 URL 白名单仍只允许内部 MinIO 地址。
- Demo 默认 `LLM_PROVIDER=fake`、`EMBEDDING_PROVIDER=fake`、8 维独立 collection，不读取宿主机 `ai-service/.env`。本次不把真实模型密钥传入容器；真实评测仍按原评测文档执行。
- 本次仅支持本机 HTTP。公网域名、TLS、首次管理员初始化和真实模型生产配置需另行实施；完整性能测试/恢复演练属于 Day 27。
