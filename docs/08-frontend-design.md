# 08. 前端设计

## 1. 技术栈

- Vue 3 + TypeScript + Vite。
- Vue Router、Pinia、Element Plus。
- Axios 用于普通 API；原生 `fetch` + ReadableStream 解析 POST SSE。
- ECharts 用于管理员统计。
- 不引入大型低代码框架，保持面试时可解释。

## 2. 应用形态

一个前端工程，按角色动态路由：

```text
src/
├─ api/             # 按 auth/order/ticket/admin 分组
├─ components/      # 通用组件
├─ composables/     # useSseChat/usePermission 等
├─ layouts/         # Customer/Agent/Admin
├─ router/
├─ stores/          # auth、ticket、notification
├─ types/           # 从 OpenAPI 生成或与契约同步
├─ utils/
└─ views/
   ├─ customer/
   ├─ agent/
   └─ admin/
```

建议通过 OpenAPI 生成 TypeScript API Client，减少手写字段漂移；生成代码不要混入业务组件。

## 3. 页面清单

### 3.1 公共

- 登录页。
- 403/404/500。
- 个人信息、退出登录。

### 3.2 消费者

- 订单列表和订单详情。
- 售后资格选择弹窗。
- 创建工单页。
- 我的工单列表。
- 工单详情：状态、时间线、对话、附件、当前提案、退货物流、评价。
- AI 助手侧栏：流式回答、引用卡片、转人工提示。

### 3.3 客服

- 工作台：待领取、我的待处理、待用户、待退货、超时。
- 工单队列筛选。
- 客服工单详情：用户问题、订单事实、物流、质保资格、时间线。
- AI Copilot 面板：分类、缺失信息、建议回复、引用、风险。
- 回复编辑器：一键插入 AI 建议但必须允许修改。
- 提案编辑/审核对话框。
- 转派、拒绝、收货检查和结案对话框。

### 3.4 管理员

- 概览看板。
- 用户和客服管理。
- 商品、SKU、订单和模拟物流管理。
- 售后规则管理。
- 知识库和文档索引状态。
- AI 配置与连接测试。
- AI 用量与成本统计。
- 审计日志。

## 4. 关键交互

### 4.1 工单状态

状态展示统一由映射表提供颜色和中文，不在多个组件重复写：

```ts
const ticketStatusMeta = {
  SUBMITTED: { label: '已提交', color: 'info' },
  PENDING_ASSIGNMENT: { label: '待分配', color: 'warning' },
  PENDING_AGENT: { label: '待客服处理', color: 'warning' },
  PENDING_CUSTOMER: { label: '待用户补充', color: 'warning' },
  WAITING_RETURN: { label: '待寄回', color: 'warning' },
  RETURN_IN_TRANSIT: { label: '退货运输中', color: 'primary' },
  RETURN_RECEIVED: { label: '已收到退货', color: 'primary' },
  PROCESSING: { label: '处理中', color: 'primary' },
  RESOLVED: { label: '已解决', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'danger' },
  CANCELLED: { label: '已取消', color: 'info' },
  CLOSED: { label: '已关闭', color: 'info' }
} as const
```

按钮是否显示由后端返回 `availableActions` 为准，前端角色判断只改善体验，不作为安全控制。

### 4.2 乐观锁冲突

提交状态命令后收到 `TICKET_VERSION_CONFLICT`：

1. 提示“工单已被其他操作更新”。
2. 重新加载详情。
3. 不自动重复危险操作。

### 4.3 提案确认

- 显示售后类型、金额、是否寄回、有效期和政策依据。
- 用户点击确认前二次确认。
- 前端生成 ULID/UUID 作为 `Idempotency-Key`，在本次请求完成前保存在内存；网络重试复用同一个 Key。
- 成功后禁用按钮并刷新工单；超时先查询提案结果，不生成新 Key 盲目重试。

### 4.4 SSE

`useSseChat` 负责：

- `AbortController` 取消。
- 逐行解析 SSE，处理跨 chunk 缓冲。
- 白名单事件 `meta/tool_status/citation/delta/done/error`。
- `delta` 累积内容，引用按 ID 去重。
- 页面离开时 abort。
- 错误后展示重试和转人工，不自动重复请求。

消息内容按纯文本/安全 Markdown 渲染。若支持 Markdown，必须禁用原始 HTML 并做 XSS 清理。

## 5. 状态管理

- `authStore`：当前用户、Token、刷新状态。
- Access Token 建议只存在内存；Refresh Token 更安全的方案是 HttpOnly Secure Cookie。若 MVP 为简化使用 localStorage，必须在文档和面试中承认 XSS 风险并加强 CSP；推荐实际实现 Cookie 方案。
- `ticketStore` 仅缓存当前详情和筛选条件，不复制所有服务端数据。
- API 401 时只允许一个 refresh 请求，其余请求排队；刷新失败统一退出。

## 6. 表单与可用性

- 工单描述 10～2000 字。
- 附件前端先校验数量/大小/MIME，但后端必须重复校验。
- 金额以分从 API 接收，统一格式化为元；禁止浮点计算。
- AI 生成内容显示“AI 建议，请核对”，并展示生成时间/模型（管理员和客服可见）。
- 所有异步按钮有 loading，避免重复点击。
- 桌面端优先，消费者页面适配移动宽度；管理端不要求完整移动端体验。

## 7. 前端测试

- Vitest：状态映射、金额格式化、SSE parser、权限工具。
- Vue Test Utils：提案确认、AI 建议插入、版本冲突。
- Playwright：登录→创建工单→客服处理→用户确认→结案的核心 E2E。
- E2E 使用 Fake AI profile，避免真实 API 费用和不稳定输出。

