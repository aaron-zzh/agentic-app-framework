---
status: draft
purpose: 记忆管道技术方案
date: 2026-05-28
---

# 记忆管道技术方案

> 基于 AgentScope LongTermMemory 接口适配。AafLongTermMemory 实现 record/retrieve，委托 AAF 读写管道。

## AgentScope 接口映射

| AAF 组件 | AgentScope 接口/类 | 说明 |
|----------|-------------------|------|
| `AafLongTermMemory` | `io.agentscope.core.memory.LongTermMemory` | AAF 实现此接口，委托内部记忆引擎 |
| 自动记忆注入 | `io.agentscope.core.memory.StaticLongTermMemoryHook` | STATIC_CONTROL 模式自动 retrieve/record |
| Agent 主动记忆 | `io.agentscope.core.memory.LongTermMemoryTools` | AGENT_CONTROL 模式，Agent 通过工具操作记忆 |
| 短期记忆 | `io.agentscope.core.memory.Memory`（`InMemoryMemory`） | 会话内消息列表 |

### LongTermMemory 接口

```java
// AgentScope 定义的接口（AAF 需实现）
public interface LongTermMemory {
    // 记录消息到长期记忆（PostCallEvent 后自动调用）
    Mono<Void> record(List<Msg> msgs);

    // 检索相关记忆（PreCallEvent 前自动调用）
    Mono<String> retrieve(Msg msg);
}
```

### LongTermMemoryMode 模式

| 模式 | 说明 | AAF 使用场景 |
|------|------|-------------|
| `STATIC_CONTROL` | 框架自动 retrieve + record | 默认模式，透明记忆管理 |
| `AGENT_CONTROL` | Agent 通过工具主动操作 | 需要精细控制时 |
| `BOTH` | 两者结合 | 自动 + Agent 可主动补充 |

## 适配器实现

> **实现状态**：`AafLongTermMemory` 尚未实现。当前 v0.1.0 的记忆管道通过
> `DefaultAssistantExecutor` → `MemoryPipelineFactory` → `DefaultMemoryPipeline` 在 Agent 调用前手动注入上下文，
> 而非通过 AgentScope Hook 自动触发。待 v0.2+ 引入 `StaticLongTermMemoryHook` 后迁移。

### AafLongTermMemory（薄门面，待实现）

```java
package com.xuejiai.aaf.framework.intelligent.cognition;

/**
 * AAF 记忆管道的 AgentScope LongTermMemory 适配。
 * retrieve 委托 AAF MemoryReadPipeline，record 委托 MemoryWritePipeline。
 */
@RequiredArgsConstructor
public class AafLongTermMemory implements LongTermMemory {

    private final MemoryReadPipeline readPipeline;
    private final MemoryWritePipeline writePipeline;
    private final String userId;
    private final String assistantId;

    @Override
    public Mono<String> retrieve(Msg msg) {
        // 委托 AAF 记忆读管道：AtomMemory 向量检索 + 时序索引 + Bundle 聚合
        var query = msg.getTextContent();
        return readPipeline.recall(userId, assistantId, query)
                .map(memories -> memories.stream()
                        .map(AtomMemory::content)
                        .collect(Collectors.joining("\n")));
    }

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        // 委托 AAF 记忆写管道：提取 → 去重 → 持久化
        var contents = msgs.stream()
                .filter(m -> m.getRole() == MsgRole.USER || m.getRole() == MsgRole.ASSISTANT)
                .map(Msg::getTextContent)
                .filter(Objects::nonNull)
                .toList();
        return writePipeline.extract(userId, assistantId, contents);
    }
}
```

## 关键 Hook 注入点

| Hook | 拦截事件 | 优先级 | 逻辑 |
|------|---------|--------|------|
| `StaticLongTermMemoryHook`（AgentScope 内置） | `PreCallEvent` | 50 | 调用 `AafLongTermMemory.retrieve()`，将结果包裹 `<long_term_memory>` 标签注入 inputMessages |
| `StaticLongTermMemoryHook`（AgentScope 内置） | `PostCallEvent` | 50 | 调用 `AafLongTermMemory.record()`，将全部对话写入长期记忆 |

### StaticLongTermMemoryHook 工作流程

```text
PreCallEvent:
  1. 提取最后一条 USER 消息作为 query
  2. 调用 longTermMemory.retrieve(query)
  3. 将结果包裹为 <long_term_memory>...</long_term_memory>
  4. 作为 SYSTEM 消息追加到 inputMessages 末尾

PostCallEvent:
  1. 获取 Memory 中所有消息
  2. 调用 longTermMemory.record(allMessages)
  3. 错误不中断流程（仅 warn 日志）
```

## 配置与初始化

```java
// 在 AgentFactory 中构建 Agent 时注入记忆管道
public ReActAgent createWithMemory(AgentDefinition def, String userId) {
    // 构建 AAF 记忆适配
    var aafMemory = new AafLongTermMemory(
            readPipeline, writePipeline, userId, def.id());

    // 短期记忆（会话内）
    var memory = new InMemoryMemory();

    // StaticLongTermMemoryHook 自动注册（STATIC_CONTROL 模式）
    var memoryHook = new StaticLongTermMemoryHook(aafMemory, memory);

    return ReActAgent.builder()
            .name(def.name())
            .model(model)
            .memory(memory)
            .hook(memoryHook)  // 自动 retrieve/record
            .build();
}
```

## 相关文档

- [功能设计 — 记忆管道](memory-pipeline.md)
- [AtomMemory 记忆引擎](../../engine/data-knowledge/atom-memory.md)
- [AgentScope 整合策略](../agentscope-integration.md)
