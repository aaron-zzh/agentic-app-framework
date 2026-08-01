# 11d framework REST 控制器暴露面（优先级 3）

> 覆盖：`engine/workflow/trigger/WebhookTriggerController`+`CronTriggerService`、`engine/dataprocess/table/{DataTableController,DataIngestController,DynamicTableService}`、`engine/workflow/{node,condition,FlowableWorkflowEngine}`。
> 2026-05-30 分区复审：framework 控制器暴露面。审查人 AI/architect。
> 鉴权前提：`SecurityConfig.PUBLIC_PATHS` **不含** `/api/webhook`、`/api/v1/data-tables`、`/api/v1/ingest`，`anyRequest().authenticated()`；数据表端点已补租户/资源边界，工作流触发仍需细化同租户 per-flow 权限与外部签名契约。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B17 | 🔴 | `workflow/condition/ConditionEvaluator#toUel/toFlowableExpression` | `value` 仍作为字符串字面量拼入 Flowable UEL，转义不足时可闭合字面量并注入后续表达式；UEL 方法调用也未被禁用，构造值仍可能触发任意方法访问 | 值改为流程变量/参数绑定，不进入表达式字符串；使用受限解析上下文禁用方法调用，并对生成表达式做结构化校验 |
| M37 | 🟠 | `workflow/trigger/WebhookTriggerController#trigger` | 同租户内仍缺少按 flow/process 的细粒度触发权限；面向外部 webhook 的入口也没有 HMAC/签名认证，无法验证来源与防重放 | 在租户边界内继续校验 per-flow execute 权限；外部入口使用带时间戳/nonce 的 HMAC 并做重放防护，与登录态内部触发入口分离 |
| M39 | 🟠 | `workflow/node/HttpNode#execute` | `url` 来自流程变量，**无 SSRF 防护**（不限制内网/`169.254.169.254`/localhost）→结合 M37 webhook 变量可打内网/元数据端点 | url 出站白名单 + 禁私有网段/链路本地地址解析 |
| m25 | 🟡 | `workflow/node/CodeExecutionNode#execute` | JS 路径走 `sandbox.executeShell("node -e " + …)`，依赖 shell + 本机 node，隔离弱于 Python 路径（`executePython`），与 GraalVm 引擎不一致 | JS 统一走 GraalVm 沙箱引擎，去除 shell 外壳 |

## 良好实践

- `CodeExecutionNode` 已接入 `ScriptSandbox`，但该实现仍缺少 OS 级隔离且保留 shell 黑名单路径（B5）；JS 子进程路径另见 m25。
- Webhook 触发已阻断跨租户流程并保护系统保留变量不被 payload 覆盖。
- `CronTriggerService` 注册/注销对称（register 先 cancel 旧任务），无泄漏。

## 对称性 / 一致性提示

- 输入 vs 解释器：UEL 字段已白名单化，但 value 仍进入表达式字符串且方法调用未禁用（B17）；HTTP URL 仍缺少出站边界（M39）。
- 认证 vs 鉴权（清单#8）：Webhook 已有租户边界，但同租户 per-flow 权限仍缺失；外部调用还缺独立 HMAC 身份与防重放（M37）。

## 待确认

- 外部 webhook 与登录态内部触发是否会拆分路由，并分别强制 HMAC 防重放和 per-flow execute 权限（M37）。
