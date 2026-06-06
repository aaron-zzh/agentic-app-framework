---
level: Practice
layer: Product
purpose: AAF 前端权限配置与控制设计——四层权限模型的前端消费与配置
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 前端权限配置与控制

> 前端如何消费后端四层权限模型（RBAC + ReBAC + 记录规则 + ABAC），实现 UI 级权限控制和权限配置界面。
> 后端权限设计：[access-control.md](../../framework/security/access-control.md)
> 所属体系：[结构化视图模式](./interaction-mode-structured-view.md) | [用户自定义字段](../../framework/intelligent/core/custom-fields.md)

## 一、设计原则

| 原则 | 含义 |
|------|------|
| 权限即配置 | 权限规则通过 EntityDef 声明，不散落在组件代码中 |
| 后端决策，前端执行 | 权限判定在后端完成，前端只负责 UI 响应（隐藏/禁用/提示） |
| 无权限 = 不存在 | 用户看不到无权限的菜单/按钮/字段/数据，而非看到灰色禁用态 |
| DSL 可描述 | 权限规则可用 Magic-DSL 声明，AI 可生成/调整 |

## 二、权限 DSL

用声明式 DSL 描述权限规则，简化配置，AI 可读可生成：

```dsl
entity Document {
  access {
    read:   role("member")
    create: role("member")
    update: owner() | role("org_admin")
    delete: role("org_admin")
  }

  fields {
    title   { visible: all(), editable: owner() | role("org_admin") }
    content { visible: all(), editable: owner() | role("editor") }
    salary  { visible: role("hr_manager"), editable: role("hr_manager") }
  }

  data {
    rule "本部门可见" {
      condition: record.department_id == actor.department_id
      applies_to: role("member")
    }
    rule "全部可见" {
      applies_to: role("org_admin")
    }
  }

  share {
    allow: owner()
    roles: ["viewer", "editor", "admin"]
  }
}
```

DSL 编译为后端权限规则 + 前端 EntityDef.access 配置，一份定义两端生效。

### DSL 权限函数

| 函数 | 含义 |
|------|------|
| `role("xxx")` | 拥有指定角色 |
| `owner()` | 资源的创建者/所有者 |
| `shared("editor")` | 被分享了 editor 权限 |
| `all()` | 所有已认证用户 |
| `none()` | 无人（禁用） |
| `org("xxx")` | 属于指定组织 |
| `department(actor.department_id)` | 同部门 |
| `\|` / `&` | 或 / 且 |

## 三、权限数据获取

### 登录后获取权限集

```typescript
// GET /api/auth/permissions → 当前用户的完整权限上下文
interface UserPermissions {
  roles: string[]                          // ["member", "project_manager"]
  orgId: string                            // 当前组织
  permissions: string[]                    // 功能权限点 ["document:create", "workflow:deploy"]
  entityAccess: Record<string, EntityAccess>  // 每个实体的 CRUD 权限
}

interface EntityAccess {
  read: boolean
  create: boolean
  update: boolean
  delete: boolean
  fieldAccess?: Record<string, FieldAccess>  // 字段级权限
}

interface FieldAccess {
  visible: boolean
  editable: boolean
}
```

### 缓存与刷新

- TanStack Query 缓存权限数据（`queryKey: ['permissions']`）
- 角色变更时后端推送 SSE 事件 → invalidate 权限缓存
- 组织切换时重新获取权限集

## 四、前端权限守卫

### 4.1 路由级（中间件）

```typescript
// middleware.ts — 无权限的路由直接 redirect，用户感知不到页面存在
export function middleware(request: NextRequest) {
  const permissions = getPermissionsFromToken(request)
  const requiredPermission = matchRoutePermission(request.pathname)
  if (!permissions.includes(requiredPermission)) {
    return NextResponse.redirect('/workspace')
  }
}
```

### 4.2 组件级

```tsx
// 无权限时不渲染（不是灰色禁用，是完全不存在）
<Can permission="document:delete">
  <DeleteButton />
</Can>

// 基于实体 access
<Can entity="document" action="update" record={doc}>
  <EditButton />
</Can>
```

### 4.3 Hook 级

```typescript
const { can, cannot } = usePermission()

if (can('document:create')) { /* 显示创建按钮 */ }
if (cannot('document', 'delete', record)) { /* 隐藏删除 */ }
```

### 4.4 EntityDef 驱动自动控制

EntityDef 中声明 access，视图引擎自动处理：

```typescript
interface EntityDef {
  access?: {
    read?: string       // 权限表达式或角色
    create?: string
    update?: string
    delete?: string
  }
  fields: Array<FieldDef & {
    access?: FieldAccess   // 字段级权限
  }>
}
```

视图引擎根据 access 自动：
- 列表工具栏：无 create 权限 → 隐藏 [+ 新建] 按钮
- 行操作菜单：无 delete 权限 → 隐藏 [删除]
- 表单字段：editable=false → 渲染为只读展示
- 字段：visible=false → 完全不渲染

