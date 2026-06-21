# AAF-099 开发日志

> 设计文档：[../../../design/apps/service/membership-completion.md](../../../design/apps/service/membership-completion.md)
> 任务列表：[./tasks.md](./tasks.md)

## 已完成（设计阶段直接落地）

- ✅ 2026-06-21 F4 充值积分 2 年统一 — `CreditRechargePayHandler` 改走 `creditService.earn()`，与 `RechargeService` 同路径
- ✅ 2026-06-21 F5 INVITE seed 修正 — `v14__invite_reward_seed.sql` 直接改 amount 200→500、expire_days 7→30；同步 `BrokerageMeServiceTest` 测试常量；FAQ Q1 充值卡片改 2 年文案

## developer-service 实施（2026-06-21）

- ✅ #01 DB v15 迁移 — `billing_subscription` 加 5 字段、`aigc_task` 加 credit_tx_id、`ai_usage_record` 加 client_ip/user_agent、sys_config 插入 `member.expiry_reminder_days=7`
- ✅ #02 Domain 层扩展 — `Subscription` 加 5 字段（autoRenew/cancelledAt/pendingPlanId/pendingYearly/lastReminderAt），`SubscriptionVO` 暴露全部，`AigcTask` 加 creditTxId
- ✅ #03 F1 取消订阅 — `SubscriptionService.cancel()` 幂等设置 cancelled_at + auto_renew=false；`SubscriptionController` POST `/cancel`
- ✅ #04 F1b 降级排队 — `SubscriptionService.downgrade()/cancelPending()`；`Controller` POST `/downgrade` + DELETE `/pending`；新增 `DowngradeDTO`
- ✅ #05 F2 升级补差价 — `SubscriptionService.upgrade()` 时间比例公式（`oldPriceUnit * remainingDays / totalDays`），创建 BizOrder/PayOrder
- ✅ #06 F2 三笔流水结算 — `CreditService.settleSubscriptionUpgrade()` + `UpgradeSettlement` 记录返回值；EXPIRE/EARN/SPEND 顺序严格按设计；新增 `SubscriptionOperationEnum.UPGRADE` + `CreditBizTypeEnum.SUBSCRIPTION_UPGRADE`
- ✅ #07 subscribe 端点路由 — service.subscribe() 内置 isUpgrade/isDowngrade 路由：升级 → upgrade()，降级 → 拒绝并提示走 /downgrade，同价位 → 续费
- ✅ #08 到期提醒调度器 — `SubscriptionExpiryReminderScheduler` 每日 09:00，幂等键 `last_reminder_at > start_at`；通知类型 `SUBSCRIPTION_EXPIRY_REMINDER`
- ✅ #09 到期处理调度器 — `SubscriptionExpireScheduler` 每日 00:15；pending=FREE 直接激活 / pending=付费档冻结（兜底 freeze=EXPIRED + 自动 FREE）；保留 `SubscriptionAutoRenewService` 接口注释占位
- ✅ #10 CreditService.refund — 反向 EARN 流水（`source=REFUND_AIGC_FAIL` + `bizId=原 SPEND tx ID` + `batchType=REFUND`）；幂等检查通过 `existsRefundForOriginalTx` 查询；退还有效期取最近到期批次的 expire_at 兜底 30 天
- ✅ #11 AiCreditGuard.refund — 接口加 `refund` 默认委托方法 + `settleByUsageReturningTxId` 默认实现；`DefaultAiCreditGuard` 覆写两者
- ✅ #12 装饰器 settle 返回 creditTxId — 新增 `CreditCallContext` ThreadLocal；`AbstractAiServiceDecorator.creditCall` 进入时清 context、settle 后写入 creditTxId
- ✅ #13 AigcTaskService 退还串接 — `completeTask`（视频/3D）改为 settle-在前 + 回填 credit_tx_id + OSS 失败转 failTask；`failTask` 检查 credit_tx_id 触发 refund；`AigcTaskExecutor.submitSync/submitMusicSync/submitVoiceSync` 同步链路捕获 ThreadLocal 并失败时退还
- ✅ #14 单元测试 — `CreditServiceImplTest` 加 refund_writesReverseEarn / refund_idempotentByBizId / refund_invalidTxIdReturnsNull / refund_inheritsNearestExpireAt / settleSubscriptionUpgrade_writesThreeCreditTransactions（旧 200 已用 100 → 升 400 后 balance=300）/ settleSubscriptionUpgrade_noOldUsage_skipsSpendStep / settleSubscriptionUpgrade_zeroAmount_throws；`SubscriptionServiceTest` 覆盖 cancel/downgrade/cancelPending/upgrade 8 项；`SubscriptionExpireSchedulerTest` 3 项；`SubscriptionExpiryReminderSchedulerTest` 3 项

> **沉淀**：装饰器 settle 后 ThreadLocal 暴露 creditTxId 是同步链路追溯扣费记录的最简方案，避免了"装饰器返回值变更"的破坏性扩散；任何同步 `svc.generate()` 调用方都可在调用后立即 `CreditCallContext.takeLastCreditTxId()` 取 ID，无需修改 AiCapability 接口契约。
