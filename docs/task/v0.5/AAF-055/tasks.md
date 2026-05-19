---
level: Practice
layer: Product
purpose: AAF-055 知识管道的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 知识管道（AAF-055）

> 负责人：architect + developer-service | 创建：05-19

## 参考项目

| 任务 | 参考项目 | 参考路径 | 借鉴点 |
|------|---------|---------|--------|
| #5501 文档导入器 | Cognee | `tmp/mem/` 设计思想 | ECL 管道 Extract 阶段、多格式解析 |
| #5502 网页抓取 | — | — | 标准实现（Jsoup + Readability） |
| #5503 自动分块策略 | Cognee + LightRAG | `tmp/mem/` 设计思想 | 按文档类型自动选择策略、质量评估 |
| #5504 增量更新 | Graphiti | `tmp/mem/graphiti/graphiti_core/` | 变更检测、差量重索引、版本追踪 |
| #5505 管道编排 | Cognee | `tmp/mem/` 设计思想 | ECL（Extract-Cognify-Load）管道模式 |

## 任务列表

### 文档导入

1. [ ] #5501 文档导入器
   - PDF 解析（Apache PDFBox，保留标题/段落/表格结构）
   - Word 解析（Apache POI，保留样式层级）
   - Markdown / HTML 解析（保留标题层级、代码块、列表）
   - 格式统一化为内部中间表示（段落列表 + 元数据）
   - verify: 各格式文档导入后结构完整，中文无乱码

2. [ ] #5502 网页抓取
   - URL 导入（单页抓取、正文提取 Readability 算法）
   - 批量 URL 导入（URL 列表 / Sitemap 解析）
   - 定时同步（Cron 配置、变更检测、增量更新）
   - 反爬处理（User-Agent、速率限制、重试）
   - verify: 给定 URL 正确提取正文内容，定时同步触发正常

### 分块与索引

3. [ ] #5503 自动分块策略
   - 按文档类型自动选择分块器（PDF→语义分块、代码→函数级分块、Markdown→标题分块）
   - 分块质量评估指标（语义完整性、长度分布、信息密度）
   - 策略配置化（知识库级别可覆盖默认策略）
   - verify: 不同类型文档自动选择合适策略，质量评分合理

4. [ ] #5504 增量更新
   - 文档变更检测（内容 hash 对比、修改时间检测）
   - 差量重索引（只重新分块和 Embedding 变更部分）
   - 版本追踪（文档版本号、分块版本关联、旧版本清理策略）
   - verify: 文档修改后只更新变更部分，旧向量正确清理

### 管道编排

5. [ ] #5505 管道编排
   - Pipeline 定义：导入→预处理→分块→Embedding→入库→图谱抽取
   - 步骤间错误处理（重试、跳过、死信队列）
   - 进度追踪（总步骤/当前步骤/百分比/预计剩余时间）
   - 管道执行日志与统计（耗时、成功率、失败原因分布）
   - verify: 完整管道执行成功，失败步骤正确重试或进入死信