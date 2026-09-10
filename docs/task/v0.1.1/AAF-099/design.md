---
level: Practice
layer: Product
purpose: 定义 AAF-099 Plan + Subscription SKU 开发期合并调整的可执行技术方案并关闭设计审计问题
status: draft
version: 1.1.0
date: 2026-09-09
author: architect
dependencies:
  - ./requirement.md
related:
  - ./design-audit.md
  - ./tasks.md
  - ./ui-design.md
  - ../../../design/apps/service/membership-completion.md
  - ../../../design/apps/service/identity-and-membership.md
scope:
  includes:
    - Plan 与 Subscription SKU 数据模型及受控管理
    - AIGC 动态会员准入
    - 会员兑换码 SKU 化
    - 分段预付价值、结账互斥与异常付款补偿事实
    - 订阅操作、支付回调、事务与并发设计
    - REST、会员页与 Workspace pricing 接入契约
    - Schema、seed、最小测试与验证门禁
  excludes:
    - 渠道代扣签约与自动续费
    - 促销、试用、自动退款与套餐内换周期
    - 生产存量数据迁移
changelog:
  - 2026-09-09 | v1.1.0：按 design-audit 关闭 B1-B5、M1-M5、m1-m2，补齐动态准入、兑换码 SKU、分段价值、结账互斥、异常付款、v101、Workspace pricing 与批准门禁
  - 2026-09-09 | v1.0.0：Plan + Subscription SKU 初版技术设计
---

# AAF-099 Plan + Subscription SKU 技术设计

## 设计基线与结论

本设计以 [requirement.md v1.0.2](./requirement.md) 为需求真理源，以真实源码与 SQL 复核结果为落点，在开发期一次性替换旧 `Plan.price + duration_days + yearly` 模型，不保留 fallback、双写或默认月付兼容。

核心结论：

- Plan 只表达会员档位、月度积分与权益集合；SKU 表达购买周期和明示价格。
- 会员等级只比较 Plan `sort`，不比较不同周期 SKU 总价。
- 月、季、年使用 `LocalDateTime.plusMonths(1/3/12)`；不再使用 30/90/365 天价格或周期公式。
- AIGC 通过一个共享 `AigcSubmissionAccessGuard` 动态映射任务类型并调用框架 `EntitlementChecker`；所有任务创建边界必须先会员准入、后积分预检。
- 会员兑换码从 `plan_id` 一次性切换为 `sku_id`，API 以 `skuCode` 表达；兑换精确激活该 SKU，不选择默认周期。
- 每条成功 NEW/UPGRADE/RENEW 流水持久化独立服务区间和 SKU 名义价格快照；升级逐段计算当前订阅世代的未消费价值。
- 每个 Subscription 行就是一个服务世代；RENEW 仍属于当前世代，UPGRADE 创建新世代。升级成功后旧世代参与抵扣的段全部标记 `SUPERSEDED`，不能再次抵扣。
- billing 自有 `billing_checkout_guard` 为每个用户提供始终可创建并可锁定的互斥行；不依赖 system.user 实体，不要求用户先有 Subscription。
- 同用户最多一个 live 会员结账。额外支付成功款只登记 `COMPENSATION_PENDING`，不激活、不续期、不发权益、不结算积分或佣金，本期不自动退款。
- `auto_renew` 从 Schema、domain、DTO/VO、CRUD、EntityDef、取消逻辑、调度分支、占位服务和测试全链删除；取消意图唯一由 `cancelled_at` 表达。
- 本轮 SQL 只允许原地修改 `v5__order_schema.sql`、`v12__init_seed_data.sql`、`v101__billing_entity_def.sql`，不得新增、复制或改动其他 migration/seed。
- Workspace pricing 指 `apps/webui/src/app/(workspace)/settings/pricing/page.tsx`；它只读复用会员目录并跳转 `/studio/me/membership`。`/studio/me/pricing` 是模型收费页，不在本轮修改范围。

## 真实实现复核

| 路径 | 当前事实 | 本设计落点 |
|------|----------|------------|
| `AigcTaskController.submit` | 解析六类任务后直接调用 `AigcTaskService.submit*` | 六个 `submit*` 在积分预检前统一调用共享 Guard。 |
| `AigcTaskApiAdapter.submit` | 独立任务再调用 `AigcTaskService`；项目任务自行创建 PREPARED intent，仅做 `creditGuard.precheck` | 独立任务由 Service Guard 覆盖；项目分支在 estimate/precheck 前调用同一 Guard。 |
| `ContentGenerationTool`、`Model3dGenerationTool`、`AigcProfessionalToolExecutionAdapter` | 均经 `AigcTaskApi.submit` | 不在通用 `ToolService.invoke` 猜测类型；由 `AigcTaskApi` 下游共享 Guard 强制准入。 |
| `BatchGenerationService.submit` | 另存批量图像任务并入 Redis 队列 | 保存/入队前显式调用同一 Guard 的 IMAGE 映射。 |
| `EntitlementService` | 已实现框架 `EntitlementChecker`；BOOLEAN 的 `check` 只校验存在且 total>0，不消费 | Guard 只依赖 `EntitlementChecker` 接口，运行时由 `EntitlementService.check(userId, code, 0)` 实现。 |
| `credit_redeem_code` / `CreditRedeemCodeService` | 只保存 Plan，兑换调用 `activateSubscription(... planId ..., yearly=false)` | v5、domain、DTO/VO、查询、生成、兑换、EntityDef、导出全部切 SKU。 |
| `SubscriptionExpireScheduler` | 可选注入 `SubscriptionAutoRenewService` 并可能代扣 | 删除接口、注入、分支和测试假设；付费 pending 到期也冻结至 FREE。 |
| `PayOrderService.markSuccess` | WAITING→SUCCESS 原子迁移 | 保留渠道事实幂等；会员履约另由 guard 行锁、Record 状态和唯一索引保护。 |
| `PayNotifyService` | BizOrder=PAID 才视为幂等终态 | 增加 `COMPENSATION_PENDING` 终态识别，避免异常成功款反复重试。 |
| `v101__billing_entity_def.sql` | 仍声明 Plan 价格/时长、Subscription.autoRenew、兑换码 Plan | 原地同步 Plan/SKU/Subscription/兑换码/购买流水管理元数据。 |
| Workspace pricing | `/settings/pricing` 复用旧 PlanCard，CTA 打开联系客服 | 使用新目录和三周期 SKU；CTA 仅导航会员页。 |

## 审计问题关闭矩阵

