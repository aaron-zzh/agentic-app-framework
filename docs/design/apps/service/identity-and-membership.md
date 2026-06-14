---
level: Practice
layer: Model
purpose: AAF 身份与会员数据模型设计（联系人 / 用户 / 订阅 / 权益 / 钱包及其与权限的整合）
status: draft
version: 0.2.0
date: 2026-05-29
author: AaronZZH
changelog:
  - 2026-05-29 | 会员权益改为关系化模型（subscription_plan/entitlement/quota/ledger）
  - 2026-05-29 | 初版：统一身份 + 联系人分层 + level.benefits
gains:
  - 能理解 AAF 为何采用统一身份模型而非 admin/member 分表
  - 能快速判断联系人、用户、订阅、权益各自的边界与归属
  - 能理解商业权益（entitlement）如何与四层权限模型平行解耦
  - 能理解 token 计费如何落地为可计量、可对账的权益消费
---

# 身份与会员设计（Identity & Membership）

> 一个人就是一个身份。能登录的是用户，会消费的是会员，还没注册的是联系人——它们是同一身份的不同侧面，不是不同的人。

## 设计立场

在 AAF 里，**同一个人既可能是后台管理者、又是前台使用者、还是 token 消费者**，采用**统一身份**而非"管理员表 + 会员表"分离模式。

**"能不能登录"是身份的一种能力，而不是身份本身**。AAF 据此把身份拆成三层语义，但每一层职责单一、互不重复。

## 身份三层模型

```text
┌──────────────────────────────────────────────────────────┐
│  contact（联系人 / 伙伴）                                 │
│   所有"人/组织"的统一身份本体。客户线索、供应商、渠道关注者、 │
│   访客都是 contact。不一定能登录。                         │
│        │                                                 │
│        │ 1:1（绑定登录能力后）                             │
│        ▼                                                 │
│  user（用户 / 账号）                                      │
│   contact 中"能登录系统"的子集。有角色、有权限、是 Operator。│
│   后台与前台共用同一张表、同一个 user_id。                  │
│        │                                                 │
│        │ 1:1（消费身份，非独立实体）                       │
│        ▼                                                 │
│  membership（会员资格）                                   │
│   user 的一种消费状态：等级 + 钱包。不是另一个人。           │
│   所有 user 默认即会员（L0 免费档），充值/成长后升级。       │
└──────────────────────────────────────────────────────────┘
```

核心判断：

- **联系人 ≠ 用户**：联系人是数据对象（CRM 线索、供应商、匿名访客），可能永远不登录。
- **用户 = 能登录的联系人**：一个 user 必然对应一个 contact；一个 contact 不一定有 user。
- **会员不是独立的人**：会员是 user 的"消费身份"，用等级 + 钱包表达，**不单独建 member_user 表**。

### 为什么不用 admin/member 分表

分表会带来五个真实问题：同一个人需要两条记录、跨端身份无法打通、后台登录被限死在管理员、积分/token 只有会员有、两套 RBAC 难以维护。统一身份用"角色决定能看到什么、等级决定能用多少"取代物理分表，更贴合 PaaS 语义。

### 为什么需要 contact 这一层

没有 contact 层，访客咨询、渠道关注者（只有 openid）、CRM 客户线索这些"还不是用户的人"就无处安放。引入 contact 后：

- 微信公众号关注者 → 建 contact + user_identity(wechat_oa)，user 字段可空
- 客服访客 → 建 contact（visitor 状态），转化注册时再补 user

## 核心实体

### contact（联系人）

```text
contact
  id              BIGINT PK
  type            VARCHAR(16)   -- PERSON=个人 / ORG=组织
  name            VARCHAR       -- 显示名
  real_name       VARCHAR       -- 真实姓名（可空）
  avatar          VARCHAR
  email           VARCHAR
  phone           VARCHAR
  source          VARCHAR(16)   -- 来源：REGISTER / IMPORT / CHANNEL / VISITOR
  status          VARCHAR(16)   -- ACTIVE / LEAD / VISITOR / ARCHIVED
  parent_id       BIGINT FK→contact  -- 所属组织（自关联）
  ext             JSONB         -- 扩展字段（地区、标签、行业等）
  + BaseEntity 审计字段（create_by / owner_id 等）
```

