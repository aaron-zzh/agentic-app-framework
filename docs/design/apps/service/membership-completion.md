---
level: Practice
layer: Model
purpose: 会员套餐订阅与积分系统补全设计——对齐 FAQ 用户契约的功能缺口
status: draft
version: 0.2.0
date: 2026-06-21
author: AaronZZH
changelog:
  - 2026-06-21 | v0.1.0 初版：FAQ 契约对齐缺口梳理与必须补全功能定义
  - 2026-06-21 | v0.2.0：升级积分立即结算（三笔流水）+ 降级排队（end_at 切换）+ 到期提醒/冻结 + 自动续费接口预留
gains:
  - 能理解当前订阅/积分系统相对 FAQ 用户契约的功能缺口与影响面
  - 能区分 AIGC 任务失败的多种入口与对应的积分扣减/退还语义
  - 能理解升级/降级的精确语义与积分批次流水变化
  - 能理解到期提醒、冻结、撤销降级、自动续费扩展点之间的关系
  - 能定位充值积分双路径不一致的修复点
related:
  - ./identity-and-membership.md
  - ./subscription-credit-batch.md
  - ../../framework/engine/governance/credit-settlement.md
---

# 会员套餐订阅与积分系统补全设计

> 当前订阅/积分主体已实现（SubscriptionService、CreditService、定时调度器、AiCreditGuard），本设计聚焦 FAQ 用户契约里"承诺了但代码未实现"或"实现与承诺不一致"的差距，做最小补全。

## 范围与立场

### 用户契约来源

`sys_config.member.faq` 配置项（v12 seed）。本设计对齐其中四问的承诺：积分获取方式、扣除规则、订阅运作、订阅修改与取消。

### 本期保留 vs 暂不实现

按业务优先级与实现成本，本期严格收敛范围。