| 审计项 | 关闭设计 | 可验证交付 |
|--------|----------|------------|
| B1 AIGC 准入缺失 | 共享 `AigcSubmissionAccessGuard`；普通任务、项目 intent、批量图像三个真实创建边界统一调用；工具经 Task API 收敛；固定 taskType→权益映射且未知类型 fail closed。 | #09917；参数化 Guard 单测、HTTP/项目/工具一致性验收。 |
| B2 兑换码仍指 Plan | v5 删除 `plan_id`、增加非空语义 `sku_id`；domain/DTO/VO/service/EntityDef/API/导出切 `skuCode`；兑换精确 SKU，自然月周期来自 SKU。 | #09916、#09918；PRO_Q1 兑换与缺失 SKU 拒绝测试。 |
| B3 v101 旧字段 | SQL 范围显式包含 v101；新增 SKU 受控资源、兑换码 SKU 关系和只读购买流水/补偿状态；#16 完成前必须新库启动并通过 CrudResourceCompiler。 | #09916 阻塞门禁。 |
| B4 续费后升级金额错误 | Record 保存 `service_generation_id/service_start_at/service_end_at/sku_price_snapshot`；逐段计算当前世代未消费名义价值；升级后原子标记旧段 SUPERSEDED。 | #09919、#09927；提前续费、多次续费、月末、连续升级。 |
| B5 首购无锁行 | 新增 billing 自有 checkout guard，`INSERT ON CONFLICT` 后 `FOR UPDATE`；live Record 部分唯一索引；额外成功款进入 COMPENSATION_PENDING。 | #09920、#09921、#09927；FREE→付费并发和双成功测试。 |
| M1 auto_renew 双真理 | 全链删除，不保留字段、接口、兼容读取或代扣占位；取消只写 `cancelled_at`。 | #09916、#09922、#09926 静态扫描。 |
| M2 活动范围冲突 | tasks.md 将 #01-#15 明确归档为现状基线，旧门禁仅保留历史证据；唯一活动范围为 #16 起。 | tasks.md Front Matter 与活动任务表。 |
| M3 前置验证/依赖不足 | #16 是原子 bootstrap：SQL、Java 元数据契约、全新库 migration+seed、应用启动、CrudResourceCompiler smoke 全绿后才允许任何下游；前端依赖 #22 REST 契约。 | #09916、依赖图。 |
| M4 Workspace pricing 漏任务 | 明确纳入 `/settings/pricing`，只读展示并跳会员页；单列 #25。 | #09925、#09928。 |
| M5 SKU 不变量脆弱 | 行内 CHECK 精确限制 FREE_DEFAULT 与所有其他 SKU；Plan 归属由受控服务和 v12 seed 断言双守护；不增加冗余 `is_payable`。 | #09916、#09927。 |
| m1 tasks 无 Front Matter | tasks.md 补齐必填元数据并保持 draft。 | tasks.md 文件头。 |
| m2 UI 状态早于批准 | 本设计与 tasks 均为 draft，且所有 UI 任务显式依赖人类批准和 #16/#22；`ui-design.md` 的 active 仅表示候选稿可引用，不构成开发授权。因本轮禁止修改 UI 文档，不改其元数据。 | 本文“人类批准门禁”与 tasks 活动入口。 |

## AIGC 动态会员准入

### 共享边界

新增组件位于 AIGC 任务服务包，只依赖 framework 接口，不反向依赖 billing 实现：

```java
public interface AigcSubmissionAccessGuard {
    void requireAccess(Long userId, AigcTaskTypeEnum taskType);
}

@Component
@RequiredArgsConstructor
final class DefaultAigcSubmissionAccessGuard implements AigcSubmissionAccessGuard {
    private final EntitlementChecker entitlementChecker;
}
```

运行时调用链固定为：

```text
入口完成认证与 taskType 解析
  → AigcSubmissionAccessGuard.requireAccess(userId, taskType)
    → taskType 映射唯一 entitlementCode
      → EntitlementChecker.check(userId, entitlementCode, 0)
        → Spring 注入 EntitlementService 实现
  → CapabilityRouter / estimateCredits
  → AiCreditGuard.precheck
  → 创建任务或项目 intent
  → 执行及既有结算/失败退还
```

Guard 是共享规则真理源；接线点只有以下三个“创建业务事实”边界：

- `AigcTaskService.submitImageTask/submitVideoTask/submit3dTask/submitMusicTask/submitVoiceTask/submitImageProcessTask`：覆盖直接 HTTP 和 `AigcTaskApi` 独立任务；必须在任何 `creditGuard.precheck` 或固定积分扣减前调用。
- `AigcTaskApiAdapter.submit` 的 `executionRunId != null` 项目分支：在 capability 路由、estimate、credit precheck 和 `intentStore.prepare` 前调用。独立分支不重复调用，由 `AigcTaskService` 承担。
- `BatchGenerationService.submit`：在批量任务保存和 Redis 入队前按 IMAGE 调用。

`ContentGenerationTool`、`Model3dGenerationTool`、`AigcProfessionalToolExecutionAdapter` 已经调用 `AigcTaskApi`，因此不新增工具专用会员判断。`ai_tool_catalog.entitlement_code` 可在 v12 为 `generateImage/generateVideo` 等填入，用于目录预过滤和解释，但不是最终安全边界；通用 `ToolService.invoke` 不根据工具名猜任务类型。

### 类型映射

| AigcTaskTypeEnum / taskType | 权益编码 | 说明 |
|-----------------------------|----------|------|
| `IMAGE` | `aigc_image_access` | 文生图、图生图、项目图像工具。 |
| `IMAGE_PROCESS` | `aigc_image_access` | 图像处理仍属于图像能力；准入通过后才固定积分扣减。 |
| `VIDEO` | `aigc_video_access` | 文生视频、图生视频、项目视频工具。 |
| `VOICE` | `aigc_audio_access` | TTS/配音。 |
| `MUSIC` | `aigc_audio_access` | 音乐生成。 |
| `MODEL_3D` | `aigc_model3d_access` | 文/图生 3D。 |

映射使用显式 `EnumMap`/switch，不允许字符串前缀、工具名或模型 capability 猜测。未知 taskType、未来新增类型未登记映射时拒绝，不默认放行。

### 错误语义

| 场景 | 业务/工具语义 | HTTP | 副作用 |
|------|---------------|------|--------|
| taskType 非法或未映射 | `AIGC_TASK_TYPE_INVALID` | 400 | 无任务、无积分变化。 |
| 权益定义缺失/类型不是 BOOLEAN | `AIGC_ENTITLEMENT_CONFIG_INVALID`（新增，服务端记录 code/type） | 500 | 无任务、无积分变化；不得降级放行。 |
| 用户无对应 BOOLEAN 权益 | `AIGC_MEMBERSHIP_REQUIRED`（新增） | 403 | 无任务、无积分变化；消息指出需升级会员，不复用“存储空间已满”。 |
| 有权益但积分不足 | 既有 `InsufficientCreditsException` | 402 | 无任务、无积分变化。 |
| 工具调用无权益 | `ToolCallResult.error(..., "AIGC_MEMBERSHIP_REQUIRED", message)` | 工具结果失败 | 不吞成通用 `GENERATION_ERROR`；项目执行保留同一业务码。 |

