---
level: Practice
layer: Product
purpose: 管理 AAF-099 会员订阅历史基线与 Plan + SKU 审计修订后的活动技术任务
status: draft
version: 1.1.0
date: 2026-09-09
author: architect
dependencies:
  - ./requirement.md
  - ./design.md
related:
  - ./design-audit.md
  - ./dev-log.md
changelog:
  - 2026-09-09 | v1.1.0：补 Front Matter；归档 #01-#15 为已由现状实现的历史基线；重排 #16 起任务以关闭设计审计问题
---

# AAF-099 会员套餐订阅与积分系统补全

> 设计文档：[../../../design/apps/service/membership-completion.md](../../../design/apps/service/membership-completion.md)

## 历史基线状态

> **状态说明（不改变下方 #01-#15 原文）**：#01-#15 是 AAF-099 初版任务分解，相关能力已经体现在当前源码/SQL 中，现仅作为“历史基线、已由现状实现”的审计证据保留，**不属于本轮 Plan + SKU 修订的活动范围，也不阻塞 #16 起任务**。表中 `☐` 是历史记录，不表示当前需要重做。尤其不得按 #01 新增 `v15__membership_completion.sql`；真实 v15 已被其他迁移占用，本轮 SQL 范围以 design.md v1.1.0 为准。

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

## 历史完工门禁（归档，不适用于本轮）

> 下列清单只保留初版任务证据，不再作为当前完成判定；其中 auto_renew/yearly 等口径已被 requirement.md v1.0.2 与 design.md v1.1.0 明确替换。

- [ ] 所有 15 项任务完成
- [ ] `pnpm nx test service` 全绿（71+ 单元测试）
- [ ] `pnpm acceptance:affected` 全绿（9 条 Gherkin 验收）
- [ ] `pnpm check:affected` 全绿（lint + typecheck + build）
- [ ] dev-log.md 每项一行记录关键决策

## Plan + Subscription SKU 审计修订活动任务

> 需求真理源：[requirement.md v1.0.2](./requirement.md)；技术设计：[design.md v1.1.0](./design.md)。表内 `#16` 对应完整技术任务号 `#09916`。**只有本节任务处于活动范围。**所有任务受“人类批准 design.md”前置门禁约束。

### 依赖主链

```text
人类批准 design.md
  → #16 新库 bootstrap 原子门禁
    ├─ #17 AIGC 动态准入
    ├─ #18 兑换码 SKU 行为
    └─ #19 分段预付价值
         → #20 单 live 结账
           → #21 支付回调与补偿事实
             → #22 生命周期与 REST 契约
               → #23 前端 API/支付弹窗
                 → #24 会员页
                   → #25 Workspace pricing
  → #26 静态旧语义清零
  → #27 最小高风险测试补齐
  → #28 最小验收覆盖
  → #29 集中最终验证
```

### 活动任务表

