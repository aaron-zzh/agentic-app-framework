---
level: Practice
layer: Product
purpose: AAF-056 RAG 增强的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# RAG 增强（AAF-056）

> 负责人：architect + developer-service | 创建：05-19

## 参考项目

| 任务 | 参考项目 | 参考路径 | 借鉴点 |
|------|---------|---------|--------|
| #5601 混合检索 | LightRAG + M-FLOW | `tmp/mem/m_flow/m_flow/` | 向量+BM25+图谱三路融合、RRF 排序 |
| #5602 检索增强生成 | LightRAG + Mem0 | `tmp/mem/mem0/mem0/` | Prompt 注入模板、上下文窗口优化 |
| #5603 引用溯源 | Cognee | `tmp/mem/` 设计思想 | 答案→源文档映射、引用标注 |
| #5604 置信度评分 | M-FLOW | `tmp/mem/m_flow/m_flow/` | 置信度传播、可靠性评估 |
| #5605 RAG 评估 | Spring AI Evaluation | 官方文档 | Faithfulness/Relevancy 评估框架 |

## 任务列表

### 混合检索

1. [ ] #5601 混合检索
   - 向量检索（PgVector 语义相似度）
   - BM25 关键词检索（PostgreSQL 全文索引 tsvector）
   - 图谱检索（Neo4j 实体关联子图）
   - 三路结果融合排序（RRF / 加权融合、可配置权重）
   - verify: 混合检索结果优于单路检索，排序合理

2. [ ] #5602 检索增强生成
   - 检索结果注入 Prompt（上下文模板、分隔符、来源标注）
   - 上下文窗口优化（Token 预算分配、长文档截断策略）
   - 多轮对话 RAG（历史对话 + 检索结果联合注入）
   - Prompt 模板管理（按场景选择不同 RAG Prompt）
   - verify: RAG 生成答案引用检索内容，答案质量优于无 RAG

### 质量保障

3. [ ] #5603 引用溯源
   - 答案→源文档映射（生成时标注引用来源 [1][2]）
   - 引用标注 UI 数据结构（引用ID、文档ID、分块ID、高亮位置）
   - 点击引用跳转到源文档对应位置
   - verify: 生成答案包含引用标注，引用指向正确源文档

4. [ ] #5604 置信度评分
   - 答案可靠性评估（检索相关性 + 生成一致性 + 事实性检查）
   - 低置信度标记（阈值可配置、前端展示警告标识）
   - 人工确认机制（低置信度答案标记为待确认、人工审核后发布）
   - verify: 低质量答案被正确标记，高质量答案评分合理

5. [ ] #5605 RAG 评估
   - 检索评估（准确率 Precision@K、召回率 Recall@K、MRR）
   - 生成评估（忠实度 Faithfulness、答案相关性、完整性）
   - 自动化测试集（问答对 + 标准答案、批量评估脚本）
   - 评估报告生成（指标趋势、对比不同配置效果）
   - verify: 评估流程可自动运行，输出指标报告