Guard 捕获 BOOLEAN 权益不足并转换为 AIGC 专用业务异常；配置缺失与用户无权益必须区分。`EntitlementService.check(..., 0)` 不调用 `consume`，BOOLEAN 不产生 ledger。

### 最小测试

- 一组参数化 Guard 单测覆盖六种 taskType 映射、未知类型 fail closed、EntitlementChecker 在 creditGuard 前调用。
- 一组入口一致性测试覆盖 HTTP IMAGE、项目 VIDEO、工具 MODEL_3D；均验证无权益时不创建任务/intent且不调用积分预检。
- 两个顺序场景：无权益但积分充足返回 403；有权益但积分不足返回 402。
- 批量图像保存/入队前被 Guard 拒绝。

## 商品、SKU 与受控管理

### SKU DDL 不变量

`billing_subscription_plan_sku` 的核心 DDL 必须使用同一个行内 CHECK：

```sql
CREATE TABLE billing_subscription_plan_sku (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    plan_id         BIGINT       NOT NULL,
    sku_code        VARCHAR(64)  NOT NULL,
    billing_cycle   VARCHAR(16)  NOT NULL,
    cycle_months    SMALLINT     NOT NULL,
    price           BIGINT       NOT NULL,
    market_price    BIGINT       NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
    sort            INTEGER      NOT NULL DEFAULT 0,
    ext             JSONB,
    -- BaseEntity 字段与现有表一致
    CONSTRAINT fk_subscription_sku_plan
        FOREIGN KEY (plan_id) REFERENCES billing_subscription_plan(id),
    CONSTRAINT uk_subscription_sku_plan_id_id UNIQUE (plan_id, id),
    CONSTRAINT ck_subscription_sku_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_subscription_sku_commercial_shape CHECK (
        (
            sku_code = 'FREE_DEFAULT'
            AND billing_cycle = 'PERPETUAL'
            AND cycle_months = 0
            AND price = 0
            AND market_price = 0
        )
        OR
        (
            sku_code <> 'FREE_DEFAULT'
            AND (
                (billing_cycle = 'MONTH' AND cycle_months = 1)
                OR (billing_cycle = 'QUARTER' AND cycle_months = 3)
                OR (billing_cycle = 'YEAR' AND cycle_months = 12)
            )
            AND price > 0
            AND market_price >= price
        )
    )
);

CREATE UNIQUE INDEX uk_subscription_sku_code
    ON billing_subscription_plan_sku(sku_code) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uk_subscription_sku_enabled_plan_cycle
    ON billing_subscription_plan_sku(plan_id, billing_cycle)
    WHERE deleted = FALSE AND status = 'ENABLED';
CREATE INDEX idx_subscription_sku_catalog
    ON billing_subscription_plan_sku(plan_id, status, sort)
    WHERE deleted = FALSE;
```

不增加 `is_payable`：可支付性由不可矛盾的结构事实推导——仅 `FREE_DEFAULT` 为内部非支付 SKU；其他合法 SKU 必为正价自然月商品，再叠加 `status=ENABLED` 才可售。新增布尔字段会与 code/cycle/price 形成第二真理源。

行内 CHECK 无法跨表证明 Plan 归属，故由两层补足：

- 所有 SKU 创建、更新、启用只经过 `SubscriptionPlanSkuCrudService`/`SubscriptionSkuPolicy`。它要求 `FREE_DEFAULT` 只属于 code=FREE 的 Plan；其他 SKU 只属于非 FREE Plan。已被引用后不得修改 code、plan、cycle、cycleMonths、price snapshot 语义；价格调整只影响新订单，历史由 Record snapshot 保留。
- v12 seed 末尾增加 `DO $$ ... RAISE EXCEPTION ... $$` 断言：恰好一条 `FREE_DEFAULT` 且属于 FREE；FREE 不拥有其他 SKU；所有非 FREE SKU 属于非 FREE Plan并满足正价自然月规则。断言失败使全新库初始化失败。

### v101 管理表达

`v101__billing_entity_def.sql` 原地完成：

- `subscription-plan` 删除 `durationDays/price/marketPrice`，保留名称、档位 sort、monthlyCredits、status、ext。
- 新增 `subscription-sku` code resource，关系字段 `plan`，展示 skuCode/billingCycle/cycleMonths/price/marketPrice/status/sort；写操作走受控 `SubscriptionPlanSkuCrudService`，不是动态表直写。
- `subscription` 删除 `autoRenew`，增加当前 SKU、pending SKU、cancelledAt，只读。
- `credit-redeem-code` 将 Plan 商品关系替换为 SKU 关系，并只读展示由 SKU 派生的 Plan/周期；生成仍走专用 `/generate` action。
- 新增或注册只读 `subscription-record` 管理资源，展示 operation、SKU、服务区间、generation、实付、名义快照、fulfillmentStatus、exceptionCode、compensation 处理信息，允许运营查询 `COMPENSATION_PENDING`；不允许通过通用 CRUD 改履约结果。
- `CrudResourceProviderConfiguration` 同步注册 SKU 和 Record provider。#16 启动 smoke 必须证明 EntityDef 字段与 DTO/VO 一致。

## 会员兑换码 SKU 化

### 数据与约束

v5 对 `credit_redeem_code` 做开发期原地替换：删除 `plan_id`，增加 `sku_id`。

```sql
sku_id BIGINT,
CONSTRAINT fk_redeem_code_sku
    FOREIGN KEY (sku_id) REFERENCES billing_subscription_plan_sku(id),
CONSTRAINT ck_redeem_code_product CHECK (
    (type = 'CREDIT' AND sku_id IS NULL AND credit_amount > 0)
    OR
    (type = 'MEMBERSHIP' AND sku_id IS NOT NULL AND credit_amount = 0)
)
```

不保留 `plan_id`、双写或“查询 Plan 首个 SKU”。

### Java 与 API 修改面

| 类/资源 | 精确修改 |
|---------|----------|
| `CreditRedeemCode` | `planId` 替换为 `skuId`。 |
| `CreditRedeemCodeCreateDTO` | `planId` 替换为必选业务输入 `skuCode`；CREDIT 时必须为空，MEMBERSHIP 时必须非空。 |
| `CreditRedeemCodeVO` | 返回 `sku`、`skuCode`、`billingCycle` 和由 SKU 派生的只读 `plan`；Plan 不再是商品身份。 |
| `CreditRedeemCodePageParam` | `planId` 过滤替换为 `skuId/skuCode`。 |
| `CreditRedeemCodeService` | 注入 SKU Repository；生成时校验启用、非 FREE、正价 SKU；createCode 写 skuId；toVO 批量加载 SKU+Plan；redeem 精确 SKU。 |
| `CreditRedeemCodeController` | `/generate`、`/generate-batch` 契约使用 skuCode；Excel 增加 SKU/Plan/周期列；`/redeem` URL 保持。 |
| `v101` | 兑换码 EntityDef 关系改为 `billing.subscription-sku`，Plan 仅派生展示。 |
| 前端 `RedeemCodeGenerateButton` 与 API 类型 | 会员码选择 SKU，不提交 Plan；月/季/年明确展示。 |

