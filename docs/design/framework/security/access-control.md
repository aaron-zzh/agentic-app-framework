---
level: Practice
layer: Model
purpose: AAF 访问控制设计（认证 + 授权）
status: draft
version: 0.3.0
date: 2026-05-06
author: AaronZZH
---

# 访问控制设计（Access Control）

> 认证（你是谁）+ 授权（你能做什么）。安全架构（加密、脱敏、审计）见 [security.md](security.md)。

## 1. 设计哲学

> 权限不是限制，而是信任的边界——让每个主体在自己的边界内自由行动，边界之外需要授权。

**AAF 访问控制三大特色：**

- **协作权限是一等公民**：不只管"能不能访问"，还管"能和谁协作"
- **用户可自调权限**：在自己权限范围内调整资源可见性，降低管理员负担
- **Agent 作为权限主体**：AI 智能体与用户受同一套规则约束，支持实时交互授权

### Operator 统一抽象

权限系统的"主体（Who）"不是 User，而是 **Operator**——Human 和 AI 的多态抽象：

- `UserPrincipal`（Human）和 `AgentPrincipal`（AI）都实现 `OperatorAware` 接口
- 业务代码通过 `OperatorContext.current()` 获取当前 Operator，不关心底层认证方式
- 权限规则对 Operator 统一生效，不为 Agent 单独建一套权限体系

详见 [Operator 模型设计](../operator.md)。

## 2. 核心公式

```
权限 = 主体（Who）× 操作（What）× 对象（Which）× 条件（When）
```

## 3. 权限模型选型

### 3.1 为什么需要分层组合

单一模型无法满足 AAF 的全部需求：

| 模型 | 能解决 | 不能解决 |
|------|--------|----------|
| **RBAC** | 系统级功能权限 | 无法表达"用户 A 分享给用户 B"的关系 |
| **ReBAC** | 资源级协作关系 | 无法做行级数据过滤 |
| **记录规则** | 行级/字段级过滤 | 无法表达复杂关系继承 |
| **ABAC** | 动态条件策略 | 规则爆炸，难以管理 |

### 3.2 最终方案：四层分层整合

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: RBAC       系统角色      "你是管理员还是普通用户"       │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: ReBAC      资源关系      "你是这个文档的 owner 还是 viewer" │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: 记录规则   数据过滤      "你只能看自己部门的数据"       │
├─────────────────────────────────────────────────────────────────┤
│  Layer 4: ABAC       动态条件      "Agent 低置信度操作需人工确认" │
└─────────────────────────────────────────────────────────────────┘
```

**各层职责与技术选型：**

| 层 | 职责 | 技术实现 | 存储 |
|----|------|----------|------|
| RBAC | 功能权限 | Spring Security 原生 | PostgreSQL |
| ReBAC | 关系权限 | 自定义 PermissionEvaluator + 图查询 | Neo4j |
| 记录规则 | 数据过滤 | JPA 拦截器 | PostgreSQL |
| ABAC | 条件策略 | 轻量策略引擎（内置） | 配置/代码 |

### 3.3 角色体系

**内置角色（系统预定义，不可删除）：**

| 角色 | 权限范围 | 说明 |
|------|----------|------|
| `super_admin` | 全部权限 | 超级管理员，跨组织 |
| `org_admin` | 组织内全部权限 | 组织管理员 |
| `member` | 基础功能 | 普通成员 |
| `guest` | 只读 | 访客 |
| `agent` | 受限执行 | AI 智能体专用角色 |

**自定义角色：**

- 组织管理员可创建自定义角色
- 自定义角色基于权限点组合，不能超越 `org_admin`
- 支持角色继承：自定义角色可继承内置角色的权限

```yaml
# 自定义角色示例
role:
  name: project_manager
  inherits: member
  permissions:
    - project:create
    - project:manage
    - task:assign
  data_scope: team  # 数据范围：仅所属团队
