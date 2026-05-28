---
level: Practice
layer: Model
purpose: 用户与访问控制模块需求规格
status: active
version: 2.0.0
date: 2026-05-06
author: AaronZZH
changelog:
  - 2026-05-06 | 基于 access-control.md + security.md 设计文档重写，扩展为完整访问控制
  - 2026-05-03 | 初版（仅 JWT + RBAC）
---

<!-- ⚠️ 早期需求，未经过六问分析。进入开发前由 product agent 补充需求分析章节 -->
<!-- scope_mode: hold -->

# 用户与访问控制模块

任务编号：AAF-022

## 背景

AAF 的 Actor 统一抽象要求 Human 和 AI Agent 共享同一套认证授权体系。访问控制不只管"能不能访问"，还管"能和谁协作"、"Agent 能做什么"。

## 用户故事

### US-1：用户认证（JWT）

**作为** 框架使用者，**我希望** 通过用户名密码登录获取 JWT Token，**以便** 访问受保护的接口。

#### 验收标准

```gherkin
Feature: 用户认证

  Scenario: 用户登录
    Given 系统中存在用户 admin
    When 使用正确的用户名密码调用登录接口
    Then 返回 JWT Access Token（2h）+ Refresh Token（7d）

  Scenario: Token 过期刷新
    Given 用户持有有效的 Refresh Token
    When Access Token 过期后调用刷新接口
    Then 返回新的 Access Token

  Scenario: 未认证访问
    Given 请求未携带 Token
    When 访问受保护接口
    Then 返回 401
```

### US-2：角色权限管理（RBAC）

**作为** 系统管理员，**我希望** 通过角色控制用户的功能权限，**以便** 不同用户有不同的操作范围。

#### 验收标准

```gherkin
Feature: 角色权限管理

  Scenario: 内置角色
    Given 系统初始化完成
    Then 存在内置角色：super_admin / org_admin / member / guest / agent

  Scenario: 角色权限控制
    Given 用户 A 拥有 org_admin 角色，用户 B 拥有 member 角色
    When 用户 B 尝试调用需要 org_admin 权限的接口
    Then 返回 403

  Scenario: 自定义角色
    Given 管理员已登录
    When 创建自定义角色 project_manager（继承 member + 额外权限）
    Then 分配该角色的用户可访问额外权限对应的接口
```

### US-3：Actor 统一抽象

**作为** 框架开发者，**我希望** Human 和 Agent 共享统一的 Actor 接口，**以便** 业务代码不需要区分操作者是人还是 AI。

#### 验收标准

```gherkin
Feature: Actor 统一抽象

  Scenario: Human 操作记录 Actor
    Given 用户 alice 创建了一篇文档
    Then 文档的 creator_type = 'HUMAN', creator_id = alice.id

  Scenario: Agent 操作记录 Actor
    Given Agent coding-agent 通过 API 创建了一个任务
    Then 任务的 creator_type = 'AI', creator_id = coding-agent.id

  Scenario: 审计日志统一格式
    Given 任何操作发生
    Then 审计日志中 actor 字段格式为 actor_type/actor_id
```

### US-4：组织隔离

**作为** 多租户场景的管理员，**我希望** 不同组织的数据天然隔离，**以便** 用户只能看到自己组织的数据。

#### 验收标准

```gherkin
Feature: 组织隔离

  Scenario: 数据隔离
    Given 用户 A 属于组织 org-1，用户 B 属于组织 org-2
    When 用户 A 查询文档列表
    Then 只返回 org-1 的文档，看不到 org-2 的数据

  Scenario: 跨组织不可见
    Given 用户 A 知道 org-2 的文档 ID
    When 用户 A 直接通过 ID 访问该文档
    Then 返回 403
```

### US-5：关系权限（ReBAC）

**作为** 文档所有者，**我希望** 将文档分享给特定用户并控制其权限级别（查看/编辑），**以便** 实现细粒度的协作权限管理。

#### 验收标准

```gherkin
Feature: 关系权限

  Scenario: 分享文档给用户
    Given 用户 alice 是 doc-1 的 owner
    When alice 将 doc-1 分享给 bob，权限为 editor
    Then bob 可以编辑 doc-1
    And 系统创建关系元组 document:doc-1#editor@user:bob

  Scenario: 权限继承
    Given space-1 下有 doc-1 和 doc-2
    And 用户 charlie 是 space-1 的 member
    When charlie 访问 doc-1
    Then charlie 自动拥有 viewer 权限（从 space member 继承）

  Scenario: 撤销分享
    Given bob 是 doc-1 的 editor
    When alice 撤销 bob 的权限
    Then bob 无法再访问 doc-1
```

### US-6：记录规则（数据权限）

**作为** 系统管理员，**我希望** 通过 DSL 规则控制用户能看到哪些行数据，**以便** 实现部门级/团队级数据隔离而无需硬编码。

#### 验收标准

