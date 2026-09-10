---
level: Practice
layer: Product
purpose: 定义 AAF-099 开发期 Plan + SKU 多周期订阅合并调整的产品范围、业务规则与验收标准
status: draft
version: 1.0.2
date: 2026-09-09
author: AaronZZH
scope_mode: hold
changelog:
  - 2026-09-09 | v1.0.2：关闭设计审计 B1-B5/M1-M5 的产品歧义，补齐动态准入、兑换码 SKU、并发付款、预付价值、Workspace pricing 与不变量验收
  - 2026-09-09 | v1.0.1：补充实际代码差距、SKU 接口契约与前端订阅管理验收
  - 2026-09-09 | v1.0.0：定义 Plan + SKU 多周期订阅增量调整
related:
  - ../../../design/apps/service/membership-completion.md
  - ../../../design/apps/service/identity-and-membership.md
  - ./tasks.md
  - ./dev-log.md
---

# AAF-099 会员订阅 Plan + SKU 开发期合并调整

任务编号：AAF-099  
scope_mode：hold

## 需求分析（六问）

| # | 问题 | 结论 |
|---|------|------|
| 1 | 需求还是方案？ | 用户同时给出了问题与收口方案。真实需求是消除“套餐档位、价格、周期”混在同一模型导致的硬编码和扩展阻塞；Plan + SKU 是在当前开发期最窄且可直接替换旧模型的解法。 |
| 2 | 真正痛点？ | 当前 `SubscriptionPlan` 同时承载权益与月价，年付由 `price × 12 × 0.8` 推导，订阅和流水又用 `yearly` 布尔值表达周期，无法加入季度商品，也无法可靠识别同商品续费；会员页尚未接入已有支付弹窗，也没有管理入口。继续叠加会把价格、周期、升级和结算规则散落到前后端。 |
| 3 | 隐含前提？ | 项目仍处于开发期，可重建数据库；现有 AAF-099 生命周期行为和支付编排可复用；月/季/年价格由 seed 明确定义；支付渠道尚无代扣签约能力；所有 AIGC 实际提交入口都能在积分处理前识别生成类型并动态校验会员准入；兑换码、管理视图和 Workspace pricing 也必须一次性切换到 SKU 语义。 |
| 4 | 最窄 MVP？ | 原地同步会员 schema、seed 与 Billing EntityDef，将付费 Plan 拆为月/季/年 SKU；订阅、待降级、流水和会员兑换码改为引用明确 SKU；删除全部自动续费语义；开放同 SKU 手动续费；在所有 AIGC 实际提交入口执行动态准入后再走积分；会员页完成交易管理，Workspace pricing 只读展示并跳转会员页。 |
| 5 | 完整愿景成本？ | 若同时建设代扣签约、促销价、试用、跨渠道/App Store SKU、分期、完整退款系统和任意换周期，将引入支付协议、价格版本、账期、对账和商店审核等独立领域，显著扩大数据模型和测试矩阵，不适合本轮开发期收口。异常重复付款仅要求形成可追踪的人工补偿待处理事实。 |
| 6 | 推荐？ | 采用保持范围的 MVP：一次性替换旧定价周期模型并关闭所有已知入口歧义；复用现有生命周期与支付能力，补齐独立 SKU、同 SKU手动续费、分段预付剩余价值、统一 AIGC 动态准入、兑换码 SKU、重复付款人工补偿和两个套餐页面闭环；不扩展自动代扣、促销或完整退款流程。 |

## 设计审计产品决策

| 审计项 | 产品决策 |
|--------|----------|
| B1 AIGC 准入 | 四类 BOOLEAN 权益必须在 Controller、项目/API、工具或其他所有实际提交入口统一生效；每次按生成类型动态映射权益编码，先准入、后积分，不允许只 seed、只展示或只保护单一入口。 |
| B2 会员兑换码 | 保留现有会员兑换码能力；会员兑换码一次性改为明确 `skuCode/sku_id` 商品身份，创建、查询、兑换结果均不得再只指向 Plan，也不得把缺失 SKU 默认成月付。 |
| B3 Billing EntityDef | 允许且要求原地同步既有 `db/seed/view/v101__billing_entity_def.sql`，与新 Plan/SKU、订阅和兑换码语义一致；仍不得新增 Flyway 文件。 |
| B4 续费后升级 | 升级剩余价值按每一段尚未消费的成功预付事实分别计算后求和；提前续费、多次续费或跨月末均不得因单周期快照被摊薄而多收费。 |
| B5 首购并发 | 同一用户同一时刻只允许一个 live 会员结账；第二次请求明确返回冲突。极端情况下不同支付单均成功时，只允许一次激活，后到成功款必须登记为“人工补偿待处理”，禁止静默吞款或重复激活。 |
| M1 `auto_renew` | 本期全部删除字段及其对外语义、管理能力和代扣分支；取消意图唯一由 `cancelled_at` 表达，不保留兼容字段。 |
| M2 活动范围 | 本需求唯一有效范围是不新增 Flyway、原地完成 Plan + SKU 收口；任何要求新增会员 Flyway 或保留旧 Plan/周期模型的历史任务均不构成本期产品要求。 |
| M3 前置验证 | 本期交付必须证明全新数据库可完整初始化、会员管理元数据可用、接口契约一致后，前端闭环才算完成；不能用后续任务代替本期完成条件。 |
| M4 Workspace pricing | 纳入本期：只读展示与会员目录一致的 Plan/SKU，并接线跳转 `/studio/me/membership`；该页不直接交易，共享组件改造后必须保持可编译。 |
| M5 SKU 不变量 | `FREE_DEFAULT` 是唯一免费永久内部 SKU；所有其他 SKU 必须属于付费 Plan、使用月/季/年周期且价格大于零，不能被目录、兑换码、后台操作或脚本绕过。 |

## 背景与目标

AAF-099 已实现升级补差价、降级排队、撤销降级、取消、到期提醒、到期冻结、升级积分结算与 AIGC 失败退还等订阅基础能力。本轮不是重新设计订阅生命周期，而是在开发期把当前 `price + yearly boolean + hardcoded 8 折` 模型收口为 Plan + SKU，并让已有能力在月、季、年规格下使用同一套商品语义。

目标如下：

- Plan 只表达会员档位和权益，即“买到什么”；SKU 表达可售周期和价格，即“怎么买”。
- 月、季、年均为独立 SKU，价格和有效期从 SKU 读取，任何端不得按月价推导。
- 支持有效付费订阅对当前同一 SKU 手动续费。
- 在现有 `billing_entitlement_def` / `billing_plan_entitlement` 中补齐 AIGC 能力准入权益，并在所有实际提交入口按生成类型动态执行统一准入；只有准入通过后才进入积分检查与结算。
- 保留会员兑换码，但会员商品身份从 Plan 一次性替换为明确 SKU，不提供默认月付兼容。
- `/studio/me/membership` 复用已有 `SubscriptionPayDialog` 和现有支付订单编排，完成套餐购买与订阅管理闭环。
- Workspace pricing 提供与会员目录一致的只读套餐展示，并统一跳转会员页交易；该页不直接创建订单。
- 首次购买和重复点击具有确定业务结果：同用户只存在一个 live 会员结账；重复成功付款只激活一次，其余款项进入可追踪的人工补偿待处理状态。
- 升级抵扣覆盖提前续费和多次续费形成的全部未消费预付价值，用户不得因续费后升级被多收费。
- 保持积分购买与会员订阅为两条独立商品、订单和结算链路。

