---
level: Practice
layer: Product
purpose: 审计 AAF-099 Plan + Subscription SKU 设计的一致性、现状兼容性与开发就绪度
status: published
version: 1.0.0
date: 2026-09-09
author: qa
scope:
  includes:
    - requirement.md、design.md、ui-design.md、tasks.md 一致性
    - 真实 Schema、seed、订阅、支付回调与前端实现抽查
    - 开发就绪度与高风险门禁判定
  excludes:
    - 源码实现与设计修订
    - 人类高风险审批
related:
  - ./requirement.md
  - ./design.md
  - ./ui-design.md
  - ./tasks.md
changelog:
  - 2026-09-09 | v1.0.0：完成独立设计审计并给出质量门禁结论
---

# AAF-099 设计审计报告

## 审计结论

**NEEDS_CHANGES**

| 级别 | 数量 | 门禁 |
|------|------|------|
| blocker | 5 | 必须为 0，当前不满足 |
| major | 5 | 必须 ≤ 2，当前不满足 |
| minor | 2 | 不阻塞，但应修复 |

质量门禁要求为 `blocker = 0 且 major <= 2`。当前存在 5 个 blocker、5 个 major，设计未达到开发就绪状态，不得进入开发。

即使后续修订后达到 PASS，本任务仍属于**破坏性 Schema/API 高风险变更**：原地改写已编号 SQL、删除字段、替换 REST 请求与响应并跨后端/前端，仍须人类明确批准后才能开发。

## 审计范围与证据

文档证据：

- `docs/task/v0.1.1/AAF-099/requirement.md`
- `docs/task/v0.1.1/AAF-099/design.md`
- `docs/task/v0.1.1/AAF-099/ui-design.md`
- `docs/task/v0.1.1/AAF-099/tasks.md`

现状抽查证据：