```gherkin
Feature: 记录规则

  Scenario: 团队数据隔离
    Given 记录规则 "member 只能看本团队数据"
    And 用户 A 属于团队 team-1
    When 用户 A 查询任务列表
    Then 只返回 team_id = team-1 的任务

  Scenario: 管理员不受限
    Given 用户 B 拥有 org_admin 角色
    When 用户 B 查询任务列表
    Then 返回组织内所有任务（不受记录规则限制）

  Scenario: 规则热加载
    Given 管理员修改了记录规则
    When 规则保存后
    Then 新规则立即生效，无需重启
```

### US-7：置信度门控（ABAC）

**作为** 框架使用者，**我希望** Agent 执行高风险操作时自动暂停等待我确认，**以便** 防止 AI 在不确定时做出不可逆操作。

#### 验收标准

```gherkin
Feature: 置信度门控

  Scenario: 高风险操作强制确认
    Given Agent 尝试删除一篇文档
    When 操作风险等级为 HIGH
    Then Agent 暂停执行，推送确认请求给用户
    And 用户确认后才执行删除

  Scenario: 低风险操作自动执行
    Given Agent 尝试读取一篇文档
    When 操作风险等级为 LOW
    Then 直接执行，无需确认

  Scenario: 置信度不足暂停
    Given Agent 置信度 < 0.7
    When Agent 尝试执行任何写操作
    Then 暂停执行，转人工处理
```

### US-8：实时交互授权

**作为** 框架使用者，**我希望** Agent 在执行过程中发现需要额外权限时能实时请求我授权，**以便** 不中断任务流程又保证安全。

#### 验收标准

```gherkin
Feature: 实时交互授权

  Scenario: Agent 请求临时权限
    Given Agent 执行任务时需要访问一个无权限的文件
    When Agent 发起授权请求
    Then 用户收到实时推送（WebSocket），显示请求原因和资源
    And 用户确认后 Agent 获得会话级临时权限继续执行

  Scenario: 授权超时
    Given Agent 发起授权请求
    When 5 分钟内用户未响应
    Then 请求过期，Agent 跳过该操作并记录日志

  Scenario: 临时权限会话结束失效
    Given Agent 获得了会话级临时权限
    When 会话结束
    Then 临时权限自动失效
```

### US-9：审计日志

**作为** 系统管理员，**我希望** 所有安全相关操作都有审计记录，**以便** 事后追溯和合规审查。

#### 验收标准

```gherkin
Feature: 审计日志

  Scenario: 登录审计
    Given 用户登录（成功或失败）
    Then 记录时间、IP、设备、结果

  Scenario: 权限变更审计
    Given 管理员修改了用户角色
    Then 记录操作人、变更前后、时间

  Scenario: Agent 操作审计
    Given Agent 执行了工具调用
    Then 记录 Agent ID、工具名、输入输出摘要、耗时

  Scenario: 审计查询
    Given 管理员打开审计日志页面
    When 按时间/操作人/类型筛选
    Then 返回匹配的审计记录列表
```

## 需求规格

### 数据模型

**sys_user**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial PK | 主键 |
| username | varchar(50) UNIQUE | 用户名 |
| password | varchar(200) | 密码（BCrypt） |
| nickname | varchar(100) | 昵称 |
| org_id | bigint FK | 所属组织 |
| status | smallint | 1 启用 / 0 禁用 |
| actor_type | varchar(16) | 固定 'HUMAN' |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

**sys_role**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial PK | 主键 |
| code | varchar(50) UNIQUE | 角色编码（如 org_admin） |
| name | varchar(100) | 角色名称 |
| inherits | varchar(50) | 继承的角色编码 |
| is_builtin | boolean | 是否内置（不可删除） |

**sys_user_role**

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | bigint FK | 用户 ID |
| role_id | bigint FK | 角色 ID |

**sys_org**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial PK | 主键 |
| name | varchar(100) | 组织名称 |
| parent_id | bigint | 父组织 ID |
| status | smallint | 1 启用 / 0 禁用 |

### 接口定义

**POST /api/auth/login** — 用户登录（返回 JWT + Refresh Token）

**POST /api/auth/refresh** — 刷新 Token

**GET /api/system/users** — 用户列表（org_admin+）

**PUT /api/system/users/{id}/roles** — 分配角色（org_admin+）

**GET /api/system/roles** — 角色列表

**POST /api/system/roles** — 创建自定义角色（org_admin+）

### 约束

- JWT Access Token 有效期 2 小时，Refresh Token 7 天
- 密码使用 BCrypt 加密存储
- 内置角色不可删除不可修改
- 所有数据查询自动附加 org_id 过滤（JPA 拦截器）
- Agent 认证通过 API Key，权限 ≤ 触发用户

### 渐进实现路径

一次性实现完整访问控制（RBAC + 记录规则 + ReBAC + ABAC），安全功能不完整等于没有安全。后续版本仅优化性能和扩展场景。

## 相关设计

- 访问控制设计：[access-control.md](../../../design/framework/security/access-control.md)
- 安全架构设计：[security.md](../../../design/framework/security/security.md)
- Operator 模型设计：[operator.md](../../../design/framework/operator.md)