### user（用户）

```text
user
  id              BIGINT PK
  contact_id      BIGINT FK→contact  -- 可空；有真实人对应时填写，系统账号/机器人为 null
  username        VARCHAR UNIQUE     -- 登录账号（可空，OAuth 用户可无）
  nickname        VARCHAR
  status          VARCHAR(16)        -- ACTIVE / DISABLED / LOCKED
  last_login_at   TIMESTAMP
  last_login_ip   VARCHAR
  + BaseEntity 审计字段
```

> 与现有 `module/system/user` 对齐：保留现有 user 子域，新增 `contact_id` 可空外键。
> `contact_id NULLABLE` 是有意设计：系统内置账号（admin/system）、API 机器人、测试账号不对应真实联系人，强制 NOT NULL 会造成无意义的 contact 记录。

### user_identity（认证方式）

> **ADR 决策（2026-06-14）**：`user_identity` 不单独建表，由以下三者组合替代，无需引入额外抽象：
>
> | 认证方式 | 存储位置 |
> |---|---|
> | 密码登录、手机号、邮箱 | `sys_user`（username / phone / email 字段） |
> | OAuth 登录 token（微信/企微/钉钉） | `sys_user_oauth`（access_token / refresh_token） |
> | 渠道身份索引（企微userId、微信openId等） | `sys_contact_identity`（channel / external_id） |
>
> **`sys_user_oauth` 与 `sys_contact_identity` 分工**：
> - `sys_user_oauth`：登录凭证管理——"你用这个平台账号登录了系统，这是你的 token"（必须有 sys_user）
> - `sys_contact_identity`：渠道身份索引——"你在各个平台叫什么 ID，用于发消息和同步"（不要求有 sys_user，外部客户也可以有）

## 权益模型：成长线 + 付费线双轨

会员权益要回答四个问题，对应四类数据：

| 问题 | 本质 | 载体 |
|------|------|------|
| 你**能用什么** | 能力开关（布尔权益） | entitlement_def(type=BOOLEAN) |
| 你**能用多少** | 资源额度（计量权益，按周期重置） | entitlement_def(type=COUNTABLE) + quota |
| 你**怎么获得** | 成长（免费）或购买（付费） | level（成长）/ subscription（付费） |
| 你**用了多少** | 消费留痕（可审计、对账、退款） | entitlement_ledger + wallet_transaction |

获取权益有两条独立的线，**不要混进同一个概念**：

```text
成长线（免费）：exp 成长值 → level 等级 → 小权益（签到加成、基础配额）
付费线（变现）：购买 subscription_plan → 有效期内 → 主力权益（token / 模型 / 存储）
```

### 权益定义层

```text
entitlement_def           权益字典（一个权益一条，code 驱动）
  id            BIGINT PK
  code          VARCHAR UNIQUE  -- ai_token / model_gpt4 / kb_storage / workflow_run ...
  name          VARCHAR
  type          VARCHAR(16)     -- BOOLEAN（开关型）/ COUNTABLE（计量型）
  unit          VARCHAR         -- 计量单位：token / 次 / GB（BOOLEAN 为空）
  description   VARCHAR

subscription_plan         套餐定义（货架商品，运营后台配置）
  id            BIGINT PK
  code          VARCHAR UNIQUE  -- FREE / PRO / TEAM / ENTERPRISE
  name          VARCHAR
  duration_days INT             -- 有效天数（FREE 为永久 0）
  price         BIGINT          -- 售价（分）
  market_price  BIGINT          -- 市场价（划线价）
  status        VARCHAR(16)     -- ENABLED / DISABLED
  sort          INT

plan_entitlement          套餐 × 权益规则（多对多，定价与权益解耦）
  id            BIGINT PK
  plan_id       BIGINT FK→subscription_plan
  ent_id        BIGINT FK→entitlement_def
  quota         BIGINT          -- 授予额度（COUNTABLE 用；-1=无限）
  reset_cycle   VARCHAR(16)     -- 重置周期：NONE / DAILY / MONTHLY / YEARLY
  refill_price  BIGINT          -- 额度用尽后单次充值价（积分），0=不可充值
  UNIQUE(plan_id, ent_id)
```

