---
level: Practice
layer: Model
purpose: 订阅积分批次化设计——订阅月度积分发放、有效期管理与按批次优先扣减
status: draft
version: 0.1.0
date: 2026-06-01
author: AaronZZH
changelog:
  - 2026-06-01 | 初版：订阅积分批次化方案设计
gains:
  - 能理解订阅积分与充值积分的区别及有效期语义
  - 能理解积分批次模型如何支持"优先扣更快到期的积分"
  - 能理解订阅激活/续期时如何发放月度积分
  - 能理解定时任务如何驱动月度积分发放与过期清理
---

# 订阅积分批次化设计

> 订阅套餐包含"每月积分"，积分是平台唯一的按量消费货币。不同来源的积分有效期不同，消费时优先扣更快到期的批次。

## 背景与问题

### 现状

当前积分（Credit）是一个纯余额账户：`CreditAccount.balance` 是一个整数，`CreditTransaction` 记录每笔流水，但没有有效期概念。积分只能通过后端手动调用 `creditService.earn()` 发放，没有与订阅套餐联动的自动发放机制。

### 目标

参考 Flova 等 AI 创作平台的定价模型，实现：

1. 订阅套餐可配置"每月发放积分数"，订阅激活时发放首月积分（30 天有效期）
2. 每月自动为有效订阅用户发放下一批月度积分
3. 不同来源的积分有独立有效期（订阅积分 30 天、充值积分永久、奖励积分按活动设定）
4. 消费时优先扣更快到期的积分批次，保障用户权益

### 与权益额度的关系

积分和权益额度（`EntitlementQuota`）是**平行的两套体系**，各司其职：

| 维度 | 权益额度（EntitlementQuota） | 积分（Credit） |
|------|------------------------------|----------------|
| 控制的是 | 配额型权益（并发数、存储量、功能开关） | 按量计费消费（AI 调用、生成任务） |
| 获取方式 | 订阅套餐授予，按周期重置 | 订阅发放 + 充值购买 + 奖励 |
| 有效期 | 跟随订阅有效期 | 各批次独立有效期 |
| 超出后 | 可用积分 refill | 余额不足则拒绝 |

两者的协作：权益额度用尽时，系统用积分执行 refill（`EntitlementService.tryRefill()`），消耗积分换取额度。

---

## 数据模型

### 现有模型扩展

**`credit_transaction` 表新增字段**（最小改动，不新建表）：

```text
credit_transaction（扩展）
  + batch_type    VARCHAR(16)   -- 批次来源：SUBSCRIPTION / TOPUP / REWARD / WEEKLY / MANUAL
  + expire_at     TIMESTAMP     -- 过期时间，NULL = 永不过期（充值积分）
  + remain        BIGINT        -- 本批次剩余可用量（消费时按批次扣减）
```

> `remain` 字段是批次扣减的核心。每次消费从最快到期的批次开始扣，更新对应批次的 `remain`，直到扣完所需金额。`CreditAccount.balance` 仍作为总余额的快速查询缓存，两者保持一致。
>
> 积分账户（`credit_account`）同时承载成长体系（`exp` + `level_id`），暂不引入独立的 wallet 表。后续如需支持余额账户、商城充值提现等多财产类型，再以 wallet 作为聚合根扩展。

**`subscription_plan` 表新增字段**：

```text
subscription_plan（扩展）
  + monthly_credits  BIGINT DEFAULT 0  -- 每月发放积分数，0 = 不发放
```

### 积分批次类型与有效期规则

| batch_type | 来源 | 有效期 | expire_at |
|------------|------|--------|-----------|
| SUBSCRIPTION | 订阅月度发放 | 30 天 | now + 30d |
| TOPUP | 用户充值购买 | 永久 | NULL |
| REWARD | 运营活动奖励 | 按活动设定 | 活动配置 |
| WEEKLY | 每周免费积分 | 7 天 | now + 7d |
| MANUAL | 后台手动发放 | 按需设定 | 可空 |

---

## 核心流程

### 订阅激活时发放首月积分

```text
SubscriptionService.activateSubscription()
  → entitlementService.instantiateQuotas()   // 现有：实例化权益额度
  → creditService.earnBatch(                  // 新增：发放首月积分
        userId,
        plan.monthlyCredits,
        BatchType.SUBSCRIPTION,
        now + 30d
    )
```

仅当 `plan.monthlyCredits > 0` 时执行。免费套餐 `monthlyCredits = 0`，不发放。

### 每月定时发放（续期积分）

定时任务每天凌晨扫描，对满足条件的订阅发放下一批月度积分：

