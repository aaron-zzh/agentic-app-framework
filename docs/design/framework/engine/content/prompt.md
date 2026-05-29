---
level: Practice
layer: Model
purpose: Prompt 引擎设计——提示词库管理、链式组装、评估优化、对外服务
status: draft
version: 0.1.0
date: 2026-05-20
author: AaronZZH
---

# Prompt 引擎

> 待细化。核心职责：提示词库管理、链式组装、智能提示、评估优化，对外提供 Prompt 服务 API。

## 定位

Prompt 引擎是 Layer 2 专项引擎，被 `intelligent/core/` 调用，同时对外（Layer 4）提供提示词管理服务。

```text
Layer 4  提示词管理服务（CRUD / 评估报告 / 版本对比）
              ↓
Layer 2  Prompt 引擎
              ↓ 被调用
Layer 3  Core / Agent / Assistant（构建 LLM 输入）
```

## 核心能力（待设计）

- **提示词库管理**：模板 CRUD、版本管理、分类标签、权限控制
- **链式组装**：System / User / Assistant 多角色消息构建，条件片段，变量注入
- **Few-shot 管理**：示例库，按语义相似度动态选取最相关示例
- **智能提示**：根据上下文自动推荐/补全提示词片段
- **评估优化**：A/B 测试，效果评分，自动优化建议

## 包结构（待实现）

```text
engine/prompt/
├── PromptEngine.java           核心接口
├── PromptTemplate.java         模板实体
├── PromptChain.java            链式组装器
├── FewShotSelector.java        Few-shot 动态选取
├── PromptEvaluator.java        效果评估
└── PromptRepository.java       持久化
```

## 与现有代码的关系

`intelligent/core/prompt/PromptTemplateService` 现有实现（模板 CRUD + 变量注入）迁移至本引擎，`core/prompt/` 保留为调用门面。
