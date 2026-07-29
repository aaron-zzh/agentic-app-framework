---
level: Practice
layer: Product
purpose: 定义 Dynamic/Predefined 调用前完整执行画像、工具边界与资源所有权
status: active
version: 1.1.0
date: 2026-07-29
author: Kiro
---

# 设计：Dynamic 子智能体执行接入复核修复（AAF-051）

## 参考文档

- [子智能体动态创建与技能合并设计](../../../design/framework/intelligent/subagent-and-skill-merge-design.md)
- [架构约束](../../../reference/dev/architecture-constraints.md)
- [代码审查规范](../../../reference/dev/code-review-standard.md)

## 方案 A 运行时修订

2026-07-29 对 AgentScope 2.0 源码和官方示例复核后，确认 Agent 实例的 `sysPrompt`、模型、Toolkit 和 middleware 是不可变执行配置，`RuntimeContext` 只承载调用态与会话态。以下修订覆盖本文后续历史签名中与执行画像相关的部分：

- 不新增共享 `HarnessAgentCatalog`，不使用请求期 `replaceAgents(...)`，本阶段不接入原生 `agent_spawn`。
- `AgentExecutionCommand` 新增 `skillSystemPromptAppendix` 与 `roleAllowedToolNames`；两者由 Assistant 在调用前解析。
- Predefined 仍从 `AgentDefinitionPort` 加载，但 `AgentSpec.tools` 来源改为 `ai_agent_definition.allowed_tools`。
- Predefined 缓存键覆盖 `agentId/version/effectiveTools/effectiveSystemPrompt`，只有完整画像相同才复用。
- Dynamic 同样在 compiler 内强制 Role∩Agent 工具交集并合并技能提示；每次创建且由 adapter 在完成、失败、超时和取消时关闭。
- 两类执行 Agent 都保持 `disableSubagents()` 与 `disableDynamicSubagents()`，避免共享 manager 参与 AAF 业务路由。

