# 13 blocker 修复开发日志（B12–B20）

> 实施 [12 修复设计](12-blocker-remediation-design.md)，人类已审核并采纳 3 项推荐决策（mock 用 @ConditionalOnProperty、FreeMarker 保留 + SAFER_RESOLVER、B9 批量纳入后续）。
> 负责：developer-service · 2026-05-30。

## 实现清单

| blocker | 文件 | 改动 |
|---------|------|------|
| B12 | `framework/.../settlement/MockPayChannelAdapter` | 类级 `@ConditionalOnProperty("aaf.pay.mock.enabled", 默认关)` |
| B12 | `aaf-api/.../application-dev.yaml`、`application-test.yaml` | 增 `aaf.pay.mock.enabled: true`（prod 不设→关闭） |
| B13 | `framework/.../storage/FileService` | upload 入口校验 MIME 白名单 + 10MB 上限（uploadImage 复用） |
| B14 | `framework/.../storage/LocalStorageService` | download/delete 经 `resolveSafe`（normalize + startsWith 基目录），拒绝路径穿越 |
| B15 | `framework/.../tool/ToolCallDispatcher` | `dispatch()` 由 public 降为包私有（无外部调用方） |
| B15 | `aaf-api/.../tool/ToolService` | 注入 OperatorContext，invoke 改走 `dispatchWithPermission(null,userId,null,...)` |
| B16 | `framework/.../table/DynamicTableService` | 标识符白名单 `IDENT` 校验 slug/列名；DML 列名比对表定义（requireKnownColumns） |
| B17 | `framework/.../workflow/condition/ConditionEvaluator` | toUel 字段名白名单 `FIELD` + 字符串值单引号转义 |
| B18 | `framework/.../dataprocess/DataRouter` | insertToTable 改委托 `DynamicTableService.insertRow`（复用校验+参数化，顺修 m28 前缀，移除自建 SQL） |
| B19 | `framework/.../messaging/MessageTemplateEngine` | FreeMarker `setNewBuiltinClassResolver(SAFER_RESOLVER)` + `setAPIBuiltinEnabled(false)` |
| B20 | `framework/.../workflow/config/FlowableConfig` | `setEnableSafeBpmnXml(true)` + 注册 RejectScriptTaskHandler（部署期拒绝 scriptTask） |
| B20 | `aaf-api/.../workflow/controller/WorkflowController#deploy` | `@PreAuthorize("hasRole('ADMIN')")` |

## 关键决策与说明

- **B15**：经全仓搜索确认 `ToolCallDispatcher.dispatch(name,args)` 仅被 `ToolService` 调用，降为包私有无其它破坏；OperatorContext 无 sessionId/roleId，按 null 传入，风险等级门控仍生效（高风险工具→PENDING/DENIED）。
- **B18**：删除 DataRouter 自建 INSERT，统一走 DynamicTableService，消除并行抽象 + 第二注入点 + m28 前缀错误，三合一。
- **B16/B18 同根**：标识符白名单 + 列名 schema 比对，同时堵注入与 Mass-Assignment 脏写。
- **B20**：脚本能力仅经受控 `CodeExecutionNode`（走 ScriptSandbox），Flowable 原生 scriptTask 部署期即拒。
- **B12 prod 隔离已核实**：`application-prod.yaml` 无 `aaf.pay.mock.enabled` 且 `mock-enable: false`。

> **沉淀（顺带核实，闭合 11f 待确认）**：`application-prod.yaml` 的 `spring.flyway.clean-disabled: true` + `aaf.flyway.clean-on-start: false`——Flyway clean 生产隔离已到位，11f 待确认项关闭。

## 验证状态

**编译已通过** ✅：`pnpm nx build service --skip-nx-cache`（绕过缓存的真实重编译）BUILD SUCCESS，四模块全部重编译（common 87 / framework 475 / auto-dev 30 / api 806 文件）。
- `FlowableConfig` 的 Flowable 解析 API（BpmnParseHandler/BpmnParse/ScriptTask/setEnableSafeBpmnXml/setPreBpmnParseHandlers）编译通过；仅一条 deprecation 警告（非错误，后续可优化）。
- 注意：首次 `pnpm nx build service` 为 Nx 缓存命中（旧结果），须用 `--skip-nx-cache` 才真正编译本次改动。

**待执行**：build 目标 `-DskipTests`，单测未跑——需 `pnpm nx test service`（及为每个 blocker 补单测，见 12 设计的验证用例）+ `pnpm acceptance` 回归。

## 后续（已审批纳入，单列任务）

- B9 鉴权矩阵批量修复（≈100 控制器加 @PreAuthorize，按 [10 鉴权矩阵](10-authorization-matrix.md) 角色映射）——独立任务，非本批。
- 顺带 major（同设计）：M37 webhook 验签/鉴权、M38 DataTableController 鉴权、M40 ingest fail-open、M24/M28 回调验签、M44/M53 pre-call 配额、M47–M50 多实例正确性。
