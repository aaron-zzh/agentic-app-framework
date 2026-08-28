---
level: Practice
layer: Model
purpose: 子智能体调用前解析 + 角色-内置两层技能合并 + 工具双层交集领域模型重设计
status: active
version: 0.3.0
date: 2026-07-29
author: Kiro
related:
  - ./architecture.md
scope:
  includes:
    - 领域模型统一（Role 双套定义收敛、SkillRoute 与技能表接线）
    - 角色-内置两层技能合并领域接口设计（不设"助理专属技能"层）
    - 工具白名单 Role∩Agent 双层交集落地（当前 SQL 已设计但代码未实现）
    - SubagentSpec 领域模型与 ai_agent_definition 降级方案
    - AgentScope 基础设施层改造（调用前完整执行画像编译与生命周期）
    - 文件清单与分阶段计划
  excludes:
    - 具体代码实现（本文档只到接口签名 + 类结构）
    - 正式任务编号与验收标准（由 architect 在评审通过后拆分）
---

# 子智能体调用前解析 + 两层技能合并 + 工具双层交集——技术方案

> 🔴 高风险架构调整：2026-07-29 经 AgentScope 2.0 源码审查后采用方案 A。禁止共享 HarnessAgent 在请求期修改 `DefaultAgentManager`；请求级 Prompt 与工具授权必须在调用前解析并编译。

## 背景与目标

当前实现存在四个交织的问题，本次一并解决：

1. **执行链路强依赖 `ai_agent_definition`**：`AssistantApplicationService → SkillRoute.agentId → AgentExecutionPort → AgentDefinitionPort` 是唯一路径，任何 Skill 命中都必须先有一条持久化 Agent 定义，否则报 `IllegalArgumentException`。已验证的两条内置记录（`system.agent.content-creator`/`system.agent.customer-service`）各自只被单一 Assistant 使用，不满足"跨助理复用需治理"的初始设计动因。
2. **`Role` 存在两套并行定义**：`intelligent.assistant.role.Role`（JPA Entity，`ai_role` 表，被 `module/ai/role` 管理后台 CRUD 使用，是真实可配置数据）与 `intelligent.assistant.model.Role`（record，纯领域，仅被 `BuiltinSystemAssistantTemplates` 两个硬编码内置助理使用）。二者字段不同（`skillIds: String` JSON vs `skillKeys: Set<String>`），没有转换关系，v2 领域模型目前读不到 `ai_role` 表的真实数据。
3. **技能加载是扁平单一来源，且 v2 硬编码路径违反了现有 SQL 设计约束**：`DefaultSkillRouter` 只读 `AssistantDefinition.skillRoutes()`（硬编码列表），不读 `Role.skillKeys`、不读 `ai_skill_definition` 表的 `builtIn=true` 内置技能。**已核实 `v2__ai_schema.sql` 的表注释明确写着技能"统一经角色（`ai_role.skillIds`）挂载"**——数据库设计者的意图是技能只有唯一入口（Role），`ai_assistant`/`ai_agent_definition` 均无技能字段。当前 v2 `AssistantDefinition.skillRoutes()` 硬编码相当于在 Assistant 上开了一个 SQL 设计未授权的"助理专属技能"入口，与既定约束冲突，需要纠正而不是延续。
4. **工具白名单的"双层交集"设计在 SQL 里已明确但代码未落地**：`ai_role.tool_whitelist` 列注释写明"运行时与 Agent 级 `allowed_tools` 取交集后收窄"，但已核实 `AgentScopeToolkitFactory.create(spec.tools())` 只读 `AgentSpec.tools()`（来自 `ai_agent_definition.tools`），完全没有读取任何 Role 的 `tool_whitelist`，也没做交集运算。`Role.toolKeys()` 目前只在 `AssistantDefinition` 构造时做过一次性一致性校验，不参与运行时 Toolkit 注册决策。SQL 设计的纵深防御意图（业务角色边界 ∩ 执行环境技术边界）完全没有实现。