最终接口以主设计文档的[调用前解析完整执行画像](../../../design/framework/intelligent/subagent-and-skill-merge-design.md#调用前解析完整执行画像)为准。

## 修复范围

本轮只修复上一轮执行接入的三个 major：

- Predefined 路径移除无关的父模型解析依赖。
- Dynamic 现场创建的 `HarnessAgent` 在全部终止路径释放。
- 未实现的父工具继承改为显式拒绝，两个内置助理改用显式工具列表。

不实现父子工具继承，不改变 Predefined 的数据库查询、定义模型解析与编译缓存行为，不修改本轮范围外的方法签名。

## 接口定义

### AgentExecutionCommand

项目现有 record 使用 `Optional<T>` 表达可选字段，因此 `parentModel` 改为非空 `Optional<ModelSpec>`，不使用可空引用，也不增加兼容构造器。

```java
public record AgentExecutionCommand(
        SubagentSpec subagentSpec,
        Optional<ModelSpec> parentModel,
        String skillSystemPromptAppendix,
        Set<String> roleAllowedToolNames,
        long sequenceBase,
        List<AgentMessage> messages,
        InvocationContext context) {

    public AgentExecutionCommand {
        Objects.requireNonNull(subagentSpec, "subagentSpec 不能为空");
        parentModel = Objects.requireNonNull(parentModel, "parentModel Optional 不能为空");
        skillSystemPromptAppendix = Objects.requireNonNull(
                skillSystemPromptAppendix, "skillSystemPromptAppendix 不能为空").trim();
        roleAllowedToolNames = Set.copyOf(Objects.requireNonNull(
                roleAllowedToolNames, "roleAllowedToolNames 不能为空"));
        // 其余既有校验保持不变
    }
}
```

语义约束：

| SubagentSpec | parentModel | 模型来源 |
|---|---|---|
| `Predefined` | `Optional.empty()` | 持久化 `AgentSpec.model()` |
| `Dynamic` | `Optional.of(model)` | Assistant 当前 CHAT 能力路由模型 |

`AssistantApplicationService.agentCommand()` 使用穷举 `switch` 构造该字段。只有 `Dynamic` 分支调用 `models.resolve(...)`；`Predefined` 分支直接返回 `Optional.empty()`，不得提前解析 CHAT 模型。

```java
var parentModel = switch (route.subagentSpec()) {
    case SubagentSpec.Predefined ignored -> Optional.<ModelSpec>empty();
    case SubagentSpec.Dynamic ignored -> {
        var selectedModel = models.resolve(
                CapabilityRoutingContext.ofCapability(
                        null, CapabilityRoutingContext.CAP_CHAT));
        yield Optional.of(
                new ModelSpec(
                        Objects.requireNonNull(
                                        selectedModel.getId(),
                                        "CHAT 模型缺少数据库主键")
                                .toString()));
    }
};
```

`HarnessAgentExecutionAdapter.resolveExecution()` 仅在 `Dynamic` 分支解包；缺失时抛出明确的 `IllegalArgumentException`。`AgentScopeSpecCompiler.compileDynamic(...)` 保留 `ModelSpec` 非空校验，形成适配器与编译器双层防御。Predefined 分支不得访问 `command.parentModel()`。

### Dynamic Agent 所有权

`ResolvedExecution` 增加 `ephemeral`：Predefined 为 `false`，Dynamic 为 `true`。`ActiveExecution` 同步持有该标记及 `AtomicBoolean released`，统一通过一次性释放方法处理映射清理与 Agent 关闭。

```java
private record ResolvedExecution(
        HarnessAgent agent,
        ModelSpec model,
        String agentIdentifier,
        ExecutionPolicy executionPolicy,
        boolean ephemeral) {}

private void release(ExecutionId executionId, ActiveExecution active) {
    activeExecutions.remove(executionId, active);
    if (active.ephemeral() && active.released().compareAndSet(false, true)) {
        active.agent().close();
    }
}
```

所有权规则：

- `compiler.compile(spec)` 返回缓存 Agent，所有权仍属 compiler；adapter 永不关闭。
- `compiler.compileDynamic(...)` 成功返回后，所有权交给当前执行；adapter 必须关闭且只关闭一次。
- Dynamic 编译器在 Agent 成功返回前若自身校验失败，由编译器关闭已创建资源；adapter 不处理未取得的对象。
- adapter 已取得 Dynamic Agent、但尚未建立事件流时发生异常，由同步 `catch` 路径释放。
- 成功注册事件流后，由 `doFinally` 释放，覆盖完成、异常、超时和订阅取消。

`executeDeferred()` 的实现顺序固定为：

1. 解析 `ResolvedExecution`。
2. 映射运行时上下文并创建带 `ephemeral` 的 `ActiveExecution`。
3. `putIfAbsent` 注册执行；若 executionId 重复，先释放当前未注册成功的 ephemeral Agent，再保持既有错误语义。
4. 事件流 `doFinally` 调用统一 `release(...)`。
5. 同步 `catch` 在事件流所有权尚未交给 `doFinally` 时调用同一释放逻辑；`released` 防止重复关闭。

不得在 `doOnComplete` 或仅错误回调中关闭，因为它们不能覆盖取消，且可能在后续事件持久化完成前提前释放。

### Dynamic 工具继承声明

`AgentScopeSpecCompiler.compileDynamic(...)` 在创建 Toolkit 和 Agent 前显式拒绝尚未实现的继承声明：

```java
if (spec.inheritParentTools()) {
    throw new IllegalArgumentException("Dynamic 子智能体暂不支持继承父 Agent 工具");
}
```

两个内置助理均改为 `inheritParentTools=false`，并在 `SubagentSpec.Dynamic.tools` 中声明实际工具。工具 `toolId`、`name` 使用稳定工具键，版本为 `1`：

```java
// 内容创作助理
List.of(
        new ToolRef("knowledge.search", 1, "knowledge.search"),
        new ToolRef("content.generate", 1, "content.generate"),
        new ToolRef("content.draft.create", 1, "content.draft.create"))

// 客服助理
List.of(
        new ToolRef("knowledge.search", 1, "knowledge.search"),
        new ToolRef("support.diagnostics.read", 1, "support.diagnostics.read"),
        new ToolRef("support.handoff", 1, "support.handoff"))
```

每个 Dynamic 工具名集合必须与同一模板的 `Role.toolKeys()` 完全相等；后续测试应按集合比较，防止声明再次漂移。

## 类结构

| 类 | 改动 | 职责边界 |
|---|---|---|
| `AgentExecutionCommand` | `parentModel` 改为 `Optional<ModelSpec>` | 表达仅 Dynamic 需要父模型 |
| `AssistantApplicationService` | 按 `SubagentSpec` 分支解析模型 | 阻止 Predefined 触发 CHAT 模型解析 |
| `HarnessAgentExecutionAdapter` | 标记 ephemeral 并统一释放 | 管理单次 Dynamic 执行资源所有权 |
| `AgentScopeSpecCompiler` | 拒绝 `inheritParentTools=true` | 禁止未实现能力静默降级 |
| `BuiltinSystemAssistantTemplates` | 显式工具列表、继承关闭 | 使模板声明与实际 Toolkit 一致 |
| `dev-log.md` | 追加本轮修复及静态审查记录 | 记录结果与未执行测试约束 |

## 模块交互

```text
AssistantApplicationService
  ├─ Predefined → parentModel=empty
  │                → definitions.findByIdAndVersion
  │                → compiler.compile(AgentSpec) → 缓存 Agent，不关闭
  └─ Dynamic → 解析 CHAT parentModel
                 → compiler.compileDynamic → 非缓存 ephemeral Agent
                 → HarnessAgentExecutionAdapter.doFinally → close
```

Predefined 路径的三项历史不变量：

- 仍查询 `AgentDefinitionPort.findByIdAndVersion(...)`。
- 仍由 `compiler.compile(spec)` 按定义及有效工具集命中缓存。
- 仍只通过 `spec.model()` 解析模型，不解析或校验父模型。

## 静态验收清单

- [ ] `AgentExecutionCommand.parentModel` 使用非空 `Optional<ModelSpec>`。
- [ ] Predefined 构造命令时不调用 `models.resolve(...)`。
- [ ] Predefined 执行分支不读取 `command.parentModel()`，且查询、缓存、`spec.model()` 路径未变。
- [ ] Dynamic 缺少 parentModel 时在执行边界明确失败。
- [ ] Dynamic Agent 在完成、异常、超时、取消、注册前异常及重复 executionId 路径恰好关闭一次。
- [ ] Predefined 缓存 Agent 不被单次执行关闭，仍由 `AgentScopeSpecCompiler.close()` 统一释放。
- [ ] `inheritParentTools=true` 在 Toolkit 创建前明确失败。
- [ ] 两个内置 Dynamic 工具集合分别与对应 `Role.toolKeys()` 相等，且继承标记为 `false`。
- [ ] `dev-log.md` 说明本轮仅完成代码修改与静态审查，未执行测试或 pnpm/mvn 命令。

## 设计决策（ADR）

| 决策 | 选择 | 原因 |
|---|---|---|
| 可选父模型表达 | `Optional<ModelSpec>` | 符合现有 record 风格，避免 null 语义 |
| 模型解析位置 | `AssistantApplicationService` 按规格分支 | 从源头隔离 Predefined 无关依赖 |
| Dynamic 资源释放 | ephemeral 标记 + `doFinally` + 一次性释放 | 覆盖 Reactor 全部终止信号并防止重复关闭 |
| 父工具继承 | 编译时明确拒绝 | 当前不具备父 Toolkit 输入，禁止静默失效 |
| 内置工具来源 | Dynamic 显式 `ToolRef` 列表 | 让声明、Role 边界与实际 Toolkit 一致 |
| 验证方式 | 仅静态审查 | 遵循本轮禁止执行测试和构建命令的约束 |
