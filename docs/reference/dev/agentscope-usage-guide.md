---
level: Practice
layer: Framework
purpose: AgentScope 使用方式与运行时原理参考——从 ReActAgent 基础用法到 HarnessAgent 多用户多智能体场景
status: published
version: 1.2.0
date: 2026-07-29
author: AaronZZH & Kiro
scope:
  includes:
    - ReActAgent/HarnessAgent 最小可用示例
    - Harness 官方架构原理（能力叠加、三层状态流转）
    - 响应式运行时原理（非阻塞、Hook、Pipeline、长期记忆、MCP）
    - 多用户多智能体（主 Agent + 子 Agent）场景实现方式
    - Agent 定义来源的两种模式对比
  excludes:
    - AAF 领域模型与 AgentScope 的映射决策（见 architecture-v2.md）
    - AgentScope 内部实现细节
gains:
  - 知道 ReActAgent 与 HarnessAgent 的关系与选型依据
  - 知道 AgentScope 2.0 官方以 Harness 为生产环境主推路径
  - 理解 Harness 能力叠加机制与三层状态流转
  - 理解响应式非阻塞运行时约束的来源
  - 知道 Hook/Pipeline/长期记忆等机制与 AAF 对应实现的关系
  - 知道多智能体场景下 Agent 定义与实例的关系
  - 知道 Agent 定义来源的两种可选模式及适用场景
---

# AgentScope 使用方式与运行时原理参考

> 基于 `tmp/agentscope-java` 官方源码、README_zh.md、SKILL.md 与 `docs/v2/zh` 官方文档站整理。仅描述 AgentScope 本身的用法，不涉及 AAF 领域决策。

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

AAF 选用 `HarnessAgent` 而非裸 `ReActAgent` 承载 Agent 层执行，与官方生产环境推荐路径一致：需要会话持久化（`AgentStateStore`）、沙箱隔离、技能装配这些工程能力，恰恰是 `HarnessAgent` 相对 `ReActAgent` 补齐的部分。

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
| **配置/数据持久化** | 数据库表或配置文件，运行时读取编译 | Agent 需要被业务方（管理员/租户）配置、版本化、审核后才能生效 | AAF `ai_agent_definition` → `AgentSpec` → 编译为 `HarnessAgent` |

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

### Hook：运行时介入机制

Hook 是 AgentScope 提供"自主但可控"的核心机制——在推理循环的关键节点插入自定义逻辑，不修改框架源码、不子类化：

```java
public interface Hook {
    <T extends HookEvent> Mono<T> onEvent(T event);
    default int priority() { return 100; }  // 数值越小优先级越高
}
```

关键事件类型：`PreReasoningEvent`/`PostReasoningEvent`（LLM 推理前后，可修改）、`PreActingEvent`/`PostActingEvent`（工具执行前后，可修改）、`ReasoningChunkEvent`/`ActingChunkEvent`（流式片段，仅通知）。

优先级分档参考：0-50 系统级（鉴权/安全）、51-100 高优先级（校验/预处理）、101-500 业务逻辑、501-1000 低优先级（日志/度量）。

AAF 的 `AafToolPermissionHook`/`AafTraceHook`/`MemoryContextHook` 都是这套 Hook 机制的具体实现，对应"权限门控""执行轨迹""记忆注入"这些横切能力，不侵入 Agent 核心循环。

### 运行时介入：中断、取消、人机协同

生产环境的"自主性"必须配合运行时可控性，AgentScope 原生提供三种机制：

- **安全中断（interrupt）**：任意时刻暂停执行，完整保留上下文和工具状态，支持无损恢复。
- **优雅取消**：终止长时间运行/无响应的工具调用，不破坏 Agent 状态，可立即恢复或重定向。
- **人机协同（HITL）**：通过 Hook 在任意推理步骤注入人工修正、补充上下文或指导，`PostReasoningEvent.stopAgent()` 暂停执行等待人工确认，恢复靠 `agent.stream(StreamOptions.defaults())` 续跑。

这套机制是 AAF `ToolPermissionChecker`/HITL 审批流程的技术底座。

### Pipeline：多 Agent 编排

用于结构化编排多个 Agent 协作，两种基本模式：

```java
// 顺序执行：上一个 Agent 的输出是下一个的输入
SequentialPipeline pipeline = SequentialPipeline.builder()
    .addAgent(researchAgent)
    .addAgent(summaryAgent)
    .addAgent(reviewAgent)
    .build();

// 并行执行：多个 Agent 独立工作，结果聚合
FanoutPipeline pipeline = FanoutPipeline.builder()
    .addAgent(agent1)
    .addAgent(agent2)
    .build();
```

选择依据：任务间有依赖（后者需要前者输出）→ Sequential；任务可独立并行、只需汇总结果 → Fanout。这对应 AAF Team 层"主导助理牵头分工"的两种基础编排原语。

### 长期记忆：三种模式

```java
LongTermMemory longTermMemory = Mem0LongTermMemory.builder()
    .apiKey(System.getenv("MEM0_API_KEY"))
    .userId("user_123")   // 多租户隔离的关键参数
    .build();

ReActAgent agent = ReActAgent.builder()
    .model(model)
    .longTermMemory(longTermMemory)
    .longTermMemoryMode(LongTermMemoryMode.BOTH)
    .build();
```

| 模式 | 行为 |
|---|---|
| `STATIC_CONTROL` | 框架自动管理（通过 Hook 在每轮前自动 retrieve/写回） |
| `AGENTIC` | Agent 自主决定何时使用记忆（通过工具调用） |
| `BOTH` | 两种方式结合 |

AAF 的立场是**不使用 AgentScope 原生 `longTermMemory`**，而是用 `MemoryContextHook`（等价于 `STATIC_CONTROL` 语义，但由 AAF 自己的 `RetrievalPipeline` 实现）手动注入——因为 AAF 已有独立的记忆/知识融合检索管道（`Cognition` 层），若同时启用两套会造成双重检索、双真理源。这是 AAF "划界"原则的具体应用，不是不知道官方机制存在。

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

- [五层智能架构 v2](../../design/framework/intelligent/architecture-v2.md) — AAF 领域模型与 AgentScope 的映射决策
- [五层智能架构 v2 开发计划](../../design/framework/intelligent/architecture-v2-development-plan.md) — 实施阶段与端口契约