- `apps/service/aaf-api/src/main/resources/db/migration/v5__order_schema.sql`
- `apps/service/aaf-api/src/main/resources/db/seed/v12__init_seed_data.sql`
- `apps/service/aaf-api/src/main/resources/db/seed/view/v101__billing_entity_def.sql`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/billing/service/SubscriptionService.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/billing/controller/SubscriptionController.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/billing/vo/{SubscribeDTO,DowngradeDTO,AdminSubscriptionDTO,SubscriptionVO,SubscriptionPlanVO}.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/billing/domain/{Subscription,SubscriptionRecord,CreditRedeemCode}.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/billing/repository/{SubscriptionRepository,SubscriptionRecordRepository}.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/billing/scheduler/{SubscriptionExpireScheduler,SubscriptionCreditScheduler}.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/billing/service/{SubscriptionAutoRenewService,CreditRedeemCodeService,EntitlementService}.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/pay/service/{PayOrderService,PayNotifyService}.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/task/controller/AigcTaskController.java`
- `apps/service/aaf-api/src/main/java/com/xuejiai/aaf/module/ai/aigc/task/service/AigcTaskApiAdapter.java`
- `apps/webui/src/lib/api/rest/billing/plans.ts`
- `apps/webui/src/features/billing/components/{PlanCard,BillingCycleToggle,SubscriptionPayDialog}.tsx`
- `apps/webui/src/app/studio/me/membership/page.tsx`

## 重点核验矩阵

| 核验项 | 结论 | 证据与判断 |
|--------|------|------------|
| 不新增 Flyway | ⚠️ 不一致 | `requirement.md`“开发期迁移约束”和 `design.md`“Schema 设计”要求只改 v5/v12；但 `tasks.md #01` 仍要求新增 `v15__membership_completion.sql`，旧完工门禁仍要求 15 项全完成。新 #16 虽写“不新增”，但活动任务指令互相冲突。 |
| 不保留 yearly/price 兼容层 | ✅ 方案方向正确 | `requirement.md`“开发期迁移约束”和 `design.md`“设计结论/破坏性变更”均要求一次性删除 `yearly`、Plan 价格和折扣路径；真实 `SubscriptionService`、DTO、VO、`plans.ts`、`PlanCard` 中旧路径均已被设计识别。仍需静态门禁覆盖所有旧字段和公式。 |
| Plan/SKU DDL 完整性 | ❌ 不完整 | 设计四张主表 DDL 基本闭合，但遗漏会员兑换码 SKU 身份、Billing EntityDef 同步和 FREE/付费 SKU 的数据库不变量，见 B2、B3、M5。 |
| 自然月 | ✅ 一致 | `requirement.md`“商品与价格”规定 1/3/12 个自然月；`design.md`“周期计算”使用 `plusMonths`，并给出月末行为。真实代码目前仍为 `plusDays(30/365)`，已列入替换范围。 |
| FREE_DEFAULT | ⚠️ 部分就绪 | 需求、设计、seed 表和到期规则一致；但核心合法性仅声明为应用层不变量，DDL 允许其他 `PERPETUAL/0/0` SKU 或错误付费 SKU，见 M5。 |
| 支付回调幂等与并发 | ❌ 未闭合 | Record 唯一索引、Record 行锁、PayOrder 原子迁移方向正确；但无 ACTIVE 订阅时没有可锁 Subscription 行，首次购买并发可创建两笔待支付订单，见 B5。 |
| 续费不提前发积分/权益 | ✅ 一致 | `requirement.md`“手动续费”、`design.md`“续费隔离规则”明确禁止 `instantiateQuotas`、升级结算、`earnBatch` 和 RESET/REFILL；#22/#24 有回归覆盖。 |
| 升级算法复用 | ❌ 金额模型错误 | 设计复用剩余时间算法，但 RENEW 更新 `source_id` 且延长 `end_at`、不重置 `start_at`，随后升级会用单周期价格快照除以多周期总时长，见 B4。 |
| 付费 pending 到期不代扣 | ✅ 设计一致 | `requirement.md`“降级与撤销降级”、`design.md`“状态机/到期”及 #19 均要求直接冻结至 FREE；真实调度器当前仍有可选 `autoRenewService.tryAutoCharge`，设计要求删除该分支。 |
| REST 契约一致 | ✅ 文档一致，依赖需修 | requirement/design/ui 对 catalog、subscribe、cancel、downgrade、pending-downgrade 和 current subscription 一致；真实 Controller/DTO 仍是旧契约，属预期改造。#20 未依赖承载 Controller 改造的 #19，见 M3。 |
| AIGC 权益复用 | ❌ 仅展示无准入 | seed 和套餐目录设计复用既有权益表，但真实 AIGC 提交链只做积分预检，任务中没有准入接线，见 B1。 |
| 积分购买隔离 | ✅ 一致 | requirement 的“两条独立购买链路”、design 的订单类型和验收均区分 Subscription SKU 与 `credit_package`；真实 `plans.ts` 也使用独立 API。 |
| 前端动作矩阵 | ✅ 设计充分 | `ui-design.md` 覆盖 NEW/UPGRADE/RENEW/DOWNGRADE/FREE、同 Plan 换周期、取消、pending、错误态和二次确认；真实页面目前 `onSubscribe={() => {}}`，属于待实现现状。Workspace pricing 范围未落到任务，见 M4。 |
| #16 起依赖与验证 | ❌ 不充分 | #16 未在下游开发前执行全新数据库初始化；#20 未依赖 #19；DDL 持久化测试延后到 #22，见 M3。 |
| 删除 auto_renew 的影响 | ⚠️ 口径冲突 | 删除本身可以落地，但必须同步取消逻辑、VO、EntityDef、测试、到期调度和占位服务。需求仍允许“暂时保留”，设计则删除，真实代码同时写字段并保留可选代扣调用，见 M1。 |

## Blocker

### B1 AIGC 权益只有 seed 和展示，没有执行准入接线

**证据**

- `requirement.md`“权益与积分”及 AC“ AIGC 准入不替代积分结算”要求先校验 `aigc_*_access`，再执行积分结算。
- `design.md`“AIGC 权益矩阵”重复声明“先查 BOOLEAN 准入，再走现有积分结算”。
- `tasks.md #16` 只补权益 seed；#17–#24 没有任何 AIGC Controller、Task API、工具目录或统一准入适配任务。
- 真实 `AigcTaskController.submit` 按类型直接调用 `AigcTaskService`。
- 真实 `AigcTaskApiAdapter.submit` 只调用 `creditGuard.precheck`，未调用 `EntitlementChecker`。
- 真实 `v12__init_seed_data.sql` 中 `generateImage`、`generateVideo` 的 `ai_tool_catalog.entitlement_code` 为 `NULL`。

**影响**