| # | 完整编号 | 任务 | 涉及模块 | 依赖 | 完成 |
|---|----------|------|----------|------|------|
| **#16** | **#09916** | **Schema/模型/元数据 bootstrap 原子门禁**：仅原地修改 v5、v12、v101；拆 Plan+SKU，加入 checkout guard、分段 Record/补偿状态，兑换码 plan_id→sku_id，删除 yearly/Plan 价格/auto_renew 旧字段；补 SKU 行内 CHECK、Plan 归属受控服务与 v12 seed 断言；同步 domain/DTO/VO/Repository/受控 CRUD/Controller/provider 及所有为编译启动必须删除的旧字段调用；v101 增 SKU、兑换码 SKU、只读 Record/补偿元数据。完成前必须以全新库执行全部 migration+seed、应用启动、CrudResourceCompiler 和 Billing 元数据读取 smoke；确认未新增 SQL 且只改 v5/v12/v101。任一失败不得启动下游 | `db/migration/v5__order_schema.sql`、`db/seed/v12__init_seed_data.sql`、`db/seed/view/v101__billing_entity_def.sql`、billing domain/vo/repository/CRUD/controller、`CrudResourceProviderConfiguration` | **人类明确批准 design.md v1.1.0** | ☐ |
| **#17** | **#09917** | **AIGC 动态会员准入**：实现共享 `AigcSubmissionAccessGuard` 与六类 taskType→`aigc_*_access` 映射；普通 Task Service、项目 `AigcTaskApiAdapter`、批量图像三个创建边界严格先 `EntitlementChecker/EntitlementService.check` 后 credit precheck；工具调用复用 Task API，不在 ToolService 猜类型；补 400/403/500 与工具稳定错误码，未知类型 fail closed | `module/ai/aigc/task/`、`module/ai/aigc/image/`、AIGC tools、AIGC ErrorCode、必要异常映射 | #16 | ☐ |
| **#18** | **#09918** | **会员兑换码精确 SKU 行为**：`CreditRedeemCodeService` 生成/查询/导出/兑换全部使用 skuCode/skuId；校验 ENABLED、正价、非 FREE；锁兑换码后调用 `activateGrantedSku`，按目标 SKU 自然月激活且成功后才核销；无 SKU/只给 Plan/禁用/FREE 明确拒绝，不得默认月付 | billing 兑换码 domain/DTO/VO/page/service/controller、SubscriptionService 赠送开通接口 | #16 | ☐ |
| **#19** | **#09919** | **分段预付价值与自然月生命周期**：成功 NEW/RENEW/UPGRADE 写 generation、serviceStart/serviceEnd、skuPriceSnapshot；RENEW 从当前 endAt 追加独立段；升级只锁定当前世代、now 后重叠的 PAID+FULFILLED+AVAILABLE 段，逐段按自身微秒区间与名义快照 HALF_UP 后求和；成功后旧候选段原子 SUPERSEDED，新 Subscription 为新世代；pay_price 仅实付，不参与名义价值 | `SubscriptionService`、`SubscriptionPeriodCalculator`、Record Repository/价值计算器 | #16 | ☐ |
| **#20** | **#09920** | **同用户单 live 会员结账**：实现 billing checkout guard 的 ensure+FOR UPDATE；所有下单先 guard、ACTIVE、live Record；通过 pay 模块 `PayOrderQueryApi` 判断 WAITING 且未过期；第二请求 409 并返回现有 payOrderId；部分唯一索引作终防线；统一锁顺序，FREE/无 Subscription 用户同样可锁 | billing checkout guard/repository/service、pay `PayOrderQueryApi`、SubscriptionService 下单 | #16、#19 | ☐ |
| **#21** | **#09921** | **支付回调、幂等与人工补偿事实**：回调按 guard→ACTIVE→Record→价值段锁序重算；合法订单只履约一次；不同 PayOrder 后到成功或 CLOSED/状态/金额变化时把 Record 与 BizOrder 标记 COMPENSATION_PENDING，记录异常码/原因/时间，PayOrder 保持 SUCCESS；禁止激活/续期/权益/积分/佣金，PayNotify 将其视为通知终态；提供只读运营查询和专用“记录人工处理结果”动作，不实现自动退款 | billing SubscriptionService/Record 管理、pay BizOrder/PayNotify、错误码 | #19、#20 | ☐ |
| **#22** | **#09922** | **订阅生命周期与 REST 契约完成**：NEW/UPGRADE/RENEW/DOWNGRADE 分类、同 Plan 换周期拒绝、pendingSku、取消/撤销、到期 FREE 冻结、管理员 skuCode；彻底删除 SubscriptionAutoRenewService、调度代扣分支、autoRenew 对外语义；完成 catalog/current/subscribe/cancel/downgrade/pending-downgrade 精确 DTO/Controller 契约。此任务完成才允许前端接线 | billing service/scheduler/controller/error code；aaf-common enum 仅按需 | #18、#19、#21 | ☐ |
| **#23** | **#09923** | **前端 REST 类型与支付弹窗切 SKU**：`plans.ts` 切 `skus[]`、当前/pending SKU 和生命周期 mutation；兑换码 API/生成表单切 skuCode；`SubscriptionPayDialog` 只发 skuCode+channelCode并复用原渠道/轮询；服务端状态只由 TanStack Query 管理 | webui billing API、SubscriptionPayDialog、RedeemCodeGenerateButton | #18、**#22 REST 契约完成** | ☐ |
| **#24** | **#09924** | **会员页交易与管理闭环**：`/studio/me/membership` 月/季/年 SKU 明示价格；PlanCard/BillingCycleToggle 删除所有推导；复用支付弹窗；接通新购、升级、同 SKU 续费、取消、降级、撤销降级和并发冲突反馈 | membership page、billing components | #23 | ☐ |
| **#25** | **#09925** | **Workspace pricing 只读接线**：改 `app/(workspace)/settings/pricing/page.tsx` 使用同一后端目录和三周期共享组件；移除联系客服交易中转；CTA 只跳 `/studio/me/membership`，不得下单/打开支付/管理订阅；保持登录与未登录渲染；不得修改 `/studio/me/pricing` 模型价格页 | Workspace settings pricing、共享 PlanCard/BillingCycleToggle | #24 | ☐ |
| **#26** | **#09926** | **旧语义静态清零**：扫描生产/测试/SQL/前端并删除会员语境 `yearly/yearlyPrice/pendingYearly/pending_yearly`、Plan duration/price、`autoRenew/auto_renew/SubscriptionAutoRenewService`、默认月付、`price*3/12/0.8`、订阅 `plusDays(30/365)`；逐条归类，保留其他领域合法 YEARLY/price/plusDays；确认只修改允许的 SQL | service/webui/SQL 全范围静态扫描 | #17、#18、#22、#24、#25 | ☐ |
| **#27** | **#09927** | **补最少高风险自动化测试，不扩张普通映射测试**：参数化 AIGC Guard+三入口；PRO_Q1 兑换/非法 SKU；表驱动提前续费、多次续费、月末、连续升级、payPrice≠snapshot；PostgreSQL 并发 NEW/第二请求409/双成功补偿零副作用；SKU CHECK 与 seed 断言；共享组件/Workspace CTA。开发过程中只编写，不反复执行，统一在 #29 跑 | service `*Test.java`/最少 IT、webui `*.test.tsx` | #17、#18、#21、#25、#26 | ☐ |
| **#28** | **#09928** | **最小必要验收覆盖**：按 requirement v1.0.2 建 AC 矩阵，新增仅覆盖 AIGC 入口一致性、兑换精确 SKU、分段价值四组合、首购并发/双成功补偿、auto_renew 清零、Workspace 只读；复用已有取消/降级/积分退还证据，不复制同义测试 | `*AcceptanceTest.java` / `*.accept.test.ts(x)`、test-report | #27 | ☐ |
| **#29** | **#09929** | **集中最终验证与交付门禁**：重新执行全新库全部 migration+seed+应用启动+CrudResourceCompiler smoke；执行静态扫描；一次性运行 `pnpm check:affected` 与 `pnpm acceptance:affected`，失败则回退对应实现修复后重跑；输出审计矩阵证据。不得 skip/降低断言；不额外重复跑无关全量测试 | 全部 affected 项目、验证报告 | #28 | ☐ |