**目标态**：
- **技能加载 = 角色技能（助理挂载的全部 Role 的 skill_ids 合并）+ 内置通用技能（`ai_skill_definition.built_in=true`）两层合并去重**，不设"助理专属技能"层——技能挂载点收敛到唯一入口 `ai_role.skill_ids`，符合现有 SQL 设计约束。助理如果需要"专属"技能，正确做法是挂一个专属 Role，不是在 Assistant 上开新入口。合并逻辑是 **AAF 领域层代码**（零 AgentScope 依赖），不依赖 AgentScope 的 `skillRepository`/`SkillBox` 机制（那套已被 `AgentScopeSpecCompiler.disableDynamicSkills()` 显式关闭，继续保持关闭，避免双路径）。
- **工具白名单 = Agent 层 `allowed_tools`（执行环境技术边界）∩ 助理挂载的全部 Role 的 `tool_whitelist` 合并结果（业务角色边界）**，落地 SQL 已设计但代码缺失的纵深防御交集，任一层拒绝则真正拒绝。
- 子智能体优先**动态创建**（父 Agent/触发它的 Assistant 当前上下文现场构造 `SubagentSpec`，不落库）；`ai_agent_definition` 保留但降级为**预定义子智能体模板目录**，只在少数需要治理/跨助理复用的场景被查询，不再是主执行链路的必经步骤。`ai_agent_definition` 不新增技能字段（技能挂载点仍唯一收敛在 Role）。
- 执行引擎基于 AgentScope `HarnessAgent`：Assistant 在调用前解析有效技能提示、角色工具边界和 `SubagentSpec`；`AgentScopeSpecCompiler` 根据完整不可变执行画像构建 Agent。Predefined 可按定义版本、最终 Prompt 和有效工具缓存；Dynamic 每次创建并在执行终止后关闭。`RuntimeContext` 只承载租户、用户、会话、授权、租约和观测等调用态，不承担 Toolkit/sysPrompt 重配置。

## 一、领域模型统一方案

### 1.1 Role 双套定义收敛

**方案：以 `ai_role` 表（v1 Entity 语义）为唯一真理源，`intelligent.assistant.model.Role` 改为该表的只读投影（record），删除硬编码构造方式。**

理由：`ai_role` 表已有管理后台（`module/ai/role`），是真实被运营/管理员使用的数据；`BuiltinSystemAssistantTemplates` 里硬编码的 `model.Role` 应该改为"读 `ai_role` 表里 `code='system.role.content-creator'` 这条记录并转换"，而不是反过来。

```java
// intelligent.assistant.model.Role —— 保留 record 形态，作为领域内只读投影，字段对齐 ai_role 表语义
public record Role(
    String key,                    // 对应 ai_role.code（新增列，见下方迁移）
    String name,
    List<String> responsibilities,
    List<String> nonResponsibilities,
    Set<String> skillKeys,         // 对应 ai_role.skillIds 解析后的技能 code 集合（不是数字 ID）
    Set<String> toolKeys) { ... }   // 对应 ai_role.toolWhitelist 解析结果
```

**需要的数据库迁移**：`ai_role` 新增 `code VARCHAR(64) UNIQUE`（稳定业务标识，替代当前只有自增 ID）；`skillIds` 语义从"数字 ID 数组"改为"技能 `code` 数组"（与下方 `ai_skill_definition.code` 对齐，理由见 1.2）。

**新增端口**：`RoleDefinitionPort`（`intelligent.assistant.port`），实现读 `ai_role` 表并转换为 `model.Role`：

```java
public interface RoleDefinitionPort {
    Optional<Role> findByCode(String roleCode);
}
```

`JpaRoleDefinitionAdapter implements RoleDefinitionPort` 放 `infrastructure.assistant.persistence`，委托现有 `AiRoleRepository`（v1 Entity 不删除，继续承载管理后台 CRUD，只是新增一个读投影出口）。

### 1.2 SkillRoute 与 ai_skill_definition 接线

**方案：`SkillRoute` 保留自包含形态（意图匹配规则仍硬编码在 Assistant 定义里，因为路由触发词属于"这个助理的意图漏斗"配置，不是技能本身的属性），但新增一层"技能内容"引用，指向 `ai_skill_definition.code`。**

