---
level: Practice
layer: Framework
purpose: AgentScope 使用方式与运行时原理参考——从 ReActAgent 基础用法到 HarnessAgent 多用户多智能体场景
status: published
version: 1.5.0
date: 2026-08-31
author: AaronZZH & Kiro
scope:
  includes:
    - ReActAgent/HarnessAgent 最小可用示例
    - Harness 官方架构原理（能力叠加、三层状态流转）
    - 响应式运行时、Middleware、Subagents、生产保护与 MCP
    - 多用户多智能体（主 Agent + 子 Agent）场景实现方式
    - Agent 定义来源的两种模式对比
  excludes:
    - AAF 领域模型与 AgentScope 的映射决策（见 architecture.md）
    - AgentScope 内部实现细节
    - AgentScope 1.x Hook、Pipeline 与历史迁移 API
gains:
  - 知道 ReActAgent 与 HarnessAgent 的关系与选型依据
  - 知道 AgentScope 2.0 官方以 Harness 为生产环境主推路径
  - 理解 Harness 能力叠加机制与三层状态流转
  - 理解响应式非阻塞运行时约束的来源
  - 知道 Middleware、Subagents、执行保护和 Cognition 边界
  - 知道多智能体场景下 Agent 定义与实例的关系
  - 知道 Agent 定义来源的两种可选模式及适用场景
  - 知道官方文档入口与模块划分，能定位到具体章节核实能力语义
---

# AgentScope 使用方式与运行时原理参考

> 基于 `tmp/agentscope-java` 官方源码、README_zh.md、SKILL.md 与 `docs/v2/zh` 官方文档站整理。仅描述 AgentScope 本身的用法，不涉及 AAF 领域决策。

> **官方推荐 vs AAF 选型（ADR-005）**：本文档记录的 `HarnessAgent` 相关内容是 **AgentScope 官方** 的能力介绍与生产推荐路径，属于学习参考。**AAF 实际编译目标是 `agentscope-core` 的 `ReActAgent`，不使用官方 `HarnessAgent`**——AAF 自建 `HarnessAgentExecutionAdapter` 承担官方 Harness 同等定位的外层运行治理，两者代码不同源，注意区分"AgentScope 官方 Harness"与"AAF 自建 Harness（即 `HarnessAgentExecutionAdapter`）"这两个不同的概念。选型依据见 [ADR-005](../../design/adr/ADR-005-agentscope-boundary-and-orchestration.md)。

## ReActAgent 与 HarnessAgent 的关系

> 本节依据官方 `docs/v2/zh/docs/harness/architecture.md`（Harness 架构）整理，是权威一手信息。

`HarnessAgent`（`agentscope-harness` 模块）**组合**（不是继承）一个 `ReActAgent`（`agentscope-core` 模块）：

```java
public class HarnessAgent implements Agent, AutoCloseable {
    private final ReActAgent delegate;   // 组合，非继承
}
```

**AgentScope 2.0 官方定位：Harness 是生产环境主推路径，不是可选附加层。** 官方 2.0 首页三大主题之首就是"Harness 工程化"："裸的 ReAct 循环只解决'一次推理'。真实任务往往要跑数小时、积累大量状态、依赖可持续沉淀的能力。Harness 把这套工程基础设施一次给齐。"

二者的定位差异：

| | `ReActAgent`（core） | `HarnessAgent`（harness） |
|---|---|---|
| 定位 | 裸推理循环：一次请求→推理→工具→回复 | 长期运行 Agent 必备工程能力的统一封装 |
| 依赖 | 仅 `agentscope-core` | `agentscope-core` + `agentscope-harness` |
| 附加能力 | 无 | 工作区驱动人格、状态持久化、双层长期记忆、对话压缩、子 Agent 编排、沙箱隔离、计划模式、技能装配、MCP 白名单、Channel 路由 |
| 适用场景 | 快速原型、单元测试、教学示例 | **AgentScope 2.0 官方推荐的生产默认形态** |

**最小依赖只需 `agentscope-core`**，最简示例（对应 AAF 里"闲聊/简单问答，不走 Agent 池"的场景）：