## 现有能力基线

以下能力以现有 AAF-099 实现为基线，本轮优先复用并切换到 Plan/SKU 数据；仅对审计已确认不正确的续费后升级计价、AIGC 准入、自动续费残留与并发付款边界按本需求修正，不建立平行流程：

- `SubscriptionService.upgrade` 的立即生效与普通升级流程继续复用；剩余价值统一改为累计尚未消费的分段预付事实。
- `CreditService.settleSubscriptionUpgrade` 的升级积分 `EXPIRE / EARN / SPEND` 三笔结算。
- `SubscriptionService.downgrade` 的到期降级排队。
- `SubscriptionService.cancelPending` 的撤销降级。
- `SubscriptionService.cancel` 的取消后保留当前周期权益、不退款。
- `SubscriptionExpireScheduler` 的到期 `EXPIRED + FREE` 冻结兜底。
- `SubscriptionExpiryReminderScheduler` 的到期提醒。
- `BizOrderService`、`PayOrderService` 和现有支付回调的订单编排。
- 前端 `SubscriptionPayDialog` 的支付渠道选择、支付结果处理和查询刷新。

## 实际代码差距

| 位置 | 当前实现 | 本轮要求 |
|------|---------|---------|
| `v5__order_schema.sql` | `billing_subscription_plan` 保存 `duration_days/price/market_price`；`subscription_record` 保存 `yearly`；订阅保存 `pending_plan_id/pending_yearly` | 原地拆出 Subscription SKU，让订阅、pending 与购买流水都引用 SKU；不新增 Flyway 文件 |
| `SubscriptionPlan` / `SubscriptionService` | Plan 同时承载权益与单价；`YEARLY_DISCOUNT=0.8`、`YEARLY_DAYS=365`；升降级按周期总价比较 | Plan 只承载档位与权益；价格和周期只读 SKU；升降级只按 Plan 档位判断 |
| `SubscribeDTO` / `DowngradeDTO` | 请求以 `planCode + billingCycle` 输入，并转换为 `boolean yearly` | 请求以唯一 `skuCode` 输入，不再接受或推导 `yearly` |
| `SubscriptionController` | 取消、降级、撤销降级接口已经存在 | 复用这些生命周期入口并切换 SKU 入参；不得另建平行取消或降级流程 |
| `plans.ts` | 套餐类型暴露 `price/yearlyPrice`，仅有购买与查询；当前订阅类型未暴露取消和 pending 管理字段 | 返回 SKU 列表并补取消、降级、撤销降级 mutation；当前订阅返回当前 SKU 与 pending SKU |
| `BillingCycleToggle` / `PlanCard` | 只有月/年；前端使用 `yearlyPrice`、`price×12` 和折扣展示 | 增加季度；价格完全取选中 SKU，不做周期或折扣计算 |
| `/studio/me/membership` | `PlanCard.onSubscribe` 为空操作，无订阅管理区 | 复用 `SubscriptionPayDialog` 接通新购/升级/续费，并接通取消、降级、撤销降级 |
| `SubscriptionPayDialog` | 已具备渠道选择、支付状态处理及查询刷新，入参仍为 Plan + billingCycle | 保留并改为 SKU 入参；不得复制第二套支付弹窗或轮询逻辑 |
| AIGC 实际提交入口 | 当前部分入口只做积分预检，四类 BOOLEAN 权益尚未统一执行 | 所有图像、视频、音频与 3D 实际提交入口均按类型动态准入，准入通过后才走积分 |
| 会员兑换码 | 会员商品只引用 Plan，兑换时无法唯一确定周期 SKU | 保留能力并一次性改为明确 `skuCode/sku_id`，禁止默认月付 |
| `v101__billing_entity_def.sql` | 仍引用 Plan 价格/周期和 Subscription `autoRenew` | 原地同步新 Plan/SKU、Subscription 与兑换码语义，确保管理资源可用 |
| 自动续费语义 | Schema、VO、CRUD、调度及占位服务仍可能引用 `auto_renew` | 本期全部删除；取消意图仅由 `cancelled_at` 表达，任何调度均不得代扣 |
| 首次会员结账 | 无有效付费订阅时重复点击/并发可形成多笔可支付订单 | 同用户只允许一个 live 会员结账；第二次请求冲突，重复成功付款进入人工补偿待处理且只激活一次 |
| 续费后升级 | 单周期价格快照无法代表提前或多次续费形成的全部未消费价值 | 按尚未消费的每段预付事实累计剩余价值，升级不得多收费 |
| Workspace pricing | 共享套餐组件可能仍使用旧价格和交易动作 | 本期改为目录只读展示与会员页跳转，不在该页交易且必须保持可编译 |

## 接口契约

- 套餐目录继续使用 `GET /api/billing/subscription-plans/catalog`，每个 Plan 返回 `skus[]` 与 `entitlements[]`；SKU 至少包含 `skuCode`、`billingCycle`、`cycleMonths`、`price`、`marketPrice`、`status`，不再返回 `yearlyPrice`。
- 新购、跨 Plan 升级和同 SKU 手动续费统一使用 `POST /api/billing/subscriptions/subscribe`，请求体为 `{ skuCode, channelCode }`；服务端依据当前订阅与目标 SKU 唯一判定 `NEW / UPGRADE / RENEW`，前端不得提交操作类型。
- 取消复用 `POST /api/billing/subscriptions/me/cancel`，无请求体；不得新增“关闭自动扣款”接口或文案。
- 降级复用 `POST /api/billing/subscriptions/me/downgrade`，请求体为 `{ skuCode }`；该 SKU 是唯一 pending 目标，不再提交 `planCode + billingCycle`。
- 撤销降级复用 `DELETE /api/billing/subscriptions/me/pending-downgrade`，不创建订单、不修改当前 SKU。
- 当前订阅继续使用 `GET /api/billing/subscriptions/me`，至少返回当前 `planCode/skuCode/billingCycle`、起止时间、状态、`cancelledAt` 与 pending SKU 摘要，供页面决定可用动作。
- 所有支付成功处理继续以 `pay_order_id` 幂等；接口返回支付单后复用现有支付结果流程，不增加独立续费支付协议。
- 同一用户已有 live 会员结账时，再次提交新购/升级/续费请求必须返回明确冲突，不得创建第二个可支付会员订单。
- 会员兑换码的创建、展示与兑换必须使用唯一 `skuCode/sku_id`；只提交 Plan 或缺失 SKU 的请求必须拒绝，不得默认选择月付 SKU。
- AIGC 提交的所有对外与内部入口必须返回一致的准入结果：先按实际生成类型校验对应 BOOLEAN 权益，再进入积分预检和扣减；无权益时即使积分充足也不得提交。
- 重复成功付款的后到回调必须返回已记录人工补偿待处理的可追踪结果，不得重复激活、重复续期或静默按成功消费处理。

## 范围

