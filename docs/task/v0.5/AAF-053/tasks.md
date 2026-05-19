---
level: Practice
layer: Product
purpose: AAF-053 知识库引擎的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 知识库引擎（AAF-053）

> 负责人：architect + developer-service | 创建：05-19

## 参考项目

| 任务 | 参考项目 | 参考路径 | 借鉴点 |
|------|---------|---------|--------|
| #5301 PgVector 集成 | Spring AI | 官方文档 | VectorStore 抽象、PgVectorStore 实现 |
| #5302 文档分块 | Cognee | `tmp/mem/` 设计思想 | ECL 管道中 Extract 阶段的分块策略 |
| #5303 Embedding 生成 | Spring AI + Cognee | 官方文档 | EmbeddingModel 抽象、批量生成、缓存 |
| #5304 相似度搜索 | LightRAG + M-FLOW | `tmp/mem/m_flow/` | 混合检索、图路由 Bundle Search |
| #5305 管理 API | Cognee | `tmp/mem/` 设计思想 | 知识库生命周期管理 |

## 任务列表

### 向量存储

1. [ ] #5301 PgVector 集成
   - PostgreSQL vector 扩展安装与配置
   - Embedding 表设计（knowledge_embedding）：文档ID、分块ID、向量、元数据
   - HNSW / IVFFlat 索引创建与参数调优
   - Spring AI VectorStore 适配
   - verify: 向量写入与 ANN 查询延迟 < 100ms

2. [ ] #5302 文档分块
   - 多策略分块器实现：固定长度、语义边界、递归字符分割
   - 重叠窗口配置（overlap size / overlap ratio）
   - 分块元数据保留（来源文档、页码、章节标题、位置偏移）
   - 分块质量评估（平均长度、语义完整性检测）
   - verify: 同一文档不同策略分块结果合理，元数据完整

3. [ ] #5303 Embedding 生成
   - 多模型支持（OpenAI text-embedding-3、本地模型）通过 Spring AI 抽象
   - 批量生成（分批调用、速率限制、失败重试）
   - Embedding 缓存（相同文本不重复生成，基于内容 hash）
   - 维度配置与归一化处理
   - verify: 批量 1000 条分块生成成功，缓存命中率 > 0

### 检索能力

4. [ ] #5304 相似度搜索
   - 余弦相似度 / 欧氏距离 / 内积 三种度量支持
   - Top-K 检索 + 相似度阈值过滤
   - 元数据过滤（按知识库ID、文档ID、标签筛选）
   - 搜索结果排序与去重
   - verify: 语义相关查询 Top-5 命中率 > 80%

### 管理接口

5. [ ] #5305 知识库管理 API
   - 知识库 CRUD（名称、描述、Embedding 模型、分块策略配置）
   - 文档与知识库关联（多对多）、文档状态（待处理/处理中/已完成/失败）
   - 统计信息接口（文档数、分块数、向量数、存储大小）
   - GraphQL Schema 定义
   - verify: API 全流程（创建知识库→上传文档→查询统计）通过