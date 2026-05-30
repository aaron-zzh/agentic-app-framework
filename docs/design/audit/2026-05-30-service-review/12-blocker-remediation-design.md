# 12 framework blocker 修复技术设计（B12–B20）

> 本文是 [2026-05-30 service 复审](README.md) 9 个 blocker 的**修复设计**（architect 产出）。
> 🔴 高风险：触及鉴权/RCE/注入/资金/存储。**须人类审核通过后**再由 developer 实施；实施前不改生产代码。
> 实施规范：[架构约束](../../../reference/dev/architecture-constraints.md)、[编码硬约束](../../../../.kiro/skills/coding-standards/SKILL.md)。
> 模型分配（[协作规范](../../../reference/team/collaboration-standard.md#模型分配策略)）：developer-service 实施；architect(review) 须用与 developer 不同 model。

## 总览

| 组 | blocker | 同根 | 顺带修复（major） |
|----|---------|------|------------------|
| A SQL 标识符注入 | B16, B18 | 标识符拼接 SQL | M38（DataTable 鉴权）、m28（前缀） |
| B 引擎沙箱化 | B17, B19, B20 | 表达式/模板/脚本未沙箱 | M37（webhook 鉴权）、B9（workflow 鉴权） |
| C 危险默认收敛 | B12, B15 | 危险默认路径未隔离 | M40（ingest fail-open） |
| D 存储输入校验 | B13, B14 | 存储输入未校验/未约束 | M20/M21（file IDOR/限流，api 侧） |

**统一原则**：白名单优先于转义；fail-closed 优先于 fail-open；危险能力默认关闭，按配置/角色显式开启。

---

## 组 A — SQL 标识符注入（B16 / B18）

**根因**：表名、列名、过滤键作为 SQL **标识符**被字符串拼接（值已 `setParameter` 参数化，标识符未）。

**改动点**：

| 文件 | 方法 | 改动 |
|------|------|------|
| `framework/.../table/DynamicTableService` | createTable / insertRow / queryRows / updateRow / deleteRow / executeDdl | 标识符落库前校验 + DML 列名比对表定义 |
| `framework/.../dataprocess/DataRouter` | insertToTable | 表名经 DynamicTableService 解析（修 m28），列名比对表定义 |

**核心**：新增标识符校验工具（放 `aaf-common` 的 SQL 工具或 DynamicTableService 私有）：

```java
private static final Pattern IDENT = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

private void requireIdent(String s) {
    if (s == null || !IDENT.matcher(s).matches())
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "非法标识符: " + s);
}
// DML：列名必须 ∈ 表定义已知列
private void requireKnownColumns(DataTableDefinition t, Collection<String> cols) {
    var known = t.getColumns().stream().map(DataColumnDefinition::getName).collect(toSet());
    cols.forEach(c -> { if (!"id".equals(c) && !known.contains(c)) throw new BusinessException(...); });
}
```

- createTable：`requireIdent(slug)` + 每个 `col.getName()` 走 `requireIdent`。
- insert/query/update：`requireKnownColumns(table, keys)`（拒绝未知键，杜绝注入与脏写）。
- DataRouter.insertToTable：`var tn = tableService.getTable(slug).getTableName();`（不再用原始 target）+ 列名比对。

**ADR**：选白名单 + schema 比对，不选标识符转义——Postgres 标识符转义易错且无法防"未知列脏写"；schema 比对一并修 Mass-Assignment。

**顺带**：DataTableController 加 `@PreAuthorize`（建表限 ADMIN，CRUD 限 `isAuthenticated()` + 表归属，闭合 M38）。

**验证**：`DynamicTableServiceTest` 注入用例（slug/列名含 `;`、`"`、`OR 1=1`、未知列）→ 期望 `BusinessException`；正常列通过。

---

## 组 B — 引擎沙箱化（B17 / B19 / B20）

### B17 ConditionEvaluator UEL 注入

`framework/.../workflow/condition/ConditionEvaluator#toUel/toFlowableExpression`：

- `field` 校验 `^[A-Za-z_][A-Za-z0-9_.]*$`，非法拒绝。
- 字符串 `value` 不再裸拼：转义单引号（`'` → `''`）并禁止表达式元字符；或改为 Flowable 变量绑定。
- 与下方 B20 一并**引擎级禁用脚本/方法调用**，双保险。

### B19 FreeMarker SSTI

`framework/.../messaging/MessageTemplateEngine` 构造器加固：

```java
freemarkerConfig.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
freemarkerConfig.setAPIBuiltinEnabled(false); // 默认即 false，显式声明
```

**ADR**：保留 FreeMarker（已有依赖、模板需变量插值），用 SAFER_RESOLVER 封堵 `?new`/`Execute` 即可，不引新模板引擎（避免并行抽象）。

### B20 BPMN 部署 RCE

| 文件 | 改动 |
|------|------|
| `api/.../workflow/controller/WorkflowController#deploy`（及 WorkflowService.deployDefinition） | `@PreAuthorize("hasRole('ADMIN')")` |
| `framework/.../workflow/config/FlowableConfig` | 禁用 scriptTask + `setEnableSafeBpmnXml(true)`（防 XXE） |

FlowableConfig 禁脚本（注册拒绝 ScriptTask 的 BpmnParseHandler，或移除脚本引擎）：

```java
config.setEnableSafeBpmnXml(true);
config.setPreBpmnParseHandlers(List.of(new RejectScriptTaskParseHandler()));
// RejectScriptTaskParseHandler#executeParse: 遇 ScriptTask → throw FlowableException("scriptTask 已禁用")
```

**ADR**：no-code 平台允许普通用户编排流程，但**部署**是高危操作→限 ADMIN；脚本节点能力由受控的 `CodeExecutionNode`（走 AAF ScriptSandbox）提供，Flowable 原生 scriptTask 一律禁用，消除沙箱旁路。

**顺带**：WebhookTriggerController 加触发鉴权 + 按 processKey 白名单（闭合 M37）。

**验证**：
- B17：`ConditionEvaluatorTest` 注入 field/value（含 `'`、`.getClass(`）→ 拒绝或安全转义。
- B19：`MessageTemplateEngineTest` 渲染含 `<#assign x="freemarker.template.utility.Execute"?new()>` → 抛异常而非执行。
- B20：`@WithMockUser`(非 ADMIN) 调 deploy → 403；含 scriptTask 的 BPMN 部署 → 解析失败。

---

## 组 C — 危险默认收敛（B12 / B15）

### B12 Mock 支付渠道生产隔离

`framework/.../settlement/MockPayChannelAdapter` 类级：

```java
@ConditionalOnProperty(prefix = "aaf.pay.mock", name = "enabled", havingValue = "true", matchIfMissing = false)
```

- 生产 profile 缺省不开启；`application-prod.yaml` 不得设 `aaf.pay.mock.enabled=true`（CI 校验）。
- `DefaultSettlementEngine` 路由到不存在渠道时已抛 BAD_REQUEST（fail-closed，保留）。

### B15 工具调用鉴权旁路收敛

| 文件 | 改动 |
|------|------|
| `framework/.../tool/ToolCallDispatcher` | `dispatch(name,args)` 改**包私有**（仅 Agent 内部同包调用），外部仅暴露 `dispatchWithPermission` |
| `api/.../tool/ToolService#invoke` | 注入 `OperatorContext`，改调 `dispatchWithPermission(sessionId, userId, roleId, name, args)` |
| `api/.../tool/ToolController` | 加 `@PreAuthorize`（见鉴权矩阵），传入会话/操作者上下文 |

```java
// ToolService
private final OperatorContext operatorContext;
public ToolCallResult invoke(String toolName, String arguments) {
    ... // disabled / 注册校验保留
    var userId = operatorContext.currentUserId().orElseThrow();
    return toolCallDispatcher.dispatchWithPermission(
        operatorContext.sessionId().orElse(null), userId, operatorContext.roleId().orElse(null),
        toolName, arguments);
}
```

**ADR**：无鉴权 `dispatch()` 仅供 Agent 内部（已被 ToolPermissionGuard 装饰链覆盖），降为包私有杜绝 REST/A2A 直调；外部入口强制经权限门控。若 OperatorContext 无 sessionId/roleId 取值，需补（小改）。

**验证**：`ToolServiceTest` 调用高风险工具且无权限 → 返回 DENIED/PENDING，不执行；编译期确认 `dispatch()` 不被 api 模块可见。

---

## 组 D — 存储输入校验（B13 / B14）

### B13 上传类型/大小校验

`framework/.../storage/StorageProperties` 新增上传约束 + `FileService` 校验：

```java
// StorageProperties 新增组件（带默认）
public record UploadLimits(Set<String> allowedContentTypes, long maxSizeBytes) {}
// 默认：图片/pdf/常见文档白名单；maxSizeBytes 默认 10MB

// FileService#upload / uploadImage 入口
private void validate(MultipartFile f) {
    if (f.getSize() > limits.maxSizeBytes()) throw new StorageException("文件超过大小限制", null);
    if (!limits.allowedContentTypes().contains(f.getContentType()))
        throw new StorageException("不允许的文件类型: " + f.getContentType(), null);
}
```

### B14 本地存储路径穿越

`framework/.../storage/LocalStorageService` download/delete（upload 亦加固）：

```java
private Path resolveSafe(String key) {
    var base = Path.of(config.basePath()).toAbsolutePath().normalize();
    var target = base.resolve(key).normalize();
    if (!target.startsWith(base)) throw new StorageException("非法路径: " + key, null);
    return target;
}
```

**ADR**：路径包含校验（normalize + startsWith）是路径穿越标准防御；与 B13 白名单组合覆盖"任意类型/任意路径"两面。

**顺带**：api 侧 FileController 按 key 归属校验 + 文件名 RFC5987 编码（M20）、SMS 限流（M21）单列工单。

**验证**：`LocalStorageServiceTest` `download("../../etc/passwd")`、`download("..\\..\\x")` → `StorageException`；`FileServiceTest` 超大/非白名单类型 → 拒绝。

---

## 实施与门控

| 项 | 内容 |
|----|------|
| 派发 | developer-service 实施；architect(review) 跨 model 审查 |
| 任务拆分 | 建议按组 A/B/C/D 拆 4 个技术任务（`#NN`），B 组内 B17/B19/B20 可并行 |
| 改动范围 | 约 10 文件、< 300 行（含测试），不破坏现有接口签名（仅 dispatch 降可见性 + ToolService 入参经上下文，属内部） |
| 数据迁移 | 无 |
| 验证门禁 | 每组新增单测 + `pnpm check:affected` 全绿；交付前 `pnpm acceptance` |
| 回归 | 工作流部署/工具调用/文件上传/支付下单主流程冒烟 |

**遗留（本设计不含，单列工单）**：M 级系统性鉴权缺失（B9 矩阵）、回调验签（M24/M28）、计费 pre-call 门控（M23/M44/M53）、多实例正确性（M47–M50）、HITL（M35/M36）。建议鉴权缺失（影响面最大）紧随 blocker 之后批量修复。

## 待人类决策

1. Mock 渠道生产策略：`@Profile("!prod")` 还是 `@ConditionalOnProperty`（默认关）？本设计取后者（更显式可测）。
2. FreeMarker 是否保留：保留 + SAFER_RESOLVER（推荐）vs 换无逻辑模板引擎。
3. 是否将"鉴权缺失批量修复（B9 矩阵）"并入本批 🔴 修复一起评审。