```

### 3.4 DSL 规则引擎

权限规则统一用 DSL 描述，由内置规则引擎解析执行：

```yaml
# 权限规则 DSL 示例
rule:
  name: team-data-access
  description: 团队成员只能访问本团队数据
  subject:
    roles: [member, project_manager]
  condition:
    data: team_id IN ${user.teamIds}
  actions: [read, write]
  
rule:
  name: sensitive-field-mask
  description: 非管理员看不到手机号
  subject:
    roles: [member, guest]
  condition:
    field: phone
  action: mask  # 脱敏展示
```

**规则引擎职责：**

| 职责 | 说明 |
|------|------|
| 规则解析 | 解析 YAML/JSON DSL，构建检查规则 |
| 条件评估 | 评估 subject/condition/action 是否匹配 |
| 动态加载 | 规则变更后热加载，无需重启 |
| 优先级处理 | 多规则冲突时按优先级决策 |

## 4. 认证（Authentication）

### 4.1 认证方案

**选型：Spring Security OAuth2 + JWT**

| 方式 | 适用场景 | 实现 |
|------|----------|------|
| JWT | Web / API 调用 | Spring Security OAuth2 Resource Server |
| OAuth2 | 第三方登录 | Spring Authorization Server |
| API Key | 外部系统 / CLI | 自定义 Filter |

**选型理由：**

- Spring Security OAuth2 完全覆盖 JWT 发放、校验、刷新、SSO 流程
- 与 Spring Boot 4 深度集成，无需额外依赖
- 支持未来扩展（SSO、多租户）

### 4.2 多端认证策略

| 端 | 认证方式 | Token 有效期 | 刷新策略 |
|----|----------|--------------|----------|
| Web | JWT | 2 小时 | Refresh Token（7 天） |
| 小程序 | JWT + 微信登录 | 2 小时 | 同上 |
| CLI | API Key | 长期 | 手动轮换 |
| Agent | 继承触发用户 | 会话级 | 随会话结束 |

### 4.3 认证流程

```
用户登录请求
    ↓
Spring Security 认证
    ↓
签发 JWT（含 userId, roles, orgId）
    ↓
后续请求携带 JWT
    ↓
Resource Server 校验 JWT
    ↓
注入 SecurityContext
```

## 5. 授权（Authorization）

### 5.1 权限主体（Who）

三类主体统一建模：

| 主体类型 | 标识格式 | 说明 |
|----------|----------|------|
| 用户 | `user:{id}` | 自然人，有身份、角色、组织归属 |
| Agent | `agent:{id}` | AI 智能体，关联触发用户 |
| 外部系统 | `system:{id}` | 通过 API Key 接入 |

**Agent 权限规则：**

```
Agent 最终权限 = 触发用户权限 ∩ Agent 自身配置
```

- Agent 不能超越触发用户的权限边界
- Agent 可配置工具白名单/黑名单
- 高风险操作受置信度门控约束

### 5.2 权限对象（Which）

基于语义对象而非数据库表：

| 对象类型 | 说明 | 权限关系 |
|----------|------|----------|
| document | 文档 | owner / editor / viewer |
| space | 空间 | owner / admin / member |
| workflow | 工作流 | owner / executor |
| tool | 工具 | user（可使用） |
| file | 文件 | owner / editor / viewer |
| directory | 目录 | owner / editor / viewer |

### 5.3 操作类型（What）

| 操作 | 说明 |
|------|------|
| read | 查看内容 |
| write | 创建 / 编辑 |
| delete | 删除 |
| execute | 触发工作流 / 调用工具 |
| share | 分享给他人 |
| manage | 配置权限 / 管理成员 |

### 5.4 授权范围（Scope）

| 范围 | 说明 | 存储 | 失效时机 |
|------|------|------|----------|
| once | 单次授权 | Redis | 执行完立即失效 |
| session | 会话授权 | Redis | 会话结束失效 |
| permanent | 永久授权 | Neo4j | 需手动撤销 |

**典型场景：**

```
# Agent 请求单次写入文件
file:config.json#write@agent:agent-123
scope: once

