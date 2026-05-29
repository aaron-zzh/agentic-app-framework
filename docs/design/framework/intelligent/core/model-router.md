---
level: Practice
layer: Model
purpose: 模型管理与路由——ai_model 统一管理 + ModelRouter 六层决策链
status: draft
version: 1.0.0
date: 2026-05-28
author: AaronZZH
---

# 模型管理与路由功能设计

> ai_model 表是所有 LLM 调用的 apiKey/baseUrl/capabilities 唯一来源。

## 定位

管理所有 AI 模型的元数据，并根据任务特征动态选择最合适的模型。屏蔽多厂商差异，上层只关心"要什么能力"，不关心"用哪个模型"。

## 核心能力

- **AiModel 实体管理**：厂商/模型名/apiKey/baseUrl/价格/能力标签/fallback 配置
- **ModelPreference**：用户级和系统级偏好（USER scope / SYSTEM scope）
- **ModelRouter 六层决策链**：按优先级逐层匹配，命中即返回
- **DynamicChatClientFactory**：按 modelId + providerType 动态构建 ChatClient

## ModelRouter 六层决策链

```text
1. 显式指定（调用方明确传入 modelId）
2. 编排指定（工作流节点配置的模型）
3. AI 辅助选择（根据任务复杂度自动选模型）
4. 用户偏好（UserPreference 配置）
5. 系统默认（SystemPreference 配置）
6. yaml 兜底（application.yml 中的 fallback 模型）
```

命中即返回，不继续往下匹配。

## 模型能力标签

| 标签 | 说明 | 示例模型 |
|------|------|---------|
| CHAT | 对话能力 | GPT-4, Claude |
| REASONING | 深度推理 | o1, DeepSeek-R1 |
| CODING | 代码生成 | Claude Sonnet |
| VISION | 图像理解 | GPT-4V |
| EMBEDDING | 向量化 | text-embedding-3 |
| RERANK | 重排序 | bge-reranker |

## 相关文档

- [技术方案 — 模型路由](model-router-tech.md)
- [Core 核心接口](core.md)