| 范畴 | 决定 | 说明 |
|------|------|------|
| 取消订阅 | ✅ 实现 | `cancelled_at` + 关闭 `auto_renew` 意图位；用户当前周期权益保留至 `end_at` 自然到期；不退款 |
| **升级订阅按比例补差价** | ✅ 实现 | 按时间比例补差价 + **积分立即结算（三笔流水）**——见 [F2](#f2-升级订阅按时间比例补差价立即生效) |
| **降级订阅排队** | ✅ 实现 | 用户点降级仅记 `pending_plan_id`，不付钱；`end_at` 时由调度器切换。**支持降级到任意付费档或 FREE**——见 [F1b](#f1b-降级排队end_at-切换) |
| **撤销降级** | ✅ 实现 | 提供 `DELETE /pending` 接口清除 `pending_plan_id`；不涉及任何已付款流转 |
| **到期提醒 + 冻结** | ✅ 实现 | `end_at` 前 7 天发提醒；到期未付费 → 旧订阅 EXPIRED + 自动激活 FREE 兜底（即"冻结"语义）——见 [F1c](#f1c-到期处理提醒--冻结) |
| AIGC / AI 任务失败积分退还 | ✅ 实现 | 仅覆盖"已 settle 后才发现失败"的窗口——见 [AIGC 任务失败分类](#aigc-任务失败分类与积分语义) |
| 充值积分有效期统一为 2 年 | ✅ 实现 | 修 `CreditRechargePayHandler` 改走 `creditService.earn()`，与 `RechargeService` 同路径 |
| `credit_grant_rule` seed 修正 | ✅ 实现 | 开发阶段直接改 `v14__invite_reward_seed.sql`：INVITE `expire_days` 7→30、`amount` 200→500 |
| **自动续费扣款（渠道代扣）** | ⏸️ **接口预留 / 调度器不实现** | 字段（`auto_renew`）和接口扩展点保留；本期 `SubscriptionExpireScheduler` 不调用代扣，未付费即冻结。未来接入微信/支付宝代扣 SDK 时仅需补 `SubscriptionAutoRenewService` 实现 |
| **7 天无消费退款（支付订单）** | ⛔ 本期不实现 | 涉及反向扣回积分批次 + 渠道退款联调；FAQ Q5 退款保留运营邮箱兜底（人工处理） |
| 防刷与防自动化滥用 | ⏸️ P2 占位 | 本期仅在 `ai_usage_record` 加 `client_ip`/`user_agent` 列做事后审计基础 |

### 必须修改的功能列表（汇总）

按"用户契约偏差"严重度排序：

| # | 缺口 | 当前状态 | 改动面 |
|---|------|---------|-------|
| **F1**  | **取消订阅** API + DB 字段 | ❌ 完全缺失 | DB 加 auto_renew/cancelled_at + Service.cancel + Controller |
| **F1b** | **降级排队（end_at 切换）** | ❌ 当前等于"再买一次" | DB 加 pending_plan_id/pending_yearly + Service.downgrade + Controller |
| **F1c** | **到期提醒 + 冻结**（旧订阅 EXPIRED + 自动 FREE 兜底） | ❌ 当前 expireSubscriptions 未自动接 FREE，亦无提醒 | DB 加 last_reminder_at + 新增两个 Scheduler |
| **F2**  | **升级订阅按时间比例补差价 + 积分立即结算** | ❌ 当前等于"再买一次" | Service.upgrade + 三笔积分流水（EXPIRE/EARN/SPEND） |
| **F3**  | **AIGC / AI 任务失败积分退还** | ❌ 已 settle 后失败时不退 | aigc_task 加 credit_tx_id + CreditService.refund + AigcTaskService.failTask 串接 |
| **F4**  | **充值积分有效期统一为 2 年** | ⚠️ `earn()` 走 2 年、purchase handler 走永久 | `CreditRechargePayHandler` 改走 `creditService.earn()` |
| **F5**  | **`credit_grant_rule` seed 修正** | ⚠️ INVITE 7 天 vs FAQ 30 天、amount 200 → 500 | 直接修改 `v14__invite_reward_seed.sql` |

> F6（注册赠积分走规则）合并到 F4——`creditService.earn()` 修好后注册赠积分自然变 2 年有效，与"充值积分 2 年"统一语义；后续运营要切规则化时新建 REGISTER seed 即可，无需代码改动。

## AIGC 任务失败分类与积分语义

### 现有架构：post-pay（成功才扣）

```text
[同步链路 ImageGenServiceDecorator/AbstractAiServiceDecorator.creditCall]
   precheck（仅检查余额，不扣）
       ↓
   delegate.call()  ← 抛异常 → 跳过 settle，未扣积分
       ↓ 返回成功
   settleByUsage()  ← 扣积分 + 写 credit_transaction + 写 ai_usage_record
```

```text
[异步链路 video / midjourney / model3d]
   AigcTaskService.submit*  → precheck（仅检查）
       ↓
   provider 提交，返回 thirdTaskId
       ↓
   *TaskSyncJob 轮询 svc.query(thirdTaskId)
       ├─ SUCCESS → AigcTaskService.completeTask()  ← 在此 settleByUsage
       └─ FAILED  → AigcTaskService.failTask()      ← 当前不做积分动作
```

### 失败场景全景表

| # | 失败入口 | 当前是否已 settle | 是否需退还 | 处理位置 |
|---|---------|-----------------|-----------|---------|
| S1 | 同步图/音/语音 delegate 抛异常（如 provider 5xx / 鉴权失败） | ❌ 未扣 | ❌ 不需 | `AbstractAiServiceDecorator.creditCall` 已正确跳过 settle |
| S2 | 同步聊天 delegate 抛异常 | ❌ 未扣 | ❌ 不需 | 同 S1 |
| S3 | 流式聊天 onComplete 之前断流（用户取消、网络中断、provider 502） | ❌ 未扣（`TokenUsageEvent` 未发） | ❌ 不需 | 现有 `ResilientChatService.withStreamUsage` 行为正确 |
| S4 | 异步视频 / Midjourney / 3D 提交失败（`submitVideoAsync` catch）| ❌ 未扣 | ❌ 不需 | 任务记录置 FAIL，无积分动作 |
| S5 | 异步任务轮询发现 provider FAILED / CANCELED | ❌ 未扣（`completeTask` 未触发） | ❌ 不需 | `*TaskSyncJob` → `AigcTaskService.failTask()`，无积分动作 |
| **S6** | **`completeTask` 中已 settle 之后，OSS 上传失败** | ✅ 已扣 | ✅ **需退还** | `AigcTaskService.completeTask` 内 OSS 上传失败时调用 refund |
| **S7** | **`completeTask` 已 settle，结果质量校验后人工/审核标记 FAIL** | ✅ 已扣 | ✅ **需退还** | `AigcTaskService.failTask` 串接 refund |
| **S8** | **provider 返回 SUCCESS 但内容明显异常**（黑图、空音频、被审核拦截） | ✅ 已扣 | ✅ **需退还** | 由内容审核后置流程触发 `failTask` |

### 退还触发原则

> **唯一原则**：仅当 `aigc_task.credit_tx_id != null` 时执行退还。无 credit_tx_id 即从未扣过，跳过。

这条规则把 S1–S5（未扣）和 S6–S8（已扣）两类自然分流，无需上层判断"是哪种失败"。

### 退还语义

退还**不是删除原扣款流水**，而是写一笔反向 EARN 流水，保留审计可溯：

```text
原扣款：CreditTransaction(type=SPEND, amount=120, batch_type=null, biz_type=AI_USAGE, biz_id=null)
       同时各批次的 remain 已被扣减
       
退还：  CreditTransaction(type=EARN, amount=120, batch_type=REFUND, source=REFUND_AIGC_FAIL,
                          biz_type=AI_USAGE, biz_id=<原 tx id>, expire_at=<继承原批次最近到期>)
       account.balance += 120
       原扣款流水的 remain 不还原（作为已发生事实保留）
```

> 设计取舍：不还原原批次 `remain`，是为了"可审计 + 简单实现"。代价是退还的积分批次有效期取最近到期批次的 `expire_at`（保守保护用户：永远不会让退还的积分比原扣的批次活得更久）。若用户要"完全等价"恢复，需重写整个 spend 链路，本期不做。

## 数据模型变更

### `billing_subscription` 字段补充

```sql
ALTER TABLE billing_subscription
  ADD COLUMN auto_renew         BOOLEAN     NOT NULL DEFAULT TRUE,
  ADD COLUMN cancelled_at       TIMESTAMP(6),
  ADD COLUMN pending_plan_id    BIGINT,
  ADD COLUMN pending_yearly     BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN last_reminder_at   TIMESTAMP(6);

COMMENT ON COLUMN billing_subscription.auto_renew       IS
  '自动续费意图位：FALSE=用户已取消，到期不续费。本期不实现渠道代扣，仅做意图记录与未来扩展位';
COMMENT ON COLUMN billing_subscription.cancelled_at     IS
  '用户主动取消时间；NULL=未取消。取消后 status 仍 ACTIVE 直到 end_at';
COMMENT ON COLUMN billing_subscription.pending_plan_id  IS
  '排队待切换的下一套餐 ID（降级用）；end_at 到期时若非空，自动激活该套餐';
COMMENT ON COLUMN billing_subscription.pending_yearly   IS
  '排队待切换是否年付（与 pending_plan_id 配套）';
COMMENT ON COLUMN billing_subscription.last_reminder_at IS
  '最近一次到期前提醒发送时间，幂等防止重复发送';
```

> `status='CANCELLED'` 在现有语义里表示"激活旧订阅时把更老的旧订阅作废"，不能复用为"用户主动取消"。新加 `cancelled_at` 字段区分。

### `aigc_task` 字段补充

```sql
ALTER TABLE aigc_task
  ADD COLUMN credit_tx_id  BIGINT;

COMMENT ON COLUMN aigc_task.credit_tx_id IS
  '关联 credit_transaction.id，settleByUsage 成功时回填；failTask 时若非空触发退还';

CREATE INDEX idx_aigc_task_credit_tx ON aigc_task(credit_tx_id) WHERE credit_tx_id IS NOT NULL;
```

### `ai_usage_record` 字段补充（P2 占位）

```sql
ALTER TABLE ai_usage_record
  ADD COLUMN client_ip   VARCHAR(64),
  ADD COLUMN user_agent  VARCHAR(255);
```

### 不变更的部分

- `credit_account` / `credit_transaction` 表结构不动；升级清算 + 退还都复用现有 EARN/SPEND/EXPIRE 流水类型 + 扩展 `batch_type` 字面值（`REFUND` / `UPGRADE_INHERIT` 等运行时新增，不需 DDL）
- `billing_subscription_plan` 表结构不动；升级补差价、降级排队都在 service 层完成

## 接口变更

### F1 取消订阅

```text
POST /api/billing/subscription/cancel
  无请求体（取当前登录用户）
  返回：SubscriptionVO（cancelled_at 已填充，status 仍 ACTIVE）
```

```java
@Transactional
public Subscription cancel(Long userId) {
    var sub = subscriptionRepository
        .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
        .orElseThrow(() -> new BusinessException(NOT_FOUND, "无生效订阅"));
    if (sub.getCancelledAt() != null) {
        return sub; // 幂等
    }
    sub.setCancelledAt(LocalDateTime.now());
    sub.setAutoRenew(false);
    return subscriptionRepository.save(sub);
}
```

> 取消后**不立即降级**、**不退款**、**不回收已发放积分**、**不清除 pending_plan_id**。订阅 status=ACTIVE 持续到 end_at，由 [F1c 到期处理](#f1c-到期处理提醒--冻结) 自然处理。

### F1b 降级排队（end_at 切换）

```text
POST /api/billing/subscription/downgrade
Body: { planCode: "FREE" | "PRO", yearly: false }
返回：SubscriptionVO（pending_plan_id 已填充）

DELETE /api/billing/subscription/pending
返回：SubscriptionVO（pending_plan_id 已清除）
```

降级请求处理：

```java
@Transactional
public Subscription downgrade(Long userId, String newPlanCode, boolean newYearly) {
    var sub = subscriptionRepository
        .findByUserIdAndStatus(userId, ACTIVE)
        .orElseThrow(() -> new BusinessException(NOT_FOUND, "无生效订阅"));
    var newPlan = planRepository.findByCode(newPlanCode)
        .orElseThrow(() -> new BusinessException(NOT_FOUND, "套餐不存在"));
    var oldPlan = planRepository.findById(sub.getPlanId())
        .orElseThrow(() -> new BusinessException(NOT_FOUND, "当前套餐不存在"));
    
    // 校验：必须是降级（newPlan.price < oldPlan.price 或 newYearly < oldYearly）
    if (!isDowngrade(oldPlan, sub.isYearly(), newPlan, newYearly)) {
        throw new BusinessException(BAD_REQUEST, "请使用升级接口或正常订阅接口");
    }
    
    // 年付降月付（同档）：oldPlan.code == newPlan.code && oldYearly && !newYearly
    // 跨档降级：newPlan.price < oldPlan.price
    
    sub.setPendingPlanId(newPlan.getId());
    sub.setPendingYearly(newYearly);
    return subscriptionRepository.save(sub);
}

@Transactional
public Subscription cancelPending(Long userId) {
    var sub = subscriptionRepository
        .findByUserIdAndStatus(userId, ACTIVE)
        .orElseThrow(() -> new BusinessException(NOT_FOUND, "无生效订阅"));
    sub.setPendingPlanId(null);
    sub.setPendingYearly(false);
    return subscriptionRepository.save(sub);
}
```

> 降级**不付钱**、**不发积分**、**不动权益**。这与升级（立即生效 + 立即结算积分）形成对称设计。

### F1c 到期处理（提醒 + 冻结）

#### `SubscriptionExpiryReminderScheduler`（新增）

每日 09:00 扫描 `end_at` 在 `expiry_reminder_days`（默认 7 天）内的 ACTIVE 订阅：

```java
@Scheduled(cron = "0 0 9 * * *")
@Transactional
public void sendReminders() {
    int reminderDays = systemConfigService.getInteger(
        SysConfigKeys.Member.EXPIRY_REMINDER_DAYS, 7);
    var threshold = LocalDateTime.now().plusDays(reminderDays);
    
    var subs = subscriptionRepository.findActiveExpiringBefore(threshold);
    for (var sub : subs) {
        // 幂等：同一周期不重复发送
        if (sub.getLastReminderAt() != null
            && sub.getLastReminderAt().isAfter(sub.getStartAt())) {
            continue;
        }
        notificationService.send(sub.getUserId(), 
            NotificationType.SUBSCRIPTION_EXPIRY_REMINDER,
            buildReminderPayload(sub));  // 含 pending_plan / 是否需付款 / 到期日
        sub.setLastReminderAt(LocalDateTime.now());
        subscriptionRepository.save(sub);
    }
}
```

新增配置项：

```sql
INSERT INTO sys_config (category, config_key, value, default_value, value_type, name, description, visible, editable)
VALUES ('member', 'member.expiry_reminder_days', '7', '7', 'integer',
        '订阅到期提醒提前天数', '订阅 end_at 前几天发送提醒', TRUE, TRUE);
```

#### `SubscriptionExpireScheduler`（新增 / 强化现有 expireSubscriptions）

每日 00:15 扫描 `end_at < now` 的 ACTIVE 订阅：

```java
@Scheduled(cron = "0 15 0 * * *")
@Transactional
public void expireAndSwitch() {
    var expired = subscriptionRepository.findActiveAndEndAtBefore(LocalDateTime.now());
    for (var sub : expired) {
        if (sub.getPendingPlanId() != null) {
            // 降级排队生效：分两路
            var pendingPlan = planRepository.findById(sub.getPendingPlanId()).orElse(null);
            if (pendingPlan != null && pendingPlan.getPrice() == 0) {
                // → FREE：直接激活，不付费
                sub.setStatus(EXPIRED);
                subscriptionRepository.save(sub);
                subscriptionService.activateSubscription(
                    sub.getUserId(), sub.getPendingPlanId(), null, false);
            } else {
                // → 付费档：本期不做自动代扣，进入冻结分支
                //   未来接入代扣后，此处调用 SubscriptionAutoRenewService.tryAutoCharge(sub)
                //   - 成功：激活 pendingPlan
                //   - 失败/未签约：走 freeze 路径
                freeze(sub);
            }
        } else {
            // 无 pending：未续费即冻结
            freeze(sub);
        }
    }
}

/** 冻结语义 = 旧订阅 EXPIRED + 自动激活 FREE 兜底 */
private void freeze(Subscription sub) {
    sub.setStatus(EXPIRED);
    subscriptionRepository.save(sub);
    subscriptionService.activateSubscription(sub.getUserId(),
        planRepository.findByCode("FREE").orElseThrow().getId(),
        null, false);
    log.info("订阅冻结至 FREE: userId={}, oldPlanId={}", sub.getUserId(), sub.getPlanId());
    // 已发放的 SUBSCRIPTION 批次积分按原 30 天有效期自然过期，不主动作废
}
```

#### 冻结的精确语义

| 维度 | 行为 |
|------|------|
| 旧付费订阅 status | `ACTIVE` → `EXPIRED` |
| 用户当前订阅 | 自动新建 FREE 套餐 ACTIVE 订阅 |
| 权益 quota | `EntitlementService.instantiateQuotas(userId, FREE.id)` 覆盖式重置为 FREE 配额 |
| 已发放的 SUBSCRIPTION 批次积分 | **保留**，按原 expire_at 自然过期（一般 30 天后清零） |
| 已充值的 TOPUP 批次积分 | 保留，2 年有效 |
| 用户回来续费 | 走正常 `subscribe(planCode)` 路径，激活新付费订阅，FREE 订阅 CANCELLED |

> 不引入独立 `FROZEN` 状态枚举，复用 `EXPIRED + 自动 FREE 兜底` 语义——更简单、与现有 `activateSubscription` 路径对齐。

### F2 升级订阅按时间比例补差价（立即生效）

`SubscriptionService.subscribe()` 检测到 `newPlan.price > oldPlan.price` 时，路由到 `upgrade()` 分支。

#### 差价公式（按时间比例）

```text
oldStartAt    = oldSub.start_at
oldEndAt      = oldSub.end_at
totalDays     = (oldEndAt - oldStartAt) 天数
remainingDays = max(0, (oldEndAt - now) 天数)

oldRemainValue = oldPlan.price * remainingDays / totalDays   // 旧套餐剩余价值（按时间）
payable        = max(0, newPlan.price - oldRemainValue)      // 用户实付差价
```

> 用"时间比例"而非"积分比例"——更稳定可审计；FAQ 文案下次运营改为"按订阅剩余时间比例"即可。

#### 升级激活流程

1. 创建 `BizOrder(SUBSCRIPTION, payable)` + `PayOrder`，subject="订阅升级 PRO→TEAM"
2. `SubscriptionRecord.operation = "UPGRADE"`（新增枚举值）
3. 支付成功（payable=0 时同步成功跳过支付）→ 进入"积分立即结算"步骤

#### 积分立即结算（核心：三笔流水）

> 以"旧月度积分 200 已消耗 100 / 升级到月度积分 400"为例，升级后语义："积分总数 400，已消耗 100，可用 300"。

```text
Step 1 [EXPIRE]：旧 SUBSCRIPTION 批次清零作废
   tx = CreditTransaction(
       type=EXPIRE,
       amount=oldBatch.remain,       // = 100
       batch_type=SUBSCRIPTION,
       source=UPGRADE_OLD_BATCH_EXPIRE,
       biz_id=oldSub.id,
       remark="升级清算：旧批次未消费部分作废"
   )
   oldBatch.remain = 0
   account.balance -= oldBatch.remain   // -= 100

Step 2 [EARN]：发新月度积分（按新套餐总额）
   newBatch = CreditTransaction(
       type=EARN,
       amount=newPlan.monthly_credits,   // = 400
       batch_type=SUBSCRIPTION,
       source=UPGRADE_NEW_MONTHLY,
       biz_id=newSub.id,
       expire_at=now + 30 days,
       remain=newPlan.monthly_credits    // = 400
   )
   account.balance += newPlan.monthly_credits   // += 400

Step 3 [SPEND]：升级继承已用部分（视作已在新批次预扣）
   tx = CreditTransaction(
       type=SPEND,
       amount=oldUsed,                   // = 100（= oldPlan.monthly_credits - oldBatch.remain_before_expire）
       category=UPGRADE_INHERIT_USAGE,
       biz_type=SUBSCRIPTION_UPGRADE,
       biz_id=newSub.id,
       remark="升级继承已用 (旧已用 100)"
   )
   newBatch.remain -= oldUsed           // 400 - 100 = 300
   account.balance -= oldUsed            // -= 100

最终：account.balance 净变 = -100 + 400 - 100 = +200
       升级前 balance = 100，升级后 balance = 300 ✓
       审计可见三笔流水，语义清晰
```

> `oldUsed` 取自旧 SUBSCRIPTION 批次发放时的 `amount` 与作废时的 `remain` 之差。若有多个 SUBSCRIPTION 批次（运营手动追加场景），按 `expire_at` 升序合并计算。

#### 升级时其他订阅状态变更

1. 旧订阅 `status=CANCELLED`，记录 `cancellation_reason="UPGRADE"`（复用 cancelled_at 字段）
2. 新订阅 `status=ACTIVE`，`start_at=now`，`end_at=now + durationDays`
3. `entitlementService.instantiateQuotas(userId, newPlanId)` 覆盖式重置权益 quota
4. 旧批次清空 + 三笔流水写入（同上）

### F3 AI/AIGC 任务失败积分退还

`CreditService` 接口新增：

```java
/**
 * 退还此前已扣减的积分（写反向 EARN 流水，不还原原批次 remain）。
 * @param creditTxId 原扣款流水 ID
 * @param reason    退还原因（写入 remark）
 * @return 退还流水 ID；找不到原流水或已退过返回 null
 */
Long refund(Long creditTxId, String reason);
```

`AiCreditGuard` 新增 default 委托方法，`DefaultAiCreditGuard` 实现委托给 `CreditService.refund`。

`AbstractAiServiceDecorator.creditCall` 修改：让 `settleByUsage` 返回 `creditTxId`，由调用方（AigcTaskService）保存到 `aigc_task.credit_tx_id`。

`AigcTaskService.failTask` 改造：

```java
public void failTask(String thirdTaskId, String errorMsg) {
    var task = taskRepo.findByTaskId(thirdTaskId).orElse(null);
    if (task == null) return;
    
    // 关键：若已扣过积分，则退还
    if (task.getCreditTxId() != null) {
        try {
            creditGuard.refund(task.getCreditTxId(),
                "AIGC 任务失败自动退还: " + errorMsg);
            log.info("[failTask] 积分已退还: taskId={}, creditTxId={}",
                task.getId(), task.getCreditTxId());
        } catch (Exception e) {
            log.warn("[failTask] 积分退还失败（不影响任务状态）: taskId={}, err={}",
                task.getId(), e.getMessage());
        }
    }
    
    task.setStatus(STATUS_FAIL);
    task.setErrorMsg(errorMsg);
    taskRepo.save(task);
    eventService.push(task.getUserId(), EVENT_FAILED, toVO(task));
}
```

`AigcTaskService.completeTask` 改造：在 OSS 上传失败时**不**置 SUCCESS，改调 `failTask` 触发退还路径。

### F4 充值积分有效期统一为 2 年

`RechargeService` 与 `CreditRechargePayHandler` 走同一条 `creditService.earn()` 路径，自动继承 2 年有效期：

```java
// CreditRechargePayHandler.onPaySuccess 改造
pkg -> {
    long total = pkg.getCredits() + pkg.getBonusCredits();
    creditService.earn(
        bizOrder.getUserId(), total, "CREDIT_PACKAGE", bizOrder.getOrderNo());
}
```

`CreditServiceImpl.earn()` 不动，保留 `TOPUP_EXPIRE_DAYS = 365 * 2`：

```java
@Override
@Transactional
public void earn(Long userId, long amount, String source, String bizId) {
    earnBatch(userId, amount, "TOPUP", source, bizId,
        LocalDateTime.now().plusDays(TOPUP_EXPIRE_DAYS));
}
```

### F5 `credit_grant_rule` seed 修正（直接改 v14）

**开发阶段策略**：v0.x 未发布，直接修改 `v14__invite_reward_seed.sql`，不新建 v15 迁移：

```sql
('INVITE', '邀请注册奖励', 500, 30, 'EVENT', 'ENABLED',
 '{"maxInvites": 20, "description": "好友通过邀请链接完成注册后发放"}'::jsonb,
 '邀请注册奖励：好友通过你的邀请链接完成注册后发放。积分有效期 30 天；每个用户最多可获得 20 次邀请奖励。')
```

> WEEKLY / REGISTER 规则**本期不补**：缺规则时调度器自然停用，运营在 admin 后台新增即可。

## 自动续费扩展点（接口预留）

本期不实现渠道代扣，但保留所有扩展位，未来接入只需补实现。

### 已预留的字段

```sql
billing_subscription.auto_renew      -- 用户意图位（取消订阅时置 FALSE）
```

### 待补充的字段（**本期不做**，未来需要时再加）

```sql
-- 渠道代扣签约表（占位）
CREATE TABLE pay_signed_contract (
    id              BIGINT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    channel_code    VARCHAR(32) NOT NULL,    -- WX_PAY / ALIPAY
    contract_no     VARCHAR(128) NOT NULL,   -- 协议号
    contract_status VARCHAR(16) NOT NULL,    -- ACTIVE / TERMINATED
    -- 其他字段省略
);
```

### 接口预留

`SubscriptionExpireScheduler.expireAndSwitch()` 中已经埋好扩展点：

```java
} else {
    // → 付费档：本期不做自动代扣，进入冻结分支
    //   未来接入代扣后，此处调用 SubscriptionAutoRenewService.tryAutoCharge(sub)
    //   - 成功：激活 pendingPlan
    //   - 失败/未签约：走 freeze 路径
    freeze(sub);
}
```

未来接入步骤：

1. 新增 `SubscriptionAutoRenewService` 接口 + 默认实现（调代扣 SDK + 写支付订单）
2. 在 `Subscription Activate` 流程里支持"用户主动签约"——前端勾选"开通自动续费"时调代扣 SDK 拉协议
3. `SubscriptionExpireScheduler` 把 `freeze(sub)` 替换为 `tryAutoCharge → freeze fallback`
4. FAQ Q4 文案恢复"会自动续费"

> **重要**：本期 `auto_renew=true` **不代表"会自动扣款"**，只是"用户没主动取消"。前端不要在订阅卡片上展示"自动续费已开启"等误导性文案，统一文案为"到期前请手动续订"。

## FAQ 文案调整建议（运营侧）

| FAQ 条目 | 当前文案 | 建议调整 |
|---------|---------|---------|
| Q1 充值积分卡片 | "永久有效（除非另有说明）" | **改为"有效期 2 年（自发放之日起计算）"**——本次设计文档已直接修改 v12 seed |
| Q1 邀请奖励卡片 | "有效期 30 天" | 不变（v14 seed 已修正为 30 天 + 500 积分） |
| Q3 升级文案 | "旧套餐仅按已使用积分比例计费" | 改为"旧套餐按已使用时间比例计费"（与 F2 实现对齐） |
| Q3 新增"如何降级" | — | 补卡片："降级在当前订阅周期到期后生效，不退款；可在订阅页随时取消已申请的降级" |
| Q4 "订阅会自动续费吗？" | "会的。订阅将在每个计费周期结束时自动续费..." | **整条删除**或改为"当前阶段订阅到期前请手动续订，自动续费功能即将上线" |
| Q4 "如何修改或取消订阅？" 末段 | "周期结束后订阅将自动失效，并不再进行自动续费" | 改为"周期结束后订阅将自动失效，请按需手动续订；未续订期间用户回到免费档使用" |
| Q5 "我如何申请退款？" | "可在购买后 7 天内申请全额退款..." | 保留，但**首句改为**"如需申请退款，请联系 service@xuejiai.com（人工审核）" |

> FAQ 是运营内容（在 `sys_config.member.faq` 维护），不是代码契约。本期文案降级 + 后端不实现，是合理的"先发布契约，再迭代实现"反向操作——把代码与文案对齐，避免承诺无法兑现。

## 实施计划

按依赖顺序拆任务，所有任务在同一 Epic 下推进。

| # | 任务 | 涉及模块 | 依赖 |
|---|------|---------|------|
| 01 | DB 迁移 v15：`billing_subscription` 加 auto_renew/cancelled_at/pending_plan_id/pending_yearly/last_reminder_at；`aigc_task` 加 credit_tx_id；`ai_usage_record` 加 client_ip/user_agent；`sys_config` 插入 `member.expiry_reminder_days` | `db/migration/v15__membership_completion.sql` | — |
| 02 | **直接修改** `db/seed/v14__invite_reward_seed.sql`：INVITE amount 200→500、expire_days 7→30 + remark 同步 | seed | — |
| 02b | **直接修改** `db/seed/v12__init_seed_data.sql` 中 FAQ JSON：Q1 充值积分卡片改"2 年有效" | seed | — |
| 03 | `Subscription` Domain + DTO 加 5 个字段；`SubscriptionVO` 暴露所有字段 | `module/billing/domain/` | #01 |
| 04 | `SubscriptionService.cancel(userId)` + `SubscriptionController` POST `/cancel` | `module/billing/service/` | #03 |
| 05a | `SubscriptionService.upgrade(userId, planCode, ...)` 时间比例补差价 | 同上 | #03 |
| 05b | `SubscriptionService.upgrade()` 积分三笔流水实现（EXPIRE/EARN/SPEND） | + `framework/engine/credit/` | #05a |
| 05c | `SubscriptionController` 在 subscribe 入口路由：升级 / 同档续约 / 降级三向分支 | 同上 | #05a, #05d |
| 05d | `SubscriptionService.downgrade(userId, planCode, yearly)` + `SubscriptionController` POST `/downgrade` + DELETE `/pending` | 同上 | #03 |
| 06a | `SubscriptionExpiryReminderScheduler`（每日 09:00 发提醒） + `NotificationType.SUBSCRIPTION_EXPIRY_REMINDER` 注册 | `module/billing/scheduler/` + `module/system/notify/` | #03 |
| 06b | `SubscriptionExpireScheduler`（每日 00:15 处理到期，含 pending 切换 + 冻结 = EXPIRED + FREE 兜底）；保留 `SubscriptionAutoRenewService` 扩展点注释 | 同上 | #03, #05d |
| 07 | `CreditService.refund(creditTxId, reason)` + `CreditServiceImpl` 实现 + 单元测试 | `framework/engine/credit/` | — |
| 08 | `AiCreditGuard.refund` default 委托 + `DefaultAiCreditGuard` 实现 | 同上 | #07 |
| 09 | `AbstractAiServiceDecorator.creditCall` 让 settleByUsage 返回 creditTxId | `framework/intelligent/core/decorator/` | #08 |
| 10 | `AigcTask` Domain 加 creditTxId；`AigcTaskService.completeTask` settle 后回填 + OSS 失败转 failTask | `module/ai/aigc/task/` | #01, #09 |
| 11 | `AigcTaskService.failTask` 检查 creditTxId 并 refund | 同上 | #07, #10 |
| 12 | `CreditRechargePayHandler.onPaySuccess` 改走 `creditService.earn()`（统一 2 年有效期） | `module/billing/service/` | — |
| 13 | 验收测试：取消订阅 / 升级补差价 + 三笔积分流水 / 降级排队 / 撤销降级 / 到期提醒 / 冻结至 FREE / AIGC 失败退还 / 充值积分 2 年有效 / INVITE 30 天 9 条 Gherkin | `*AcceptanceTest.java` | 全部 |

## 关键约束与回滚

- **F1b 降级校验**：必须在 service 层严格校验"是降级"（newPlan.price < oldPlan.price 或 同档年付→月付），否则路由错乱
- **F2 升级补差价**：旧订阅作废时 `entitlement_quota` 必须重新实例化（覆盖式），否则用户会同时持有两套权益
- **F2 升级三笔流水的事务边界**：EXPIRE / EARN / SPEND 三笔必须同事务（`@Transactional` REQUIRED），任一失败整体回滚
- **F1c 冻结调度的并发**：`SubscriptionExpireScheduler` 与 `SubscriptionExpiryReminderScheduler` 不重叠（00:15 vs 09:00），避免同一订阅同事务下被双重处理
- **F1c last_reminder_at 幂等**：用 `last_reminder_at > start_at` 判定本周期已发过；订阅升级/降级排队后 `start_at` 不变（除升级激活新订阅 `start_at=now`），不会误重发
- **F3 退还的事务边界**：refund 必须与 failTask 的状态变更在同一事务内（用 `@Transactional` + `Propagation.REQUIRED`）；失败时回滚到任务原状态
- **F4 双路径修复**：保留 `RechargeService` 既有路径不动；保留 `TOPUP_EXPIRE_DAYS = 365 * 2` 作为充值积分有效期单一来源
- **F5 直接改 v14 seed 的合理性**：AAF 未 v1.0 发布、开发期 DB 重建无成本；与 AGENTS.md "禁兼容层" 一致——开发期不留双路径
- **回滚预案**：所有 DB 变更通过 v15 Flyway 迁移；无破坏性 DDL（仅 ADD COLUMN，不删字段、不改类型）；代码层面通过 feature flag `member.completion_v0_2_enabled` 控制 F1/F1b/F1c/F2/F3 启用；F4/F5 是修不一致 bug，无需 flag

## 暂不实现项的未来路径（占位）

| 缺口 | 未来设计入口 | 关键依赖 |
|------|-------------|---------|
| **自动续费扣款（接口预留）** | 新建 `SubscriptionAutoRenewService` + `pay_signed_contract` 表 + 在 `SubscriptionExpireScheduler.expireAndSwitch()` 替换冻结分支为 `tryAutoCharge → freeze fallback` | 微信支付代扣 / 支付宝代扣 SDK 接入 |
| 7 天无消费可退款 | 扩展 `PayRefundService.applyRefund`：增加"是否在 7 天内 + 该次充值产生的批次 remain == amount" 校验 + 反向扣减 credit_transaction.remain | 渠道退款回调链路稳定 |
| 防刷与防自动化滥用 | 新建 `module/risk` + 在 OperatorContext 链路注入 IP/UA → ai_usage_record + 滑动窗口频率限流（基于 Redis） | Redis 集群可用 |
| WEEKLY/REGISTER 规则化 | 运营在 admin 后台 `credit_grant_rule` 新增即可启用；`AuthService.grantRegistrationCredits` 切到 `creditGrantService.grant` | 无 |
