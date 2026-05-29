---
level: Practice
layer: Model
purpose: 混合检索技术方案——UnifiedRetrievalService + HybridSearchService 实现
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# 混合检索技术方案

> UnifiedRetrievalService 外层路由 + HybridSearchService 内层三路融合 + 双层 RRF + LLM 重排。

## 检索流程

```text
输入（query, userId, knowledgeBaseIds[], memoryStrategy）
    │
    ▼ 查询理解（IntentUnderstandingService，LLM 驱动）
    │   提取关键实体、时间范围、意图类型
    │
    ▼ 路由决策（按 MemoryStrategy 或 LLM 动态判断）
    │
    ▼ 并行检索（虚拟线程，StructuredTaskScope）
    │   ├── AtomMemoryEngine.recall(scope, query)
    │   │     → 向量检索（PgVector cosine）
    │   │     → 时序索引（PostgreSQL，按时间窗口）
    │   │     → BundleSearch（关联记忆聚合）
    │   │
    │   └── HybridSearchService.search(knowledgeBaseIds, query)
    │         → SimilaritySearchService（PgVector）
    │         → PostgreSQL FTS（ts_rank BM25）
    │         → GraphSearchService（Neo4j 多跳 Cypher）
    │         → 内层 RRF 融合
    │
    ▼ 外层 RRF 融合（跨记忆/知识库）
    │   score = Σ 1/(k + rank_i)，k=60
    │
    ▼ LLM 重排（可选，RerankService）
    │   调用重排序模型返回精排分数
    │
    ▼ Value 过滤（ValueService.filter）
    │   敏感/违规/越权内容拦截
    │
    ▼ 输出 RetrievalResult
```

## RRF 融合算法

```text
RRF(d) = Σ 1 / (k + rank_r(d))

k = 60（常数，平衡高排名和低排名的权重差异）
rank_r(d) = 文档 d 在第 r 路检索结果中的排名

优势：不依赖各路检索的分数归一化，只用排名，天然消除来源偏差
```

## 并行执行

```java
// 虚拟线程并行检索
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var memoryTask = scope.fork(() -> atomMemoryEngine.recall(memoryScope, query));
    var knowledgeTask = scope.fork(() -> hybridSearchService.search(kbIds, query));
    scope.join().throwIfFailed();
    return rrfFuse(memoryTask.get(), knowledgeTask.get());
}
```

## 包结构

```text
intelligent/cognition/retrieval/
  ├── UnifiedRetrievalService      统一检索门面（编排并行+RRF+重排+过滤）

engine/knowledge/rag/              （引擎层，被 UnifiedRetrievalService 调用）
  ├── HybridSearchService          知识库三路融合检索
  └── RagSearchResult              检索结果

engine/knowledge/search/           （引擎层）
  ├── SimilaritySearchService      向量检索（PgVector）
  └── GraphSearchService           图谱检索（Neo4j）

engine/memory/                     （引擎层）
  ├── AtomMemoryEngine             记忆检索（向量+时序+Bundle）
  └── BundleSearchService          记忆束关联检索

intelligent/cognition/memory/
  └── MemoryRerankerService        LLM 重排
```

> 注意：`HybridSearchService` 位于 `engine/knowledge/rag/` 而非 `cognition/retrieval/`，
> 符合架构约束——引擎层提供检索能力，智能层编排调用。

## 相关文档

- [功能设计 — 混合检索](retrieval.md)
- [记忆管道](memory-pipeline.md)
- [AtomMemory 记忆引擎](../../engine/data-knowledge/atom-memory.md)
- [NexusKB 知识引擎](../../engine/data-knowledge/nexus-knowledge.md)

---

## AgentScope 接口映射

### 核心类映射

| AAF 组件 | AgentScope 接口/类 | 说明 |
|----------|-------------------|------|
| `AafKnowledge` | `io.agentscope.core.rag.Knowledge` | AAF 实现此接口，委托 HybridSearchService |
| 自动 RAG 注入 | `io.agentscope.core.rag.GenericRAGHook` | Generic 模式自动检索注入 |
| Agent 主动检索 | `io.agentscope.core.rag.KnowledgeRetrievalTools` | Agentic 模式，Agent 通过工具检索 |
| 检索配置 | `io.agentscope.core.rag.model.RetrieveConfig` | limit、scoreThreshold 等参数 |
| 文档模型 | `io.agentscope.core.rag.model.Document` | 检索结果载体（content + score + metadata） |