# Agent 请求会话级读取目录
directory:/src#read@agent:agent-123
scope: session
```

### 5.5 关系权限模型（ReBAC）

**核心概念：关系元组**

借鉴 Google Zanzibar 设计，用关系元组表达权限：

```
<object>#<relation>@<subject>

示例：
document:doc-123#owner@user:alice       # alice 是 doc-123 的 owner
document:doc-123#editor@user:bob        # bob 是 doc-123 的 editor
space:space-456#member@user:alice       # alice 是 space-456 的成员
document:doc-123#parent@space:space-456 # doc-123 属于 space-456
file:/src/main.ts#read@agent:agent-123  # agent-123 可读取 main.ts
directory:/src#read@agent:agent-123     # agent-123 可读取 /src 目录
```

**权限继承：**

```
space 成员 → 自动获得 space 下文档的 viewer 权限
document owner → 自动拥有 editor + viewer 权限
directory viewer → 自动获得目录下文件的 viewer 权限
```

### 5.6 权限 Schema 定义

```yaml
types:
  user:
    relations: {}
  
  agent:
    relations:
      trigger_user: user  # Agent 的触发用户
  
  space:
    relations:
      owner: user | agent
      admin: user | agent
      member: user | agent
    permissions:
      can_manage: owner | admin
      can_write: can_manage | member
      can_read: can_write
  
  document:
    relations:
      owner: user | agent
      editor: user | agent
      viewer: user | agent
      parent: space
    permissions:
      can_delete: owner
      can_write: owner | editor | parent->can_write
      can_read: can_write | viewer | parent->can_read
      can_share: owner | editor
  
  directory:
    relations:
      owner: user | agent
      editor: user | agent
      viewer: user | agent
      parent: directory  # 支持嵌套目录
    permissions:
      can_write: owner | editor | parent->can_write
      can_read: can_write | viewer | parent->can_read
  
  file:
    relations:
      owner: user | agent
      editor: user | agent
      viewer: user | agent
      parent: directory
    permissions:
      can_write: owner | editor | parent->can_write
      can_read: can_write | viewer | parent->can_read
  
  tool:
    relations:
      user: user | agent  # 可使用该工具
    permissions:
      can_execute: user
```

### 5.7 单用户直接授权

支持精确到单个用户对单个资源的权限：

| 场景 | 实现 |
|------|------|
| 永久授权 | 关系元组无过期时间 |
| 临时授权 | 关系元组带 `expires_at` |
| 会话授权 | Redis 存储，会话结束自动失效 |
| 链接分享 | `document#viewer@link:{token}` |

### 5.8 记录规则（数据权限）

借鉴 Odoo 记录规则设计，用 DSL 描述数据过滤条件：

```yaml
rule: own-data-access
subject:
  role: member
scope:
  data: org_id = ${user.orgId} AND (owner_id = ${user.id} OR team_id IN ${user.teamIds})
```

**支持的变量：**

- `${user.id}` — 当前用户 ID
- `${user.orgId}` — 当前组织 ID
- `${user.teamIds}` — 所属团队 ID 列表
- `${user.roles}` — 角色列表

### 5.9 组织隔离（多层结构）

支持多层组织结构，通过 Neo4j 存储组织树，实现灵活的层级隔离：

**组织层级模型：**

```
集团（Group）
  └─ 公司（Company / Org）
       └─ 部门（Department）
            └─ 团队（Team）
                 └─ 用户（User）
```

**Neo4j 关系存储：**

```cypher
(group:Group)-[:HAS_ORG]->(org:Org)
(org:Org)-[:HAS_DEPT]->(dept:Department)
(dept:Department)-[:HAS_TEAM]->(team:Team)
(user:User)-[:BELONGS_TO]->(team:Team)
(user:User)-[:MEMBER_OF]->(org:Org)
```

**数据隔离策略：**

| 隔离级别 | 过滤条件 | 适用场景 |
|----------|----------|----------|
| 集团级 | `group_id = ${user.groupId}` | 集团内所有数据 |
| 公司级 | `org_id = ${user.orgId}` | 公司内数据（默认） |
| 部门级 | `dept_id IN ${user.deptPath}` | 本部门及下级部门 |
| 团队级 | `team_id IN ${user.teamIds}` | 仅所属团队 |
| 个人级 | `owner_id = ${user.id}` | 仅自己创建的 |