- 开发期原地调整会员订阅 schema、seed 与既有 Billing EntityDef，不增加新的 Flyway SQL 文件。
- Plan + SKU 多周期商品模型，付费 Plan 提供月、季、年三个独立 SKU。
- 新购、跨 Plan 升级、跨 Plan 降级排队、撤销降级、取消和同 SKU 手动续费。
- 套餐目录返回 Plan、其启用 SKU 列表及关联权益，不再返回后端计算的 `yearlyPrice`。
- 订阅、待降级目标、订阅购买流水和会员兑换码保存明确 SKU 身份，不再用 Plan 默认值或 `yearly` 布尔值推导商品。
- 删除 `auto_renew` 字段及其 VO、CRUD、调度代扣分支、`SubscriptionAutoRenewService` 和对外语义；取消意图只由 `cancelled_at` 表达。
- 在现有权益定义和套餐权益关联中增加 AIGC 能力准入 seed，并让四类权益在所有实际提交入口统一动态执行，然后才进入积分流程。
- 会员页展示月/季/年 SKU，接入已有支付弹窗并增加订阅管理区。
- Workspace pricing 同步为只读会员目录展示与 `/studio/me/membership` 跳转接线，不在该页直接交易。
- 首次购买重复点击/并发冲突、重复成功付款异常记录与“人工补偿待处理”闭环；仅限形成可审计、可人工处理的业务事实。
- 提前续费、多次续费后升级时，累计抵扣所有尚未消费的分段预付价值。
- 修正 `member.faq` 中“自动续费”“只有月/年”等与本轮规则冲突的文案。

## 非范围

- 自动续费签约、渠道代扣、自动创建续费订单、扣款失败重试。
- 促销活动、优惠券、限时价、首购价、价格叠加规则。
- 免费试用、试用转付费。
- App Store、Google Play 或其他应用商店 SKU。
- 分期付款、账期付款。
- 常规退款规则或退款流程改造；异常重复付款的人工补偿待处理记录不扩展为自动退款、退款审批、退款执行或对账系统。
- 同一 Plan 内从一个周期 SKU 切换到另一个周期 SKU；本轮只支持“同 SKU 续费”和“跨 Plan 升/降级”。
- 重写 AAF-099 已有降级、取消、冻结、提醒或积分结算算法；升级剩余价值仅修正续费后组合场景的业务口径。
- 在 Workspace pricing 直接下单、唤起支付或管理订阅；交易与管理仍统一在 `/studio/me/membership`。

## 用户故事

- 作为首次购买会员的用户，我希望在同一套餐下选择月、季或年 SKU 并完成支付，以便按适合自己的周期订阅。
- 作为当前会员，我希望对当前同一 SKU 主动续费，以便在不依赖自动代扣的情况下延长到期时间。
- 作为当前会员，我希望继续升级、预约降级、撤销降级或取消订阅，以便管理下一个订阅阶段。
- 作为 AIGC 用户，我希望四类生成能力在所有提交方式下都执行相同的会员准入并在通过后再结算积分，以便权限与收费结果一致。
- 作为兑换码用户，我希望会员兑换码明确对应具体周期 SKU，以便兑换结果可预期且不会被默认成月付。
- 作为付款用户，我希望重复点击或并发购买被明确阻止；若不同订单意外都付款成功，额外款项可追踪并进入人工补偿，而不是被静默吞掉或重复激活。
- 作为提前续费的会员，我希望升级时抵扣每一段尚未消费的预付价值，以便不会因续费次数增加而多付款。
- 作为 Workspace 用户，我希望在 pricing 页只读比较真实套餐价格并跳转统一会员页交易，以便不同入口信息一致。
- 作为运营人员，我希望每个周期价格都是明确 SKU 数据，以便调整价格时无需修改折扣代码。

## 用户旅程

### 新购

1. 用户进入 `/studio/me/membership`，选择月、季或年周期。
2. 页面按所选周期展示每个 Plan 对应的启用 SKU 价格；FREE 始终只展示一次。
3. 用户选择付费 SKU，页面将 `skuCode`、SKU 名称和 SKU 实付金额传入已有 `SubscriptionPayDialog`。
4. 弹窗沿用现有微信、支付宝、Mock 或联系客服入口创建 `SUBSCRIPTION` 业务订单。
5. 仅在支付成功回调后激活所选 Plan/SKU，刷新当前订阅、权益和积分；失败、取消或关闭订单不激活。
6. 同一用户已有 live 会员结账时，第二次新购、升级或续费请求立即得到冲突结果，不再生成可支付订单。
7. 若极端情况下不同支付单均成功，只允许最先满足条件的一笔激活；后到成功款登记为“人工补偿待处理”，关联用户、订单、金额与原因，供运营人工退款或补偿，不能静默结束或重复激活。

### 跨 Plan 升级

1. 有效付费用户选择更高档 Plan 的任一启用 SKU。
2. 系统汇总当前时刻以后每一段尚未消费的成功预付事实，分别计算其剩余价值并求和；目标价格取目标 SKU 明示价格，升级应付金额不得因提前续费或多次续费而高于正确差额。
3. 已消费区间不参与抵扣；完全未开始的续费段保留全部预付价值，部分消费段只抵扣未消费部分，同一预付事实不得重复抵扣。
4. 支付成功后目标 Plan/SKU 立即生效，目标周期从升级成功时间重新计算；已用于抵扣的旧预付区间不得再次产生权益或后续抵扣。
5. 升级积分仍执行现有三笔结算，不增加第二套清算逻辑。

### 同 SKU 手动续费

1. 有效付费用户在当前订阅管理区点击“续费当前套餐”。
2. 支付弹窗固定使用当前 `skuCode` 和当前 SKU 价格，不提供换周期。
3. 支付成功后，当前订阅 `end_at` 从原 `end_at` 按该 SKU 周期顺延；不从支付时间重置。
4. 写入 `RENEW` 购买流水并清除已存在的取消标记；不触发升级补差价和升级三笔积分结算。
5. 续费时不提前重复发放月度订阅积分，也不重置当前权益用量；既有月度积分发放和权益重置调度按原节奏继续。

### 降级与撤销降级

1. 用户选择更低档 Plan 的目标 SKU，系统将目标保存为 pending，不立即收费、不改变当前权益。
2. 页面展示目标 Plan、目标 SKU 周期和当前周期结束时间，并提供“撤销降级”。
3. 撤销后清除 pending 目标，当前订阅不变。
4. 因本期没有自动代扣，目标为 FREE 时到期直接切换 FREE；目标为付费 SKU 时不得自动收费，未完成目标 SKU 手动购买则按既有冻结逻辑回到 FREE。

### 会员兑换码

1. 运营创建会员兑换码时必须选择一个明确的可兑换 SKU，兑换码展示其 Plan 与周期。
2. 用户兑换时只获得该 `skuCode/sku_id` 对应的会员规格，不允许再由 Plan 推导周期。
3. 历史或非法数据若只有 Plan、没有 SKU，兑换必须明确失败并提示运营修正，不得默认月付。
4. `FREE_DEFAULT` 不作为付费会员兑换码商品。

### Workspace pricing

1. 用户在 Workspace pricing 查看与会员目录同源的 Plan、启用 SKU、周期、价格和权益摘要。
2. 用户选择套餐 CTA 后跳转 `/studio/me/membership` 完成购买或管理；Workspace pricing 本身不创建订单、不唤起支付、不执行取消或降级。
3. 无论用户是否登录，该页均不得因共享套餐组件切换到 SKU 语义而出现编译或运行断裂。