区分两个概念（当前代码混为一谈）：
- **路由规则**（"什么话该走这条路"）：`SkillRoute` 的 `intentTerms`/`priority`/`defaultRoute`，是 Assistant 专属配置，不共享
- **技能内容**（"命中后要做什么、用什么系统提示词补充、需要哪些工具"）：应该指向 `ai_skill_definition` 记录，可被多个 Assistant/Role 共享复用

```java
public record SkillRoute(
    String skillKey,              // 改为强制对应 ai_skill_definition.code（新增校验）
    Set<String> intentTerms,
    SubagentSpec subagentSpec,    // 替换原 agentId + agentDefinitionVersion，见二、
    String actionKey,
    ToolPolicy.ActionEffect actionEffect,
    int priority,
    boolean defaultRoute) { ... }
```

`name` 字段删除——技能名称从 `ai_skill_definition.name` 取，不在 `SkillRoute` 里重复维护（避免两处数据不一致）。

新增端口 `SkillCatalogPort`：

```java
public interface SkillCatalogPort {
    Optional<SkillDef> findByCode(String skillCode);
    List<SkillDef> findBuiltIn();                     // 对应 ai_skill_definition.builtIn=true
}
```

`SkillDef` 沿用现有 `intelligent.core.skill.SkillDef`（已存在，语义匹配），不新建。

## 二、角色-内置两层技能合并领域接口

> 已核实 `v2__ai_schema.sql`：`ai_skill_definition` 表注释"统一经角色（`ai_role.skill_ids`）挂载"，`ai_role.skill_ids` 列注释同样强调"统一经角色挂载"。数据库设计者的意图是技能挂载点唯一收敛在 Role，`ai_assistant`/`ai_agent_definition` 均不设技能字段。因此本方案**不设"助理专属技能"层**，只做两层合并：角色技能（可能来自助理挂载的多个 Role）+ 内置通用技能。

新增领域服务 `EffectiveSkillResolver`（放 `intelligent.assistant.application`，零 AgentScope 依赖）：

```java
public interface EffectiveSkillResolver {
    /**
     * 合并两层技能来源，按 skillKey 去重（同 key 时角色技能优先于内置通用技能）。
     *
     * @param assignedRoles 该助理当前挂载的全部角色（含默认角色，通过 ai_assistant_role 关联）
     */
    List<SkillDef> resolve(List<Role> assignedRoles);
}

public final class DefaultEffectiveSkillResolver implements EffectiveSkillResolver {
    private final SkillCatalogPort skillCatalog;

    @Override
    public List<SkillDef> resolve(List<Role> assignedRoles) {
        var merged = new LinkedHashMap<String, SkillDef>();
        // 第二层：内置通用技能，最先放入（最低优先级，会被角色技能同名覆盖）
        skillCatalog.findBuiltIn().forEach(s -> merged.put(s.skillId().toString(), s));
        // 第一层：角色技能（多个 Role 合并，按 Role 列表顺序，后者覆盖前者同名）
        assignedRoles.stream()
            .flatMap(role -> role.skillKeys().stream())
            .map(skillCatalog::findByCode).flatMap(Optional::stream)
            .forEach(s -> merged.put(s.skillId().toString(), s));
        return List.copyOf(merged.values());
    }
}
```

**用途**：`AssistantApplicationService` 在物化/路由前调用 `effectiveSkillResolver.resolve(assignedRoles)`，得到的合并技能集合用于：
1. 生成 `EffectiveContextManifest`（已有概念，由 `architecture.md` 定义，补充技能来源说明）
2. 作为 `SkillRoute.skillKey` 的可见性校验依据——`SkillRoute.skillKey` 必须在合并结果中，否则该路由不可用（替代当前"必须属于 `Role.skillKeys`"这条单角色校验，扩展为"必须属于该助理挂载的所有角色技能 + 内置技能合并结果"）