## 五、权限配置界面（管理员）

### 5.1 角色管理

```text
/workspace/settings/roles
├── 角色列表（内置 + 自定义）
├── [+ 新建角色]
└── 角色详情
    ├── 基本信息（名称/描述/继承自）
    ├── 功能权限矩阵（实体 × CRUD 勾选表格）
    │   ┌──────────┬──────┬──────┬──────┬──────┐
    │   │ 实体      │ 查看 │ 创建 │ 编辑 │ 删除 │
    │   ├──────────┼──────┼──────┼──────┼──────┤
    │   │ 文档      │  ✓  │  ✓  │  ✓  │  ✗  │
    │   │ 工作流    │  ✓  │  ✗  │  ✗  │  ✗  │
    │   └──────────┴──────┴──────┴──────┴──────┘
    ├── 字段级权限（展开实体 → 字段可见/可编辑勾选）
    └── 数据范围（全部 / 本组织 / 本部门 / 仅本人）
```

### 5.2 用户角色分配

```text
/workspace/settings/users/{id}
├── 用户信息
├── 所属组织/部门
├── 角色分配（多选 Tag）
└── 特殊权限覆盖（针对个人的额外授权/限制）
```

### 5.3 资源分享（ReBAC）

```text
文档详情页 → [分享] 按钮 → 弹窗
├── 当前协作者列表
│   ├── 张三（owner）
│   ├── 李四（editor）[▼ 改为 viewer] [× 移除]
│   └── [+ 添加协作者]
├── 添加：搜索用户/团队 → 选择角色（viewer/editor/admin）
└── 链接分享：[生成分享链接] → 设置权限级别 + 有效期
```

### 5.4 记录规则配置

```text
/workspace/settings/data-rules
├── 规则列表
└── 规则编辑
    ├── 适用实体：[Document ▼]
    ├── 适用角色：[member ▼]
    ├── 条件：record.department_id == actor.department_id
    │   （可视化条件构建器 或 DSL 表达式输入）
    └── 效果：仅可见满足条件的记录
```

## 六、与 EntityDef 的集成

权限配置保存后，EntityDef API 返回时自动合并权限信息：

```text
GET /api/entities/{slug}/definition
  → 字段列表（已按当前用户权限过滤 visible=false 的字段）
  → access 对象（当前用户对该实体的 CRUD 权限）
  → 视图引擎根据返回值自动控制 UI
```

前端组件无需手动判断权限——EntityDef 返回什么就渲染什么，无权限的字段/操作根本不在返回数据中。

## 七、Agent 权限（AI 特有）

| 场景 | UI 表现 |
|------|---------|
| Agent 执行操作 | 操作卡片标注"由 AI 执行"+ 权限范围标签 |
| 低置信度操作 | 弹出确认卡片："AI 建议执行 [删除文档]，是否允许？" |
| Agent 权限边界 | Copilot 面板显示当前 Agent 可用的操作列表 |
| 审计日志 | 设置页可查看 Agent 历史操作记录 |

Agent 受同一套权限规则约束，但增加置信度门控：

```dsl
entity Document {
  access {
    delete: role("org_admin") | (agent() & confidence("> 0.9") & human_confirm())
  }
}
```

## 八、多组织数据隔离

```text
顶部导航栏 → 组织切换器 [当前组织 ▼]
  → 切换组织 → 重新获取权限集 + 刷新所有数据
```

- 每条记录隐含 `org_id`，后端自动注入过滤条件
- 前端无需处理隔离逻辑——切换组织 = 切换权限上下文 = 数据自动变化
- 超级管理员可跨组织查看（后端记录规则不注入 org_id 过滤）
- 组织管理员只能管理本组织的角色/用户/数据

## 九、AI 生成权限配置

用户可通过自然语言配置权限：

```text
用户："项目经理角色可以查看和编辑所有项目，但只能删除自己创建的"
  → AI 生成权限 DSL：
    entity Project {
      access {
        read:   role("project_manager")
        create: role("project_manager")
        update: role("project_manager")
        delete: role("project_manager") & owner()
      }
    }
  → 预览权限矩阵 → 用户确认 → 保存生效
```

## 十、实现范围

| 能力 | 说明 |
|------|------|
| 权限数据获取 + 缓存 | 登录后获取，TanStack Query 缓存，SSE 刷新 |
| 路由/组件/Hook 三级守卫 | 无权限 = 不存在 |
| EntityDef 驱动自动控制 | 视图引擎根据 access 自动显隐 |
| 角色管理界面 | CRUD + 权限矩阵 + 数据范围 |
| 资源分享（ReBAC） | 协作者管理 + 链接分享 |
| 记录规则配置 | 可视化条件构建器 |
| 多组织切换 | 顶部切换器 + 数据自动隔离 |
| 权限 DSL | 声明式定义，AI 可生成 |
| Agent 权限 + 置信度门控 | 确认卡片 + 审计日志 |
