# 工具执行授权权限设计

> 状态：草案 | 作者：AaronZZH & Kiro | 日期：2026-05-25

## 背景

当前 AAF 的工具权限模型（`ToolPermissionGuard` + `ToolPermissionChecker`）已具备基本的风险分级和 HITL 审批能力，但存在以下不足：

- **缺乏上下文感知**：同一工具在不同场景下风险不同（读 vs 写、测试环境 vs 生产环境）
- **无自动升级机制**：执行过程中风险升级时无法动态调整
- **审批粒度粗**：只区分工具级别，不区分参数级别（如 `deleteFile("/tmp/x")` vs `deleteFile("/etc/passwd")`）
- **无审计闭环**：权限决策未与执行追踪关联

参考 Kiro CLI 的权限模型（分层风险评估 + 上下文感知 + 自动升级），提出改进方案。

## Kiro CLI 权限模型分析

Kiro CLI 对工具/操作的权限控制核心思想：

```
低风险（读文件、搜索）     → 直接执行，无需确认
中风险（安装依赖、改配置） → 执行但告知用户
高风险（生产变更、数据删除）→ 解释风险 + 等待明确确认
```

关键设计点：

| 特性 | Kiro CLI 做法 | AAF 当前做法 | 差距 |
|------|-------------|-------------|------|
| 风险评估维度 | 操作类型 + 影响范围 + 可逆性 | 仅工具注册时的静态等级 | 缺动态评估 |
| 确认粒度 | 参数级（如具体文件路径） | 工具级 | 粒度不够 |
| 会话记忆 | 同类操作确认一次后续自动 | MEDIUM 级有临时授权 | 基本对齐 |
| 升级机制 | 执行中发现高风险自动升级 | 无 | 缺失 |
| 非破坏性替代 | 优先选择非破坏性方案 | 无 | 缺失 |

## 改进设计

### 核心原则

- **上下文决定风险**：同一工具的风险等级由调用参数和运行环境动态决定
- **最小权限**：Agent 默认只有声明的工具白名单权限
- **渐进信任**：低风险自动执行 → 中风险首次确认后记忆 → 高风险每次确认
- **可审计**：每次权限决策记录到执行追踪（关联 execution_step）
- **非阻塞降级**：审批超时时 Agent 可选择替代方案而非死等

### 风险评估模型（三维）

```
最终风险 = max(静态风险, 参数风险, 环境风险)
```

| 维度 | 评估方式 | 示例 |
|------|---------|------|
| 静态风险 | 工具注册时声明 | `deleteFile` → HIGH |
| 参数风险 | 运行时规则匹配 | 路径含 `/etc/` → CRITICAL |
| 环境风险 | 运行环境上下文 | 生产环境 → 升一级 |

### 参数级风险规则

```java
public interface ParameterRiskRule {
    /** 评估参数风险，返回 null 表示不适用 */
    ToolRiskLevel evaluate(String toolName, Map<String, Object> arguments);
}
```

内置规则示例：

| 规则 | 触发条件 | 风险升级 |
|------|---------|---------|
| 路径敏感 | 参数含 `/etc/`、`/prod/`、`.env`、`credentials` | → CRITICAL |
| 批量操作 | 参数含 `*`、`--all`、`--force`、数组长度 > 100 | → HIGH |
| 数据删除 | 操作类型为 DELETE 且无 WHERE 条件 | → CRITICAL |
| 外部网络 | 目标 URL 非白名单域名 | → HIGH |
| 金额敏感 | 金额参数 > 阈值 | → HIGH |

### 环境上下文

```java
public record ExecutionEnvironment(
    String envType,        // dev / staging / production
    boolean isSandboxed,   // 是否在沙箱中
    String callerAgentId,  // 调用方 Agent
    int cascadeDepth       // 调用链深度（深度越大越谨慎）
) {
    public int riskBoost() {
        int boost = 0;
        if ("production".equals(envType)) boost += 1;
        if (cascadeDepth > 3) boost += 1;
        return boost;
    }
}
```

