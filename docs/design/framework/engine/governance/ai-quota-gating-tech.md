---
level: Practice
layer: Model
purpose: AI 能力调用计费门控（pre-call 积分门控 + 资源权益额度）技术方案，落地 M44/M53
status: draft
version: 1.1.0
date: 2026-05-30
author: AaronZZH & Kiro
---

# AI 计费门控技术方案

> 为 AI 能力调用补 **pre-call 门控 + 事后真实计量**，落地审查项 M44（chat 无 pre-call 门控）、M53（video/image/embedding 等普遍无门控）。
> 🔴 含计费策略决策（免费层、fail 策略），对接 [credit-settlement.md](credit-settlement.md) / [settlement-tech.md](settlement-tech.md)。

## 背景与核心矛盾

- **M44**：`ResilientChatService` 仅事后发 `TokenUsageEvent` 计量，**无 pre-call 门控** → 余额耗尽仍继续调用 → 成本失控。
- **M53**：image/video/embedding/rerank/speech/music/model3d/omni 等能力**普遍无 pre-call 门控**。
- **不能硬接线**：既有 `EntitlementService.check()` 对**未播种的权益抛 `IllegalArgumentException`**、对**无 quota 记录的用户抛 `QuotaExceededException`**。直接把 `check()` 塞进 AI 调用路径，会让所有未配置计费的能力/未发额度的用户**全部调用失败 → 砸掉全站 AI**。

本方案的关键是：**门控只增不破**——未配置计费时 fail-open（放行+计量缺省），仅在「已配置且超额」时 fail-closed（拒绝），并以开关灰度上线。

> **免费助理入口**：可设置系统级虚拟用户（`SYSTEM_FREE_USER`），绑定特定 Agent 入口，该入口的请求计入虚拟用户积分（平台承担成本），设独立预算上限，超限降级而非无限放行。待后续 Agent 配置化时落地。

## 双轨门控策略（核心决策）

AI 能力按性质分两轨，分别用不同机制门控：

| 轨道 | 适用能力 | 门控机制 | 原因 |
|------|---------|---------|------|
| **积分轨** | 模型调用（chat/image生成/video/embedding/rerank/speech/music/model3d/omni） | `CreditService` 积分余额检查 + 事后扣减 | 成本直接对应 API 费用，货币化计量 |
| **权益轨** | 资源占用（图像存储/知识库容量/Agent 数量等） | `EntitlementChecker` 权益额度检查 + 扣减 | 占用上限，套餐分层控制，不是按次消耗 |

> **术语说明**：`EntitlementDef` 是**权益**定义，`code` 字段是其唯一标识符（技术键）。`EntitlementQuota` 是**权益额度**（用户实例）。文档统一用"权益"和"权益额度"，不再使用"权益码"指代权益定义。

## 既有资产（复用，禁止并行抽象）

| 组件 | 作用 |
|------|------|
| `EntitlementChecker`（impl `EntitlementService`） | `check/consume/checkAndConsume`；COUNTABLE/BOOLEAN；`total=-1` 无限；不足走 `CreditService` refill |
| `EntitlementDef` / `EntitlementQuota` / `EntitlementLedger` / `PlanEntitlement` | 权益定义/用户额度/流水/套餐挂接 |
| `@Entitlement` + `EntitlementAspect` | 声明式门控（check→proceed→consume，SpEL cost，userId 取自 SecurityContext） |
| `ResilientChatService` + `TokenUsageEvent` | chat 调用 + 事后 token 计量事件（已有） |
| `CreditService` | 积分余额查询、扣减、充值 |

## 设计原则

- **积分轨 fail-closed**：`CreditService` 不可用或 userId=null → 拒绝，不放行。积分直接对应真实 API 成本，不能因异常免费放行。
- **权益轨 fail-open**：权益定义未播种（数据库无记录）→ 放行 + warn。权益是"限制"，没配置限制不等于全部拒绝，避免漏跑迁移脚本把功能搞崩。
- **无灰度开关**：门控上线即强制执行。新用户注册赠 50 积分，积分用完需充值或开会员，无需观察模式。
- **userId 来源显式**：AI 路径 userId 多来自上下文（`ctx.userId()` / 显式入参），不依赖 SecurityContext（与 `@Entitlement` 切面的差异点）。
- **预算预警**：积分低于阈值时提前通知用户，不等到耗尽才拒绝（双线：预警线 warn，截止线 block）。

## 积分轨：AI 模型调用门控

### 门控网关（新建 `AiCreditGuard`）

封装 `CreditService`，实现「余额预检 + 事后真实扣减」+ 开关：

```java
public interface AiCreditGuard {
    /**
     * 调用前预检：积分余额 > 0 才放行；userId=null → 拒绝（无法归账）。
     * 余额低于预警阈值时异步发 CreditLowEvent，不阻塞调用。
     */
    void precheck(Long userId, String capability);

    /**
     * 调用成功后按实际消耗扣积分（CreditService.spend）+ 写流水。
     * 失败仅 warn，不回滚已完成的 AI 调用。
     */
    void settle(Long userId, String capability, long actualCost);
}
```

实现要点：
- `precheck`：查 `CreditService.getBalance(userId)`，余额 ≤ 0 抛 `InsufficientCreditsException`；余额低于预警阈值发 `CreditLowEvent`（异步通知，不阻塞调用）。
- `settle`：调用 `CreditService.deduct(userId, cost, capability)`，失败仅 warn（不回滚已完成的 AI 调用）。
- chat 特殊处理：precheck 仅检查"是否有余额"（余额 > 0），settle 按 `TokenUsageEvent` 的真实 token 数扣减（两者结合）。