> 同一个"ai_token"权益，FREE 套餐配 quota=10000、PRO 配 quota=1000000、ENTERPRISE 配 quota=-1（无限）。定价改 plan，权益规则改 plan_entitlement，互不影响。

### 订阅授予层

```text
subscription              用户订阅实例（购买后产生，决定有效期）
  id            BIGINT PK
  user_id       BIGINT FK→user
  plan_id       BIGINT FK→subscription_plan
  start_at      TIMESTAMP
  end_at        TIMESTAMP       -- 到期时间（永久套餐为空）
  status        VARCHAR(16)     -- ACTIVE / EXPIRED / CANCELLED
  source_id     BIGINT          -- 关联购买流水 subscription_record.id

subscription_record       购买流水（新购 / 续费，对接支付）
  id              BIGINT PK
  user_id         BIGINT FK→user
  plan_id         BIGINT FK→subscription_plan
  operation       VARCHAR(16)   -- NEW（新购）/ RENEW（续费）
  pay_order_id    BIGINT        -- 关联 pay 模块订单
  pay_price       BIGINT
  pay_status      VARCHAR(16)   -- UNPAID / PAID
  pay_time        TIMESTAMP
```

### 额度消费层

```text
entitlement_quota         用户当前额度实例（订阅生效时按 plan_entitlement 实例化）
  id              BIGINT PK
  user_id         BIGINT FK→user
  ent_id          BIGINT FK→entitlement_def
  total           BIGINT        -- 本周期总额度
  used            BIGINT        -- 已用
  remain          BIGINT        -- 剩余（= total - used + 充值）
  last_reset_at   TIMESTAMP
  next_reset_at   TIMESTAMP     -- 周期重置时间（定时任务扫描）
  UNIQUE(user_id, ent_id)

entitlement_ledger        额度变更流水（账本，每次扣减/充值/重置留痕）
  id            BIGINT PK
  quota_id      BIGINT FK→entitlement_quota
  delta         BIGINT          -- 变化量（负数=消费，正数=充值/重置）
  operation     VARCHAR(16)     -- USE / REFILL / RESET / ADJUST
  biz_type      VARCHAR(24)     -- AI_CALL / KB_UPLOAD / MANUAL ...
  biz_id        BIGINT
  created_at    TIMESTAMP
```

### 成长线 + 积分账户

```text
level                     成长等级（免费，按 exp 自动升降，给小权益）
  id            BIGINT PK
  code          VARCHAR UNIQUE  -- L0 / L1 / L2 ...
  name          VARCHAR
  exp_min       INT
  exp_max       INT
  perks         JSONB           -- 成长小权益（签到加成倍率、基础配额加成等，轻量）
  sort          INT

credit_account            积分账户（1:1 挂 user，同时承载成长体系）
  user_id       BIGINT PK FK→user
  balance       BIGINT          -- 当前可用积分总余额（= 所有批次 remain 之和）
  frozen        BIGINT          -- 冻结中的积分
  total_earned  BIGINT          -- 累计获得
  total_spent   BIGINT          -- 累计消费
  exp           INT             -- 成长值
  level_id      BIGINT FK→level -- 当前成长等级

credit_transaction        积分流水 + 批次（1:N，每次发放产生一条批次记录）
  id            BIGINT PK
  account_id    BIGINT FK→credit_account
  type          VARCHAR(16)     -- EARN / SPEND / FREEZE / UNFREEZE / EXPIRE
  amount        BIGINT          -- 本次变化量
  balance_after BIGINT          -- 操作后账户余额
  source        VARCHAR(24)     -- 来源标识
  biz_id        VARCHAR         -- 关联业务 ID
  -- 批次有效期字段（EARN 类型时有意义）
  batch_type    VARCHAR(16)     -- SUBSCRIPTION / TOPUP / REWARD / WEEKLY / MANUAL
  expire_at     TIMESTAMP       -- 过期时间，NULL = 永不过期（充值积分）
  remain        BIGINT          -- 本批次剩余可用量
  created_at    TIMESTAMP
```