### 权限决策流程（改进后）

```
工具调用请求
    │
    ├── 1. 白名单检查（Role 级）
    │       → 不在白名单 → DENIED
    │
    ├── 2. 静态风险等级
    │
    ├── 3. 参数风险规则评估（动态升级）
    │
    ├── 4. 环境风险叠加
    │
    ├── 5. 最终风险 = max(静态, 参数, 环境)
    │
    └── 6. 按最终风险决策：
            NONE/LOW   → AUTO_GRANTED（记录日志）
            MEDIUM     → 检查会话缓存 → 有则 GRANTED，无则 PENDING
            HIGH       → 每次 PENDING（展示参数摘要）
            CRITICAL   → DENIED（需管理员）
```

### 会话级信任记忆（防重复授权）

核心原则：**用户确认一次后，同一工具的后续调用（含失败重试）不再弹确认。**

```java
public record TrustGrant(
    String toolName,
    GrantScope scope,
    String pattern,       // PATTERN 模式时的匹配规则
    Instant grantedAt,
    boolean consumed      // ONCE 模式是否已消费
) {}

public enum GrantScope {
    ONCE,       // 仅本次调用（用完即失效）
    SESSION,    // 本次会话内同工具自动通过（默认）
    PATTERN     // 本次会话内匹配参数模式的调用自动通过
}
```

#### 防重复场景

| 场景 | 行为 |
|------|------|
| 工具已授权 SESSION，执行失败后重试 | 自动通过，不再弹确认 |
| 工具已授权 SESSION，换不同参数调用 | 自动通过 |
| 工具已授权 ONCE，执行失败后重试 | 需重新确认（已消费） |
| 工具已授权 PATTERN `path:/tmp/**`，参数含 `/tmp/x` | 自动通过 |
| 工具已授权 PATTERN `path:/tmp/**`，参数含 `/etc/x` | 需确认（不匹配） |

#### 用户确认时的选项

用户审批时可选择授权范围：

```
[工具调用确认] Agent 请求调用 writeFile（风险等级: MEDIUM）
  参数: {"path": "/tmp/output.json", "content": "..."}

  [1] 允许这一次
  [2] 本次会话都允许 writeFile          ← 默认
  [3] 允许 /tmp/ 下的文件操作
  [4] 拒绝
```

选择映射：
- [1] → `grantWithScope(session, "writeFile", ONCE, null)`
- [2] → `grantWithScope(session, "writeFile", SESSION, null)`
- [3] → `grantWithScope(session, "writeFile", PATTERN, "path:/tmp/**")`
- [4] → `deny(session, "writeFile")`

#### Pattern 匹配规则

格式：`key:glob`

| Pattern | 含义 |
|---------|------|
| `path:/tmp/**` | path 参数匹配 /tmp/ 下任意路径 |
| `path:/home/user/project/**` | 限定项目目录 |
| `url:https://api.example.com/**` | 限定 API 域名 |
| `bucket:my-bucket` | 限定 S3 bucket |

### 权限决策优先级（完整）

每次权限决策写入 `execution_step`：

```java
// 在 GuardedToolCallback.call() 中
var step = new ExecutionCompletedEvent.StepRecord(
    stepIndex,
    parentStepId,
    StepType.TOOL_CALL,
    null,
    toolName,
    arguments,                    // input
    permissionResult.name(),      // output（决策结果）
    mapStatus(permissionResult),
    null,
    Instant.now(),
    Instant.now()
);
```

审计查询："哪些工具调用被拒绝了" → `SELECT * FROM execution_step WHERE step_type='TOOL_CALL' AND status='FAILED'`

### 非阻塞降级策略

当审批超时时，Agent 不死等，而是：

```java
public record FallbackStrategy(
    Duration timeout,
    FallbackAction action
) {}

public enum FallbackAction {
    WAIT,           // 继续等待（默认）
    SKIP,           // 跳过该工具，继续执行
    ALTERNATIVE,    // 使用低风险替代方案
    ABORT           // 中止执行
}
```

工具注册时可声明替代方案：

