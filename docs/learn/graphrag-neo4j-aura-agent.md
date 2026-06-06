---
level: Reality
layer: Framework
purpose: Neo4j Aura Agent 技术调研笔记，记录 GraphRAG Agent 工程实践对 AAF 的借鉴价值
status: draft
version: 0.1.0
date: 2026-06-06
author: AaronZZH
scope:
  includes:
    - GraphRAG 三层检索模式
    - 知识图谱节点建模
    - Agent 工具链式调用
    - 响应结构设计
    - MCP 作为对外接口
gains:
  - 了解 GraphRAG Agent 工程实践的成熟模式
  - 为 AAF 知识引擎检索策略提供外部验证
---

# GraphRAG Agent 工程实践调研：Neo4j Aura Agent

> 原文：<https://neo4j.com/blog/genai/build-context-aware-graphrag-agent/>
> 调研日期：2026-06-06
> 调研人：AaronZZH

## 背景

Neo4j 于 2025 年 10 月发布 **Aura Agent**，是在其云数据库产品 AuraDB 上新增的低代码 Agent 构建平台。文章以法律合同审查 Agent 为例，展示了从知识图谱建模到 Agent 部署的完整流程。

本文不关注平台产品本身（低代码托管服务非 AAF 方向），重点提取其背后的**技术模式**作为 AAF 知识引擎和 Agent 层设计的参考。

---

## 核心技术模式

### 知识图谱节点建模：文本与向量分离

文章的合同知识图谱结构：

```
Agreement → ContractClause → Excerpt
Agreement → Organization → Country
```

关键设计：`Excerpt` 节点同时存储两个字段：
- `text`：原始合同文本（供 LLM 引用，保证可读性）
- `embedding`：文本向量（供向量检索，机器理解用）

**两者显式分离，不混用。**

**AAF 借鉴**：知识库 `Chunk` 节点建模时，`text` 和 `embedding` 必须是独立字段，不能把向量藏在 JSONB 或混合字段里。这与 `tmp/mem/graphiti` 的设计思路一致，应在知识引擎节点模型中固化。

---

### 三层检索工具分层

文章把 Agent 检索工具分为三层，并给出了明确的使用场景和风险评级：

| 层级 | 工具类型 | 适用场景 | 风险 |
|------|---------|---------|------|
| 精确层 | Cypher 模板 | 已知问题、高频问题、需要控制结果 | 低 |
| 语义层 | 向量相似度 | 模糊语义匹配、找相似内容 | 中 |
| 动态层 | Text2Cypher | 聚合查询、即席查询、兜底 | 高 |

文章明确指出：**Text2Cypher（NL→图查询）不应作为默认路径，只能作为兜底**，原因是 LLM 生成的 Cypher 在生产环境中存在不稳定性。

**AAF 借鉴**：知识引擎检索策略应遵循这个梯度——精确优先，动态兜底。若 AAF 引入 NL→Cypher 能力，必须配套置信度门控，不能直接暴露为默认检索路径。

---

### 工具链式调用（Tool Chaining）

文章最有价值的示例：

> 用户问"找提到产品单位的合同"
> → Agent 调用**向量相似度工具**，找到语义相关的 Excerpt 节点
> → Agent 自动调用 **Cypher 模板工具**，从 Excerpt 沿图关系回溯到 Agreement
> → 组合输出完整合同信息

这是 Agent 在单次对话中**自主决定工具调用顺序**，每步输出作为下一步输入。这正是 GraphRAG 相比普通 RAG 的核心优势：向量找到入口节点，图遍历补全关联上下文。

**AAF 借鉴**：AAF 的 Agent 层需要支持单次对话内的多工具串联（短工具链）。需确认 AgentScope 的 Tool 调用机制是否支持多轮工具编排，这是 GraphRAG 场景的关键能力，若不支持需要在工具调度层补充。

---

### 响应结构包含推理链

文章展示的 API 响应 JSON 结构分为四段：

```json
[
  { "type": "thinking", "thinking": "..." },
  { "type": "cypher_template_tool_use", "name": "工具名", "input": {...} },
  { "type": "cypher_template_tool_result", "output": {...} },
  { "type": "text", "text": "最终回答" }
]
```

这使得前端可以渲染"推理过程"，用户能看到 Agent 调用了哪个工具、为什么调用、得到了什么结果。

**AAF 借鉴**：AAF 的 SSE/WebSocket 对话响应协议应预留 `thinking`、`tool_use`、`tool_result`、`text` 四段结构。这是 AI 应用可解释性的基础，在法律、医疗、合规等垂直场景是硬需求。相关设计可参考 `docs/reference/api/ag-ui-protocol.md`。

---

### MCP 作为 Agent 对外暴露接口

文章最后提到：将 Aura Agent 的 REST API 端点包装为 MCP Server，可直接接入 Claude Desktop 等工具。

**AAF 借鉴**：AAF 已有 MCP 技术栈（见 `docs/reference/api/mcp-tools.md`）。知识库 Agent 和业务 Agent 对外暴露时，MCP Server 是标准接入协议，可以让 AAF 构建的 Agent 被 Claude/Cursor 等外部工具直接调用，无需专用客户端。这是低成本扩大 AAF Agent 生态影响力的路径。

---

## 与 AAF 现有参考材料的关联

| 本文技术点 | AAF 现有参考 | 一致性 |
|-----------|------------|--------|
| 图节点存储 text + embedding | `tmp/mem/graphiti` | ✅ 一致 |
| 三层检索梯度 | `tmp/mem/mem0` 多级记忆 | ✅ 方向一致 |
| 工具链式调用 | `tmp/agent/agentscope` Tool 调用 | ⚠️ 需确认多轮支持 |
| 响应含推理链 | `docs/reference/api/ag-ui-protocol.md` | ⚠️ 需对齐四段结构 |
| MCP 接口 | `docs/reference/api/mcp-tools.md` | ✅ 已有规划 |

---

## 结论

Neo4j Aura Agent 的产品形态（低代码托管）不适合 AAF 借鉴，但它将 GraphRAG Agent 的工程模式讲得清晰且经过生产验证：

- 图节点建模：文本与向量分离
- 检索策略：精确 → 语义 → 动态三层梯度
- Agent 能力：工具链式串联是 GraphRAG 核心价值
- 响应协议：包含推理链的四段结构
- 对外接口：MCP 是标准接入方式

这些与 AAF `tmp/` 参考材料的方向高度一致，可作为 AAF 知识引擎设计的**外部验证**。