兑换事务顺序：锁兑换码 → 校验 UNUSED/未过期 → 锁 checkout guard 与当前 ACTIVE → 调用 `SubscriptionService.activateGrantedSku(userId, skuId, "REDEEM_CODE", codeId)` → 以 `redeemedAt` 为 start、SKU `cycleMonths` 为 end 激活精确 SKU → 成功后标记 REDEEMED。任一步失败整体回滚，兑换码仍可用。赠送开通沿用现有“结束旧 ACTIVE、创建新 ACTIVE”语义，不创建支付 Record，不参与升级抵扣；不得给季度码套月付周期。

## Subscription 与购买流水

### Subscription

`billing_subscription` 增加 `sku_id`、`pending_sku_id`；删除 `auto_renew/pending_plan_id/pending_yearly`。FREE 引用 `FREE_DEFAULT` 且 `end_at IS NULL`，付费 SKU 必须有 end_at。`source_id` 仅用于审计最近一次生效/延长来源，不再参与升级金额计算。

每次 NEW 或 UPGRADE 创建一个新的 Subscription 行，且该行 ID 即 `service_generation_id`。同 SKU RENEW 不创建新 Subscription，因此所有续费段仍属于同一世代。ACTIVE 每用户唯一索引保留。

### Record 精确字段

```sql
CREATE TABLE subscription_record (
    id                       BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id                  BIGINT       NOT NULL,
    plan_id                  BIGINT       NOT NULL,
    sku_id                   BIGINT       NOT NULL,
    operation                VARCHAR(16)  NOT NULL,
    pay_order_id             BIGINT,
    sku_price_snapshot       BIGINT       NOT NULL,
    pay_price                BIGINT       NOT NULL,
    pay_status               VARCHAR(16)  NOT NULL DEFAULT 'UNPAID',
    pay_time                 TIMESTAMP(6),
    fulfillment_status       VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    service_generation_id    BIGINT,
    service_start_at         TIMESTAMP(6),
    service_end_at           TIMESTAMP(6),
    value_status             VARCHAR(16),
    superseded_by_record_id  BIGINT,
    superseded_at            TIMESTAMP(6),
    exception_code           VARCHAR(48),
    exception_reason         VARCHAR(500),
    exception_detected_at    TIMESTAMP(6),
    compensation_resolved_at TIMESTAMP(6),
    compensation_result      VARCHAR(500),
    -- BaseEntity 字段与现有表一致
    CONSTRAINT fk_subscription_record_plan_sku
        FOREIGN KEY (plan_id, sku_id)
        REFERENCES billing_subscription_plan_sku(plan_id, id),
    CONSTRAINT fk_subscription_record_pay_order
        FOREIGN KEY (pay_order_id) REFERENCES pay_order(id),
    CONSTRAINT fk_subscription_record_generation
        FOREIGN KEY (service_generation_id) REFERENCES billing_subscription(id),
    CONSTRAINT fk_subscription_record_superseded_by
        FOREIGN KEY (superseded_by_record_id) REFERENCES subscription_record(id),
    CONSTRAINT ck_subscription_record_operation
        CHECK (operation IN ('NEW', 'UPGRADE', 'RENEW')),
    CONSTRAINT ck_subscription_record_pay_status
        CHECK (pay_status IN ('UNPAID', 'PAID')),
    CONSTRAINT ck_subscription_record_fulfillment
        CHECK (fulfillment_status IN ('PENDING', 'FULFILLED', 'CLOSED', 'COMPENSATION_PENDING')),
    CONSTRAINT ck_subscription_record_service_segment CHECK (
        fulfillment_status <> 'FULFILLED'
        OR (
            pay_status = 'PAID'
            AND service_generation_id IS NOT NULL
            AND service_start_at IS NOT NULL
            AND service_end_at IS NOT NULL
            AND service_end_at > service_start_at
            AND value_status IN ('AVAILABLE', 'SUPERSEDED')
        )
    ),
    CONSTRAINT ck_subscription_record_superseded CHECK (
        (value_status = 'SUPERSEDED' AND superseded_by_record_id IS NOT NULL AND superseded_at IS NOT NULL)
        OR (value_status IS DISTINCT FROM 'SUPERSEDED' AND superseded_by_record_id IS NULL AND superseded_at IS NULL)
    ),
    CONSTRAINT ck_subscription_record_compensation CHECK (
        fulfillment_status <> 'COMPENSATION_PENDING'
        OR (pay_status = 'PAID' AND exception_code IS NOT NULL AND exception_detected_at IS NOT NULL)
    ),
    CONSTRAINT ck_subscription_record_price
        CHECK (sku_price_snapshot >= 0 AND pay_price >= 0)
);

CREATE UNIQUE INDEX uk_subscription_record_pay_order
    ON subscription_record(pay_order_id)
    WHERE deleted = FALSE AND pay_order_id IS NOT NULL;
CREATE UNIQUE INDEX uk_subscription_record_live_checkout
    ON subscription_record(user_id)
    WHERE deleted = FALSE AND fulfillment_status = 'PENDING';
CREATE UNIQUE INDEX uk_subscription_record_service_segment
    ON subscription_record(service_generation_id, service_start_at, service_end_at)
    WHERE deleted = FALSE AND fulfillment_status = 'FULFILLED';
CREATE INDEX idx_subscription_record_upgrade_value
    ON subscription_record(user_id, service_generation_id, service_end_at, id)
    WHERE deleted = FALSE AND fulfillment_status = 'FULFILLED'
      AND pay_status = 'PAID' AND value_status = 'AVAILABLE';
CREATE INDEX idx_subscription_record_compensation
    ON subscription_record(fulfillment_status, exception_detected_at DESC)
    WHERE deleted = FALSE AND fulfillment_status = 'COMPENSATION_PENDING';
```

`pay_price` 永远表示实际收款：UPGRADE 是补差价，不能作为目标 SKU 名义服务价值。`sku_price_snapshot` 是订单创建时目标 SKU 的明示名义价格，未来升级抵扣只读该字段。

### 成功段写入规则

- NEW：回调时创建新 Subscription；Record 写 `generation=newSub.id`、`serviceStart=paySuccessAt`、`serviceEnd=plusMonths`、`valueStatus=AVAILABLE`。
- RENEW：锁定当前 Subscription；Record 写 `generation=current.id`、`serviceStart=current.endAt`、`serviceEnd=current.endAt.plusMonths(...)`；Subscription.endAt 更新为该 serviceEnd。连续提前续费自然形成首尾相接的多个段。
- UPGRADE：目标 Record 在成功回调中创建新 Subscription 世代并写从成功时刻开始的目标 SKU 服务段；其 `sku_price_snapshot=targetSku.price`，即使 `pay_price` 仅为补差价或零。
- 管理员赠送和兑换码不创建 paid Record，故不产生可抵扣名义价值；后续升级只抵扣真实成功购买段。

