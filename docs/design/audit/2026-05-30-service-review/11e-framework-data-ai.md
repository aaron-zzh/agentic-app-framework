# 11e framework 数据处理 · AI · 知识库（优先级 4）

> 覆盖：`engine/dataprocess/`（AiEnricher/DataCleaner/DataRouter/DataPipeline/FieldMapper）、`intelligent/ai/`（chat ResilientChatService、image Midjourney）、`engine/knowledge/`（VectorService/HybridSearch/WebScraping 等）。
> 承接 [11 执行计划](11-followup-review-plan.md) 优先级 4。审查人 AI/architect · 2026-05-30。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B18 | 🔴 | `dataprocess/DataRouter#insertToTable` | `INSERT INTO {tableName} ({列名})` 把 routeTarget 表名 + item 键作 SQL 标识符拼接（值已参数化，标识符未）→SQL 注入（与 [B16](11d-framework-controllers.md) 同根，第二注入点，经 DataIngest 数据流可达） | 标识符白名单校验 + 与表定义列名比对；与 B16 统一修复 |
| M42 | 🟠 | `dataprocess/AiEnricher#buildPrompt` | 将外部/摄入数据原文直接拼入 LLM 提示词，无分隔/转义→**提示词注入**（可篡改分类/摘要结果并落库） | 用户内容用明确分隔符包裹 + 系统提示约束；对输出做校验 |
| M43 | 🟠 | `dataprocess/DataRouter#insertToKnowledgeBase` | TODO 占位：`count++` 但 `// TODO 调用知识库 API` 未实际写入，却上报 `inserted_count`→假成功（M13 类） | 实现写入或显式抛未实现，禁止假报计数 |
| M44 | 🟠 | `intelligent/ai/chat/ResilientChatService` | 仅事后 `publishUsage` 计量，**无 pre-call 配额强制**（`TokenMeteringService.isQuotaExceeded` 全程未被调用）→用户可超额，LLM 成本失控（M23 续） | 调用前校验配额；超额拒绝或降级到限额模型 |
| M45 | 🟠 | `knowledge/KnowledgeVectorService#search(query,topK)` | 两参重载**无租户/kbId 过滤**→跨知识库越权检索（三参带 filter 重载存在但易被绕过） | 强制要求 kbId 过滤；移除无过滤重载或内置租户条件 |
| M46 | 🟠 | `knowledge/importer/WebScrapingService`（scrapeUrl/parseSitemap/scrapeBatch） | 抓取任意 URL **无 SSRF 防护**（可达内网/`169.254.169.254`），且 `Jsoup...maxBodySize(0)` **无限响应体**→内网探测 + 内存 DoS | URL 出站白名单 + 禁私有网段；设置 maxBodySize 上限 |
| m27 | 🟡 | `intelligent/ai/image/MidjourneyImageService#imagine` | `notifyHook` 仅传 URL 无签名/共享密钥→入站回调无法验签（[M24](09-file-sms-aigc.md) 框架层根因） | 回调带签名/nonce，入站校验 |
| m28 | 🟡 | `dataprocess/DataRouter#insertToTable` | 用原始 `target` 作表名（无 `data_` 前缀），与 `DynamicTableService`（`data_{slug}`）命名不一致→写错表/写入失败 | 统一表名解析（复用 DynamicTableService.getTable） |
| m29 | 🟡 | `ResilientChatService#call` | 对**任何**异常一律降级 fallback 模型（含内容策略拒绝/参数错误）→可能双倍成本、掩盖真实错误 | 仅对可重试错误（超时/5xx）降级 |

## 良好实践

- `HybridSearchService.bm25Search` 用参数化 native query（`:query/:kbId/:topK`）+ `knowledge_base_id` 过滤，注入安全且租户隔离正确；三路虚拟线程并行 + RRF 融合，异常降级串行。
- `ResilientChatService` 完整路由链（显式→编排→偏好→默认→兜底）+ 计量事件发布；`HybridSearchService.vectorSearch` 经 `SimilaritySearchService` 带 kbId。
- `DataCleaner`/`FieldMapper` 纯内存处理，无注入面；`WebScrapingService` 噪声标签清理 + 批量速率限制 + 重试。

## 对称性 / 一致性提示

- 注入面（清单）：DataRouter 拼接 SQL（B18，与 B16/11d 同根）、AiEnricher 拼接提示词（M42）、WebScraping 任意 URL（M46）——外部输入进解释器/外联普遍未隔离。
- 占位/重复（清单#13）：`insertToKnowledgeBase` TODO 假实现（M43，与 M13 同类）。
- 计费一致性：LLM 无 pre-call 配额（M44），与 M23/[02 区](02-payments-billing-credit.md)资金一致性呼应。
- 状态变更 vs 通知（清单#7）：Midjourney 回调无验签（m27 → M24）。

## 待确认

- `knowledge/graph/EntityExtractionService` 实体抽取大概率与 AiEnricher 同类提示词注入（未逐读）。
- `KnowledgeVectorService.search(query,topK)` 两参重载的实际调用方是否都补了 kbId 过滤（决定 M45 实际暴露面）。
- `WebScrapingService` 的 url 入口是否来自用户（知识库 URL 导入）——决定 M46 暴露面与 SSRF 实际可达性。