```java
ReActAgent agent = ReActAgent.builder()
    .name("Assistant")
    .sysPrompt("You are a helpful AI assistant.")
    .model(DashScopeChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-max")
        .build())
    .build();

Msg response = agent.call(userMsg).block();  // .block() 仅允许在 main()/测试中使用
```

**AAF 选型说明（ADR-005，2026-08-31 改判）**：本节以上是 AgentScope 官方 `HarnessAgent` 的能力与选型依据介绍，属于官方推荐路径的客观描述。**AAF 实际落地不使用官方 `HarnessAgent`**——ADR-005 定案编译目标为 `agentscope-core` 的 `ReActAgent`，AAF 自建 `HarnessAgentExecutionAdapter` 作为唯一实现，在 core `ReActAgent` 之外自持外层装配（builder 参数、生命周期、缓存策略、middleware 装配、中断、能力启停），这个自建外层在 AAF 术语里叫"AAF Harness"，与本节介绍的官方 `HarnessAgent`（`agentscope-harness` 模块）是两个不同的东西，不要混淆。选型依据：`HarnessAgent` 对官方工程能力（工作区、长期记忆、原生子 Agent、动态 Skill、文件/Shell）的使用率为零（15 个 `disableXxx()` 全部禁用），AAF 已有的 TaskBoard、租约/fencing、预算、终态、事件事实、HITL、持久恢复等治理能力与官方 Harness 是平级不可替代关系而非包含关系，自建薄适配层复用 core 推理循环即可满足需求，不需要承担官方 Harness 的完整能力面与升级维护成本。详见 [ADR-005](../../design/adr/ADR-005-agentscope-boundary-and-orchestration.md)、[五层智能架构 · 双层循环](../../design/framework/intelligent/architecture.md#双层循环)。

### Harness 三条工作原理（官方原文）

理解 Harness 的实现方式只需记住三件事：

1. **能力叠加在推理循环关键时机上，不改写循环本身**——工作区注入、压缩、子 agent、沙箱、Plan Mode，每个能力钩在 ReAct 循环的关键时机；core 的算法没有变化，Harness 只是往里加东西。
2. **能力之间互不依赖，只通过三个共享对象通信**：
   - `RuntimeContext`——这次 `call()` 是谁在说话（`sessionId`/`userId`/自定义 extra），**不持久化**
   - 工作区——谁读写哪些文件，物理落地方式（本机/沙箱/KV存储）由配置决定
   - `AgentStateStore`——跨调用如何恢复运行时状态
3. **内置 middleware 注册顺序固定，自定义的跑在最前面**——通过 `.middleware(...)` 加的自定义逻辑，会先于 Harness 所有内置 middleware 执行。

### 状态三层流转（官方原文）

| 层次 | 内容 | 生命周期 |
|---|---|---|
| 调用内状态 | `AgentState`（对话上下文/权限规则/Plan Mode 状态/工具状态）+ `RuntimeContext`（sessionId/userId/沙箱句柄/extra） | 单次 `call()` 内 |
| 跨调用状态 | `AgentStateStore` 里的运行时快照 + 对话日志（永不压缩）+ 子任务记录 + 沙箱元数据 | 按 `(userId, sessionId)` 寻址，每次调用结束自动写盘、下次自动加载 |
| 长期记忆 | `memory/YYYY-MM-DD.md`（只追加）→ 后台节流合并 → `MEMORY.md`（每轮注入 system prompt） | 跨 session 累积 |

三个易忽略的细节：system prompt 每轮重新拼装，改 `AGENTS.md`/`MEMORY.md` 立即生效不需要重启；压缩/记忆提炼/后台维护都受节流闸门控制，不会每轮都跑；`AgentState` 持久化由 core 的 `ReActAgent` + `AgentStateStore` 自动完成，Harness 层不重复做这件事。

## 核心概念：定义与实例分离

理解 `HarnessAgent` 的关键是区分两个层次：

- **定义（Definition）**：Agent 叫什么、用什么系统提示词、绑定什么模型、能调用哪些工具——相对静态，决定"这是一个什么样的 Agent"。
- **实例/会话状态（Instance/AgentState）**：某次具体调用的上下文、记忆、执行进度——按 `(userId, sessionId)` 隔离，随会话动态变化。

`HarnessAgent` 本身**不持有长期状态**，只持不可变配置；会话可变状态外置在 `AgentState`（`AgentStateStore` 负责加载/保存）。这意味着同一个 Agent 定义可以被多个并发会话安全复用。

## 基本示例：单个 HarnessAgent

最小可用配置只需要 model、系统提示词、工具集：

```java
HarnessAgent agent = HarnessAgent.builder()
    .name("my-agent")
    .model(model)                    // 实现 io.agentscope.core.model.Model
    .sysPrompt("你是一个助手，负责...")
    .toolkit(new Toolkit())          // 可注册若干 @Tool 方法
    .maxIters(10)                    // ReAct 循环步数上限
    .build();

// 单轮调用
Msg response = agent.call(List.of(userMsg)).block();

// 流式调用，带会话上下文
RuntimeContext ctx = RuntimeContext.builder()
    .sessionId(conversationId)
    .userId(userId)
    .build();
agent.stream(List.of(userMsg), StreamOptions.defaults(), ctx)
    .subscribe(event -> { /* 处理事件 */ });
```

可选按需叠加的能力（均为 builder 上的独立配置项，互不依赖）：

| 能力 | Builder 方法 | 用途 |
|---|---|---|
| 会话持久化 | `.stateStore(redisAgentStateStore)` | 跨进程/重启恢复 `AgentState` |
| 长会话压缩 | `.compaction(CompactionConfig...)` | 消息数超阈值后自动摘要压缩 |
| 沙箱隔离 | `.filesystem(new DockerFilesystemSpec()...)` | 工具执行在隔离容器中进行 |
| 技能装配 | `.skillRepositories(...)` | 按需披露的技能集，激活后才暴露对应工具 |
| 计划模式 | `.enablePlanMode()` | 只读规划阶段，需显式确认后进入执行 |

一个 `HarnessAgent` 实例可以安全地被多个不同 `(userId, sessionId)` 并发调用——状态隔离由 `RuntimeContext` + `AgentStateStore` 保证，不需要为每个用户创建新的 Java 对象。

## 复杂场景：多用户多智能体

参考实现：`tmp/agentscope-java/agentscope-examples/agents/agentscope-codingagent/src/main/java/io/agentscope/harness/coding/agent`

这是一个真实的编程助手系统，同时服务多用户会话，且包含两个职责不同的 Agent（编程 Agent + 代码评审 Agent）。核心设计分三层：

### 第一层：多个职责不同的 Agent 用 Factory 静态定义

`CodingAgentFactory` 和 `ReviewerAgentFactory` 分别定义两个职责边界清晰的 Agent：

```java
// 编程 Agent：可读写代码、可调用 GitHub API、请求评审
HarnessAgent coding = HarnessAgent.builder()
    .name("agentscope-coding-agent")
    .model(buildModel())              // 模型可由环境变量动态选择
    .sysPrompt(CodingSystemPrompt.build(workingDir, linearCtx))
    .toolkit(codingToolkit)
    .maxIters(50)
    .compaction(CompactionConfig.builder()
        .triggerMessages(40).keepMessages(15).build())
    .build();

// 评审 Agent：只有评审类工具，禁止写代码，禁止再派生子 Agent
HarnessAgent reviewer = HarnessAgent.builder()
    .name("agentscope-reviewer-agent")
    .model(buildModel())
    .sysPrompt(ReviewerSystemPrompt.build(workingDir, repoOwner, repoName, prNumber))
    .toolkit(reviewerToolkit)         // add_finding/publish_review 等，无写权限
    .maxIters(30)
    .disableSubagents()               // 明确禁止评审 Agent 再委派
    .build();
```

要点：**两个 Agent 的种类和职责在代码里提前确定**，不是运行时由 LLM 自主决定"现在需要创建一个什么样的新 Agent"。工具白名单按角色严格区分（评审 Agent 拿不到写代码的工具），这是能力护栏在 Agent 粒度的体现。

### 第二层：会话与用户隔离——同一 Agent 定义，多用户复用

`SessionAgentManager` 负责会话生命周期，与 Agent 定义完全解耦：

```java
// 每次新会话生成独立 sessionKey，不是新建 Agent 定义
String sessionKey = "agent:" + agentId + ":main:" + UUID.randomUUID();
```

多用户并发场景下，`coding-agent` 这一个定义可以同时服务任意多个用户会话，区分方式是 `sessionKey`（内部映射到 `AgentState` 存储槽位），不是"每个用户一个 Agent 对象"。`agentCache`（按 sessionKey 缓存 `Agent` 实例，注释写明"避免同一 JVM 生命周期内重复创建"）进一步说明：缓存的粒度是**会话级**，不是**用户数量级或 Agent 定义数量级**。

### 第三层：主 Agent 按需动态委派子 Agent

评审 Agent 不是常驻等待的实例，是编程 Agent 判断"需要评审"时**现场创建**：

```java
// 主 Agent 判断需要评审时，动态创建 Reviewer 实例并注入 PR 上下文
HarnessAgent reviewer = ReviewerAgentFactory.create(
    reviewWorkspace, reviewerToolkit, repoOwner, repoName, prNumber);
```

子 Agent 的**定义模板**（`ReviewerAgentFactory`）仍然是静态代码，但**具体实例**按任务参数（`prNumber` 等）现场构造，执行完即可丢弃——不需要为每次评审持久化一条新的"Agent 定义"记录。

这与 AgentScope 官方的子 Agent 机制（`docs/harness/subagent.md`）是同一思路的两种实现方式。官方文档原文："让主 agent 把'可独立处理、上下文重、可并行'的任务委派出去，避免主线程膨胀。**每个子 agent 都是一个临时实例**（本地的 `HarnessAgent` 或远程 stub），跑自己的会话，结果通过工具返回给父 agent。"

子 Agent 声明支持三种来源，构建时合并，无需额外注册：

| 方式 | 适用场景 | 配置方式 |
|---|---|---|
| 内置 `general-purpose` | 通用兜底（镜像主 Agent 能力） | 总是存在，无需配置 |
| 工作区 spec 文件 | 项目特有、需要版本控制 | `workspace/subagents/<id>.md`，文件名即 `agent_id` |
| 编程式声明 | 运行时才能确定（远程、动态参数） | `builder.subagent(SubagentDeclaration.builder()...)` |

主 Agent 通过 `agent_spawn agent_id="reviewer" task="..."` 这样的工具调用委派任务，不需要额外注册步骤。

## Agent 定义来源：两种模式对比

| 模式 | 定义来源 | 适用场景 | 示例 |
|---|---|---|---|
| **代码静态定义** | Java Factory 方法，启动时确定 | Agent 种类少、职责固定、不需要业务侵入的运营/审核流程 | `CodingAgentFactory`/`ReviewerAgentFactory` |
| **配置/数据持久化** | 数据库表或配置文件，运行时读取编译 | Agent 需要被业务方（管理员/租户）配置、版本化、审核后才能生效 | AAF `ai_agent_definition` → `AgentSpec` → 编译为 `ReActAgent`（`AgentScopeSpecCompiler`，ADR-005 后编译目标，不是官方 `HarnessAgent`） |

两种模式的共同点（也是本文档最重要的结论）：**无论定义来源是代码还是数据库，Agent 的"实例"永远是运行时按需构造/复用的，不需要为每个用户或每次任务持久化一条新的定义记录**。持久化的是"定义"这份配置，不是"实例"。

判断该用哪种模式的经验法则：
- 只服务于单一固定上层调用者、职责在设计期就能穷尽 → 代码静态定义即可，省去持久化和治理成本
- 需要被多个不同上层复用、需要业务方配置/审核/追踪版本 → 数据持久化定义更合适

## 运行时原理：响应式、非阻塞

AgentScope 是**基于 Project Reactor 的响应式框架**，这是理解一切 API 行为的前提：

- **几乎所有操作返回 `Mono<T>`/`Flux<T>`**：Agent 调用、模型推理、工具执行都是异步非阻塞的。
- **`.block()` 是被严格禁止的反模式**（除 `main()` 方法/测试代码外）——在 Agent 逻辑、Service 方法、库代码中调用 `.block()` 会破坏响应式调用链，导致线程阻塞。AAF 的 `AgentExecutionPort.execute()` 返回 `Flux<ExecutionEvent>` 正是遵循这一约束。
- **禁止 `ThreadLocal`**：响应式流可能在不同线程间调度，`ThreadLocal` 不可靠。上下文传递统一用 `Mono.deferContextual()` / Reactor Context，这也是 AAF 用显式 `RuntimeContext`/`InvocationContext` 取代 `AafContextHolder`（ThreadLocal）的官方依据来源。
- **禁止 `Thread.sleep()`**：改用 `Mono.delay(Duration)`。

### 消息驱动模型

Agent 之间、Agent 与外界的交互统一用不可变的 `Msg` 对象传递，内容按 `ContentBlock` 类型区分：

| ContentBlock | 用途 |
|---|---|
| `TextBlock` | 纯文本 |
| `ThinkingBlock` | 思维链（CoT）推理过程 |
| `ToolUseBlock` | 工具调用请求 |
| `ToolResultBlock` | 工具执行结果 |

```java
Msg msg = Msg.builder()
    .role(MsgRole.USER)
    .content(TextBlock.builder().text("Hello").build())
    .build();

String text = msg.getTextContent();  // 优先用安全的 helper 方法，避免直接 .getContent().get(0) 可能 NPE
```

### Middleware：运行时介入机制

AgentScope 2.0 使用 `MiddlewareBase` 在推理循环关键阶段插入横切逻辑，不修改框架源码、不子类化 Agent：

| 阶段 | 典型用途 |
|---|---|
| `onAgent` | 整轮执行的前后处理、预算预检、轨迹 |
| `onReasoning` | 推理前后处理、上下文与任务提醒 |
| `onActing` | 工具权限、审批、结果脱敏和裁剪 |
| `onModelCall` | 模型计量、超时、重试观测 |
| `onSystemPrompt` | 按调用上下文组装系统提示 |

AAF 的记忆注入、权限、轨迹和计量分别由 v2 middleware 调用稳定端口完成。领域层不导入 `MiddlewareBase`，也不保留 Hook、ThreadLocal 或直接 SQL 接线。

### 运行时介入：取消、暂停与 HITL

生产环境的自主执行必须受 AAF 任务生命周期约束：

- 取消通过响应式订阅和任务状态传播到运行中的 Agent/工具，不用线程阻塞或轮询。
- 计划模式和工具审批只负责 Agent 运行时暂停；approval、责任主体和恢复点持久化在 AAF。
- `enablePendingToolRecovery(true)` 可恢复挂起的工具调用，但不能替代 `TaskControlPort` 或授权事实表。
- 重复审批回调、恢复和副本接管必须使用同一 executionId 与幂等键。

### Subagents 与 AAF 编排

AgentScope 2.0 的 Harness 通过 subagent middleware 支持单个 Assistant 内的自主任务委派。AAF 不使用 1.x `Pipeline`/`MsgHub` 作为 Team 或工作流主路径：

- Assistant 内探索性委派可使用 subagent。
- Team 的多 Assistant 分工、聚合和仲裁由 AAF Team 层负责。
- 确定性流程由 AAF 工作流引擎负责，节点执行单元是 `HarnessAgentExecutionAdapter` 包裹的 core `ReActAgent`（不是官方 `HarnessAgent`，见前文"AAF 选型说明"）。
- 子 Agent 使用独立 executionId/sessionId；父任务只通过 AAF 任务契约和事件聚合。

### 长期记忆边界

AgentScope 2.0 仍提供 `LongTermMemory` 和 Harness 工作区记忆能力，但 AAF 默认不把它们作为长期记忆源。长期记忆、知识和价值观唯一归 `Cognition`；调用前由 `MemoryContextMiddleware` 注入，执行后通过治理流水线写回。

`AgentState.context` 只是可压缩、可重建的推理工作集；`MEMORY.md` 默认关闭，如启用也只能是可从 Cognition 重建的缓存。禁止 AgentScope 记忆与 AAF Cognition 双写。

### 生产运行保护

以下 API 已在 AgentScope 2.0 `HarnessAgent.Builder` 中确认存在：

| API | 用途 | AAF 使用约束 |
|---|---|---|
| `modelExecutionConfig(ExecutionConfig)` | 模型调用超时和重试 | 与任务预算、截止时间和计量事件一致 |
| `toolExecutionConfig(ExecutionConfig)` | 工具调用超时和重试 | 副作用工具同时要求稳定幂等键 |
| `toolExecutionContext(ToolExecutionContext)` | 显式传递工具执行上下文 | 不用 ThreadLocal，不放凭证正文 |
| `enablePendingToolRecovery(true)` | 恢复挂起的工具调用 | 与持久 approval 和任务恢复配合 |

旧文档中的 `structuredOutputReminder(...)` Builder 示例不属于 2.0 正式版公开 API，不得继续复制；结构化输出应以当前模型接口和实际版本契约为准。

### MCP 集成

```java
McpClientWrapper mcpClient = McpClientBuilder.stdio()
    .command("npx")
    .args("-y", "@modelcontextprotocol/server-filesystem@0.6.2", "/path/to/files")
    .build();

toolkit.registration()
    .mcpClient(mcpClient)
    .enableTools(List.of("read_file", "write_file"))
    .apply();
```

生产环境务必固定 MCP server 版本号（如上例 `@0.6.2`），防止供应链投毒——这也是官方 SKILL.md 明确标注的安全要求。

## 相关文档

### 官方真理源

| 类型 | 位置 | 说明 |
|------|------|------|
| 官方文档（v2 中文） | <https://java.agentscope.io/v2/zh/docs/index.html> | 能力**语义**的权威源。本文与官方冲突时以官方为准。三层模块划分：核心组件 `building-blocks/` · Harness `harness/` · 集成 `integration/` |
| 官方文档（v2 英文） | <https://java.agentscope.io/v2/en/docs/index.html> | 中文章节缺失时以英文版为准 |
| 上生产检查清单 | <https://java.agentscope.io/v2/zh/docs/others/going-to-production.html> | 官方生产化要求，接线改动前对标 |
| 迁移指南 | <https://java.agentscope.io/v2/zh/docs/change-log.html> | 升级 AgentScope 版本前必读 |
| 本地源码副本 | `tmp/agentscope-java/` | 含 `agentscope-core`、`agentscope-harness`、`agentscope-extensions`；`SKILL.md` 是官方能力速查。**行为事实**的最终判据 |

### 能力判定三条规则

**一 · 不得凭模块名推断。** 判定"官方是否提供某能力"必须打开源码确认。模块名与实际能力经常不对应。

**二 · 涉及安全边界时，不得凭官方文档推断，必须落到源码。** 官方文档之间会互相矛盾，且陈旧文档不会自动打标。

实例：Plan Mode 的只读限制是否沿 `agent_spawn` 传播到子 Agent——

| 来源 | 结论 | Last updated |
|------|------|-------------|
| [子 Agent](https://java.agentscope.io/v2/zh/docs/harness/subagent.html) 文档 | 会自动继承 | 2026-08-09 |
| [计划模式](https://java.agentscope.io/v2/zh/docs/harness/plan-mode.html) 文档 | 标注「已知缺口」，不会继承 | 2026-07-16 |
| `harness/agent/tool/AgentSpawnTool.java:299-301` | **会继承** | — |

源码是 `parentState.getPlanModeContext().isPlanActive()` → `ha.enterPlanMode(currentUserId, sessionId)`。旧文档陈旧未更新。若当初按 plan-mode 文档判断"限制不传播"而自行补一层子 Agent 只读约束，就会写出一段永远为真的冗余代码。

**三 · builder 默认值必须验证，不能假设"没调用就是关闭"。** Harness builder 在 `build()` 时会默认创建部分组件并注册工具与 middleware。已确认的例子：即使未启用工作区能力，builder 仍会创建 `LocalFilesystem`、`WorkspaceManager`、`WorkspaceMessageBus`、`WorkspaceAsyncToolRegistry`，装配 `InboxMiddleware` 并注册 `WaitAsyncResultsTool`。判定实际生效的工具集与 middleware 清单，应在 `build()` 之后对最终 Toolkit 与 middleware 做断言，而不是依据 builder 调用序列推断。

### AAF 内部文档

- [五层智能架构](../../design/framework/intelligent/architecture.md) — AAF 领域模型、AgentScope 映射决策、实施阶段与端口契约
- [AgentScope 复用边界专项评估](../../design/audit/2026-08-30-agentscope-boundary/README.md) — 2026-08-30 时点的复用/自研测绘、编排层质量、能力差距与演进路线推荐
