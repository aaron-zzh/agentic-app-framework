---
level: Practice
layer: Product
purpose: AAF-054 知识图谱的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 知识图谱（AAF-054）

> 负责人：architect + developer-service | 创建：05-19

## 任务列表

### 图谱建模

1. [ ] #5401 Neo4j 图谱建模
   - 实体节点设计（Entity 标签、属性 Schema：name/type/source/embedding）
   - 关系边设计（Relation 类型、属性：weight/confidence/source/timestamp）
   - 复合索引（全文索引 + 属性索引）、唯一约束
   - Spring Data Neo4j 集成与 Repository 层
   - verify: 节点/关系 CRUD 操作正常，索引查询性能达标

2. [ ] #5402 实体关系抽取
   - LLM 驱动的实体识别（NER）与关系抽取 Prompt 设计
   - 三元组生成（Subject-Predicate-Object）与结构化输出解析
   - 去重合并策略（实体消歧、同义词合并、关系归一化）
   - 批量抽取任务队列与进度追踪
   - verify: 给定文档抽取实体/关系，准确率 > 70%

### 图谱检索

3. [ ] #5403 图增强检索
   - 子图检索（给定实体返回 N 跳邻居子图）
   - 路径查询（两实体间最短路径、所有路径）
   - 社区发现（Louvain 算法、社区摘要生成）
   - 检索结果与向量检索结果融合
   - verify: 子图检索返回结构正确，社区划分合理

4. [ ] #5404 多跳推理
   - 关系链推理（A→B→C 推导 A 与 C 的隐含关系）
   - 推理路径可视化数据结构输出
   - 置信度传播（路径越长置信度衰减）
   - 推理结果缓存与失效策略
   - verify: 多跳推理返回路径与置信度，结果可解释

### 图谱维护

5. [ ] #5405 图谱维护
   - 增量更新（新文档→增量抽取→合并入图）
   - 冲突解决（同一实体不同来源属性冲突时的合并策略）
   - 图谱版本管理（快照、回滚、变更日志）
   - 图谱健康度指标（孤立节点数、连通分量、平均度数）
   - verify: 增量更新不破坏已有图谱结构，冲突正确解决