**与 AgentScope 的边界（回应"基于 AgentScope 实现"的要求）**：合并逻辑本身是纯 Java 领域代码，不导入 `io.agentscope.*`。AgentScope 承担的是**合并结果如何影响 HarnessAgent 执行**——即把 `EffectiveSkillResolver` 解析出的技能列表，在编译/调用时转换成系统提示词追加段（技能的 `instructions`/`systemPrompt` 拼进 sysPrompt，或作为 `RuntimeContext` extra 传入）。工具白名单不在这一步处理，见下方「三、工具白名单双层交集」独立小节——技能与工具是两条不同的合并规则，不可混同。

这一步转换放在适配器层（`infrastructure.agentscope.compiler`），领域层只产出"合并后的技能列表"这个纯数据结果。

## 三、工具白名单双层交集（补齐 SQL 已设计但代码未落地的能力）

> 已核实 `AgentScopeToolkitFactory.create(spec.tools())` 只读 `AgentSpec.tools()`（来自 `ai_agent_definition.tools`），完全未读取任何 Role 的 `tool_whitelist`，`Role.toolKeys()` 仅在 `AssistantDefinition` 构造时做过一次性一致性校验，不参与运行时 Toolkit 注册决策——SQL 注释描述的交集设计形同虚设。

**技能与工具的合并规则不同，需要分别处理，不能类比**：技能只有"会不会"一个维度，唯一入口是 Role，去重按覆盖优先级处理；工具需要两个独立维度同时满足才能被调用——业务角色边界（Role 授权范围）与执行环境技术边界（Agent 执行体本身的能力上限），两层都说"行"才真的行，这是纵深防御，用**交集**不是覆盖。

```java
public interface EffectiveToolResolver {
    /**
     * Agent 层 allowedTools（执行环境技术边界）∩ 已解析角色工具集合（业务角色边界）。
     * Role 空集表示角色层未限制；Role 非空且 Agent allowedTools 为空时拒绝执行。
     */
    List<ToolRef> resolve(
        Set<String> roleAllowedToolNames,
        List<ToolRef> agentAllowedTools);
}

public final class DefaultEffectiveToolResolver implements EffectiveToolResolver {
    @Override
    public List<ToolRef> resolve(
            Set<String> roleAllowedToolNames,
            List<ToolRef> agentAllowedTools) {
        if (roleAllowedToolNames.isEmpty()) return List.copyOf(agentAllowedTools);
        if (agentAllowedTools.isEmpty()) {
            throw new IllegalStateException("Agent 层未声明 allowedTools，无法与角色白名单取交集");
        }
        return agentAllowedTools.stream()
            .filter(ref -> roleAllowedToolNames.contains(ref.name()))
            .toList();
    }
}
```

**用途**：`SubagentSpec.Dynamic.tools` 与主 HarnessAgent 的 Toolkit 注册都必须先经 `EffectiveToolResolver.resolve(...)`，不能直接使用 `AgentSpec.tools()`/`ai_agent_definition.tools` 原始值——这是本次改造对当前代码的**修复**，不是新增能力，SQL 设计意图已经存在，只是从未被实现。

## 四、SubagentSpec 领域模型与 ai_agent_definition 降级

### 4.1 SubagentSpec：替代 `agentId + agentDefinitionVersion`

```java
// intelligent.agent.model.SubagentSpec
public sealed interface SubagentSpec {

    /** 预定义模板——从 ai_agent_definition 查（治理场景）。 */
    record Predefined(AgentId agentId, long version) implements SubagentSpec {}

    /** 动态构造——从触发它的 Assistant 当前 Role/技能/工具上下文现场拼装，不落库。 */
    record Dynamic(
        String name,
        String description,
        String systemPromptFragment,   // 追加到主 Agent sysPrompt 之后的补充指令
        List<ToolRef> tools,           // Agent 技术上限；compiler 仍需与 command 的角色工具集合取交集
        ExecutionPolicy executionPolicy,
        boolean inheritParentTools      // true 时忽略 tools，直接继承父全部工具
    ) implements SubagentSpec {}
}
```