新增四个权益只能出现在套餐目录和 quota 中，FREE 用户仍可越过视频、音频、3D 会员准入；核心商业权限 AC 无法实现，且直接影响收费边界。

**修复建议**

- 在设计中确定唯一准入点和按任务类型到权益编码的映射，覆盖 Controller 直提、`AigcTaskApi` 项目执行及工具调用路径。
- 不要只在单个 HTTP Controller 上加固定 `@Entitlement`；统一入口需按 IMAGE/VIDEO/VOICE/MUSIC/MODEL_3D 动态选择编码。
- 增加任务、单测和验收：有权益但积分不足、无权益但积分充足、各入口行为一致。

### B2 会员兑换码仍以 Plan 为商品，无法满足非空 SKU 订阅

**证据**

- 真实 `v5__order_schema.sql` 的 `credit_redeem_code` 仅保存 `plan_id`。
- 真实 `CreditRedeemCode`、`CreditRedeemCodeCreateDTO` 和 `CreditRedeemCodeService.validateGeneration` 只接受 Plan。
- `CreditRedeemCodeService.redeem` 调用 `subscriptionService.activateSubscription(userId, planId, null, false)`。
- `design.md`“Subscription”要求每条订阅 `sku_id NOT NULL`；管理员开通也改为 `skuCode`。
- `design.md` 后端改动清单和 `tasks.md #16–#24` 均未处理会员兑换码。

**影响**

Plan 拆 SKU 后，会员码无法唯一确定月/季/年规格；若强行保留调用，要么编译失败，要么产生无 SKU/错误默认周期的订阅，破坏 Schema 不变量。

**修复建议**

二选一并写入需求：

- 保留会员码：把会员码商品身份改为 `sku_id/skuCode`，同步 v5、domain、DTO/VO、服务、EntityDef 与测试；或
- 本期取消会员码：明确移除 MEMBERSHIP 类型及相关 API/字段，并评估已有运营能力影响。

不得用“默认月付 SKU”隐式兼容 Plan 身份。

### B3 Billing EntityDef 会继续引用被删除字段

**证据**

- `v101__billing_entity_def.sql` 的 `subscription-plan` 仍声明 `durationDays`、`price`、`marketPrice`。
- 同文件 `subscription` 仍声明并展示 `autoRenew`。
- `design.md` 要从 Plan DTO/VO/domain 删除前三个字段，并从 SubscriptionVO 删除 `autoRenew`。
- `design.md` 与 #16 仅列 v5/v12，#17 仅泛指 billing domain/repository/vo/service，没有列出 `v101__billing_entity_def.sql`。
- `CrudResourceCompiler` 会校验 EntityDef 字段与 Controller 输入/输出类型，Billing 资源仍在 `CrudResourceProviderConfiguration` 注册。

**影响**

全新数据库初始化后，管理元数据与 Java 类型不一致，可能在启动编译 EntityDef 时直接失败；即使未失败，管理页也会展示不存在字段。当前“只改 v5/v12”的 SQL 范围与真实元数据依赖冲突。

**修复建议**

- 在 requirement/design 中允许并要求原地更新既有 `db/seed/view/v101__billing_entity_def.sql`，仍不得新增 Flyway 文件。
- 明确 SKU 是否进入通用管理资源；若进入，补 EntityDef/Controller/CRUD；若不进入，说明运营修改 SKU 的边界。
- #16 后立即以全新库启动应用，验证 CrudResourceCompiler 全绿。

### B4 续费后升级的剩余价值算法会错误计价

**证据**

- `design.md`“状态机”规定 RENEW：保留同一 Subscription，`end_at` 顺延，`source_id` 更新为本次续费 Record，`start_at` 不变。
- `design.md`“升级金额”规定：`oldSkuPrice = current.sourceRecord.skuPriceSnapshot`，`totalDays = current.endAt - current.startAt`。
- 一次提前续费后，`sourceRecord` 只代表一个周期价格，但 `start_at` 到 `end_at` 已覆盖两个周期；多次续费偏差进一步放大。

**影响**

续费后再升级会低估未消费价值并多收升级差价，属于真实资金计算错误。现有 AC 只测普通升级和普通续费，无法发现该组合路径。

**修复建议**

- 重新定义可审计的预付价值模型，例如按每条成功购买 Record 保存生效区间并逐段计算未消费价值，或保存当前订阅累计预付名义价值及对应区间。
- 不得仅把 `source_id` 指向最新续费记录后继续使用当前公式。
- 增加“提前续费后升级”“多次续费后升级”“续费跨月末后升级”金额测试。

