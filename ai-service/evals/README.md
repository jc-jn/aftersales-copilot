# Day 25 RAG 与安全评测

范围：47条人工设计的虚构问题、12个独立演示chunk；不读取业务数据、不写业务Qdrant。政策窗口与Java演示seed一致（仅退款/退货退款7天、换货15天、维修365天），最终资格仍由Java决定。

类别：四类政策20条、商品排障8条、通用/组合5条、普通无答案6条、版本/范围反例3条、注入5条。32条标定、15条验证；每3条中第3条为验证。报告保存问题标签快照、数据摘要、模型/维度、Prompt版本、Top1/Top3分数、Top5文档、扫描指标、引用、拒答和实际Token。

模型配置（`ai-service/.env`，不提交Git）：

```dotenv
LLM_PROVIDER=deepseek
LLM_BASE_URL=https://api.deepseek.com
LLM_CHAT_MODEL=deepseek-v4-pro
LLM_API_KEY=<your-deepseek-key>
LLM_MAX_TOKENS=512
EMBEDDING_PROVIDER=siliconflow
EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_API_KEY=<your-siliconflow-key>
EMBEDDING_DIMENSION=1024
QDRANT_COLLECTION=aftersales_bge_m3_v1
```

DeepSeek的JSON输出与非思考模式见[官方接口](https://api-docs.deepseek.com/api/create-chat-completion)。2026-10-08核实[人民币价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing)：高峰缓存未命中输入9元、输出27元/百万Token；脚本始终按这组较高价格估算。SiliconFlow [价格页](https://www.siliconflow.cn/pricing)标明 `BAAI/bge-m3` 免费，`Pro/BAAI/bge-m3`为收费模型，本次不使用Pro。Embedding[接口](https://docs.siliconflow.cn/docs/api/embeddings-post)逐批检查实际维度，不能用8192输入长度当作向量维度。

在`ai-service`执行：

```powershell
# 免费回归（不会调用聊天API，报告不产生真实阈值结论）
.\.venv\Scripts\python.exe -m evals.run --output evals/reports/fake-local.json
# 真实检索评测
.\.venv\Scripts\python.exe -m evals.run --real --output evals/reports/retrieval-local.json
# 真实检索 + 47次聊天评测
.\.venv\Scripts\python.exe -m evals.run --real --chat --budget-cny 20 --output evals/reports/full-local.json
```

输出路径必须不存在。退出码0表示评测门槛通过；2表示完整运行但不存在通过验证的阈值；1表示配置/请求/预算错误。报告先持久化预算预留，再调用模型；无重试，最多47次聊天调用，每次输出512Token。预算基于UTF-8字节数加消息余量保守预留，实际usage缺失或超出预留则停止。它是本次脚本的费用控制；最终扣费以服务商账单为准，账户硬限额需在服务商设置。

付费评测当前只允许2026-10-08核实的价格；以后运行须先核实官方价格并更新脚本的日期/费率，防止沿用过期费率。免费回归不受此限制。

阈值扫描0.00–1.00，默认要求标定集无答案误接受率为0、Recall@5至少0.5；先满足约束，再按召回/检索精度/较高阈值选择。验证集独立检查同样门槛。`thresholdApproved=false` 时不允许直接采用。评测不自动修改`.env`或生产知识库，未标定时应用关闭证据回答并转人工。

2026-10-08 真实结果：

| 项目 | chat-v2首轮 | chat-v3复测 |
|---|---:|---:|
| 真实聊天调用 | 47 | 47 |
| 标定候选阈值 | 0.63 | 0.63 |
| 标定Recall@5 | 91.67% | 91.67% |
| 验证Recall@5 | 75.00% | 75.00% |
| 验证无答案检索误接受 | 1/3 | 1/3 |
| 无答案最终误答（含注入） | 1/11 | 0/11 |
| 可答/不可答准确率 | 85.11% | 80.85% |
| 已输出引用的预期文档精度 | 91.94% | 100% |
| 保守高峰价费用估算 | 0.205308元 | 0.251793元 |
| 阈值通过验证 | 否 | 否 |

复测普通无答案6/6、注入5/5均转人工，Provider本身的5条注入也都返回needsHuman=true。所有注入样例只使用虚构数据，无真实密钥进入Prompt。两轮合计费用上界估算0.457101元，预算预留合计3.107637元，低于用户20元上限。

首轮暴露“退款到账时间”被答为“申请窗口7天”，升级chat-v3要求直接支持用户所问字段、证据不足拒答后，该例转人工。更保守的Prompt同时降低可答召回，不能把拒答率改善解释为整体准确率提高。首轮事实标签按政策分组过宽，复测改为问题所需事实；报告保存各自标签，问题/文档与检索分数未变，两个版本的requiredFactsAccuracy不直接比较。

报告：[首轮](reports/real-2026-10-08.json)、[chat-v3](reports/real-chat-v3-2026-10-08.json)。结论：实现和评测已完成，当前候选检索阈值未通过门槛，`RAG_SCORE_THRESHOLD`保留未配置。后续需要增加独立标注问题、调整检索/语料后重新评测，不能根据这15条验证问题调阈值并继续称为独立验证。

局限：小规模虚构语料不能代表业务数据；Cosine离线扫描不评估Qdrant容量；“文档ID正确”和“包含关键事实”不等于严格语义支持，需要人工复核。过滤反例使用评测时间/范围/当前版本，在线浏览器链路仍只允许GLOBAL，Java当前版本/有效期授权尚未贯通在线检索。本次没有扩展该业务链。运行Provider选择真实服务后所有业务AI调用可能收费，测试已强制Fake，脚本预算不覆盖业务服务调用。
