# Day 24 跨服务契约

固定样例由 Java/Python/前端共同读取，不需要启动数据库、RabbitMQ 或模型。

- `ticket-analysis-request.json`：当前 Java Outbox 的 MQ envelope（分析事件只包含 ID，事实查询不在本次范围）。
- `ticket-analysis-callback.json`：Python → Java 分析回调，含 64 位 ID、nullable usage 与人工降级。
- `chat-sse.json`：Python → Java → 浏览器的 meta/token/done 事件。
- `*.schema.json`：JSON Schema draft 2020-12，Python 检查样例和运行输出，Java 检查实际生成与消费，前端检查拆包。

事件名称、必填字段或版本改变时应同时更新 Schema、样例与三端测试。未知 MQ schemaVersion 必须在调用模型前拒绝。内部 ID 保持整数，前端不要读取 MQ 或回调的 64 位 ID；公共业务 API 的 ID 使用字符串。