**积分批次有效期规则**：

| batch_type | 来源 | 有效期 |
|------------|------|--------|
| SUBSCRIPTION | 订阅月度发放 | 30 天 |
| TOPUP | 用户充值购买 | 永久（expire_at = NULL） |
| REWARD | 运营活动奖励 | 按活动设定 |
| WEEKLY | 每周免费积分 | 7 天 |
| MANUAL | 后台手动发放 | 按需设定 |

**消费顺序**：优先扣更快到期的批次（`ORDER BY expire_at ASC NULLS LAST`），永久积分最后消费。

**订阅套餐积分配置**（`subscription_plan` 扩展字段）：

```text
subscription_plan（扩展）
  + monthly_credits  BIGINT DEFAULT 0  -- 每月发放积分数，0 = 不发放
```

订阅激活时发放首月积分（30 天有效期）；此后每月由定时任务自动发放。详见 [订阅积分批次化设计](subscription-credit-batch.md)。

> 暂不引入独立的 wallet 表。`credit_account` 同时承担积分账户和成长体系的职责。后续如需支持余额账户、商城充值提现等多财产类型，再以 wallet 作为聚合根扩展各子账户。

> 注意分工：**token 不在积分账户里**，token 是 `entitlement_def(code='ai_token')` 的一种计量权益，挂在 entitlement_quota。`credit_account` 只管"积分"这一种通用货币，积分可用于额度充值（refill）。这样区分了"通用货币（积分）"与"专项额度（token/存储/调用次数）"，避免字段膨胀。积分本身按批次管理有效期，不同来源（订阅发放/充值购买/奖励）的积分有效期不同，消费时优先扣更快到期的批次。

### token 计费如何落地（AAF-074 #3）

```text
1. 定义 entitlement_def(code='ai_token', type=COUNTABLE, unit='token')
2. 各 subscription_plan 通过 plan_entitlement 配不同月配额 + reset_cycle=MONTHLY
3. 用户订阅生效 → 实例化 entitlement_quota（total=配额, next_reset=次月）
4. AI 调用 → 扣 entitlement_quota.remain，写 entitlement_ledger(USE)
5. 余额不足 → 用 wallet.point 走 refill（按 plan_entitlement.refill_price）
6. 月初定时任务扫 next_reset_at 到期的 quota → 重置 total，写 ledger(RESET)
```

按模型差异化定价：用 `biz_type` 或额外的 `model_weight` 配置表表达"GPT-4 每次扣 10 token、GPT-3.5 扣 1 token"，v0.9 先用调用方传入 cost，不引入复杂定价引擎。

## 与权限设计的整合

权限主体复用现有 [Operator 模型](../../framework/operator.md)，授权复用现有[四层权限架构](../../framework/security/access-control.md)。**身份模型只提供"谁"，不重造权限。**

### 关键解耦：功能权限 vs 商业权益

最容易混淆的一点——**会员套餐/等级不是 RBAC 角色**。两者解耦：

| 维度 | 功能权限（能不能做） | 商业权益（能用多少/多高级） |
|------|---------------------|---------------------------|
| 问题 | "能否访问用户管理页" | "能否用 GPT-4、月配额多少 token" |
| 载体 | RBAC 角色（role） | 权益（entitlement）+ 套餐（subscription_plan） |
| 机制 | Spring Security + 四层权限 | `@Entitlement` 配额检查器 |
| 变更频率 | 低（年级别） | 高（随运营调整套餐） |
| 谁拥有 | 凭角色（管理员/成员/访客） | 凭订阅（免费/专业/企业） |

混在一起的后果：运营调一次套餐配额就要动 RBAC 角色表，权限系统被商业策略绑架。因此**权益走独立的配额检查通道，与权限平行**。

### 五类"权限/权益"各归其位

权限四层（复用现有架构）+ 权益一层（新增），在同一调用链上各管一段、互不耦合：