```text
SubscriptionCreditScheduler（新增，每日 00:05 执行）
  → 查询所有 status=ACTIVE 的 subscription
  → 对每个订阅，检查上次发放时间（last_credit_issued_at）
  → 若距上次发放 ≥ 30 天，则：
      creditService.earnBatch(userId, plan.monthlyCredits, SUBSCRIPTION, now+30d)
      subscription.lastCreditIssuedAt = now
```

`subscription` 表新增 `last_credit_issued_at TIMESTAMP` 字段，记录上次积分发放时间，防止重复发放。

### 积分消费（按批次优先扣减）

`CreditServiceImpl.spend()` 改造为按批次扣减：

```text
spend(userId, amount)
  1. 查询该用户所有 remain > 0 的批次
     ORDER BY expire_at ASC NULLS LAST（最快到期的优先，永久积分最后）
  2. 遍历批次，依次扣减 remain，直到 amount 扣完
  3. 更新各批次 remain
  4. 更新 CreditAccount.balance（总余额 -= amount）
  5. 写一条汇总 CreditTransaction（type=SPEND，不含 expire_at）
```

> 对外接口签名 `spend(Long userId, long amount, String source, String bizId)` 不变，调用方无感知。

### 积分过期清理

定时任务每日凌晨扫描过期批次：

```text
CreditExpireScheduler（新增，每日 00:10 执行）
  → 查询 expire_at < now AND remain > 0 的批次
  → 将 remain 清零
  → 更新 CreditAccount.balance（减去过期的 remain）
  → 写 CreditTransaction(type=EXPIRE, amount=过期金额)
```

---

## 接口变更

### CreditService 新增方法

```java
/**
 * 发放带有效期的积分批次。
 * @param expireAt 过期时间，null = 永不过期
 */
void earnBatch(Long userId, long amount, String batchType, String source, String bizId, LocalDateTime expireAt);
```

原有 `earn()` 方法保持不变，内部调用 `earnBatch(..., null)`（永久积分）。

### CreditTransaction 新增查询

```java
// CreditTransactionRepository 新增
List<CreditTransaction> findByAccountIdAndRemainGreaterThanOrderByExpireAtAscNullsLast(Long accountId);
List<CreditTransaction> findByExpireAtBeforeAndRemainGreaterThan(LocalDateTime now);
```

---

## 充值积分套餐（P2，后续实现）

当前方案不包含用户自助购买积分的流程，作为 P2 规划：

```text
credit_package（待建）
  id            BIGINT PK
  name          VARCHAR         -- "100元 1000积分"
  price         BIGINT          -- 售价（分）
  credits       BIGINT          -- 赠送积分数
  bonus_credits BIGINT          -- 赠送加赠积分（促销用）
  status        VARCHAR(16)     -- ENABLED / DISABLED
  sort          INT
```

购买流程复用 `BizOrderService + PayOrderService`，支付成功后调 `creditService.earnBatch(..., null)`（永久积分）。

---

## 实施计划

| 优先级 | 内容 | 涉及文件 |
|--------|------|---------|
| P0 | `credit_transaction` 加 `batch_type / expire_at / remain` 字段 + Flyway 迁移 | DB 迁移 + `CreditTransaction.java` |
| P0 | `CreditServiceImpl.spend()` 改为按批次扣减 | `CreditServiceImpl.java` |
| P0 | 新增 `earnBatch()` 接口与实现 | `CreditService.java` + `CreditServiceImpl.java` |
| P1 | `subscription_plan` 加 `monthly_credits` 字段 | DB 迁移 + `SubscriptionPlan.java` |
| P1 | `subscription` 加 `last_credit_issued_at` 字段 | DB 迁移 + `Subscription.java` |
| P1 | 订阅激活时发放首月积分 | `SubscriptionService.activateSubscription()` |
| P1 | 新增 `SubscriptionCreditScheduler`（月度积分发放） | 新增 Scheduler |
| P1 | 新增 `CreditExpireScheduler`（过期清理） | 新增 Scheduler |
| P2 | 充值积分套餐（`CreditPackage` + 购买流程） | 新增模块 |

---

## 关键约束

- `CreditAccount.balance` 与所有批次 `remain` 之和必须始终一致，任何修改必须在同一事务内完成
- `spend()` 使用悲观锁（`findByUserIdForUpdate`）防止并发超扣
- 过期清理任务必须在积分发放任务之后执行，避免当天发放的积分被误清理
- 月度积分发放以 `last_credit_issued_at` 为准，不依赖订阅 `start_at` 计算，防止时区和闰月问题
