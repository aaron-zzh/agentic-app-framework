# 11d framework REST 控制器暴露面（优先级 3）

> 覆盖：`engine/workflow/trigger/WebhookTriggerController`+`CronTriggerService`、`engine/dataprocess/table/{DataTableController,DataIngestController,DynamicTableService}`、`engine/workflow/{node,condition,FlowableWorkflowEngine}`。
> 承接 [11 执行计划](11-followup-review-plan.md) 优先级 3。审查人 AI/architect · 2026-05-30。
> 鉴权前提：`SecurityConfig.PUBLIC_PATHS` **不含** `/api/webhook`、`/api/v1/data-tables`、`/api/v1/ingest`，`anyRequest().authenticated()`→均需登录，但**无 per-resource 鉴权**。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B16 | 🔴 | `table/DynamicTableService`（createTable/insertRow/queryRows/updateRow/deleteRow/executeDdl） | **SQL/DDL 注入**：`slug`、列名、过滤键作为 SQL **标识符**直接拼接（值已参数化，标识符未）。`slug=x; DROP TABLE…`、filter key `id=1 OR 1=1` 等→建表/查询/增改删全链路注入，可删库/越权读写 | 标识符走白名单校验（仅 `[a-z_][a-z0-9_]*`）+ 与表定义列名比对；slug/列名注册时即校验 |
| B17 | 🔴 | `workflow/condition/ConditionEvaluator#toUel/toFlowableExpression` | 用 `field`/`value` 拼接 Flowable UEL（`field` 裸拼入 `${…}`，字符串值仅单引号无转义）→**UEL 表达式注入**。no-code 条件作者可注入 `'.getClass()…` 等触发方法调用/RCE | UEL 字段名白名单 + 值用 Flowable 参数绑定（不进表达式串）；对 field/value 严格转义 |
| M37 | 🟠 | `workflow/trigger/WebhookTriggerController#trigger` | 无验签、无 per-process 鉴权：任意认证用户可按 `processKey` 触发**任意工作流**，`payload` 直接作流程变量→可触发特权流程 + 链 HttpNode(SSRF)/CodeExecutionNode；且"外部回调"设计意图与"需认证"矛盾（同 M10） | 加 HMAC/签名验签 + 按 processKey 鉴权 + 触发器白名单；明确回调认证方式 |
| M38 | 🟠 | `table/DataTableController`（全部端点） | 全表 CRUD **无 per-resource 鉴权、无租户隔离**：任意认证用户可建表/读写/删任意表（B9 类），并暴露 B16 注入根 | 加 `@PreAuthorize` + 表归属/租户校验；建表限管理员 |
| M39 | 🟠 | `workflow/node/HttpNode#execute` | `url` 来自流程变量，**无 SSRF 防护**（不限制内网/`169.254.169.254`/localhost）→结合 M37 webhook 变量可打内网/元数据端点 | url 出站白名单 + 禁私有网段/链路本地地址解析 |
| M40 | 🟠 | `table/DataIngestController#ingest` | scope/`canAccessTable` 仅在 `apiKey != null` 时校验；经 JWT 访问（apiKey 为 null）则**跳过全部 scope/表校验**（fail-open，M9 续） | 强制要求 ApiKey 主体；apiKey 为 null 时拒绝或按用户租户校验 |
| M41 | 🟠 | `DataIngestController#ingest` + `DynamicTableService#insertBatch` | `items` 与批量插入**无限额**→无界批量写入资源滥用/DoS | 限制单次批量条数 + 速率限制 |
| m25 | 🟡 | `workflow/node/CodeExecutionNode#execute` | JS 路径走 `sandbox.executeShell("node -e " + …)`，依赖 shell + 本机 node，隔离弱于 Python 路径（`executePython`），与 GraalVm 引擎不一致 | JS 统一走 GraalVm 沙箱引擎，去除 shell 外壳 |
| m26 | 🟡 | `DynamicTableService#insertBatch` | 循环逐条 `insertRow`（N 次 native query），无批量化 | 用批量 INSERT / JDBC batch |

## 良好实践

- `CodeExecutionNode` 通过 `ScriptSandbox` 执行（Python 走 `executePython`），脚本节点确实接入沙箱（B5 主体已落地，JS 路径除外见 m25）。
- `DataIngestController` 确实实现了 ApiKey scope + `canAccessTable` 检查（缺陷在 null 分支 fail-open，M40）。
- DynamicTableService 的 **数据值** 全部走 `setParameter` 参数化（注入面仅限标识符，B16）。
- `CronTriggerService` 注册/注销对称（register 先 cancel 旧任务），无泄漏。

## 对称性 / 一致性提示

- 注入面（清单）：标识符拼接 SQL（B16）、UEL 拼接（B17）、URL 无校验（M39）——三类"外部输入进解释器"未隔离。
- 认证 vs 鉴权（清单#8）：webhook/datatable 需登录但无 per-resource 鉴权（M37/M38），ingest scope null 分支 fail-open（M40），与 B9/B10 系统性鉴权缺失同源。
- 状态变更 vs 通知（清单#7）：webhook 触发无验签（M37），与 B3/M24 回调验签缺失同类。

## 待确认

- `FlowableWorkflowEngine`（16KB 未逐行）：BPMN/流程定义部署入口是否鉴权、是否允许任意 BPMN 部署（决定 B17/M37 的 RCE 链可达性）——优先级 5 复核。
- `DataColumnDefinition` 是否在注册时即校验列名字符集（若已校验可降 B16 部分面）。