**向上穿透与向下继承：**

```yaml
# 数据权限 DSL
rule:
  name: dept-data-access
  scope:
    level: department
    inherit_down: true   # 可看下级部门数据
    inherit_up: false    # 不可看上级部门数据
```

**跨组织协作：**

- 默认组织间数据隔离
- 支持显式跨组织分享（通过 ReBAC 关系元组）
- 跨组织操作强制审计

```
# 跨组织分享
document:doc-123#viewer@org:other-org
```

企业需要更强隔离（物理隔离）→ 独立部署。

## 6. 实时交互授权

### 6.1 场景

Agent 执行任务时发现需要额外权限，实时请求用户授权。

### 6.2 流程

```
Agent 执行任务
    ↓
发现需要额外权限
    ↓
创建授权请求（pending）
    ↓
WebSocket 推送给用户
    ↓
用户确认/拒绝
    ↓
├─ 确认 → 授予会话级临时权限 → Agent 继续
└─ 拒绝 → Agent 跳过或终止
```

### 6.3 授权请求数据

| 字段 | 说明 |
|------|------|
| requestId | 请求唯一标识 |
| agentId | 发起请求的 Agent |
| sessionId | 会话 ID |
| resource | 请求访问的资源 |
| permission | 请求的权限 |
| reason | 为什么需要 |
| expiresAt | 请求过期时间（默认 5 分钟） |

### 6.4 临时权限存储

会话级临时权限存储在 Redis，会话结束自动失效：

```
Key: session_perm:{sessionId}:{resource}:{permission}
TTL: 会话超时时间
```

## 7. 置信度门控

与权限系统联动，根据操作风险和 Agent 置信度决定执行策略：

| 风险等级 | 置信度要求 | 执行策略 |
|----------|-----------|----------|
| LOW | 无 | 直接执行 |
| MEDIUM | > 0.7 | 低于阈值需确认 |
| HIGH | 任意 | 必须人工确认 |
| CRITICAL | — | 必须人工执行 |

## 8. 数据存储架构

### 8.1 存储分工

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   PostgreSQL    │  │     Neo4j       │  │     Redis       │
│   ─────────────  │  │   ─────────────  │  │   ─────────────  │
│   • 用户/角色    │  │   • 关系元组     │  │   • 权限缓存    │
│   • 记录规则     │  │   • 权限继承图   │  │   • 会话权限    │
│   • 审计日志     │  │                 │  │   • 授权请求    │
│   • 业务数据     │  │                 │  │                 │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### 8.2 策略引擎与图数据库职责分离

```
┌─────────────────────────────────────────────────────────────────┐
│  策略引擎（应用层）                                              │
│  ─────────────────────────────────────────────────────────────  │
│  • 策略解析：解析权限 Schema DSL，构建检查规则                    │
│  • 条件评估：ABAC 动态条件（置信度、时间、IP、属性）              │
│  • 合规审计：记录权限检查结果，支持溯源                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓ 查询关系上下文
┌─────────────────────────────────────────────────────────────────┐
│  Neo4j（图数据库）                                               │
│  ─────────────────────────────────────────────────────────────  │
│  • 关系存储：主体 → 关系 → 资源（关系元组）                       │
│  • 路径遍历：权限继承链、组织层级、协作关系                       │
│  • 上下文提供：返回关系 + 节点属性，供策略引擎评估                │
└─────────────────────────────────────────────────────────────────┘
```

**分工原则：**

- 图数据库只负责**高效回答"是否存在路径"**，不做业务判断
- 策略引擎负责**解释路径含义 + 评估附加条件**，做最终决策
- 两者解耦：更换图数据库不影响策略逻辑，更换策略引擎不影响关系存储

### 8.3 为什么选择 Neo4j 存储关系权限

**问题：权限继承需要图遍历**