### Knowledge 接口

```java
// AgentScope 定义的接口（AAF 需实现）
public interface Knowledge {
    // 添加文档到知识库
    Mono<Void> addDocuments(List<Document> documents);

    // 检索相关文档
    Mono<List<Document>> retrieve(String query, RetrieveConfig config);
}
```

### RAGMode 模式

| 模式 | 说明 | AAF 使用场景 |
|------|------|-------------|
| `GENERIC` | 每次推理前自动检索注入 | 知识问答、客服场景 |
| `AGENTIC` | Agent 通过工具主动检索 | 复杂任务，Agent 决定何时检索 |

## 适配器实现

> **实现状态**：`AafKnowledge` 尚未实现。当前 v0.1.0 的知识库检索通过
> `UnifiedRetrievalService`（已实现，位于 `cognition/retrieval/`）直接调用 `HybridSearchService`（位于 `engine/knowledge/rag/`），
> 结果由 `DefaultAssistantExecutor` 手动注入 Agent 上下文。
> 待 v0.2+ 引入 `GenericRAGHook` 后，通过 `AafKnowledge` 适配器自动触发。

### AafKnowledge（薄门面，待实现）

```java
package com.xuejiai.aaf.framework.intelligent.cognition;

/**
 * AAF 知识库的 AgentScope Knowledge 适配。
 * retrieve 委托 AAF HybridSearchService（三路融合 + RRF）。
 */
@RequiredArgsConstructor
public class AafKnowledge implements Knowledge {

    private final HybridSearchService hybridSearchService;
    private final List<Long> knowledgeBaseIds;

    @Override
    public Mono<List<Document>> retrieve(String query, RetrieveConfig config) {
        // 委托 AAF 三路融合检索：向量 + FTS + 图谱 → RRF
        return Mono.fromCallable(() ->
                hybridSearchService.search(knowledgeBaseIds, query, config.getLimit()))
                .map(results -> results.stream()
                        .map(r -> Document.builder()
                                .metadata(DocumentMetadata.builder()
                                        .contentText(r.content())
                                        .build())
                                .score(r.score())
                                .build())
                        .toList());
    }

    @Override
    public Mono<Void> addDocuments(List<Document> documents) {
        // 委托 AAF 文档引擎处理入库（分块 + Embedding + 存储）
        return Mono.fromRunnable(() ->
                documents.forEach(doc ->
                        hybridSearchService.index(knowledgeBaseIds.get(0),
                                doc.getMetadata().getContentText())));
    }
}
```

## 关键 Hook 注入点

| Hook | 拦截事件 | 优先级 | 逻辑 |
|------|---------|--------|------|
| `GenericRAGHook`（AgentScope 内置） | `PreCallEvent` | 50 | 提取 query → 调用 `AafKnowledge.retrieve()` → 注入 `<retrieved_knowledge>` 到 inputMessages |

### GenericRAGHook 工作流程

```text
PreCallEvent:
  1. 从 inputMessages 提取最后一条 USER 消息的文本作为 query
  2. 调用 knowledge.retrieve(query, defaultConfig)
  3. 将检索结果格式化为 <retrieved_knowledge>...</retrieved_knowledge>
  4. 作为 USER 消息追加到 inputMessages 末尾
  5. 错误不中断流程（仅 warn 日志）
```

## 配置与初始化

```java
// 在 AgentFactory 中构建 Agent 时注入 RAG
public ReActAgent createWithRAG(AgentDefinition def, List<Long> kbIds) {
    // 构建 AAF 知识库适配
    var aafKnowledge = new AafKnowledge(hybridSearchService, kbIds);

    // 检索配置
    var retrieveConfig = RetrieveConfig.builder()
            .limit(5)
            .scoreThreshold(0.5)
            .build();

    // GenericRAGHook 自动注册
    var ragHook = new GenericRAGHook(aafKnowledge, retrieveConfig);

    return ReActAgent.builder()
            .name(def.name())
            .model(model)
            .hook(ragHook)  // 自动检索注入
            .build();
}

// Agentic 模式：注册为工具让 Agent 主动调用
var ragTools = new KnowledgeRetrievalTools(aafKnowledge);
toolkit.registerObject(ragTools);
```

## 相关文档（补充）

- [AgentScope 整合策略](../agentscope-integration.md)