### M44 落地（chat）

- `ResilientChatService.call(...)`：调用前 `guard.precheck(ctx.userId(), "chat")`（仅检查余额 > 0）。
- `TokenUsageEvent` 监听器：`guard.settle(userId, "chat", prompt+completion tokens)`（事后按真实 token 扣积分）。

### M53 落地（其它模型能力）

- 各能力服务入口 `guard.precheck(userId, capability)`，成功后 `guard.settle(userId, capability, cost)`。
- 统一封装 `withCredit(userId, capability, estimatedCost, supplier)` 模板方法减少重复。

## 权益轨：资源占用门控

资源类权益定义（`EntitlementDef`，type=COUNTABLE）：

| 权益（code） | 资源 | 计量单位 | 检查时机 |
|-------------|------|---------|---------|
| `kb_storage` | 知识库存储 | GB | 上传前检查剩余容量 |
| `image_storage` | 图像存储 | 张/GB | 上传前检查 |
| `agent_count` | Agent 数量 | 个 | 创建前检查 |
| `workflow_count` | 工作流数量 | 个 | 创建前检查 |

- 直接复用 `@Entitlement` 注解或 `EntitlementChecker.checkAndConsume()` 即可，无需新建门控组件。
- 套餐分层：FREE 套餐挂接基线额度，PRO 套餐覆盖更高额度或 `total=-1` 无限。

## 免费层策略

新用户注册即发 `FREE` 套餐：

- 积分轨：注册赠送 **50 积分**（`CreditService` 发放）。
- 权益轨：`FREE` 套餐 + `PlanEntitlement` 挂接各资源权益的基线额度（如知识库 1GB、Agent 3 个）。
- 注册钩子激活 `FREE` 订阅 → 复用既有「订阅生效实例化 quota」逻辑 → 用户获得 `entitlement_quota` 记录。
- 付费套餐（PRO 等）覆盖更高额度或无限。

## fail 策略矩阵

### 积分轨（fail-closed）

| 场景 | 处理 |
|------|------|
| 积分充足（> 预警阈值） | 放行 + 事后扣减 |
| 积分低于预警阈值 | 放行 + 发预警通知 + 事后扣减 |
| 积分耗尽（≤ 0） | 拒绝（InsufficientCredits） |
| CreditService 不可用 | 拒绝（服务异常，不免费放行） |
| userId=null | 拒绝（无法归账，不放行） |

### 权益轨（fail-open）

| 场景 | 处理 |
|------|------|
| 未播种该权益（数据库无记录） | 放行 + warn（未纳入限制） |
| 有权益，用户无额度记录 | 依免费层：已发 FREE→有 quota；仍无→拒绝 |
| 有额度且充足 | 放行 + 扣减 |
| 有额度但超额 | 拒绝（QuotaExceeded） |

## 上线节奏（非分阶段设计，仅实施顺序）

1. 落 `AiCreditGuard` + 接线所有 AI 路径。
2. 播种资源权益 + FREE 套餐 + 注册钩子（发积分/额度）。
3. 上线观测积分流水与拒绝率。

## 改动清单（实施时）

| 项 | 内容 |
|----|------|
| 新建 | `AiCreditGuard` 接口 + `DefaultAiCreditGuard`（framework） |
| 改 | `ResilientChatService` 加 precheck；`TokenUsageEvent` 监听器接 settle |
| 改 | 各 AI 能力服务（image/video/embedding/...）入口接 `AiCreditGuard` |
| 迁移 | Flyway seed 资源类 `entitlement_def`（kb_storage/image_storage/agent_count 等）+ `FREE` 套餐 + `plan_entitlement` 挂接 |
| 钩子 | 注册流程：赠送 50 积分 + 激活 FREE 订阅（provision quota） |
| 配置 | 预警阈值从 `sys_config`（`ai.credit_warn_threshold`）读取，默认 10 |

## 决策记录

| 日期 | 决策 | 结论 | 理由 |
|------|------|------|------|
| 2026-05-30 | 门控接入安全性 | `AiCreditGuard` graceful degrade | 直接接 `EntitlementChecker.check` 会因未播种/无 quota 砸全站 AI |
| 2026-05-30 | 复用 vs 新建 | 复用 entitlement 引擎 + CreditService，仅加 AI 门面 | 禁并行抽象 |
| 2026-05-30 | 免费层 | 注册赠 50 积分 + FREE 订阅 | 保证用户有积分和 quota 记录，避免 fail-closed 误伤 |
| 2026-05-30 | 双轨门控 | 模型调用→积分轨；资源占用→权益轨 | 模型调用成本货币化，资源占用套餐分层，两者性质不同 |
| 2026-05-30 | 术语统一 | "权益"（EntitlementDef）+ "权益额度"（EntitlementQuota），不用"权益码" | code 只是唯一标识符，不是权益本身 |
| 2026-05-30 | 无灰度开关 | 门控上线即强制，无 enforce 开关 | 积分需提前充值，无需观察模式；逻辑更干净 |
| 2026-05-30 | fail 策略分轨 | 积分轨 fail-closed；权益轨 fail-open | 积分对应真实成本不能免费放行；权益是限制，未配置不等于全部拒绝 |

## 相关文档

- [credit-settlement.md](credit-settlement.md) / [credit-tech.md](credit-tech.md) / [settlement-tech.md](settlement-tech.md) — 积分/结算引擎
- [identity-and-membership.md](../../../apps/service/identity-and-membership.md) — 套餐/订阅/会员
- [docs/design/audit/2026-05-30-service-review/README.md](../../../audit/2026-05-30-service-review/README.md) — M44/M53 来源