### 取消与到期

1. 用户在管理区确认取消；系统只记录本周期结束后不继续保持付费套餐的意图，不退款、不提前撤销权益。
2. 页面明确显示权益可使用至 `end_at`，不得声称已关闭某个渠道自动代扣协议。
3. 用户在到期前仍可对同 SKU 手动续费；支付成功后清除取消标记并顺延到期时间。
4. 到期仍未完成手动续费时，旧订阅变为 `EXPIRED` 并激活 FREE 兜底。

## 业务规则

### 商品与价格

- 一个 Plan 可以关联多个 SKU；同一 Plan 在本轮最多各有一个 `MONTH`、`QUARTER`、`YEAR` 启用 SKU。
- SKU 的 `sku_code` 全局唯一。建议稳定编码为 `{PLAN_CODE}_M1`、`{PLAN_CODE}_Q1`、`{PLAN_CODE}_Y1`，例如 `PRO_Q1`。
- 月、季、年周期分别为 1、3、12 个自然月；有效期按日历月顺延，不用固定 30/90/365 天推导。
- `price`、`market_price` 均属于 SKU。价格单位继续为分；`market_price` 仅作为静态对比价，不构成促销活动或动态优惠。
- 前后端不得再包含 `YEARLY_DISCOUNT`、`price × 12 × 0.8` 或 `price × 3` 等价格推导。
- 已禁用 SKU 可保留历史引用，但不出现在可售目录，也不可创建新支付订单。
- FREE 是 Plan，并固定关联不可支付的内部 SKU `FREE_DEFAULT`：`billing_cycle=PERPETUAL`、`cycle_months=0`、`price=0`；该 SKU 仅用于注册初始化、降级到 FREE 和到期冻结，不进入可支付 SKU 或会员兑换码列表。
- `FREE_DEFAULT` 是全系统唯一允许 `PERPETUAL / 0 / 0` 的 SKU，且只能属于 FREE Plan；任何其他 SKU 都必须属于非 FREE Plan，周期为 `MONTH / QUARTER / YEAR`、`cycle_months` 为 `1 / 3 / 12` 且 `price>0`。目录、交易、兑换码、后台管理与初始化数据均不得绕过该不变量。
- 不符合上述不变量的 SKU 不可创建、启用、展示、购买、兑换或激活订阅；已有非法数据不得通过默认值自动修复成月付。

### 操作判定

- 当前无有效付费订阅或当前为 FREE，购买付费 SKU 判定为 `NEW`。
- 目标 `sku_id` 等于当前 `sku_id`，判定为 `RENEW`。
- 目标 Plan 排序/等级高于当前 Plan，判定为 `UPGRADE`，与目标 SKU 周期无关。
- 目标 Plan 排序/等级低于当前 Plan，判定为 `DOWNGRADE`，写入 pending 目标。
- 目标 Plan 与当前 Plan 相同但 SKU 不同，返回明确“不支持套餐内换周期”错误，不伪装成升级、降级或续费。
- 不以不同周期的总价直接比较会员等级；Plan 档位是升降级判定的唯一依据。

### 会员结账互斥与异常付款

- live 会员结账指已创建、仍允许用户完成支付且尚未终结的新购、升级或续费结账；同一用户同一时刻最多一个。
- 用户重复点击、并发请求或在一个 live 结账未终结时发起另一会员交易，第二次请求必须返回明确冲突，并指示继续处理现有结账；不得再创建可支付会员订单。
- 同一支付单回调重放继续按幂等处理，不产生第二个业务结果。
- 极端情况下不同支付单均成功时，只有第一笔合资格成功款可以激活、升级或续期；后到成功款不得重复改变订阅、权益或积分。
- 后到成功款必须登记为“人工补偿待处理”，至少可追踪用户、业务订单、支付订单、实付金额、发现时间、冲突原因和处理状态，并进入运营可处理清单。
- 本期只建立异常事实与人工处理状态，不实现自动退款、退款审批、补偿执行或完整退款流程；人工完成处理后可记录处理结果，不得把未处理款项伪装为已消费或静默成功。

### 升级剩余价值

- 升级时的旧订阅剩余价值等于当前时刻以后所有尚未消费预付分段的剩余价值之和，不得只取最近一次续费或单个 SKU 价格快照。
- 每次成功新购、续费形成可审计的预付事实及其适用区间；完全未开始的区间按该段实付价值全额计入，正在消费的区间按未消费比例计入，已消费部分不计入。
- 提前续费、多次续费、跨月末或不同自然月天数下，所有未消费分段都必须且只能计入一次；升级应付金额最低为零，不产生本期退款。
- 升级成功后，已用于抵扣的旧预付分段不得再次用于后续升级抵扣或继续发放旧 Plan 权益。

### 手动续费

- 只有状态为 `ACTIVE`、具有付费 SKU 且目标为当前同一 SKU 的订阅可续费。
- 续费订单未支付、支付失败或关闭时，不修改 `end_at`、取消标记、权益、积分或订阅状态。
- 支付成功后只顺延 `end_at`、记录 `RENEW` 流水并清除 `cancelled_at`；同一支付订单重复回调不得重复顺延。
- 续费不执行升级补差价，不执行升级三笔积分结算，不立即实例化一套新权益额度，不提前发放下一批月度订阅积分。

### 取消、降级与自动续费边界

- 取消继续遵循现有业务结果：记录 `cancelled_at`，保留当前周期、无退款、到期冻结；`cancelled_at` 是唯一取消意图，续费成功时清除。
- 本期彻底移除 `auto_renew` 字段、对外字段、管理编辑项、CRUD 语义、调度代扣分支和 `SubscriptionAutoRenewService`；不得保留兼容字段、占位服务或“关闭自动续费”动作。
- 降级继续遵循现有排队规则：当前周期不付费、不换权益；pending 目标改为 SKU 语义。
- 本期任何定时任务不得主动创建支付订单或调用渠道扣款。
- 页面和 FAQ 必须使用“手动续费”“取消后到期不再保持付费套餐”等准确文案，不得承诺自动续费或暗示存在代扣签约。

### 权益与积分

- Plan 继续通过现有 `billing_plan_entitlement` 关联权益；SKU 不重复配置权益，同一 Plan 的月/季/年 SKU 获得相同权益。
- AIGC 能力准入使用现有 BOOLEAN 权益：图像映射 `aigc_image_access`，视频映射 `aigc_video_access`，语音/音乐等音频生成统一映射 `aigc_audio_access`，3D 模型映射 `aigc_model3d_access`；BOOLEAN 的 `quota=1` 表示可用，未关联或 `quota=0` 表示不可用，`reset_cycle=NONE`。
- 上述映射必须在所有实际提交入口统一动态执行，包括直接 HTTP 提交、项目/API 执行、工具调用以及后续接入同一生成能力的入口；不得仅保护某个 Controller、仅依赖工具目录声明或仅在页面隐藏按钮。
- 每次提交必须先识别实际生成类型并完成 BOOLEAN 准入，再进入现有积分预检、预占、扣减与失败退还流程；无权益但积分充足仍拒绝，有权益但积分不足仍由积分规则拒绝。
- 初始 AIGC 权益矩阵：FREE 仅开放图像；PRO 开放图像、视频、音频；TEAM 与 ENTERPRISE 开放图像、视频、音频、3D。目录展示、当前权益和实际准入必须使用同一权益事实。
- 不可识别类型或未建立权益映射的 AIGC 提交不得默认放行。
- 套餐月度积分继续保留为 Plan 层的会员附加权益，由现有调度按 30 天节奏发放；季度和年度 SKU 不在购买时一次性提前发放 3/12 个月积分。
- 权益额度不足只能通过升级 Plan 解决；不得用积分充值直接增加 `plan_entitlement` 配额。

