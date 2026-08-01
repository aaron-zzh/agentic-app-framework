# 11g framework 编排 · 工作流引擎 · AI 能力 + API 剩余（收官）

> 覆盖：`engine/workflow`（FlowableWorkflowEngine/WorkflowEngine/FlowableConfig）、`intelligent/agent`（CognitiveCycleExecutor/AgentScheduler）、`intelligent/team`（TeamOrchestrator）、`intelligent/assistant/TaskBoard`、`intelligent/ai` 其余能力（video 等抽样），以及 API 剩余模块批量确认。
> 2026-05-30 分区复审：编排、工作流与剩余 API。审查人 AI/architect。

## 问题清单（2026-08-01 复核）

| 编号 | 级别 | 状态 | 位置 | 结论 |
|------|------|------|------|------|
| M53 | 🟠 | PARTIAL（待计费策略决策） | `intelligent/ai/*`、`engine/knowledge/embedding/EmbeddingService` | 逐项核实：**streaming 不成立**——`ResilientChatService.stream` 已有 `creditGuard.precheck` + `withStreamUsage` 计量，`call` 两个重载同样有；**embedding 成立**——`EmbeddingService.embed/embedBatch` 既不门控也不计量，接口连 ownerId 都没有，无法归属成本；调用方遍布记忆抽取/去重/检索与知识库导入适配器。已在 `EmbeddingService` 类注释中标注该缺口与闭环前置条件，避免新增调用方误以为免费。闭环需先定：①系统触发的 embedding（会话后异步记忆抽取、知识库批量导入）由谁承担成本；②计费口径（按次/按 token）。定了之后再改接口并贯穿全部调用方 |

## API 剩余模块批量确认（不逐文件深审）

> 方法级鉴权已大面积补齐；当前剩余风险集中在角色范围、SELF 资源归属和 org 过滤，详见 [10 鉴权矩阵](10-authorization-matrix.md)。

按 [10 鉴权矩阵](10-authorization-matrix.md) 批量修复即可，预期复现模式：

- **B9 授权边界**：部分端点角色过宽，SELF 归属和 org 过滤仍未清零。
- **M15 实体响应残余**：全局扫描未再复现“CRUD 普遍以实体作请求体”；Company 与 Channel/Webhook 写入口已 DTO 化，但 SMS、AI Output、Document、Team 等控制器仍有直接实体出参，继续按模块收敛为 VO。
- **回调安全残余**：SMS 等剩余回调仍需逐协议验签与抗重放。
- **M23/M53 计费门控旁路**：image-to-image/edit 与 framework registry/streaming/embedding 仍未统一门控。
- **占位**：examples/*（建议移出生产）、部分 autodev doc。

逐条工单见鉴权矩阵；本审查不再为每个 API 控制器单列条目（边际收益递减，模式已饱和）。

## 良好实践

- 已确认的实体敏感字段均增加 `@JsonIgnore`：用户密码、OAuth access/refresh token、Channel/Webhook 密钥、SMTP 密码、模型及供应商 API Key。
- `FlowableWorkflowEngine` 完整封装定义/实例/任务/信号管理，业务层不直接依赖 Flowable API（接口抽象良好）；`returnTask` 用 changeActivityState 实现回退。
- `TeamOrchestrator` 持久化已从内存迁移到 JPA Repository（TeamRepository 等），DAG `getReadyTasks` 依赖判定正确，任务拆解输出已有结构校验。
- `TaskBoard` 用不可变 record + computeIfPresent 原子更新状态，取就绪任务并标记运行已合并为原子操作，提供 snapshot/restore 支持 checkpoint。
- `DashScopeVideoGenerationService` 等异步提交 + 轮询查询，错误码解析清晰；`@ConditionalOnProperty` 按 key 装配。

## 对称性 / 一致性提示

- 计费一致性：主要 API 已执行 pre-call 检查，但 framework registry/streaming/embedding 仍存在门控旁路（M53）。

## 待确认（移交后续/配置侧）

- Flyway clean 生产隔离：`application-prod.yaml` 的 `spring.flyway.clean-disabled`（见 11f）。
- `intelligent/cognition`（personalization/learning/retrieval/memory pipeline）、`engine/{memory,checkpoint,budget,metadata,monitor,meta}`、`agentscope/` 适配层未逐读——属编排细节，预期为正确性/占位类问题，非新安全类。
