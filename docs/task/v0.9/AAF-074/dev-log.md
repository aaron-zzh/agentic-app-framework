# 开发记录：AAF-074 会员与权益关系化模型

执行者：AI/developer-service

## #7401 会员等级（成长线）

✅ 2026-05-29 — developer-service

- Level 实体 + LevelRepository + LevelService + LevelController
- exp/level_id 列补入 credit_account（复用 wallet），CreditAccount 实体同步补字段
- LevelService.addExp() 自动查区间匹配升降级
- seed 数据：L0(0-99)/L1(100-499)/L2(500+)
- 决策：直接在 CreditAccount 补 exp/level_id 字段而非 remark JSON，保持类型安全

## #7402 订阅（付费线）

✅ 2026-05-29 — developer-service

- SubscriptionPlan/PlanEntitlement/Subscription/SubscriptionRecord 四表实体
- SubscriptionService：购买复用 BizOrderService+PayOrderService，MOCK 渠道同步激活
- 激活时取消旧订阅 + 实例化 entitlement_quota
- seed 数据：FREE(永久¥0)/PRO(30天¥99)/TEAM(30天¥299)/ENTERPRISE(365天¥2999)

## #7403 权益消费 + @Entitlement

✅ 2026-05-29 — developer-service

- EntitlementDef/EntitlementQuota/EntitlementLedger 三表实体
- EntitlementService 实现 EntitlementChecker 接口：checkAndConsume 核心逻辑
- @Entitlement 注解 + EntitlementAspect（aaf-framework），SpEL 解析 cost
- 切面执行顺序：方法成功后扣减（避免失败仍扣费）
- 周期重置 resetExpiredQuotas() 供定时任务调用
- refill 逻辑：额度不足→查 plan_entitlement.refill_price→消耗积分(CreditService.spend)→补额度

> **沉淀**：@Entitlement 与 RBAC 平行——RBAC 管"能不能做"，Entitlement 管"额度够不够"。框架层定义注解+接口+切面，业务层(billing)提供实现，解耦干净。

## #7404 账单

✅ 2026-05-29 — developer-service

- BillingQueryService：复用 credit_transaction + entitlement_ledger 查询
- 接口：积分流水/权益流水/日月汇总/CSV 导出骨架
- BillingController 暴露 REST API


## 审查修复

✅ 2026-05-29 — developer-service

- Blocker1：EntitlementAspect 切面顺序修正为 check→proceed→consume，额度不足时方法不执行
- Blocker1：EntitlementChecker 接口拆为 check/consume 两方法，保留 checkAndConsume 便捷直调
- Blocker1：EntitlementService 实现 check（只读预判）和 consume（真扣减），BOOLEAN 类型仅 check 校验
- Blocker2：SubscriptionRecordRepository 加 findByPayOrderIdAndPayStatus，消除 onPaySuccess 全表扫描
- Blocker2：SubscriptionRepository 加 findByStatusAndEndAtBefore，消除 expireSubscriptions 全表扫描

> **沉淀**：AOP 切面拦截资源消耗类操作，必须先检查再执行——否则拦截形同虚设。check 阶段用 readOnly 事务预判可行性，consume 阶段才开写事务真扣减。


## #7405 用户中心前端（EntityDef 配置驱动）

✅ 2026-05-29 — developer-webui

- 新建 `billing-entities.ts`，配置 5 个实体：levelEntity/subscriptionPlanEntity/subscriptionEntity/entitlementQuotaEntity/walletTransactionEntity
- 统一 group="billing" groupLabel="会员中心"，icon 分别用 crown/credit-card/zap/wallet/receipt
- subscriptionEntity.status 带 color（ACTIVE 绿/EXPIRED 灰/CANCELLED 红）
- walletTransactionEntity 配 quickFilters 区分收入/支出
- 在 index.ts 导入 billingEntities 并 registerAll，不动现有 sampleEntities
- 决策：独立文件而非追加 sampleEntities 数组，保持业务域隔离、便于后续按模块拆分
