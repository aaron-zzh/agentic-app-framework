# 11g framework 编排 · 工作流引擎 · AI 能力 + API 剩余（收官）

> 覆盖：`engine/workflow`（FlowableWorkflowEngine/WorkflowEngine/FlowableConfig）、`intelligent/agent`（CognitiveCycleExecutor/AgentScheduler）、`intelligent/team`（TeamOrchestrator）、`intelligent/assistant/TaskBoard`、`intelligent/ai` 其余能力（video 等抽样），以及 API 剩余模块批量确认。
> 承接 [11 执行计划](11-followup-review-plan.md) 残留项。审查人 AI/architect · 2026-05-30。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B20 | 🔴 | `WorkflowController#deploy`（无 @PreAuthorize）→ `WorkflowService#deployDefinition` → `FlowableWorkflowEngine#deploy` + `FlowableConfig`（未禁 scriptTask） | **BPMN 部署 RCE 链坐实**：任意认证用户 `POST /api/system/workflow/deploy` 部署任意 BPMN，FlowableConfig 未禁用 scriptTask（Groovy/JUEL），部署含 scriptTask 的流程即任意代码执行，**绕过 AAF ScriptSandbox**；叠加 [B17](11d-framework-controllers.md) UEL 注入。是 11d 待确认项的确定结论 | deploy 限管理员 + 禁用 Flowable scriptTask/限制脚本引擎；BPMN 上传走白名单校验 |
| M52 | 🟠 | `intelligent/agent/CognitiveCycleExecutor#execute` | `definition.setSystemPrompt(enrichedPrompt)` **就地修改缓存共享的 AgentDefinition**（ConfigCacheManager 缓存同一实例）→跨请求 systemPrompt 累积污染 + 无界增长 + 并发改共享对象 | 用局部副本拼装 prompt，勿 mutate 缓存实体 |
| M53 | 🟠 | `intelligent/ai/*`（video/image/embedding 等生成服务） | AI 能力服务普遍**无 pre-call 配额/积分门控**（仅事后或不计量）→付费生成成本失控（泛化 [M44](11e-framework-data-ai.md)/[M23](09-file-sms-aigc.md)） | 生成入口统一接入配额/权益门控（EntitlementAspect / TokenMeteringService.isQuotaExceeded） |
| m33 | 🟡 | `intelligent/team/TeamOrchestrator#parseAndSaveTasks/decomposeGoal` | 用正则解析 LLM JSON 输出（脆弱，字段乱序/嵌套即失败）；`decomposeGoal` 拼 goal 进提示词（M42 类，低危） | 用 JSON 解析器 + schema 校验；用户内容分隔包裹 |
| m34 | 🟡 | `intelligent/agent/AgentScheduler` | 用 `License.userId` 派生 `Random` seed 作"提高破解成本"——security-by-obscurity，对调度无功能价值且不构成真实防护 | 移除或改为正规授权校验 |
| m35 | 🟡 | `intelligent/assistant/TaskBoard#nextReady`+`markRunning` | 取下一就绪任务与标记运行非原子（会话级并发下可能重复取同一任务） | 取+标记合并为原子操作 |

## API 剩余模块批量确认（不逐文件深审）

> 全仓 `@PreAuthorize` 仅出现在 apikey/dict/log/menu **4 处控制器**；其余绝大多数控制器（workflow、ai/*、aigc/*、company、stats、notify、config、mail、dashboard、org、task、entity、knowledge/*、document、autodev/* 等）**无方法级鉴权**——系统性复现 [B9](07-system-admin-and-rbac.md)。

按 [10 鉴权矩阵](10-authorization-matrix.md) 批量修复即可，预期复现模式：

- **B9 鉴权缺失**：上述无 @PreAuthorize 控制器全体。
- **M15 实体作 DTO/响应 + M16 敏感字段缺 @JsonIgnore**：CRUD 控制器普遍。
- **M24/M5 回调无验签**：aigc/* 各回调、notify。
- **M23/M53 计费未门控**：aigc/{video,media,voice,model3d,omni,batch}。
- **占位（M13 类）**：examples/*（建议移出生产）、部分 autodev doc。

逐条工单见鉴权矩阵；本审查不再为每个 API 控制器单列条目（边际收益递减，模式已饱和）。

## 良好实践

- `FlowableWorkflowEngine` 完整封装定义/实例/任务/信号管理，业务层不直接依赖 Flowable API（接口抽象良好）；`returnTask` 用 changeActivityState 实现回退。
- `TeamOrchestrator` 持久化已从内存迁移到 JPA Repository（TeamRepository 等），DAG `getReadyTasks` 依赖判定正确。
- `TaskBoard` 用不可变 record + computeIfPresent 原子更新状态，提供 snapshot/restore 支持 checkpoint。
- `DashScopeVideoGenerationService` 等异步提交 + 轮询查询，错误码解析清晰；`@ConditionalOnProperty` 按 key 装配。

## 对称性 / 一致性提示

- 已有模式 vs 新建（清单#13）：CognitiveCycleExecutor 改缓存共享实体（M52），破坏缓存只读假设。
- 认证 vs 鉴权（清单#8）：BPMN deploy 无鉴权 + 引擎未禁脚本（B20），是 B9 + 沙箱绕过的叠加。
- 计费一致性：AI 能力普遍无 pre-call 门控（M53），与 02 区资金一致性主题闭合。

## 待确认（移交后续/配置侧）

- Flyway clean 生产隔离：`application-prod.yaml` 的 `spring.flyway.clean-disabled`（见 11f）。
- `intelligent/cognition`（personalization/learning/retrieval/memory pipeline）、`engine/{memory,checkpoint,budget,metadata,monitor,meta}`、`agentscope/` 适配层未逐读——属编排细节，预期为正确性/占位类问题，非新安全类。
