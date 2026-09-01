## #10601～#10602 思考参数配置与透传（2026-09-02）

- ✅ developer-service：`AiModel` 新增字段 + `L0ReActAgentFactory` 透传，`compile` 通过

### 关键发现：#10601 原范围技术前提因 AAF-105 不再成立

`AiModel.enableThinking`（"思考型模型流式时正文断流"问题的原始出处）核实确认唯一消费方是 `DynamicChatClientFactory`，服务对象是 `ResilientChatService`——AAF-105 已明确将其排除在 L0 重构范围外（"本批不重构，标注已退出智能层 L0"）。AAF-105 完成后 L0（及全部自主 execution）统一走 AgentScope core `ReActAgent`，核实 `agentscope-core/.../accumulator/ReasoningContext.java`：`TextAccumulator`/`ThinkingAccumulator` 独立并行处理正文与思考内容，`TextBlock`/`ThinkingBlock` 是两个独立事件流内容块类型（`THINKING_BLOCK_*`/`TEXT_BLOCK_*` 分开投递）——**core 路径从设计上不存在"开启思考导致正文断流"问题**，这是模型 provider 层（`DashScopeChatModel` 等）的职责，AAF 不需要写代码解决。

人类拍板方向 (a)：把 #10601/#10602 范围改为"核实 core 路径通道天然分离 + 补齐 `thinkingBudget`/`reasoningEffort` 参数透传"，不再去改已被排除的 Spring AI 直连链路。

### 实现文件

| 文件 | 说明 |
|------|------|
| `apps/service/aaf-api/.../db/migration/v2__ai_schema.sql` | `ai_model` 表新增 `thinking_budget INTEGER`/`reasoning_effort VARCHAR(16)` 两列（直改原文件，见下方偏离说明） |
| `.../core/model/AiModel.java` | 新增 `thinkingBudget`/`reasoningEffort` 字段（Lombok `@Getter`/`@Setter` 自动生成访问器） |
| `.../infrastructure/agentscope/model/L0ReActAgentFactory.java` | 新增 `generateOptions(AiModel)` 私有方法：仅 `enableThinking=true` 时构造并下发 `GenerateOptions`，未配置字段不下发 |

### ⚠️ 偏离阶段约束：直接修改历史迁移文件 `v2__ai_schema.sql`（人类已授权）

此前阶段约束是"不新增迁移文件，改表直接改 `v16__intelligent_runtime_schema.sql`"。`ai_model` 表定义在 `v2__ai_schema.sql`（早于本阶段范围），按约束字面意思应该用 `ALTER TABLE` 追加进 v16。人类明确指示"开发阶段可以直接修改原 SQL"，因此本次直接在 `v2__ai_schema.sql` 的 `ai_model` 表定义处插入两个新列，不产生 `ALTER TABLE` 追加语句，保持表结构定义完整不割裂。此举依赖"开发阶段可重建数据库、不需要保证 Flyway checksum 兼容已运行环境"的前提，记录在案供后续核实生产上线前是否需要收敛为标准迁移流程。

### 验证

- 按人类要求本次不执行 `pnpm nx test service`；已执行 `pnpm nx compile service`，BUILD SUCCESS（6 模块全绿）。
- 人工核对：`PromptEnvelopeCaptureMiddleware` 确认已记录 `thinkingBudget`/`reasoningEffort`（无需新增审计代码）；`L0ReActAgentFactory.generateOptions` 三种取值组合（未开启思考/开启但未配置参数/开启且配置参数）均已覆盖判断逻辑。

---