## 分段预付价值与升级

### 候选段

升级创建订单时，在 checkout guard 和当前 ACTIVE 行锁内查询并按 `id ASC FOR UPDATE` 锁定：

```text
user_id = current.userId
service_generation_id = current.id
pay_status = PAID
fulfillment_status = FULFILLED
value_status = AVAILABLE
service_end_at > now
```

只允许当前 Subscription 世代；旧世代、已 SUPERSEDED、已结束、COMPENSATION_PENDING 和赠送开通均不参与。

### 逐段公式

对每一候选段 `r`：

```text
overlapStart = max(now, r.service_start_at)
overlapEnd   = r.service_end_at
segmentMicros = MICROS(r.service_start_at, r.service_end_at)
remainMicros  = MICROS(overlapStart, overlapEnd)
segmentRemain = HALF_UP(r.sku_price_snapshot * remainMicros / segmentMicros) 到整数分
oldRemainValue = Σ segmentRemain
payable = max(0, targetSku.price - oldRemainValue)
```

时间以数据库 `TIMESTAMP(6)` 微秒精度归一；每段独立按自身自然月区间和自身名义快照计算，再求和。完全未来段 `remainMicros=segmentMicros`，其名义价值全额计入；正在消费段只计未消费比例。不得用 `current.startAt/current.endAt` 摊薄一条 SKU 价格，不得读取最新 source Record 代表整段订阅，也不得使用 `pay_price` 当名义价值。

下单时把候选段 ID、各段计算值、合计和计算时刻保存到 Record `ext`/审计快照（或等价结构化字段）；回调时在同一 guard 下重新锁定和重算。若金额或状态与订单创建快照不一致，正常未支付订单进入 CLOSED 并返回状态变化；支付已成功则进入 COMPENSATION_PENDING，不能按旧快照履约。

### 防止连续升级重复抵扣

UPGRADE 成功事务：

1. 获取 checkout guard；锁当前 ACTIVE；锁目标 Record；按 ID 锁全部候选旧段。
2. 以回调时刻重算并确认应付和订单金额。
3. 创建目标 Subscription 新世代和目标服务段。
4. 将本次实际纳入抵扣的旧世代所有 AVAILABLE 候选段更新为 `SUPERSEDED`，写 `superseded_by_record_id=targetRecord.id`、`superseded_at=effectiveAt`。
5. 结束旧 Subscription，激活新 Subscription，执行既有升级权益实例化和积分三笔结算。
6. 目标 Record 置 FULFILLED；同一事务提交。

即使 `oldRemainValue >= targetSku.price` 且 payable=0，仍执行第 4 步；本期不退款，超出目标名义价格的旧价值不跨世代保留。下一次升级只能读取新世代目标段，不会再次读取旧续费段。

覆盖示例：

- 提前续费：当前段按剩余比例，未来续费段全额。
- 多次续费：每个未来段各计一次；不只取最后一条。
- 月末：`Jan-31 + 1 month = Feb-28`，该段分母是自身 Jan31→Feb28；下一段从 Feb28 继续，不硬编码 30 天。
- 连续升级：PRO 世代段在 PRO→TEAM 后 SUPERSEDED；TEAM→ENTERPRISE 只读取 TEAM 新世代段。

## 结账互斥、锁与异常付款

### 始终可锁的 billing guard

v5 新增纯 billing 技术表：

```sql
CREATE TABLE billing_checkout_guard (
    user_id     BIGINT PRIMARY KEY,
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

不外键到 system.user，避免 billing 直接依赖 user entity/repository；userId 由认证上下文提供。Repository/API 精确契约：

```java
public interface MembershipCheckoutGuardRepository {
    @Modifying
    @Query(value = """
        INSERT INTO billing_checkout_guard(user_id, create_time, update_time)
        VALUES (:userId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (user_id) DO NOTHING
        """, nativeQuery = true)
    void ensureGuard(@Param("userId") Long userId);

    @Query(value = """
        SELECT user_id FROM billing_checkout_guard
        WHERE user_id = :userId FOR UPDATE
        """, nativeQuery = true)
    Long lockGuard(@Param("userId") Long userId);
}

public interface PayOrderQueryApi {
    boolean isLive(Long payOrderId, LocalDateTime now); // WAITING 且 expireTime > now
}
```

所有会员下单、会员码激活、支付成功履约、取消/降级/续费、到期切换按需先 `ensureGuard` 再 `lockGuard`。跨业务包只依赖 pay 模块暴露的 `PayOrderQueryApi`，billing 不访问 pay repository。

### 下单事务

`subscribe(skuCode, channelCode)` 单事务顺序：

1. `ensureGuard(userId)`，随后 `SELECT ... FOR UPDATE` 锁 guard。
2. 锁 ACTIVE Subscription（无行也不影响互斥）。
3. `findLiveByUserIdForUpdate` 查 `fulfillment_status=PENDING`；用 `PayOrderQueryApi.isLive` 校验。已关闭/过期则先置 CLOSED；仍 live 则抛 `SUBSCRIPTION_PENDING_PAYMENT_EXISTS`（HTTP 409），返回已有 payOrderId 供客户端继续处理，不创建第二单。
4. 读取并校验目标 SKU/Plan，分类操作，锁升级候选段并计算金额。
5. 依次创建 BizOrder、PayOrder、SubscriptionRecord(PENDING)，绑定关系并提交。
6. `uk_subscription_record_live_checkout` 是遗漏检查时的数据库终防线；唯一冲突同样映射 409，不重试创建。

### 支付成功事务与极端双成功

渠道 `pay_order` 保持 SUCCESS 作为不可抹除的收款事实。`SubscriptionService.onPaySuccess`：

1. 无锁读取 Record 仅取得 userId；获取该用户 checkout guard。
2. 按固定顺序锁 ACTIVE Subscription → 目标 Record → 升级候选段（ID 升序）。
3. Record 已 FULFILLED：同支付单幂等返回。Record 已 COMPENSATION_PENDING：幂等返回异常状态。
4. 重新分类、重算、核对世代和订单金额。仍合法则只履约一次，Record=FULFILLED，BizOrder=PAID。
5. 若 Record 已 CLOSED、另一个不同支付单已先改变订阅世代/操作结果、存在后到重复成功或金额快照失效，则：
   - Record `pay_status=PAID`、`fulfillment_status=COMPENSATION_PENDING`；
   - 写 `exception_code`（至少 `DUPLICATE_SUCCESS`、`CHECKOUT_CLOSED_BUT_PAID`、`OPERATION_STATE_CHANGED`、`AMOUNT_SNAPSHOT_CHANGED`）、reason、detectedAt；
   - BizOrder 状态置 `COMPENSATION_PENDING`；PayOrder 保持 SUCCESS；
   - 不创建/修改 Subscription，不调用 `instantiateQuotas`、升级积分结算、月度积分、佣金或任何退款 API；
   - handler 正常返回，使 PayNotifyTask=SUCCESS。`PayNotifyService` 将 BizOrder 的 PAID 与 COMPENSATION_PENDING 都视为业务通知终态。
6. 运营通过 v101 的只读 Record 资源筛选 COMPENSATION_PENDING；人工处理后只允许专用管理动作记录 `compensation_resolved_at/result`。本期不自动退款、不实现退款审批或执行。

### 锁顺序与死锁规避

同一用户所有会员写操作遵守：

```text
checkout_guard(userId)
  → ACTIVE subscription
    → target/live subscription_record（按 id 升序）
      → value segment records（按 id 升序）
        → BizOrder/PayOrder application service