### 两条独立购买链路

| 维度 | 会员订阅 | 积分购买 |
|------|----------|----------|
| 商品 | Plan + Subscription SKU | `credit_package` |
| 业务订单类型 | `SUBSCRIPTION` | `CREDIT_PACKAGE` / `RECHARGE` / 既有积分购买类型 |
| 入口 | `/studio/me/membership` | 积分详情/充值入口 |
| 支付成功结果 | 激活、升级或续费会员订阅，并按会员规则产生权益 | 增加充值积分批次，不创建或改变会员订阅 |
| 有效期 | 由 SKU 周期决定 | 继续使用充值积分 2 年有效规则 |
| 关系 | 套餐可按既有规则赠送月度订阅积分，但这不是购买积分包 | 购买积分不授予 Plan、SKU 或会员权益 |

### 会员兑换码

- 保留现有会员兑换码的创建、管理、校验与兑换能力，不取消 MEMBERSHIP 类型。
- 会员兑换码必须唯一关联明确的付费 `sku_id`，对外以 `skuCode` 表达；Plan 只作为该 SKU 的派生展示信息，不再作为兑换商品身份。
- 创建会员兑换码时未选择 SKU、选择 `FREE_DEFAULT`、禁用 SKU 或非法 SKU 必须拒绝。
- 兑换时必须按兑换码保存的 SKU 激活相应 Plan 与自然月周期；缺失 SKU 的旧数据或请求必须明确失败，不得选择 Plan 的月付 SKU、首个 SKU 或任何默认 SKU 兼容。
- 同一兑换事实仍遵循现有兑换幂等和使用次数规则；本轮不改变兑换码营销、发放或核销策略。

## 数据语义

### 概念登记表

| 产品概念 | 数据实体 | 主表 | 语义 |
|---------|---------|------|------|
| 会员档位 | Plan | `billing_subscription_plan` | 稳定描述名称、等级、展示配置、月度会员积分和权益集合，不承载付费周期价格。 |
| 可售规格 | Subscription SKU | `billing_subscription_plan_sku` | 描述一个 Plan 如何购买，包括唯一编码、周期、周期数、价格、状态和排序。 |
| 当前订阅 | Subscription | `billing_subscription` | 描述用户当前生效的 Plan/SKU、起止时间、状态、取消意图及 pending 目标。 |
| 购买流水 | Subscription Record | `subscription_record` | 描述 `NEW / UPGRADE / RENEW` 等成功预付事实、适用区间、SKU、支付订单、支付金额和支付状态；作为升级剩余价值的分段依据。 |
| 会员兑换码 | Membership Redeem Code | `credit_redeem_code` | 描述可兑换的唯一会员 SKU；Plan 仅由 SKU 派生，不再独立决定兑换结果。 |
| 异常重复付款 | Manual Compensation Case | 复用现有可落地的订单异常/人工处理记录 | 描述额外成功款的用户、订单、金额、原因与“人工补偿待处理/已处理”状态；不代表完整退款系统。 |
| 权益定义 | Entitlement Definition | `billing_entitlement_def` | 定义稳定权益编码、类型和单位，包括新增 AIGC 能力准入。 |
| 套餐权益 | Plan Entitlement | `billing_plan_entitlement` | 定义 Plan 与权益的额度、重置周期和补充规则；SKU 不重复一份。 |
| 积分商品 | Credit Package | `credit_package` | 独立充值商品，不是 Subscription SKU。 |

### Plan

- 保留稳定编码 `FREE`、`PRO`、`TEAM`、`ENTERPRISE`。
- 承载 `name`、`status`、`sort`、`monthly_credits`、`ext` 及权益关系。
- 不再以 Plan 的 `price`、`market_price`、`duration_days` 表达付费规格。

### Subscription SKU

- 必需语义：`sku_code`、`plan_id`、`billing_cycle`、`cycle_months`、`price`、`market_price`、`status`、`sort`、`ext`。
- 可售周期为 `MONTH / QUARTER / YEAR`，`cycle_months` 分别为 `1 / 3 / 12`；有效期按自然月顺延。`PERPETUAL` 只供 `FREE_DEFAULT` 内部 SKU 使用，其 `cycle_months=0`。
- SKU 一经被订阅或流水引用，只能禁用，不得改变其所属 Plan 或复用编码表达另一商品。
- 首次 seed 使用下表静态金额，单位均为分；年度实付价保持旧模型结果的价格连续性，但这些值是 SKU 数据，不是运行时折扣公式。

| SKU | Plan | 周期 | `price` | `market_price` | 可支付 |
|-----|------|------|---------|----------------|--------|
| `FREE_DEFAULT` | FREE | PERPETUAL | 0 | 0 | 否 |
| `PRO_M1` | PRO | MONTH | 2900 | 3900 | 是 |
| `PRO_Q1` | PRO | QUARTER | 8200 | 11700 | 是 |
| `PRO_Y1` | PRO | YEAR | 27840 | 46800 | 是 |
| `TEAM_M1` | TEAM | MONTH | 29900 | 39900 | 是 |
| `TEAM_Q1` | TEAM | QUARTER | 85000 | 119700 | 是 |
| `TEAM_Y1` | TEAM | YEAR | 287040 | 478800 | 是 |
| `ENTERPRISE_M1` | ENTERPRISE | MONTH | 300000 | 360000 | 是 |
| `ENTERPRISE_Q1` | ENTERPRISE | QUARTER | 850000 | 1080000 | 是 |
| `ENTERPRISE_Y1` | ENTERPRISE | YEAR | 2880000 | 4320000 | 是 |

### Subscription

- 当前订阅必须同时保存 `plan_id` 和非空 `sku_id`；FREE 订阅引用 `FREE_DEFAULT`。
- 旧 `pending_plan_id + pending_yearly` 一次性替换为可唯一确定目标商品的 `pending_sku_id`；降级到 FREE 同样保存 `FREE_DEFAULT`，不保留第二套 pending Plan 或周期字段。
- `end_at` 由目标 SKU 周期计算；`FREE_DEFAULT.end_at` 为空，同 SKU 续费从当前 `end_at` 顺延，升级从支付成功时间开始新周期。
- 订阅不再包含 `auto_renew`；`cancelled_at` 非空表示用户已表达当前付费周期结束后不再保持付费套餐，续费成功后清空。任何页面、接口、管理能力或调度不得从其他字段推导取消或代扣意图。
- 当前订阅关联的成功购买流水必须足以区分每段预付适用区间与实付价值，供续费后升级累计未消费价值；不得用最新一条流水覆盖此前仍未消费的预付事实。

### Subscription Record