`SkillRoute.subagentSpec` 字段类型即为 `SubagentSpec`——每条 Skill 路由自己决定命中后是"委派预定义 Agent"还是"动态构造子任务"，两种共存，由具体场景的治理需求决定，不是二选一的全局开关。

### 4.2 ai_agent_definition 的新定位

- **表结构不变，不新增技能字段（技能挂载点仍唯一收敛在 `ai_role.skill_ids`，见二、），不删除，`AgentDefinition`/`AgentDefinitionRepository`/`AgentDefinitionPort`/`JpaAgentDefinitionAdapter` 全部保留**
- **唯一变化**：`AgentDefinitionPort.findByIdAndVersion` 不再是 `HarnessAgentExecutionAdapter.execute()` 主链路的必经调用，改为只在 `SubagentSpec` 是 `Predefined` 分支时才被调用；`ai_agent_definition.allowed_tools` 仍是"执行环境技术边界"这一层输入，参与 `EffectiveToolResolver` 交集运算，不单独使用
- 当前两条内置数据（`system.agent.content-creator`/`system.agent.customer-service`）：因为已确认各自单一归属、不需要治理级隔离执行，**改为 `SubagentSpec.Dynamic`**，`v201__install_system_agent_definitions.sql` 删除（这次真的可以删，因为不再有代码路径依赖它们）
- `ai_agent_definition` 表保留给未来真正出现的"多助理共享、需要独立审核/超时/模型配置"的专家执行器（例如通用代码执行器），当前无生产数据占用，是"预留能力"，Dashboard 统计指标可以保留（数值为 0 也是有效信息，不用删）

## 五、AgentScope 基础设施层改造

### 调用前解析完整执行画像

AgentScope 2.0 将 `sysPrompt`、模型、Toolkit 和 middleware 视为 Agent 实例的不可变配置；`RuntimeContext` 只隔离调用态和会话态。因此 Assistant 必须在进入基础设施层前完成业务解析：

```java
public record AgentExecutionCommand(
    SubagentSpec subagentSpec,
    Optional<ModelSpec> parentModel,
    String skillSystemPromptAppendix,
    Set<String> roleAllowedToolNames,
    long sequenceBase,
    List<AgentMessage> messages,
    InvocationContext context) {}
```

`skillSystemPromptAppendix` 由 `EffectiveSkillResolver` 结果按 `priority` 降序、稳定标识升序拼接，过滤空提示并按文本去重。`roleAllowedToolNames` 是全部角色工具白名单的合并结果；当前单 Role 模型直接使用 `definition.role().toolKeys()`，多角色关联落地后改为 union。

### HarnessAgentExecutionAdapter 直接解析执行规格

```java
private ResolvedExecution resolveExecution(AgentExecutionCommand command) {
    return switch (command.subagentSpec()) {
        case SubagentSpec.Predefined predefined -> {
            var spec = definitions.findByIdAndVersion(
                    predefined.agentId(), predefined.version()).orElseThrow(...);
            yield new ResolvedExecution(
                    compiler.compile(
                            spec,
                            command.skillSystemPromptAppendix(),
                            command.roleAllowedToolNames()),
                    spec.model(),
                    predefined.identifier(),
                    spec.executionPolicy(),
                    false);
        }
        case SubagentSpec.Dynamic dynamic -> {
            var parentModel = command.parentModel().orElseThrow(...);
            yield new ResolvedExecution(
                    compiler.compileDynamic(
                            dynamic,
                            parentModel,
                            command.skillSystemPromptAppendix(),
                            command.roleAllowedToolNames()),
                    parentModel,
                    dynamic.identifier(),
                    dynamic.executionPolicy(),
                    true);
        }
    };
}
```

本阶段不使用 AgentScope 原生 `agent_spawn`，不新增共享 `HarnessAgentCatalog`，不访问 `getSubagentAgentManager()`，也不调用 `replaceAgents(...)`。Dynamic 的 `ephemeral=true` 所有权由 adapter 持有，通过 `doFinally` 和同步异常路径覆盖完成、失败、超时和取消。

### AgentScopeSpecCompiler 的缓存边界

Predefined 根据完整不可变执行画像缓存：