### B5 首次购买并发没有可锁行，可创建两笔可支付订单

**证据**

- `design.md`“支付回调与幂等/并发控制”要求在锁定 ACTIVE Subscription 后检查 live pending。
- 对“无 ACTIVE 订阅”的 NEW 场景不存在 Subscription 行可锁；真实 `SubscriptionService.subscribe` 也允许该状态。
- `uk_subscription_record_pay_order` 只保证一个 pay order 对应一个 Record，不限制同一用户同时存在两条 WAITING Record。
- `uk_subscription_active_user` 仅约束支付后 ACTIVE Subscription，不能阻止重复下单或重复付款。

**影响**

并发首次购买可生成两笔有效支付单。若两笔都支付，首个回调激活后，第二个回调会遇到状态变化；用户已付款但第二笔无法按 NEW 生效，设计也没有退款/补偿策略。

**修复建议**

- 使用始终存在的用户行锁、按 userId 的数据库 advisory lock，或专用 checkout/idempotency 记录作为统一互斥点；FREE 行不能作为唯一前提。
- 明确同用户重复成功付款的补偿或人工处理策略。
- 增加并发首次 NEW、FREE→付费双击、支付成功与第二次下单交错的集成测试。

## Major

### M1 auto_renew 保留还是删除没有唯一规范结论

**证据**

- `requirement.md`“取消、降级与自动续费边界”写“`auto_renew` 如暂时保留，只是兼容性意图字段”。
- 同文档“开发期迁移约束”又禁止兼容层；完成定义要求旧语义清零。
- `design.md`“范围决策/Subscription”明确删除 `auto_renew` 和 `SubscriptionAutoRenewService`。
- 真实 `SubscriptionService.cancel` 写 `autoRenew=false`；`SubscriptionCrudService` 读取它；`SubscriptionExpireScheduler` 仍可选调用 `autoRenewService.tryAutoCharge`；`v101` 和测试也引用该字段。

**影响**

Developer 可按需求保留，也可按设计删除，取消与到期行为存在双真理。局部删除字段会造成编译、启动或调度偏差。

**修复建议**

本期既无代扣，建议统一为**删除**，但必须在 requirement 中去掉“可暂留”，并把以下内容列为同一原子任务：Schema、domain、VO/CRUD、取消逻辑、EntityDef、调度可选分支、占位接口和相关测试。取消意图只由 `cancelled_at` 表达。

### M2 tasks.md 仍包含与本轮相反的活动任务和旧门禁

**证据**

- `tasks.md #01` 要新增 `v15__membership_completion.sql`。
- 旧“完工门禁”要求 15 项全部完成；#01–#15 均仍为未完成状态。
- 后续 #16 才声明原地修改 v5/v12，且说明“只追加，不替换历史”。
- 真实 migration 中现有 v15 已是 `v15__async_task.sql`，旧任务名也无法安全复用。

**影响**

执行者按文档可能新增 Flyway、重做历史功能或认为旧任务仍阻塞，直接违反本轮硬约束。

**修复建议**

把 #01–#15 明确标记为“历史基线/已实现/不在本轮执行”，删除旧活动门禁或移入历史文档；当前唯一执行范围应为 #16–#24。

### M3 #16 的前置验证和 #20 的依赖关系不足

**证据**

- #16 完成破坏性 DDL/seed 后，#17 立即依赖它，但全新数据库和 DDL 约束测试延后到 #22/#24。
- #20 前端 API 契约依赖 #17/#18，却未依赖 #19；真实 REST Controller 接线和支付回调/lifecycle URL 改造位于 #19。

**影响**

后端模型和前端可在未证明 SQL 可初始化、REST 尚未完成时并行推进，增加返工并使契约测试失真。

**修复建议**

- #16 完成条件增加：全新数据库执行全部 migration/seed、应用启动、v101 编译、关键约束 smoke test、确认无新增 SQL 文件。
- #20 至少依赖完成 REST DTO/Controller 契约的任务；可将 #19 拆为“REST/生命周期”和“回调/调度”，避免前端被无关后端实现阻塞。

### M4 UI 设计纳入 Workspace pricing，但任务未覆盖对应页面

**证据**