- 流水保存 `sku_id`，并保留实际支付金额；不再保存或读取 `yearly` 布尔值。FREE 的系统激活不产生支付购买流水。
- `operation` 至少可区分 `NEW`、`UPGRADE`、`RENEW`；降级排队不产生已支付购买流水。
- 每条成功新购或续费事实都必须可审计地对应其预付适用区间与实付价值；多次续费不得互相覆盖，升级时按尚未消费的各段汇总。
- `pay_order_id` 必须保证同一支付成功处理幂等，避免回调重放导致重复激活或重复顺延。
- 不同支付单的成功事实不得因幂等规则被混同；发生额外成功款时必须保留支付事实并关联“人工补偿待处理”，但不得生成第二次订阅生效结果。

### Entitlement

- 新增 AIGC 权益通过 `billing_entitlement_def` 和 `billing_plan_entitlement` seed 表达，不建立第二套套餐权益来源。
- AIGC 权益是“是否可调用某类生成能力”的会员门槛，不替代积分计费；所有实际提交入口先按生成类型动态校验对应权益，再按现有积分规则结算。
- 套餐目录、当前权益和实际提交准入读取同一权益事实；前端不得维护平行权益清单，后端入口不得以未配置映射为由默认放行。

## 前端交互要求

- 改造页面为 `/studio/me/membership`，不得误改模型收费标准页 `/studio/me/pricing`。
- `BillingCycleToggle` 提供“月付 / 季付 / 年付”；FREE 卡片不随周期重复出现。
- `PlanCard` 只显示当前周期对应 SKU 的后端价格，不计算季度价、年度价或折扣。
- `PlanCard.onSubscribe` 接入现有 `SubscriptionPayDialog`；允许调整其入参为 `skuCode`，但不得新建第二套支付弹窗或支付状态轮询。
- 当前订阅管理区至少显示：当前 Plan、当前 SKU 周期、状态、`start_at`、`end_at`、取消状态、pending 目标。
- 可用动作按状态显示：新购/升级、续费当前 SKU、取消、选择低档 Plan 的 SKU 降级、撤销降级。
- 同 Plan 其他周期 SKU 对有效会员不可点击，展示“本期暂不支持换周期”；失效 SKU 不展示购买按钮。
- 取消和降级操作需要二次确认，确认文案明确生效时点、是否收费和到期行为。
- 支付成功继续刷新当前订阅、权益和积分查询；支付失败保留当前页面和订阅状态，并显示可重试反馈。
- Workspace pricing 使用与会员页一致的目录数据展示 Plan、月/季/年 SKU、价格和权益摘要；不得复制或推导价格。
- Workspace pricing 的套餐 CTA 只跳转 `/studio/me/membership`，不得在该页直接创建订单、打开支付弹窗或执行订阅管理动作。
- 共享套餐组件切换为 SKU 语义后，Workspace pricing 必须保持类型、构建和运行可用；无可售 SKU 时提供明确的不可售状态而非编译失败。

## Seed 要求

- 直接修改 `v12__init_seed_data.sql` 的会员套餐、套餐 SKU、权益定义、套餐权益和 `member.faq` seed，并原地同步 `db/seed/view/v101__billing_entity_def.sql` 的 Billing 管理元数据。
- 保留 FREE、PRO、TEAM、ENTERPRISE 四个 Plan；增加不可支付的 `FREE_DEFAULT` 内部 SKU，并为三个付费 Plan 各增加月、季、年独立 SKU，价格按数据语义中的静态 seed 表落库。
- 增加四个 AIGC BOOLEAN 权益编码及上述初始矩阵，复用现有通用权益和 `billing_plan_entitlement` 关联方式。
- Billing EntityDef 不得继续暴露 Plan 的价格/周期旧字段或 Subscription `autoRenew`；会员兑换码管理必须选择并展示明确 SKU，不得只选择 Plan。
- `member.faq` 必须说明月/季/年、手动续费、取消后权益保留至到期、到期未续费回到 FREE，以及积分购买与订阅独立；删除“订阅会自动续费”“按年付费即升级”等不准确承诺。
- Seed 必须满足 `FREE_DEFAULT` 唯一免费永久 SKU、其余 SKU 均为正价付费自然月周期的不变量，并支持全新数据库一次初始化；不要求兼容已运行旧 schema 的数据迁移或双写。

## 开发期迁移约束

- 只允许原地修改现有 `apps/service/aaf-api/src/main/resources/db/migration/v5__order_schema.sql`、`apps/service/aaf-api/src/main/resources/db/seed/v12__init_seed_data.sql` 和 `apps/service/aaf-api/src/main/resources/db/seed/view/v101__billing_entity_def.sql` 中对应定义；不得新增 Flyway migration/seed 文件承载本轮会员改造。
- 这是开发期干净替换，不保留 `yearly`、Plan 默认月付、硬编码折扣、`auto_renew`、旧新字段双写、fallback 或 legacy adapter。
- 全新数据库完成初始化后，Plan/SKU、Subscription、会员兑换码与 Billing EntityDef 必须语义一致，会员管理资源可读取；若不一致则本期未完成。
- 代码、接口、测试和前端类型应一次性切换到 SKU 语义；本需求文档不要求本轮任务修改任何 `docs/reference`。

## 验收标准

- [ ] **Scenario: 套餐目录返回月季年独立 SKU**  
  **Given** PRO 的 `PRO_M1`、`PRO_Q1`、`PRO_Y1` 均为启用状态且各自保存了价格  
  **When** 已登录用户获取会员套餐目录  
  **Then** PRO 返回三个 SKU 的 `skuCode`、`billingCycle`、周期和明示价格，响应中不存在由月价计算的 `yearlyPrice`，季度价与年度价均等于各自 seed 值。

- [ ] **Scenario: 前端不推导周期价格**  
  **Given** `PRO_M1.price=2900`、`PRO_Q1.price=8200`、`PRO_Y1.price=27840`  
  **When** 用户依次切换月付、季付、年付  
  **Then** PRO 卡片分别显示 2900、8200、27840 分对应的金额，前端不执行 `2900×3`、`2900×12` 或 8 折计算。

- [ ] **Scenario: 新购复用已有支付弹窗**  
  **Given** 当前用户只有 FREE 且选择启用的 `PRO_Q1`  
  **When** 用户点击购买并在 `SubscriptionPayDialog` 完成支付  
  **Then** 系统创建 `SUBSCRIPTION` 业务订单，支付成功后生成 `ACTIVE` 的 PRO 订阅，`sku_id` 指向 `PRO_Q1`，`end_at` 为生效时间后 3 个自然月，并刷新订阅、权益和积分查询。

- [ ] **Scenario: 前端订阅管理区接通既有生命周期接口**  
  **Given** 用户在 `/studio/me/membership` 查看一条尚未到期的 `TEAM_Q1` 订阅，且页面已获得当前 SKU、取消状态与 pending SKU  
  **When** 用户分别执行“续费当前 SKU”“取消订阅”“降级到 `PRO_M1`”或“撤销降级”中的任一可用动作并确认  
  **Then** 页面依次只调用既有 `/subscribe`、`/me/cancel`、`/me/downgrade` 或 `/me/pending-downgrade` 对应接口，成功后刷新套餐、当前订阅、权益和积分查询，并按最新状态隐藏不可用动作；页面不得仅本地修改状态或调用积分购买接口。