```java
private record DefinitionKey(
    AgentId agentId,
    long version,
    List<ToolRef> effectiveTools,
    String effectiveSystemPrompt) {}
```

编译步骤固定为：

1. `EffectiveToolResolver.resolve(roleAllowedToolNames, agentAllowedTools)` 计算 Toolkit 实际注册工具。
2. 基础 Prompt 与 `skillSystemPromptAppendix` 规范化拼接，得到最终系统提示。
3. Predefined 按完整键复用；Dynamic 不缓存，每次创建。
4. 两类叶子 Agent 均保持 `disableSubagents()`、`disableDynamicSubagents()`，防止递归委派和共享 manager 参与业务路由。
5. Toolkit 注册交集后的工具；`ToolGatewayPort` 在调用时继续鉴权，形成可见能力与最终授权的纵深防御。

### AgentScope 2.0 契约限制

`HarnessAgent.Builder.subagentFactory` 的公共签名是：

```java
subagentFactory(String name, Function<String, Agent> factory)
```

内部桥接明确忽略父 `RuntimeContext`，不能从共享父 Agent 的 factory 中读取请求级 `SubagentSpec`。虽然底层 `SubagentFactory.create(RuntimeContext)` 接收父上下文，Builder 并未公开该重载。共享 `DefaultAgentManager.replaceAgents(...)` 会让并发请求互相覆盖 factory，可能串用 Prompt、工具授权或租户上下文，属于禁止方案。

未来若必须使用原生 `agent_spawn`，只能保存静态构建计划，并为每次请求创建独立父 HarnessAgent，让 factory 捕获该次不可变 command；不得共享父实例的可变 manager。

## 六、影响文件清单（按改动类型分类）

| 类型 | 文件 | 改动 |
|---|---|---|
| 新增 | `intelligent.assistant.port.RoleDefinitionPort` | 新端口 |
| 新增 | `infrastructure.assistant.persistence.JpaRoleDefinitionAdapter` | 新适配器 |
| 新增 | `intelligent.agent.port.SkillCatalogPort` | 新端口 |
| 新增 | `infrastructure.*.JpaSkillCatalogAdapter` | 新适配器 |
| 新增 | `intelligent.assistant.application.EffectiveSkillResolver` + 默认实现 | 新领域服务（角色+内置两层） |
| 新增 | `intelligent.assistant.application.EffectiveToolResolver` + 默认实现 | 新领域服务（Role∩Agent 双层交集，修复现有 SQL 设计缺口） |
| 新增 | `intelligent.agent.model.SubagentSpec`（sealed interface） | 新领域模型 |
| 改动 | `intelligent.agent.model.AgentExecutionCommand` | 新增技能提示附录与中性角色工具约束 |
| 改动 | `intelligent.assistant.model.SkillRoute` | 字段替换（agentId+version → subagentSpec，删 name） |
| 改动 | `intelligent.assistant.model.Role` | 语义对齐 ai_role 投影 |
| 改动 | `intelligent.assistant.model.AssistantDefinition` | `validateRoutes` 校验扩展为"该助理挂载全部角色技能+内置技能"合并结果 |
| 改动 | `intelligent.infrastructure.assistant.BuiltinSystemAssistantTemplates` | 改为读 `RoleDefinitionPort`/`SkillCatalogPort` 构造，不再硬编码；两个内置 Agent 改 `SubagentSpec.Dynamic` |
| 改动 | `infrastructure.agentscope.execution.HarnessAgentExecutionAdapter` | 按命令直接解析 Predefined/Dynamic 执行画像并管理生命周期 |
| 改动 | `infrastructure.agentscope.compiler.AgentScopeSpecCompiler` | 两类规格统一合并 Prompt、强制工具交集；Predefined 完整画像缓存 |
| 改动 | `infrastructure.assistant.spring.AssistantInfrastructureAutoConfiguration` | 注册 EffectiveSkillResolver 并接入应用服务 |
| 测试 | `AssistantApplicationServiceTest`、`AgentScopeSpecCompilerTest`、`HarnessAgentExecutionAdapterTest` | 验证提示顺序、并发隔离和临时释放 |
| 改动 | `infrastructure.agentscope.tool.AgentScopeToolkitFactory` | `create(refs)` 调用方改传交集后的 `List<ToolRef>`，工厂本身签名不变 |
| 数据库迁移 | `ai_role` 新增 `code` 列 | 新迁移脚本 |
| 数据库迁移 | `ai_role.skillIds` 语义变更（数字ID→技能code） | 需数据回填脚本 |
| 删除 | `db/seed/v201__install_system_agent_definitions.sql` | 两条内置 Agent 改 Dynamic 后不再需要 |
| 保留不动 | `ai_agent_definition` 表、`AgentDefinition`、`AgentDefinitionRepository`、`AgentDefinitionPort`、`JpaAgentDefinitionAdapter` | 降级为可选分支，非主链路；表结构不加技能字段 |