| 类型 | 例子 | 归属 | 实现 |
|------|------|------|------|
| 功能权限 | 能否进后台、删用户 | role（RBAC） | Spring Security `@PreAuthorize` |
| 数据权限 | 只能看自己的对话/订单 | owner_id（记录规则） | `@DataScope` + JPA 拦截器 |
| 协作关系 | 文档分享给某人 | ReBAC 关系元组 | PermissionEvaluator |
| 动态条件 | AI 低置信度需人工确认 | ABAC | 策略引擎 |
| **商业权益** | 能用哪个模型、配额多少 | entitlement | `@Entitlement` 检查器（新增） |

### `@Entitlement` 与权限平行

新增一个与四层权限平行的权益切面，判定顺序：**先 RBAC（有无资格）→ 再 Entitlement（额度够不够）→ 执行 → 扣减留痕**。

```java
@PreAuthorize("hasPermission('ai:chat')")        // RBAC：有没有资格用 AI
@Entitlement(code = "ai_token", cost = "#tokens") // 权益：额度够不够，自动扣减
public ChatResponse chat(@P("tokens") int tokens, ...) { ... }
```

- `@Entitlement` 检查 `entitlement_quota.remain`，足够则放行并在方法成功后扣减、写 `entitlement_ledger`
- 不足则尝试 refill（消耗 wallet.point），仍不足抛 `QuotaExceededException`
- 复用 `engine/budget` 的预算控制能力实现，不另造轮子

这样运营随便调套餐配额（改 `plan_entitlement` 数据），完全不动 RBAC；反之改角色也不影响计费。

### 主体映射

```text
contact（非 user）  → 不是权限主体，仅数据对象（无登录、无角色、无权益）
user               → Operator(HUMAN)，凭 role 得功能权限、凭 subscription 得权益额度
assistant（AI）     → Operator(AI)，权限 = 委托 user 权限 ∩ scope；权益消耗委托者额度
```

> AI 代用户执行时，token 等额度从**委托者**的 entitlement_quota 扣减，与 `owner_id = 委托者` 语义一致。

### 登录入口如何区分前后台

不做物理隔离的 `/admin/login` 与 `/app/login`，而是**同一登录接口 + 角色驱动**：

```text
登录 → 签发 JWT（含 userId + roles）
     → 前端按 roles 是否含管理角色决定展示后台菜单
     → 后台路由守卫校验功能权限（RBAC）
     → 前台功能按 entitlement 配额控制可用范围
```

### 数据归属

直接复用 `BaseEntity` 已有的 `owner_id`（始终指向 user.id），无需新增机制：

- 查"我的数据"：`WHERE owner_id = currentUserId()`
- AI 代为操作：`owner_id = 委托者 user.id`，`create_by_type = 'AI'`

## 模块归位

```text
module/system/contact     ← contact（新增子域，已实现）
module/system/user        ← user + user_identity 组合（扩展现有子域，contact_id 已加）
module/system/role        ← role / permission（现有，不动）
module/billing            ← 计费域（AAF-074），含三组表：
                              · 套餐权益：subscription_plan / entitlement_def / plan_entitlement
                              · 订阅：subscription / subscription_record
                              · 消费：entitlement_quota / entitlement_ledger
                              · 积分成长：credit_account（含 exp/level_id）/ credit_transaction / level
```

> billing 通过 `module/system/api` 暴露的接口获取 user 信息，禁止直接访问 user 的 repository/entity。
> `@Entitlement` 检查器放 `aaf-framework`（复用 `engine/budget`），billing 模块提供权益数据。

## 设计决策（ADR 摘要）

| 决策 | 选择 | 原因 |
|------|------|------|
| 用户建模 | 统一 user 表，不分 admin/member | AAF 是 PaaS，B/C 用户高度重叠；分表导致双记录、双 RBAC |
| 身份本体 | 引入 contact 层 | 容纳访客 / 渠道关注者 / CRM 线索等非登录实体 |
| contact_id | `sys_user.contact_id` NULLABLE | 系统账号/机器人无需对应真实联系人，强制 NOT NULL 造成无意义记录 |
| user_identity | 不单独建表，由 sys_user + sys_user_oauth + sys_contact_identity 组合替代 | 三者职责已清晰分离（密码/凭证/渠道索引），无需引入额外抽象层 |
| 会员建模 | 会员是 user 的消费身份（订阅 + 钱包），不建 member_user | 会员不是另一个人，避免冗余表 |
| 权益模型 | 关系化 plan/entitlement/quota/ledger | token 计费是核心变现路径，须可计量/对账/退款 |
| 获取双轨 | level（成长免费）+ subscription（付费）并存 | 成长线零成本激励活跃，付费线承载变现，职责分离 |
| 权益与权限 | `@Entitlement` 与四层权限平行解耦 | 商业策略高频变更，不应绑架功能权限系统 |
| token 归属 | token 是 entitlement 计量权益，不进积分账户 | 区分"通用货币（积分）"与"专项额度"，避免积分账户字段膨胀 |
| 租户层 | 不引入 | 当前为单用户/工作区模式，按需后置（避免过度设计） |

