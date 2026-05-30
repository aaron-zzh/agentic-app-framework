# 02 支付 · 计费 · 积分

> 覆盖：积分记账、支付订单、充值编排、权益消费、订阅、对账。

## 问题清单

| 编号 | 级别 | 位置 | 问题 | 修复建议 |
|------|------|------|------|---------|
| B2 | 🔴 | `module/pay PayOrderController#recharge` + `RechargeService#initiateRecharge` | `recharge(userId, amount, channelCode=MOCK)` 全部客户端可控，MOCK 渠道默认同步成功→任意用户给任意账户免费铸造任意积分 | userId 取 `OperatorContext`；金额来自服务端订单；MOCK 渠道生产禁用 |
| B3 | 🔴 | `PayOrderController#notify` + `PayOrderService#handleNotify` | 回调直接信任 `dto.success()` 置为 SUCCESS，无渠道验签；端点又不在白名单（真实网关打不通） | 实现渠道验签；明确回调认证方式；状态机幂等保留 |
| M3 | 🟠 | `RechargeService#onPaySuccess` + `CreditServiceImpl#earn` | MOCK 同步成功已入账，若真实异步回调再触发，`onPaySuccess` 未判重、`earn` 无按 bizId 幂等→重复入账 | `earn`/入账以 bizId 或订单状态做幂等 |
| M4 | 🟠 | `module/billing EntitlementService#consume/deduct` | `check`(readOnly) 与 `consume` 分事务，`deduct` 读改写 quota 无锁/无版本（积分侧有 `findByUserIdForUpdate`，权益侧没有）→并发丢失更新、remain 变负 | quota 扣减加行锁或 `@Version` 乐观锁+重试 |
| M-str | 🟠 | `EntitlementService`、`SubscriptionService` | 订阅状态用魔法串 `"ACTIVE"` 而非已存在的 `SubscriptionStatusEnum` | 统一用枚举，消除魔法串 |

## 良好实践

- `CreditServiceImpl` 的 spend/freeze/unfreeze 用 `findByUserIdForUpdate` 悲观锁 + 写流水 `balanceAfter`，单账户记账并发安全、可追溯。
- `PayOrderService#handleNotify` 用状态 `WAITING→SUCCESS` 守卫，回调幂等正确。
- 权益 refill 走"预判 canRefill（不扣）→ consume 真扣"两段，设计清晰。

## 对称性 / 一致性提示

- 状态变更 vs 通知（清单#7）：充值入账与订单状态非幂等（M3）。
- 认证 vs 鉴权（清单#8）：积分查询/充值未绑定当前用户（B2/M1）。
- 已有模式 vs 新建（清单#13）：状态魔法串绕过已有枚举（M-str）。

## 待确认

- `ReconcileService`/`PayRefundService` 未深读：退款金额上限校验、对账差异处理、重复退款幂等需补审。
- `BizOrderService#create` 是否对 merchantOrderNo 唯一性/重复下单做约束。