```

首次查询 Record 取得 userId 不加锁且不据此写入，拿到 guard 后必须重读。SKU/Plan 仅只读，不在持锁顺序中升级为写锁。禁止在持有 Record 锁后再反向获取 guard；禁止自动重试整个支付业务动作。不同用户无共享 guard，降低交叉死锁面。

## auto_renew 全链删除

原子删除清单：

- v5 删除 `billing_subscription.auto_renew` 与注释。
- `Subscription` 删除字段/getter/setter；`SubscriptionVO`、管理 VO、前端类型删除 `autoRenew`。
- `SubscriptionCrudService` 和 v101 删除字段读取、展示和过滤。
- `SubscriptionService.cancel` 只幂等写 `cancelled_at`；RENEW 成功只清空 `cancelled_at`。
- `SubscriptionExpireScheduler` 删除 `@Autowired(required=false) SubscriptionAutoRenewService` 和 `tryAutoCharge` 分支；付费 pending 无支付事实时冻结至 FREE。
- 删除 `SubscriptionAutoRenewService.java`，不留空接口、TODO、注释调用示例或替代字段。
- 删除/改写所有 `setAutoRenew/getAutoRenew/autoRenew=false` 测试、fixture、FAQ 和前端文案。
- 任何 Scheduler 不得创建会员 BizOrder/PayOrder 或调用结算渠道。

`cancelled_at` 仅表示“当前付费周期结束后不继续保持付费套餐”；它不是渠道签约状态。取消不提前终止权益、不退款，成功同 SKU RENEW 清空该时间。

## 支付宝自动续费后续方案（暂不实现）

本节只记录后续演进边界，不改变 AAF-099 当前范围：本期不新增 Schema、REST、渠道接口、调度器或前端入口，继续保持用户主动选择同一 SKU 并完成一次性支付的手动续费。正式实施前必须先确认商户已签约支付宝周期扣款/代扣产品，并以支付宝当期开放接口、签约条款和合规要求为准完成独立需求与设计审核。

### 设计原则

- 用户必须显式开启并在支付宝完成协议签约；未签约、协议失效或用户解约时不得后台扣款。
- 自动续费使用独立代扣能力，不允许 Scheduler 调用现有 `alipay_pc/alipay_wap` 页面支付，也不把二维码或收银台支付伪装为自动续费。
- Plan 继续负责等级与权益，SKU 继续负责周期与价格；每次续费按签约时选定、扣款前仍可售的明确 SKU 创建 RENEW 订单。
- 支付成功与订阅履约继续分离：渠道扣款成功后复用 `PayNotifyService → SubscriptionService`，只有 checkout `FULFILLED` 才延长订阅；异常进入 `COMPENSATION_PENDING`。
- 不保存银行卡、账户密码等支付凭证，只保存支付宝协议号及必要状态；所有签约、扣款、解约回调必须验签、幂等并留审计记录。

### 数据模型

后续新增独立的自动扣款协议聚合，不复用 `cancelled_at` 表达渠道协议状态：

```text
billing_payment_mandate
  id / user_id / channel_code / agreement_no
  status(PENDING/ACTIVE/SUSPENDED/REVOKED/EXPIRED)
  signed_at / revoked_at / expire_at
  create_time / update_time

billing_subscription（扩展）
  renewal_mode(MANUAL/AUTO)
  renewal_sku_id
  renewal_mandate_id
  next_charge_at

billing_renewal_attempt
  subscription_id / service_generation_id / target_period
  sku_id / pay_order_id / mandate_id
  status / attempt_no / next_retry_at / failure_code
```

`agreement_no` 必须按敏感标识保护；`subscription_id + service_generation_id + target_period` 建唯一约束，保证同一服务世代、同一目标周期最多创建一个逻辑续费尝试。具体 DDL 必须使用届时批准的新迁移任务，不回改本轮 v5/v12/v101。

### 服务与渠道边界

新增支付宝周期扣款适配能力，与现有一次性 `PayChannelAdapter.charge()` 分离，最小职责为：

```java
interface RecurringPaymentAdapter {
    AgreementSignResult createAgreement(AgreementSignRequest request);
    AgreementStatus queryAgreement(String agreementNo);
    void terminateAgreement(String agreementNo);
    RecurringChargeResult charge(RecurringChargeRequest request);
}
```

业务层新增 mandate service 和 renewal service：前者负责签约、查询、解约及回调验签后的协议状态迁移；后者只在 ACTIVE 协议下创建幂等 RENEW BizOrder/PayOrder，并遵守既有 `checkout_guard → ACTIVE Subscription → Record` 锁顺序。渠道协议状态不能由前端参数直接修改。

### 续费流程

```text
用户主动开启自动续费
  → 服务端创建签约请求
  → 用户跳转支付宝确认协议
  → 支付宝签约回调验签
  → mandate=ACTIVE，subscription.renewal_mode=AUTO
  → 到期前调度创建唯一 renewal attempt
  → 按 renewal_sku_id 服务端定价并调用协议扣款
  → 支付宝扣款回调或主动查单
  → PayOrder SUCCESS
  → PayNotifyService 路由 RENEW 履约
  → checkout FULFILLED 后顺延自然月周期并计算 next_charge_at