## 当前完工门禁

- [ ] 人类已明确批准 design.md v1.1.0 的九项高风险决策；批准记录可审计。
- [ ] #16 全新库 migration+seed、应用启动、CrudResourceCompiler 与 Billing 元数据 smoke 全绿；在此之前没有启动下游。
- [ ] SQL diff 只涉及 v5、v12、v101，且没有新增 Flyway 文件。
- [ ] AIGC HTTP、项目/AigcTaskApi、工具与批量入口使用同一动态映射，严格先权益后积分。
- [ ] 会员兑换码唯一绑定明确 SKU，PRO_Q1 兑换得到三个自然月，无默认周期。
- [ ] 成功 NEW/UPGRADE/RENEW 都有服务区间与 SKU 名义快照；升级只抵扣当前世代可用段，旧段不会重复参与。
- [ ] FREE 用户并发首购只有一个 live 结账；第二请求 409；额外成功款为可查询 COMPENSATION_PENDING 且无履约副作用。
- [ ] `auto_renew`、yearly、Plan 价格/时长、硬编码周期/折扣路径静态清零。
- [ ] Workspace pricing 只读目录且 CTA 只跳会员页；`/studio/me/pricing` 未被误改。
- [ ] 最少高风险测试已补，`pnpm check:affected` 全绿。
- [ ] `pnpm acceptance:affected` 全绿且 requirement.md v1.0.2 高风险 AC 有证据。