```
检查 bob 是否有 doc-1 的读权限：
1. bob 是 doc-1 的 viewer？
2. bob 是 doc-1 的 editor？
3. bob 是 doc-1 所属 space 的 member？
4. bob 是 space 的 admin？
5. ...（可能多层嵌套）
```

**PostgreSQL 方案的问题：**

- 递归 CTE 查询复杂，性能随层级下降
- 多表 JOIN，难以优化
- 权限继承规则变更需改 SQL

**Neo4j 方案的优势：**

| 维度 | PostgreSQL | Neo4j |
|------|------------|-------|
| 关系遍历 | 递归 CTE，O(n) | 原生图遍历，O(logN) |
| 查询复杂度 | 多表 JOIN | 单次路径查询 |
| 继承规则 | 硬编码 SQL | Schema 声明式 |
| 可视化调试 | 困难 | Neo4j Browser 直观 |

**典型查询对比：**

```sql
-- PostgreSQL：检查权限继承（复杂）
WITH RECURSIVE permission_path AS (
    SELECT subject_id, object_id, relation FROM permission_tuple
    WHERE subject_id = 'bob' AND object_id = 'doc-1'
    UNION ALL
    SELECT p.subject_id, t.object_id, t.relation
    FROM permission_path p
    JOIN permission_tuple t ON ...
)
SELECT EXISTS (SELECT 1 FROM permission_path WHERE ...);
```

```cypher
// Neo4j：检查权限继承（简洁）
MATCH path = (u:User {id: 'bob'})-[:EDITOR|VIEWER|MEMBER*1..5]->(d:Document {id: 'doc-1'})
RETURN path IS NOT NULL AS hasPermission
```

### 8.3 数据同步机制

PostgreSQL 作为写入源，事件驱动同步到 Neo4j：

```
权限变更（PostgreSQL）
    ↓
发布领域事件
    ↓
事件监听器
    ├─ 同步到 Neo4j
    └─ 失效 Redis 缓存
```

### 8.4 PostgreSQL 表结构

```sql
-- 权限关系元组（作为 Neo4j 的写入源）
CREATE TABLE permission_tuple (
    id BIGSERIAL PRIMARY KEY,
    object_type VARCHAR(50) NOT NULL,
    object_id VARCHAR(100) NOT NULL,
    relation VARCHAR(50) NOT NULL,
    subject_type VARCHAR(50) NOT NULL,
    subject_id VARCHAR(100) NOT NULL,
    subject_relation VARCHAR(50),
    expires_at TIMESTAMP,
    granted_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(object_type, object_id, relation, subject_type, subject_id)
);

-- 记录规则
CREATE TABLE record_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    model VARCHAR(64) NOT NULL,
    domain_force TEXT,
    perm_read BOOLEAN DEFAULT true,
    perm_write BOOLEAN DEFAULT true,
    active BOOLEAN DEFAULT true
);

-- 记录规则与角色关联
CREATE TABLE record_rule_role (
    rule_id BIGINT REFERENCES record_rule(id),
    role_id BIGINT REFERENCES sys_role(id),
    PRIMARY KEY (rule_id, role_id)
);
```

## 9. 权限检查流程

### 9.1 完整流程

```
请求到达
    ↓
┌─────────────────────────────────────────────────────────────────┐
│  Phase 1: 快速检查（内存，无 IO）                                │
│  • JWT 有效性                                                   │
│  • 用户状态                                                     │
│  • Admin 快速通道 → 直接放行                                     │
└─────────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────────┐
│  Phase 2: 权限检查                                              │
│  • Redis 缓存查询 → 命中则返回                                   │
│  • Neo4j 图查询（RBAC + ReBAC 统一）                            │
│  • 结果写入缓存                                                  │
└─────────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────────┐
│  Phase 3: 数据过滤                                              │
│  • JPA 拦截器注入记录规则                                        │
│  • 自动附加 org_id + 数据范围条件                                │
└─────────────────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────────────────┐
│  Phase 4: 条件评估（ABAC）                                       │
│  • 置信度检查（Agent）                                          │
│  • 时间/IP 等动态条件                                           │
└─────────────────────────────────────────────────────────────────┘
    ↓
执行操作
```