```

调度时间、有限重试次数和间隔必须配置化，并受支付宝产品规则约束。扣款失败、协议失效或最终重试耗尽时不得履约、不得延长周期；系统发送提醒并保留用户一键手动续费，到期后继续由现有调度回落 `FREE_DEFAULT`。

### REST 与前端边界

后续最小接口集合：

```http
POST   /api/billing/subscriptions/me/auto-renew/sign
GET    /api/billing/subscriptions/me/auto-renew
DELETE /api/billing/subscriptions/me/auto-renew
POST   /api/pay/orders/notify/alipay/agreement
POST   /api/pay/orders/notify/alipay/recurring
```

前三个接口必须登录并校验当前订阅归属；支付宝回调端点公开但必须验签。会员页展示续费 SKU、扣款金额/周期、协议状态、下次扣款时间，并提供开启和关闭入口；开启前明确授权，关闭时同时停止本地 AUTO 状态并调用支付宝解约，失败则显示真实协议状态，不能伪报关闭成功。

### 实施阶段与验收门禁

1. 商户资质与支付宝沙箱验证：确认可用产品、接口、回调和扣款限制。
2. 协议签约闭环：签约、查询、解约、验签、幂等和审计。
3. 自动扣款闭环：唯一续费尝试、调度、有限重试、主动查单和通知。
4. 订阅履约与补偿：复用现有 RENEW、checkout 和 `COMPENSATION_PENDING`，覆盖重复回调、扣款成功但履约失败、协议失效和到期回落。
5. 前端与合规验收：明确授权、扣款前提醒、失败提醒、随时关闭和手动续费兜底。

在上述设计另立任务并获人类批准前，仓库不得重新加入 `auto_renew` 字段、`SubscriptionAutoRenewService` 占位接口或任何自动创建支付单的会员 Scheduler。

## REST 契约

### 套餐目录

```http
GET /api/billing/subscription-plans/catalog
```

```java
public record SubscriptionPlanVO(
        Long id,
        String code,
        String name,
        Long monthlyCredits,
        String ext,
        List<SubscriptionSkuVO> skus,
        List<PlanEntitlementVO> entitlements,
        String status,
        Integer sort) {}

public record SubscriptionSkuVO(
        Long id,
        String skuCode,
        String billingCycle,
        Integer cycleMonths,
        Long price,
        Long marketPrice,
        String status,
        Integer sort,
        String ext) {}
```

目录只返回 ENABLED Plan 下 ENABLED 正价 SKU；FREE Plan 返回 `skus=[]`，`FREE_DEFAULT` 不作为可购项。响应不存在 Plan `durationDays/price/marketPrice/yearlyPrice`。

### 订阅动作

```http
POST /api/billing/subscriptions/subscribe
{ "skuCode": "PRO_Q1", "channelCode": "alipay_pc" }

GET /api/billing/subscriptions/me

POST /api/billing/subscriptions/me/cancel

POST /api/billing/subscriptions/me/downgrade
{ "skuCode": "PRO_M1" }

DELETE /api/billing/subscriptions/me/pending-downgrade
```

```java
public record SubscribeDTO(
        @NotBlank @Size(max = 64) String skuCode,
        @NotBlank @Size(max = 32) String channelCode) {}

public record DowngradeDTO(@NotBlank @Size(max = 64) String skuCode) {}

public record AdminSubscriptionDTO(@NotBlank @Size(max = 64) String skuCode) {}
```

当前订阅 VO 返回当前 skuId/skuCode/billingCycle/cycleMonths、start/end/status/cancelledAt、pending SKU 摘要；不返回 autoRenew、pendingPlanCode 或 pendingYearly。

### 兑换码动作

```http
POST /api/billing/credit-redeem-codes/generate
{
  "type": "MEMBERSHIP",
  "creditAmount": 0,
  "skuCode": "PRO_Q1",
  "expiresAt": "2027-01-01T00:00:00",
  "remark": "季度会员码"
}

POST /api/billing/credit-redeem-codes/redeem
{ "code": "CRED-..." }
```

只提交 Plan、缺失 SKU、FREE_DEFAULT、禁用/非法 SKU 均 400；不存在默认月付。

## Billing 错误码

| 常量 | code | HTTP | 消息/用途 |
|------|------|------|-----------|
| `SUBSCRIPTION_SKU_NOT_FOUND` | 9_000_006 | 400 | SKU 不存在。 |
| `SUBSCRIPTION_SKU_DISABLED` | 9_000_007 | 400 | SKU 不可售。 |
| `SUBSCRIPTION_RENEW_ACTIVE_REQUIRED` | 9_000_008 | 409 | 当前状态不可续费。 |
| `SUBSCRIPTION_DOWNGRADE_ENDPOINT_REQUIRED` | 9_000_009 | 400 | 降级应走降级入口。 |
| `SUBSCRIPTION_PENDING_PAYMENT_EXISTS` | 9_000_010 | 409 | 已有 live 会员结账。 |
| `SUBSCRIPTION_OPERATION_STATE_CHANGED` | 9_000_011 | 409 | 状态变化，未支付请求刷新。 |
| `SUBSCRIPTION_INTERNAL_SKU_NOT_PURCHASABLE` | 9_000_012 | 400 | FREE_DEFAULT 不可购买/兑换。 |
| `SUBSCRIPTION_SKU_PLAN_INVALID` | 9_000_013 | 400 | SKU 与 FREE/付费 Plan 归属不合法。 |
| `REDEEM_MEMBERSHIP_SKU_REQUIRED` | 9_000_014 | 400 | 会员码必须指定明确 SKU。 |
| `SUBSCRIPTION_PAYMENT_COMPENSATION_PENDING` | 9_000_015 | 202 | 收款已登记人工补偿待处理，不代表履约成功。 |

支付回调是服务间可靠通知，不把 COMPENSATION_PENDING 当异常抛出重试；查询/管理接口展示该状态。

## 周期与生命周期

```java
public final class SubscriptionPeriodCalculator {
    public static LocalDateTime endAt(LocalDateTime startAt, int cycleMonths);
    public static LocalDateTime renewEndAt(LocalDateTime currentEndAt, int cycleMonths);
}
```

- FREE_DEFAULT cycleMonths=0，endAt=null。
- NEW/UPGRADE/会员码：effectiveAt.plusMonths(cycleMonths)。
- RENEW：锁定后的 current.endAt.plusMonths(cycleMonths)，并把旧 endAt→新 endAt 作为独立服务段。
- `2026-12-31T10:00 + 3 months = 2027-03-31T10:00`；逐段按各自实际微秒数计算价值。
- RENEW 不调用 `EntitlementService.instantiateQuotas`、`CreditService.settleSubscriptionUpgrade`、`CreditService.earnBatch`、RESET/REFILL；月度积分仍按既有 30 天调度。
- pending 付费 SKU 到期时无已支付新购事实，不自动收费，旧订阅 EXPIRED 后激活 FREE_DEFAULT。

## 前端与 Workspace pricing

### 共享契约

`plans.ts` 一次性切换 `BillingCycle = "MONTH" | "QUARTER" | "YEAR" | "PERPETUAL"` 和 `skus[]`；mutation 请求只发送 skuCode/channelCode。服务端状态由 TanStack Query 管理，不复制到 Zustand。

`BillingCycleToggle` 提供月/季/年。`PlanCard` 接收选中 SKU，价格/市场价直接读取后端；删除 `yearlyPrice`、`price*12`、折扣和等效月价公式。`SubscriptionPayDialog` 接收 skuCode/displayName/price/billingCycle，仍复用现有渠道、二维码、跳转和轮询。

### 会员页

`/studio/me/membership` 是唯一交易和订阅管理页：新购、升级、同 SKU 续费、取消、降级、撤销降级均在此完成。前端不提交 operation，后端分类。

### Workspace pricing

`apps/webui/src/app/(workspace)/settings/pricing/page.tsx`：

- 继续调用同一个 `GET /subscription-plans/catalog`，三周期展示后端 SKU 明示价格和权益。
- 删除“联系客服开通”交易中转及该 CTA 的弹窗状态；CTA 使用 Next Link/router 导航 `/studio/me/membership`。
- 不调用 subscribe/cancel/downgrade，不打开 `SubscriptionPayDialog`，不复制价格或权益矩阵。
- 登录态/非登录态均可只读；当前订阅仅用于“当前套餐”展示，不改变 CTA 的交易边界。
- `/studio/me/pricing` 模型能力收费标准页保持不变。

## SQL 范围与 #16 原子门禁

唯一允许修改的 SQL：

1. `apps/service/aaf-api/src/main/resources/db/migration/v5__order_schema.sql`：Plan/SKU、Subscription、Record、checkout guard、兑换码、BizOrder COMPENSATION_PENDING 注释/约束；删除旧字段。
2. `apps/service/aaf-api/src/main/resources/db/seed/v12__init_seed_data.sql`：Plan/SKU 静态价格、四类 AIGC 权益矩阵、工具目录提示字段、FAQ、SKU/Plan seed 断言。
3. `apps/service/aaf-api/src/main/resources/db/seed/view/v101__billing_entity_def.sql`：Plan/SKU/Subscription/Record/兑换码管理元数据。

不得新增 SQL，不得修改 v13/v17/v102 或其他脚本。本轮是全新库替换；已执行旧 checksum 的开发库必须删除并重建，不得用 `flyway repair` 冒充迁移。

#16 不是“只改 SQL 后交给下游补齐”，而是 bootstrap 原子切片：同时完成使新 Schema/EntityDef 可编译启动所需的 billing domain、DTO/VO、Repository、受控 CRUD/Controller/provider、旧字段调用删除。完成判定必须依次通过：

```text
空 PostgreSQL 数据库/全新数据卷
  → 执行全部 migration
  → 执行全部 seed（含 v12 与 v101）
  → 应用启动成功
  → CrudResourceCompiler 编译所有 Billing EntityDef 成功
  → smoke 读取 Plan/SKU/Subscription/兑换码/Record 管理资源元数据
  → 确认 git diff 中无新增 SQL 文件且只改 v5/v12/v101
