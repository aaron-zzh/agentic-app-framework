# 11e framework 数据处理 · AI · 知识库（优先级 4）

> 覆盖：`engine/dataprocess/`（AiEnricher/DataCleaner/DataRouter/DataPipeline/FieldMapper）、`intelligent/ai/`（chat ResilientChatService、image Midjourney）、`engine/knowledge/`（VectorService/HybridSearch/WebScraping 等）。
> 2026-05-30 分区复审：数据处理、AI 与知识库。审查人 AI/architect。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| M42 | 🟠 | `dataprocess/AiEnricher#buildPrompt` | 将外部/摄入数据原文直接拼入 LLM 提示词，无分隔/转义→**提示词注入**（可篡改分类/摘要结果并落库） | 用户内容用明确分隔符包裹 + 系统提示约束；对输出做校验 |
| M45 | 🟠 | `knowledge/KnowledgeVectorService#search(query,topK)` | 两参重载仍无租户/kbId 过滤；当前无生产调用，尚未形成实际越权路径，但后续调用可绕过三参过滤重载→潜在跨知识库检索旁路 | 移除无过滤重载或强制内置租户条件；在此之前禁止新增生产调用 |
| M46 | 🟠 | `knowledge/importer/WebScrapingService`（scrapeUrl/parseSitemap/scrapeBatch） | 抓取任意 URL **无 SSRF 防护**（可达内网/`169.254.169.254`），且 `Jsoup...maxBodySize(0)` **无限响应体**→内网探测 + 内存 DoS | URL 出站白名单 + 禁私有网段；设置 maxBodySize 上限 |
| m29 | 🟡 | `ResilientChatService#call` | 对**任何**异常一律降级 fallback 模型（含内容策略拒绝/参数错误）→可能双倍成本、掩盖真实错误 | 仅对可重试错误（超时/5xx）降级 |

## 良好实践

- `HybridSearchService.bm25Search` 用参数化 native query（`:query/:kbId/:topK`）+ `knowledge_base_id` 过滤，注入安全且租户隔离正确；三路虚拟线程并行 + RRF 融合，异常降级串行。
- `ResilientChatService` 完整路由链（显式→编排→偏好→默认→兜底）+ 调用前配额检查 + 调用后计量事件发布；`HybridSearchService.vectorSearch` 经 `SimilaritySearchService` 带 kbId。
- `DataCleaner`/`FieldMapper` 纯内存处理，无注入面；`WebScrapingService` 噪声标签清理 + 批量速率限制 + 重试。

## 对称性 / 一致性提示

- 注入面（清单）：AiEnricher 拼接提示词（M42）、WebScraping 任意 URL（M46）——外部输入进解释器/外联仍未充分隔离。

## 待确认

- `knowledge/graph/EntityExtractionService` 实体抽取大概率与 AiEnricher 同类提示词注入（未逐读）。
- `WebScrapingService` 的 url 入口是否来自用户（知识库 URL 导入）——决定 M46 暴露面与 SSRF 实际可达性。