- [ ] **Scenario: 未支付不得激活 SKU**  
  **Given** 当前用户选择 `TEAM_M1` 并创建了待支付订单  
  **When** 用户取消支付、支付失败或订单关闭  
  **Then** 当前订阅、`plan_id`、`sku_id`、`end_at`、权益额度和积分均不变，且不产生 `PAID` 的订阅流水。

- [ ] **Scenario: 同 SKU 手动续费从原到期时间顺延**  
  **Given** 用户当前 `PRO_Q1` 订阅为 `ACTIVE`，`end_at=2026-12-31T10:00:00`  
  **When** 用户对 `PRO_Q1` 手动续费且支付成功  
  **Then** 同一当前订阅的 `end_at` 更新为 `2027-03-31T10:00:00`，写入一条 `operation=RENEW`、`sku_id=PRO_Q1`、支付状态为 `PAID` 的流水，并清除 `cancelled_at`。

- [ ] **Scenario: 续费不触发升级结算或提前发放**  
  **Given** 用户当前 SKU 与续费目标 SKU 相同且已有未消耗权益和本月订阅积分  
  **When** 续费支付成功  
  **Then** 不计算升级补差价，不生成升级 `EXPIRE/EARN/SPEND` 三笔积分流水，不重置权益 `used/remain`，也不立即生成下一批 `SUBSCRIPTION_MONTHLY` 积分。

- [ ] **Scenario: 支付回调重放不重复续期**  
  **Given** 某续费支付订单已成功将 `end_at` 顺延 3 个月  
  **When** 相同 `pay_order_id` 的成功回调再次到达  
  **Then** 系统识别已处理流水，不再次修改 `end_at`，且该支付订单只对应一条成功续费事实。

- [ ] **Scenario: 跨 Plan 升级累计抵扣尚未消费的预付分段**  
  **Given** 用户当前为有效 `PRO_Y1`，并已有一段正在消费和至少一段提前续费形成的未来预付区间，现选择等级更高的 `TEAM_Q1`  
  **When** 系统创建升级订单  
  **Then** 系统分别计算每段在升级时刻尚未消费的价值并求和，目标金额取 `TEAM_Q1.price`，应付金额为不小于零的正确差额；支付成功后 TEAM 立即生效并执行既有升级积分三笔结算，已抵扣区间不再重复使用。

- [ ] **Scenario: 会员等级不按周期总价比较**  
  **Given** 低档 Plan 的年度 SKU 总价高于高档 Plan 的月度 SKU 总价  
  **When** 用户从低档年度 SKU 选择高档月度 SKU  
  **Then** 系统仍按 Plan 等级判定为升级，不因 SKU 总价大小将其判定为降级。

- [ ] **Scenario: 降级保存目标 SKU 并延迟生效**  
  **Given** 用户当前为 `TEAM_Y1` 且选择 `PRO_Q1`  
  **When** 用户确认降级  
  **Then** 系统保存可唯一确定 `PRO_Q1` 的 pending SKU，不创建支付订单、不改变当前 `plan_id/sku_id/end_at` 和权益，页面显示目标季度 SKU 将在当前周期结束后处理。

- [ ] **Scenario: 撤销降级只清除 pending 目标**  
  **Given** 当前 TEAM 订阅已排队降级到 `PRO_Q1`  
  **When** 用户点击撤销降级并确认  
  **Then** pending SKU 被清空，当前 TEAM 订阅、结束时间、权益和积分不变。

- [ ] **Scenario: 付费降级目标不得触发自动代扣**  
  **Given** 订阅到期时 pending 目标为付费 `PRO_Q1` 且用户没有完成该 SKU 的手动支付  
  **When** 到期调度执行  
  **Then** 系统不创建支付订单、不调用支付渠道，旧订阅变为 `EXPIRED` 并按既有冻结规则激活 FREE。

- [ ] **Scenario: 取消后权益保留且仍可手动续费**  
  **Given** 用户当前 `PRO_M1` 尚未到期  
  **When** 用户取消订阅  
  **Then** 系统记录取消时间但保持 `ACTIVE`，权益保留到原 `end_at`，不退款且不发起代扣；若用户随后成功续费同一 `PRO_M1`，则清除取消标记并从原 `end_at` 顺延 1 个自然月。

- [ ] **Scenario: 到期未续费冻结到 FREE**  
  **Given** 有效付费订阅已到 `end_at`，没有已支付续费且没有可直接切换的 FREE pending 目标  
  **When** 到期调度执行  
  **Then** 旧订阅状态变为 `EXPIRED`，系统激活 FREE 兜底，且已发订阅积分继续按原批次有效期自然过期。

- [ ] **Scenario: AIGC 权益复用现有权益模型**  
  **Given** `aigc_image_access`、`aigc_video_access`、`aigc_audio_access`、`aigc_model3d_access` 已写入 `billing_entitlement_def` 并通过 `billing_plan_entitlement` 关联套餐  
  **When** 用户获取套餐目录和当前权益  
  **Then** FREE 仅返回图像准入，PRO 返回图像/视频/音频准入，TEAM 与 ENTERPRISE 返回四类准入；不存在第二张 AIGC 套餐权益表或前端硬编码权益矩阵。

- [ ] **Scenario: 所有 AIGC 提交入口统一动态准入**  
  **Given** 图像、视频、语音/音乐和 3D 分别映射到四个 BOOLEAN 权益，且同一能力可从直接 HTTP、项目/API 或工具调用入口提交  
  **When** 同一用户从任一入口提交任一生成类型  
  **Then** 系统均按实际类型动态校验同一个对应权益编码，准入结果一致；未知类型或缺少映射时拒绝，不存在只保护页面、单个 Controller 或单个工具的绕行入口。

- [ ] **Scenario: AIGC 无权益但积分充足仍被准入拒绝**  
  **Given** FREE 用户积分充足但没有 `aigc_video_access`  
  **When** 用户从任一实际入口发起视频生成  
  **Then** 系统在积分预检、预占或扣减前拒绝提交，不创建生成任务且不改变积分。

- [ ] **Scenario: AIGC 有权益后仍需通过积分结算**  
  **Given** 用户拥有 `aigc_video_access` 但积分不足  
  **When** 用户从任一实际入口发起视频生成  
  **Then** BOOLEAN 准入通过后，现有积分规则因余额不足拒绝执行；系统不得把 BOOLEAN 权益额度当作可消费积分。

- [ ] **Scenario: 积分购买不改变会员订阅**  
  **Given** 用户当前为 FREE  
  **When** 用户购买一个 `credit_package` 且支付成功  
  **Then** 系统只增加有效期 2 年的充值积分批次，不创建 `SUBSCRIPTION` 订单，不设置 `plan_id/sku_id`，也不授予付费 Plan 权益。

- [ ] **Scenario: 会员订阅不是积分充值订单**  
  **Given** 用户购买 `PRO_M1` 且支付成功  
  **When** 系统处理支付结果  
  **Then** 订单类型为 `SUBSCRIPTION`，激活 PRO/SKU 并沿用会员月度积分规则，但不创建 `credit_package` 购买记录或充值积分批次。