## 落地路径（v0.9）

### 现状基线（2026-05-29 勘察）

已存在、可复用，**不重造**：

- `module/system/user`：`User`（sys_user）+ `UserOauth`（sys_user_oauth，provider=wechat/wecom/dingtalk）
- `security/oauth`：微信/企微/钉钉**真实** OAuthClient（非 Mock）
- `module/pay` + `v5__order_schema.sql`：`pay_order` / `biz_order` / `biz_order_item` / `credit_account` / `credit_transaction` / `credit_token_rule` + 充值编排 `RechargeService`
- `engine/credit`：`CreditService`（earn/spend/freeze/unfreeze/hasBudget）
- `engine/settlement`：`SettlementEngine` + `PayChannelAdapter` 接口 + **仅 `MockPayChannelAdapter`**（无真实微信/支付宝）
- `PayChannelEnum`：微信/支付宝全渠道编码已定义（wx_pub/wx_lite/wx_app/wx_native/alipay_*）
- 审计字段双轨：`org_id`/`workspace_id`（保留不用）+ `owner_id`/`create_by_type`（Operator 模型）

> 现有 `credit_account` 即本设计的积分账户，`credit_transaction` 在此基础上扩展批次有效期字段（`batch_type / expire_at / remain`），`credit_token_rule` ≈ token 计费雏形。v0.9 在此基础上补关系化权益模型，不推倒重来。

### 身份层（system 模块）

- ✅ `sys_contact` 表 + `sys_contact_identity` 表（v9__contact_schema.sql）
- ✅ `sys_user.contact_id` 可空外键（v9__contact_schema.sql ALTER TABLE）
- ✅ `user_identity` 不单独建表，由 `sys_user` + `sys_user_oauth` + `sys_contact_identity` 三者组合替代
- ⏳ 补微信小程序登录（`wx_lite` 注册登录）
- ⏳ 登录接口返回 roles，前端角色驱动菜单（webui）

### 计费层（billing 模块，AAF-074）

- ⏳ 定义层：`entitlement_def` / `subscription_plan` / `plan_entitlement` + 字典 seed + 枚举类
- ⏳ 订阅层：`subscription` / `subscription_record`（对接已有 `pay_order` / `biz_order`）
- ⏳ 消费层：`entitlement_quota` / `entitlement_ledger` + 周期重置定时任务
- ⏳ 成长钱包：`level`（新建）/ 复用 `credit_account` 作 wallet / `credit_transaction` 作流水
- ⏳ `@Entitlement` 配额检查切面（复用 `engine/credit.hasBudget`）
- 迁移脚本：直接改 `v5__order_schema.sql` 追加会员/权益表（v0.x 阶段允许）

### 支付层（AAF-073）

- ⏳ 真实 `WxPayChannelAdapter` / `AlipayChannelAdapter`（实现 `PayChannelAdapter`，Mock 默认开启）
- ⏳ 退款（`refund` 落地 + 退款单 + 状态追踪）+ 对账（账单下载 + 差异比对 + 报表）

### 后置（不在 v0.9）

- 🔵 团队/工作区 RBAC（粗粒度协作角色）→ v1.x
- 🔵 完整 CRM（contact 扩展为客户/供应商管理）→ AAF-097+
- 🔵 按模型差异化定价引擎（v0.9 先用调用方传入 cost）→ v1.x
- 🔵 一个套餐多种定价（Stripe product/price 双层）→ 有需求时再拆