- `ui-design.md`“设计结论/Workspace pricing”要求改 Hero、周期、CTA、移除联系客服中转，并复用 PlanCard。
- #21 的涉及范围只列 `app/studio/me/membership/` 与 `features/billing/components/`，没有 Workspace pricing 页面。
- requirement 的主要交易入口只要求 `/studio/me/membership`，未明确 Workspace pricing 改造是本轮 AC。

**影响**

UI 设计范围与任务/需求范围不一致；共享组件切 SKU 后，Workspace pricing 可能编译失败或继续展示错误行为，却没有明确责任任务和验收。

**修复建议**

二选一：把 Workspace pricing 明确加入 requirement、#21/#23 和验收；或从本轮 ui-design 范围移除，只要求共享组件改造后保持现有页面可编译并跳转。

### M5 FREE_DEFAULT 与付费 SKU 关键不变量只靠应用校验

**证据**

- requirement 规定唯一内部 `FREE_DEFAULT=PERPETUAL/0/0`，其他 SKU 必须为付费自然月周期。
- design DDL 的 `ck_subscription_sku_cycle` 允许任意 SKU 使用 `PERPETUAL/0`，价格 CHECK 也允许非 FREE SKU 为 0。
- “FREE_DEFAULT 必须属于 FREE、其他 SKU 必须属于非 FREE 且 price>0”只写为应用层附加不变量。

**影响**

Seed、后台 CRUD 或后续脚本可写出第二个免费永久 SKU或零价付费 SKU，操作分类和购买边界将依赖脆弱约定。

**修复建议**

至少增加 SKU 行内 CHECK：只有 `sku_code='FREE_DEFAULT'` 可使用 `PERPETUAL/0/0`，其余必须为 MONTH/QUARTER/YEAR 且 `price>0`；再由受控服务校验其 Plan 归属。补数据库约束测试。

## Minor

### m1 tasks.md 缺少 Front Matter

`requirement.md`、`design.md`、`ui-design.md` 均有 Front Matter，`tasks.md` 直接以标题开始，不符合任务文档元数据完整性要求。补齐 `level/layer/purpose/status/version/date/author/changelog`。

### m2 ui-design.md 状态早于高风险设计批准

`ui-design.md` 标记 `status: active`，但 requirement/design 仍为 draft，且 `design.md`“开发启动条件”明确尚需人类审核。建议在批准前保持 draft，批准后统一更新状态和审核记录。

## 已确认可复用且无现状冲突的部分

- `PayOrderService.markSuccess` 已使用 `WAITING -> SUCCESS` 原子状态迁移，可作为渠道回调第一层幂等。
- `PayNotifyService` 已有持久通知和失败重试；设计增加业务 Record 唯一键与行锁的方向正确。
- `SubscriptionService.computeUpgradePayable`、`CreditService.settleSubscriptionUpgrade` 可作为普通升级算法与三笔积分结算基线，但需修正“续费后升级”的价值模型。
- `SubscriptionCreditScheduler` 当前按 `last_credit_issued_at` 30 天发放；设计禁止 RENEW 调用发放/重置逻辑，能保持“不提前发积分”。
- `SubscriptionPayDialog` 已集中支付渠道、二维码/跳转和轮询，适合改 SKU 入参，不应复制第二套弹窗。
- `plans.ts` 当前已将积分包购买与订阅购买分为独立 API，设计继续保持该边界。
- `PlanCard`、`BillingCycleToggle` 的旧价格推导和月/年类型均已被设计明确列为一次性替换对象。

## 修订后复审入口条件

复审前至少满足：

- [ ] 关闭 B1：AIGC 四类准入有唯一执行接线、任务和测试。
- [ ] 关闭 B2：会员兑换码切 SKU 或明确移除，Schema/API/测试一致。
- [ ] 关闭 B3：v101 Billing EntityDef 与新类型一致，全新库可启动。
- [ ] 关闭 B4：续费后升级价值模型重新设计并覆盖组合测试。
- [ ] 关闭 B5：无 ACTIVE 用户也有确定的下单互斥与重复付款处理。
- [ ] requirement 与 design 对 `auto_renew` 给出唯一结论。
- [ ] tasks.md 只保留一个活动执行范围，#16 先完成全新库验证，前端依赖 REST 契约完成。
- [ ] Workspace pricing 明确纳入或移出本轮范围。
- [ ] 人类对修订后的破坏性 Schema/API 设计作出明确批准。
