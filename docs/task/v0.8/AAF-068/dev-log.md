# 开发记录：AAF-068 图像生成

执行者：AI/developer-service

## 图像生成计费优化（2026-06-20）

✅ 2026-06-20 — developer-service

- `ImageConfig.ImageModeConfig` 加 `qualityPricing: Map<String, BigDecimal>` 字段，支持按画质（standard/hd）分级单价
- `ImageGenerationService` 覆写 `estimateCost`：TOKEN 计费（Gemini）返回 0，其余按张×quality单价计费，兜底用 `model.modelPrice`
- `ImageResult.standardUsage()` count 改为 `urls.size()`，多图结算时反映实际张数
- `AiUsage` 接口加 `count()` 便捷方法
- `DefaultAiCreditGuard.calcCost` 拆分 `PER_USE`（按次不变）与 `PER_UNIT`（按 `usage.count()×unitPrice` 结算），修复多图批量生成只扣 1 次积分的问题

> **沉淀**：图像计费与视频对齐——视频按 `pricePerSecond×duration`，图像按 `pricePerImage×count`；quality 分级单价存 `image_config.generate.qualityPricing`，无配置兜底 `model_price`。