**未覆盖、需要 architect 二次确认的遗留问题**：`intelligent.assistant.role.Role`（v1 Entity）与新 `RoleDefinitionPort` 的关系——本方案让 v2 领域层通过端口读 v1 Entity 数据，`module/ai/role` 管理后台的 CRUD 保持不变，两者不冲突，但需要确认 `ai_role.skillIds` 字段语义变更（数字 ID → 技能 code）是否会破坏现有管理后台的编辑体验。

## 七、分阶段计划（建议）

1. **阶段一（数据库+只读投影）**：`ai_role` 加 `code` 列 + 回填；`RoleDefinitionPort`/`SkillCatalogPort` 只读接线；不改任何执行逻辑，先验证数据能正确读出
2. **阶段二（两层技能合并 + 工具双层交集落地）**：`EffectiveSkillResolver`/`EffectiveToolResolver` 实现 + 单测；`BuiltinSystemAssistantTemplates` 改为从端口读取构造，两个内置助理数据迁移验证；`AgentScopeSpecCompiler.compile` 接入 `EffectiveToolResolver`（修复现有交集缺口）
3. **阶段三（完整执行画像接线）**：`AgentExecutionCommand` 增加技能提示与角色工具约束；`AssistantApplicationService` 调用 `EffectiveSkillResolver`；`HarnessAgentExecutionAdapter`/`AgentScopeSpecCompiler` 对 Predefined/Dynamic 统一强制 Prompt 与工具边界；不新增共享 Catalog
4. **阶段四（回归验证）**：`pnpm check:affected` 全绿，重点验证相同定义不同画像的并发隔离、Dynamic 全终止路径释放，以及内容创作/客服两个内置助理行为不变

## 风险提示

- 阶段三改 `SkillRoute` 字段属于接口签名变更，按协作规范需协调者同步所有调用方（当前已知调用方：`AssistantApplicationService`、`CompletionValidator`、`EffectiveContextPort`、`JpaEffectiveContextAdapter`、`AssistantCapabilityManifest`，共 5 处，触发"≥5 文件协调者评估"红线）
- `ai_role.skillIds` 语义变更（数字 ID → code）是破坏性数据迁移，需要先确认现网是否已有非内置的用户自建角色数据，避免回填脚本覆盖真实业务数据
- AgentScope 2.0 的 Agent 实例配置（sysPrompt、model、Toolkit、middleware）在构建后固定；`RuntimeContext` 的属性 bag 可变且线程安全，但只应用于调用态共享，不会自动重配置 Agent。不得把工具授权只写入 RuntimeContext 而继续暴露未收窄 Toolkit
- 禁止在共享 HarnessAgent 上按请求调用 `DefaultAgentManager.replaceAgents(...)`：reasoning 与后续 `agent_spawn` 之间存在快照覆盖窗口，并发请求可能串用 Prompt、工具或租户上下文
- 阶段二新增的工具交集校验是**修复性变更**：现网若存在 `ai_agent_definition.allowed_tools` 与对应角色 `tool_whitelist` 不一致（历史配置从未被校验过），接入交集后可能导致原本能调用的工具突然被拒绝，需要上线前跑一遍现网数据的交集预演，排查潜在的误拒面