### 9.2 性能指标

| 场景 | 预期耗时 |
|------|----------|
| Admin 用户 | < 1ms |
| 缓存命中 | < 2ms |
| 缓存未命中 | 5-15ms |
| 无权限请求（缓存） | < 2ms |

### 9.3 缓存策略

- 缓存正向和负向结果（拒绝也缓存）
- TTL：5 分钟
- 权限变更时主动失效相关缓存
- Admin 角色跳过缓存查询

## 10. 技术实现方案

### 10.1 分层复用原则

> 标准问题用成熟技术，AAF 特有需求自己实现。

| 模块 | 实现方式 | 工作量 |
|------|----------|--------|
| 认证（JWT/OAuth2） | Spring Security 直接用 | 低 |
| RBAC 功能权限 | Spring Security `@PreAuthorize` | 低 |
| ReBAC 关系权限 | 自定义 PermissionEvaluator + Neo4j | 中 |
| 记录规则 | JPA 拦截器 | 中 |
| ABAC 条件 | 内置策略引擎 | 低 |
| 实时授权 | WebSocket + Redis | 中 |

### 10.2 Spring Security 集成点

| 功能 | Spring Security 组件 |
|------|---------------------|
| JWT 认证 | OAuth2 Resource Server |
| 角色检查 | `@PreAuthorize("hasRole(...)")` |
| 资源权限 | 自定义 `PermissionEvaluator` |
| 方法安全 | `@EnableMethodSecurity` |

### 10.3 与元引擎的集成

权限引擎在 Layer 2（引擎层），贯穿所有执行路径：

```
用户请求 / Agent 执行
    ↓
[认证] JWT 校验
    ↓
[RBAC] 功能权限
    ↓
[ReBAC] 资源关系（Neo4j）
    ↓
[记录规则] 数据过滤（JPA）
    ↓
[ABAC] 置信度门控
    ↓
执行 / 拒绝 / 请求确认
```

## 11. 运营与 Agent 权限

### 11.1 运营人员权限

| 角色 | 权限范围 | 数据范围 |
|------|----------|----------|
| ops_admin | 全部运营功能 | 全部数据 |
| ops_auditor | 内容审核 | 全部内容 |
| ops_support | 客服工单 | 分配的工单 |

运营操作强制审计，记录操作人、时间、目标、详情。

### 11.2 Agent 权限

| 约束 | 说明 |
|------|------|
| 权限上限 | 不超过触发用户 |
| 工具白名单 | 可配置允许/禁止的工具 |
| 置信度门控 | 低置信度操作需确认 |
| 会话隔离 | 临时权限仅限当前会话 |

## 12. 渐进实现路径

| 版本 | 范围 | 说明 |
|------|------|------|
| **v0.1** | RBAC + JWT | Spring Security 标准配置，内置角色 |
| **v0.2** | 记录规则 | JPA 拦截器，org_id 过滤 |
| **v0.3** | ReBAC 核心 | Neo4j 集成，关系元组，权限继承 |
| **v0.4** | 完整方案 | 实时授权，Agent 权限，置信度门控 |

## 13. 相关文档

- [安全架构设计](security.md) — 加密、脱敏、审计、AI 安全

## 14. 决策记录

| 日期 | 决策点 | 结论 | 理由 |
|------|--------|------|------|
| 2026-05-06 | 权限模型 | RBAC + ReBAC + 记录规则 + ABAC 分层组合 | 单一模型无法满足全部需求 |
| 2026-05-06 | 关系权限存储 | Neo4j | 图遍历性能优于递归 CTE |
| 2026-05-06 | 认证方案 | Spring Security OAuth2 + JWT | 成熟方案，零开发量 |
| 2026-05-06 | Agent 权限 | 继承触发用户 ∩ 自身配置 | 安全边界明确 |
| 2026-05-06 | 实时授权 | WebSocket + Redis 会话权限 | 支持 Agent 交互场景 |