- [ ] **Scenario: 禁用 SKU 不可购买**  
  **Given** `TEAM_Q1.status=DISABLED`  
  **When** 用户获取目录或直接提交该 `skuCode` 购买请求  
  **Then** 目录不返回该 SKU，直接购买请求返回明确的不可售错误，且不创建业务订单或支付订单。

- [ ] **Scenario: 同 Plan 换周期被明确拒绝**  
  **Given** 用户当前为有效 `PRO_M1`  
  **When** 用户尝试购买 `PRO_Y1`  
  **Then** 系统返回“本期暂不支持套餐内换周期”，不将其判定为续费、升级或降级，也不创建支付订单。

- [ ] **Scenario: 会员兑换码明确绑定 SKU**  
  **Given** 运营创建一个季度 PRO 会员兑换码  
  **When** 兑换码创建、展示并被用户成功兑换  
  **Then** 兑换码始终引用 `PRO_Q1` 的 `skuCode/sku_id`，用户获得 PRO 三个自然月；任何只提供 PRO Plan 而未提供 SKU 的创建或兑换请求均明确失败，不得默认 `PRO_M1`。

- [ ] **Scenario: FREE_DEFAULT 与付费 SKU 不变量不可绕过**  
  **Given** 系统尝试创建第二个免费永久 SKU、让 `FREE_DEFAULT` 属于非 FREE Plan、让付费 SKU 使用 `PERPETUAL` 或让非 FREE SKU 价格为零  
  **When** 数据来自初始化、后台管理、兑换码或交易入口中的任一来源  
  **Then** 系统拒绝该数据，且非法 SKU 不可启用、展示、购买、兑换或激活订阅；只有属于 FREE 的 `FREE_DEFAULT` 可为 `PERPETUAL/0/0`。

- [ ] **Scenario: 首次购买重复点击只保留一个 live 结账**  
  **Given** 用户当前只有 FREE，尚无其他付费订阅  
  **When** 用户双击购买或并发提交两次 `PRO_M1` 新购请求  
  **Then** 只产生一个 live 会员结账，第二次请求返回明确冲突并可继续处理现有结账，不产生第二个可支付会员订单。

- [ ] **Scenario: live 结账期间不能并发发起另一会员交易**  
  **Given** 用户已有一笔尚可支付的会员新购、升级或续费结账  
  **When** 用户又发起不同 SKU 的会员交易  
  **Then** 新请求明确冲突，当前订阅、权益和积分不变，系统不创建第二笔可支付会员订单。

- [ ] **Scenario: 不同支付单重复成功只激活一次并进入人工补偿**  
  **Given** 极端情况下同一用户的两个不同会员支付单都收到成功结果  
  **When** 后到成功结果被处理  
  **Then** 系统不重复激活、升级、续期、发放权益或结算积分；保留该笔成功付款，并登记包含用户、订单、实付金额、原因和状态的“人工补偿待处理”记录进入运营清单，不得静默吞款或把它标为已消费。

- [ ] **Scenario: 多次提前续费后升级不多收费**  
  **Given** 用户连续提前续费两次，形成当前消费段和两个尚未开始的预付段，随后在任一自然月日期升级  
  **When** 系统计算升级应付金额  
  **Then** 当前段未消费价值与两个未来段全部预付价值分别计算且各计一次；结果不因只读取最近续费流水而降低抵扣，也不因月末天数差异多收费，应付金额最低为零且本期不自动退款。

- [ ] **Scenario: 取消意图仅由 cancelled_at 表达**  
  **Given** 本期不提供自动代扣  
  **When** 用户取消订阅、查询当前订阅、运营查看会员记录或到期调度执行  
  **Then** 取消意图仅由 `cancelled_at` 表达，系统和页面不存在 `auto_renew` 字段、编辑动作或“关闭自动续费”语义，调度不创建支付订单也不调用渠道扣款；同 SKU 续费成功后清除 `cancelled_at`。

- [ ] **Scenario: Workspace pricing 只读展示并跳转会员页**  
  **Given** Workspace pricing 使用与会员页相同的套餐目录  
  **When** 用户切换月/季/年并点击任一付费套餐 CTA  
  **Then** 页面展示所选 SKU 的明示价格和权益摘要，不推导价格，不直接创建订单或打开支付弹窗，只跳转 `/studio/me/membership`；共享组件切换 SKU 后该页面仍可编译并正常渲染。

- [ ] **Scenario: 全新数据库的 Billing 元数据与 SKU 语义一致**  
  **Given** 使用本期允许原地修改的既有 schema、seed 与 `v101__billing_entity_def.sql` 初始化全新数据库  
  **When** 应用读取会员管理资源  
  **Then** Plan 不再暴露价格/周期旧字段，Subscription 不存在 `autoRenew`，会员兑换码选择明确 SKU，SKU 管理遵守 FREE/付费不变量，且本期没有新增 Flyway 文件。

- [ ] **Scenario: FAQ 不承诺自动续费**  
  **Given** `member.faq` 已按本轮规则更新  
  **When** 未登录用户读取公开会员 FAQ  
  **Then** FAQ 明确支持月/季/年和同 SKU 手动续费，并说明取消后使用至到期、未续费回到 FREE、积分购买独立；内容中不存在“会自动续费”或渠道代扣承诺。

## 完成定义

- Plan/SKU、订阅、pending、流水和会员兑换码的数据语义与本需求一致，旧 `yearly`、Plan 默认月付和硬编码折扣路径已移除。
- 四类 AIGC BOOLEAN 权益在所有实际提交入口按生成类型动态执行，且严格先准入、后积分；目录展示与执行结果来自同一权益事实。
- 新购、升级、续费、降级、撤销降级、取消、到期冻结的主流程和异常路径均有与上述 AC 对应的验证；首次并发结账只有一个 live 订单。
- 提前续费、多次续费和跨月末后升级均按尚未消费的分段预付事实正确抵扣，不得多收费或重复抵扣。
- 不同支付单重复成功时只产生一次订阅生效结果，额外成功款形成可追踪的“人工补偿待处理”记录并进入人工清单；本期不因此扩展完整退款系统。
- `auto_renew` 字段、VO、CRUD、调度代扣分支和 `SubscriptionAutoRenewService` 全部删除；取消意图只由 `cancelled_at` 表达，任何定时任务均不发起代扣。
- 会员兑换码保留并唯一绑定明确 SKU，缺失 SKU 不得默认月付；`FREE_DEFAULT` 与所有付费 SKU 的不变量在初始化、管理、兑换和交易中一致成立。
- 会员页完成月/季/年展示、已有支付弹窗接入和订阅管理操作；Workspace pricing 完成只读目录展示与会员页跳转，不直接交易且保持可编译。
- AIGC 权益、Plan、SKU、会员兑换码、FAQ 与 Billing EntityDef 可在全新数据库初始化后直接使用，管理元数据不引用已删除字段。
- 积分购买和会员订阅仍为两条独立链路，任何代码或文案都没有自动续费承诺。
- 本轮仅原地修改既有 `v5__order_schema.sql`、`v12__init_seed_data.sql` 与 `v101__billing_entity_def.sql` 的相关内容，未新增 Flyway SQL 文件，未修改 `docs/reference`，未建立旧新模型兼容层。
- 本轮未实现自动代扣、促销或常规退款流程改造；异常重复付款仅达到可审计、可进入人工补偿处理的最小闭环。
