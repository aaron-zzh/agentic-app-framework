---
level: Practice
layer: Model
purpose: Prompt 组件设计——提示词管理、链式组装、Token 预算感知、Few-shot、评估优化
status: draft
version: 0.1.0
date: 2026-05-28
author: AaronZZH & Kiro
---

# Prompt 组件设计

> Prompt 是 LLM 的唯一输入，组装质量直接决定输出质量。本组件位于 `intelligent/core/prompt/`，为所有 LLM 调用提供模板管理和上下文组装能力。

## 定位

```text
intelligent/core/prompt/  ← 不是独立引擎，是 Core 层的基础能力
  被 Agent、Assistant、Cognition 层调用
  服务层提供管理 API（CRUD + 版本对比）
```

## 核心能力

| 能力 | 说明 | 状态 |
|------|------|------|
| 模板库 | 命名模板 CRUD + 分类 + 变量声明 | ✅ 已实现 |
| 变量注入 | `${var}` 模式替换 | ✅ 已实现 |
| 版本管理 | 多版本共存，激活版本切换 | ✅ 已实现 |
| 链式组装 | 多模板片段按顺序拼接 | ✅ 基础实现 |
| Token 预算感知 | 计算剩余空间，按优先级截断 | ⚠️ 待实现 |
| 多角色消息组装 | system/user/assistant 多段结构化 | ⚠️ 待实现 |
| Few-shot 管理 | 示例库 + 按相关度选取 Top-K | ⚠️ 待实现 |
| 记忆窗口注入 | 按配置注入 N 轮历史对话 | ⚠️ 待实现 |
| 评估优化 | 测试用例 → 执行 → 评分 → 迭代 | ⚠️ 待实现 |

## Token 预算感知组装

> 参考 Dify `PromptTransform._calculate_rest_token` 设计。

核心问题：LLM 上下文窗口有限，Prompt 组装时必须知道"还剩多少空间"。

```text
模型上下文窗口（如 128K）
  - 预留输出空间（max_tokens，如 4K）
  - System Prompt（P0，不可压缩）
  - 当前消息（P0，不可压缩）
  = 剩余可用空间
    → 按 P1-P5 优先级依次填充，超出则截断
```

设计：

```java
public record TokenBudget(
    int totalContextWindow,   // 模型上下文窗口
    int reservedForOutput,    // 预留输出空间
    int fixedPromptTokens,    // P0 固定部分已占用
    int remaining             // 剩余可分配
) {
    public static TokenBudget calculate(String modelId, List<PromptMessage> fixedMessages) {
        // 查 ai_model 表获取 contextWindow
        // 计算 fixedMessages 的 token 数
        // remaining = contextWindow - reservedForOutput - fixedTokens
    }
}
```

与 P0-P5 优先级的关系：

| 优先级 | 内容 | 截断策略 |
|--------|------|---------|
| P0 | System Prompt + 当前消息 | 不可压缩 |
| P1 | 工作记忆 | 超出时摘要压缩 |
| P2 | 短期记忆（近期会话） | 按窗口大小截断 |
| P3 | 知识库检索结果 | 减少 Top-K |
| P4 | 用户画像 | 压缩为一句话摘要 |
| P5 | 情景记忆 | 最先丢弃 |

## 多角色消息组装

当前只支持单字符串模板，需要升级为结构化多消息：

```java
public record PromptMessage(
    Role role,        // SYSTEM / USER / ASSISTANT
    String content,
    List<MediaContent> media  // 多模态（v1.0+）
) {
    public enum Role { SYSTEM, USER, ASSISTANT }
}

// 组装结果是 List<PromptMessage>，不是单个 String
```

## Few-shot 管理

```text
Few-shot 示例库（按模板关联）：
  ├── 示例 1：input → output
  ├── 示例 2：input → output
  └── 示例 N：input → output

选取策略：
  1. 按与当前输入的语义相似度排序
  2. 取 Top-K（受 Token 预算约束）
  3. 注入到 Prompt 的 user/assistant 交替消息中
```

## 记忆窗口注入

> 参考 Dify `memory_config.window.size`。

```text
配置：
  window_enabled: true
  window_size: 10        // 最近 10 轮
  max_tokens: 2000       // 或按 Token 数截断

注入位置：System Prompt 之后、当前消息之前
格式：历史消息按 user/assistant 交替排列
```

## 评估优化

```text
评估流程：
  1. 定义测试用例集（输入变量 + 期望输出/评分标准）
  2. 对每个用例执行 Prompt → LLM → 获取实际输出
  3. 对比评分（精确匹配 / 语义相似度 / LLM-as-Judge）
  4. 生成评估报告（准确率、失败用例、改进建议）

优化闭环：
  评估报告 → AI 分析薄弱点 → 生成改进版 Prompt → 重新评估 → 迭代
```

## 与其他组件的关系

```text
MemoryPipeline（Cognition）→ 产出 MemoryContext
                                    ↓
PromptTemplateService（Core）→ 组装最终 Prompt
                                    ↓
ResilientChatService / AgentScope → 发送给 LLM
```

## 相关文档

- [执行流全景](../../execution-flow.md) — Prompt 在调用链中的位置
- [用户感知与个性化](../cognition/personalization.md) — P4 用户画像注入
- [记忆管道](../../engine/data-knowledge/atom-memory.md) — P1-P5 上下文来源
