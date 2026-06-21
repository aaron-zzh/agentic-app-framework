# AAF-099 会员套餐订阅与积分系统补全

> 设计文档：[../../../design/apps/service/membership-completion.md](../../../design/apps/service/membership-completion.md)

## 范围

按设计文档 v0.2.0 的"必须修改的功能列表"实现 F1–F5 + F1b + F1c 共 7 个功能群，并补全验收测试。

**暂不实现**（设计文档已定）：自动续费扣款（接口预留）、7 天无消费退款、防刷限流。

## 已完成的预改动（无需重做）

以下改动已在设计阶段直接落地，developer 不需要再做：

- ✅ `db/seed/v14__invite_reward_seed.sql`：INVITE amount 200→500、expire_days 7→30 + remark 同步
- ✅ `db/seed/v12__init_seed_data.sql`：FAQ Q1 充值积分卡片"永久有效"→"有效期 2 年（自发放之日起计算）"
- ✅ `module/billing/service/CreditRechargePayHandler.java`：`earnBatch(...,null)` 改走 `creditService.earn(...)`（统一 2 年有效期）
- ✅ `test/.../BrokerageMeServiceTest.java`：测试常量 200/7 同步为 500/30

→ 即 F4、F5 已完成，剩余实施任务从 #01 开始。

## 技术任务（按依赖顺序）

| # | 任务 | 涉及模块 | 依赖 | 完成 |
|---|------|---------|------|------|
| **#01** | DB 迁移 v15：`billing_subscription` 加 5 字段（auto_renew/cancelled_at/pending_plan_id/pending_yearly/last_reminder_at）；`aigc_task` 加 credit_tx_id；`ai_usage_record` 加 client_ip/user_agent；`sys_config` 插入 `member.expiry_reminder_days=7` | `db/migration/v15__membership_completion.sql` | — | ☐ |
| **#02** | `Subscription` Domain + `SubscriptionVO` 暴露所有 5 个字段；`AigcTask` Domain 加 creditTxId | `module/billing/domain/`、`module/ai/aigc/task/domain/` | #01 | ☐ |
| **#03** | F1：`SubscriptionService.cancel(userId)` + `SubscriptionController` POST `/api/billing/subscription/cancel` | `module/billing/service/`、`module/billing/controller/` | #02 | ☐ |
| **#04** | F1b：`SubscriptionService.downgrade(userId, planCode, yearly)` 校验降级 + 设置 pending；`cancelPending(userId)` 清除；新接口 POST `/downgrade` + DELETE `/pending` | 同上 | #02 | ☐ |
| **#05** | F2 升级补差价：`SubscriptionService.upgrade(userId, newPlanCode, channelCode, yearly)` 时间比例公式 + 创建 BizOrder/PayOrder | 同上 | #02 | ☐ |
| **#06** | F2 升级积分立即结算：`SubscriptionService` 新增 `settleUpgradeCredits()` 写三笔流水（EXPIRE/EARN/SPEND）；`CreditServiceImpl` 暴露必要的批量操作能力 | 同上 + `framework/engine/credit/` | #05 | ☐ |
| **#07** | `SubscriptionController.subscribe` 端点路由：升级 / 同档续约 / 降级三向分支 | 同上 | #03、#04、#05 | ☐ |
| **#08** | F1c 提醒器：`SubscriptionExpiryReminderScheduler` 每日 09:00 扫描，发 `SUBSCRIPTION_EXPIRY_REMINDER` 站内信，写 `last_reminder_at` 幂等 | `module/billing/scheduler/`、`module/system/notify/` | #02 | ☐ |
| **#09** | F1c 到期处理器：强化或新增 `SubscriptionExpireScheduler` 每日 00:15，pending=FREE 直接激活 / pending=付费档走冻结 / 无 pending 走冻结；`SubscriptionAutoRenewService` 注释占位 | 同上 | #04 | ☐ |
| **#10** | `CreditService.refund(creditTxId, reason)` 接口 + `CreditServiceImpl` 实现 + 单元测试 | `framework/engine/credit/` | — | ☐ |
| **#11** | `AiCreditGuard.refund` default 委托 + `DefaultAiCreditGuard` 实现 | 同上 | #10 | ☐ |
| **#12** | `AbstractAiServiceDecorator.creditCall` 让 settleByUsage 返回 creditTxId；同步图音视频路径回填 `aigc_task.credit_tx_id` | `framework/intelligent/core/decorator/` | #11 | ☐ |
| **#13** | `AigcTaskService.completeTask` settle 后回填 credit_tx_id + OSS 失败转 `failTask`；`failTask` 检查 credit_tx_id 触发 refund | `module/ai/aigc/task/service/` | #02、#12 | ☐ |
| **#14** | 单元测试：`CreditServiceImplTest.refund_*`、`SubscriptionServiceTest.{cancel,downgrade,upgrade,settleUpgradeCredits}_*`、`SubscriptionExpireSchedulerTest.*`、`SubscriptionExpiryReminderSchedulerTest.*` | service + framework 测试目录 | 各功能完成后 | ☐ |
| **#15** | 验收测试（9 条 Gherkin）：取消订阅 / 升级补差价 + 三笔流水 / 降级排队 / 撤销降级 / 到期提醒 / 冻结至 FREE / AIGC 失败退还 / 充值积分 2 年 / INVITE 30 天+500 | `*AcceptanceTest.java` | 全部 | ☐ |