```java
public record ToolMeta(
    String name,
    ToolRiskLevel riskLevel,
    String alternativeTool,       // 低风险替代（如 deleteFile → moveToTrash）
    FallbackStrategy fallback,
    List<ParameterRiskRule> parameterRules
) {}
```

## 批量授权与读写分离（参考 Kiro CLI）

### 权限三级（对齐 Kiro `/tools` 模型）

| 级别 | 含义 | 对应 Kiro |
|------|------|-----------|
| `ALLOWED` | 自动通过，无提示 | `allowed` |
| `REQUIRES_APPROVAL` | 每次使用前需确认（默认） | `requires-approval` |
| `DENIED` | 工具被禁用，直接拒绝 | `denied` |

### 批量授权 API

```java
// 一次性信任所有工具（对应 /tools trust-all）
void grantAll(String sessionId);

// 批量信任指定工具（对应 /tools trust tool1 tool2）
void grantBatch(String sessionId, List<String> toolNames);

// 撤回信任（对应 /tools untrust）
void revoke(String sessionId, String toolName);

// 重置为默认（对应 /tools reset）
void resetSession(String sessionId);
```

### 读写分离自动授权

工具注册时声明 `readOnly` 属性：

```java
public record ToolMeta(
    String name,
    String description,
    String source,
    ToolType type,
    ToolRiskLevel riskLevel,
    boolean readOnly,          // 新增：只读工具自动通过
    String parametersSchema
) {}
```

决策逻辑：
- `readOnly = true` → 无论风险等级，自动 `AUTO_GRANTED`（记录日志）
- `readOnly = false` → 走正常风险评估流程

### deny 黑名单

优先级：**deny > allow > 默认**（与 Kiro `deniedServices` 优先于 `allowedServices` 一致）

```java
// 会话级黑名单
void deny(String sessionId, String toolName);
void denyBatch(String sessionId, List<String> toolNames);
```

### 启动预授权（Agent 级白名单）

Agent 定义中声明 `allowedTools`，该 Agent 执行时这些工具自动信任：

```java
// AgentDefinition 新增字段
private List<String> allowedTools;  // 预授权工具列表
```

等价于 Kiro 的 `--trust-tools=tool1,tool2` 启动参数。

### 权限决策优先级（完整）

```
1. deny 黑名单（会话级）         → DENIED
2. deny 黑名单（Agent 级）       → DENIED
3. grantAll（会话级）            → GRANTED
4. grantBatch / trust（会话级）  → GRANTED
5. allowedTools（Agent 级）      → GRANTED
6. readOnly = true               → AUTO_GRANTED
7. 风险评估（静态 + 参数 + 环境）→ 按等级决策
```

## 改造计划

### 需要修改的文件

| 文件 | 改动 |
|------|------|
| `ToolRiskLevel` | 不变 |
| `ToolRegistry.ToolMeta` | 增加 `alternativeTool`、`parameterRules`、`fallback` 字段 |
| `ToolPermissionChecker` | 重构为三维评估（静态 + 参数 + 环境） |
| `ToolPermissionGuard` | 集成参数级规则 + 记录到执行追踪 |
| `HumanApprovalService` | 增加 GrantScope 支持 |

### 新增文件

| 文件 | 职责 |
|------|------|
| `ParameterRiskRule` | 参数风险规则接口 |
| `BuiltinParameterRules` | 内置规则（路径敏感、批量操作等） |
| `ExecutionEnvironment` | 运行环境上下文 |
| `TrustGrant` | 会话级信任记忆 |

### 实现优先级

| 阶段 | 内容 |
|------|------|
| P1 | ParameterRiskRule 接口 + 内置规则 + ToolPermissionChecker 三维评估 |
| P1 | TrustGrant 的 PATTERN 模式（替代当前简单的 Set<String>） |
| P2 | 与 execution_step 关联（依赖执行追踪落地） |
| P2 | 非阻塞降级 + alternativeTool |
| P3 | 前端审批 UI（展示参数摘要 + 授权范围选择） |