```

任一步失败，#16 未完成，#17 及所有下游不得开始。

## 最小测试与集中验证

遵循用户“开发期尽量不添加/执行测试”的约束：不为普通映射和样式扩充广泛测试；只为资金、权限、并发、破坏性 DDL 增加最少高风险测试，代码完成后集中一次运行。不得删除项目强制 `pnpm check:affected` 和必要验收。

最小自动化集合：

- AIGC：一组参数化 Guard + 三入口一致性测试。
- 兑换码：一组 `PRO_Q1` 精确激活、缺失/禁用/FREE SKU 拒绝测试。
- 价值：一组表驱动测试覆盖提前续费、多次续费、Jan31/Feb28 月末、连续升级和 pay_price≠sku snapshot。
- 并发：一组 PostgreSQL 集成测试覆盖 FREE 用户并发 NEW 只一条 live Record、第二请求 409、两不同 PayOrder 成功时后到 COMPENSATION_PENDING 且零履约副作用。
- DDL/metadata：#16 全新库 smoke；最终阶段重复一次并读取 EntityDef。
- 前端：一组共享组件/Workspace pricing 测试，证明三周期不推导价格、CTA 只跳会员页。
- 验收：对 requirement v1.0.2 的高风险 AC 建最小覆盖矩阵；其余已有生命周期 AC 可复用现有测试证据，不复制测试。

最终集中执行：

```text
pnpm check:affected
pnpm acceptance:affected
```

静态扫描必须覆盖生产、测试、SQL、前端：

```text
YEARLY_DISCOUNT
YEARLY_DAYS
yearlyPrice
pendingYearly
pending_yearly
autoRenew
auto_renew
SubscriptionAutoRenewService
durationDays（会员 Plan 语境）
billing_subscription_plan.price
price * 12 / price*12 / ×12×0.8
plusDays(365) / plusDays(30)（订阅周期语境）
planId（会员兑换码商品身份语境）
```

允许其他领域合法使用 `YEARLY` reset cycle、其他商品 price 和日期 plusDays；扫描结果须逐条人工归类，不能机械全局删除。

## 破坏性变更与回滚

- 删除 Plan 价格/时长、Subscription auto_renew/pending Plan/yearly、Record yearly、兑换码 plan_id；新增 SKU、服务段、checkout guard 和异常付款状态。
- REST 从 planCode+billingCycle 改 skuCode，catalog/current subscription/兑换码 DTO/VO 破坏性变化。
- 旧前端、旧测试和旧数据库不能与新代码混用，不提供兼容层。
- 开发环境回滚必须把代码与 v5/v12/v101 一起回退到同一 Git 版本并重建数据库。
- 若进入共享测试/生产后需要保留数据，必须停止本方案并另立真实迁移任务重新审核；不得直接改已执行 SQL。

## 人类批准门禁

本设计仍为 `draft`，风险等级 🔴。以下项目必须由人类明确批准后，协调者才可把 #16 置为可执行；未批准前不得修改源码或 SQL：

- [ ] 同意开发期删除并重建数据库，且只原地修改 v5/v12/v101。
- [ ] 同意兑换码从 Plan 一次性切 SKU，不提供默认月付兼容。
- [ ] 同意分段名义价值以 `sku_price_snapshot` 计算，`pay_price` 仅为实付；零差价不退款。
- [ ] 同意升级成功把旧世代候选段全部 SUPERSEDED，超额旧价值不跨世代保留。
- [ ] 同意同用户一个 live 结账；额外成功款只进入人工 `COMPENSATION_PENDING`，本期不自动退款。
- [ ] 同意 AIGC 无会员权益返回 403、积分不足返回 402，配置缺失 fail closed 为 500。
- [ ] 同意彻底删除 auto_renew 和代扣占位路径。
- [ ] 同意 Workspace pricing 仅只读并跳 `/studio/me/membership`。
- [ ] 批准人、日期和结论已记录到任务审核记录或对话审计轨迹。

`ui-design.md status: active` 只表示 UI 候选稿可供本设计引用，不覆盖上述门禁。design.md 未转为 approved/published、tasks.md 未记录人类批准时，任何 agent 都不得把 UI active 解释为开发授权。