## 验收标准（Gherkin 摘要，详细见 #15）

```gherkin
Scenario: 取消订阅后周期内权益保留
  Given 用户已订阅 PRO 月付，end_at 还有 25 天
  When 用户调用 POST /subscription/cancel
  Then 订阅 cancelled_at 已填充，auto_renew=false，status 仍为 ACTIVE
  And 订阅权益与积分不受影响

Scenario: 升级 PRO→TEAM 补差价 + 三笔积分流水
  Given 用户 PRO 月付订阅已用 5 天，剩 25 天，PRO 月度积分 200 已消耗 100
  When 用户升级到 TEAM（月度积分 400）
  Then 应付差价 = 299 - 29 * 25/30 = 274.83 元
  And credit_transaction 写入 EXPIRE/EARN/SPEND 三笔
  And 升级后 balance = 300（总额 400 - 已消耗 100）

Scenario: 降级到 FREE 排队，end_at 切换
  Given 用户 PRO 月付订阅
  When 用户调用 POST /subscription/downgrade { planCode: "FREE" }
  Then pending_plan_id 已填充
  And 订阅保持 ACTIVE 至 end_at
  When end_at 到达
  Then SubscriptionExpireScheduler 扫到，激活 FREE，旧订阅 EXPIRED

Scenario: 撤销降级
  Given 用户已设 pending_plan_id=FREE
  When 用户调用 DELETE /subscription/pending
  Then pending_plan_id 已清除

Scenario: 到期提醒
  Given 用户订阅 end_at 还有 6 天
  When 调度器 09:00 扫描运行
  Then 用户收到 SUBSCRIPTION_EXPIRY_REMINDER 站内信
  And last_reminder_at 已写入

Scenario: 到期未续费冻结至 FREE
  Given 用户 PRO 订阅 end_at 已过且无 pending
  When SubscriptionExpireScheduler 00:15 扫描
  Then 旧订阅 status=EXPIRED
  And 自动激活 FREE 订阅 ACTIVE
  And 已发的 SUBSCRIPTION 批次积分按原 30 天有效期保留

Scenario: AIGC 任务 settle 后失败自动退还
  Given 视频任务已 settleByUsage 扣 100 积分，credit_tx_id 已回填
  When 内容审核标记失败，调用 failTask
  Then CreditService.refund 写入反向 EARN +100
  And account.balance 回到原值

Scenario: 充值积分 2 年有效
  Given 用户购买 100 元积分套餐
  When 支付成功
  Then credit_transaction 写入 EARN，batch_type=TOPUP，expire_at = now + 730 天

Scenario: 邀请奖励 500 积分 30 天
  Given 用户 A 邀请用户 B 注册成功
  When 系统按 INVITE 规则发放
  Then 用户 A 收到 500 积分，expire_at = now + 30 天
```

## 完工门禁

- [ ] 所有 15 项任务完成
- [ ] `pnpm nx test service` 全绿（71+ 单元测试）
- [ ] `pnpm acceptance:affected` 全绿（9 条 Gherkin 验收）
- [ ] `pnpm check:affected` 全绿（lint + typecheck + build）
- [ ] dev-log.md 每项一行记录关键决策
