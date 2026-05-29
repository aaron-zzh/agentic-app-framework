# EntityDef 扩展指南

## EntityDef 核心概念

EntityDef 是 AAF 配置驱动架构的核心——定义一个实体配置，系统自动生成列表/表单/看板视图 + REST API + 数据库表。

```text
EntityDef 配置 → ViewEngine 自动渲染 UI
             → 后端自动注册 CRUD API
             → 数据库自动建表
```

## 基本结构

```typescript
interface EntityDef {
  slug: string;              // URL 路径 + 唯一标识
  label: string;             // 显示名称
  apiPath: string;           // 后端 API 路径
  icon?: string;             // 侧边栏图标（lucide 图标名）
  group?: string;            // 侧边栏分组
  fields: FieldDef[];        // 字段定义
  listView: ListViewConfig;  // 列表配置
  formView?: FormViewConfig; // 表单配置
  kanbanView?: KanbanViewConfig;
}
```

## 创建实体示例

```typescript
// 客户管理实体
export const customerEntity: EntityDef = {
  slug: 'customer',
  label: '客户',
  apiPath: '/api/customers',
  icon: 'users',
  group: '业务管理',
  mixins: ['timestamp', 'audit', 'softDelete'],
  fields: [
    { name: 'name', type: 'text', required: true, label: '客户名称' },
    { name: 'phone', type: 'text', label: '联系电话' },
    { name: 'email', type: 'email', label: '邮箱' },
    { name: 'status', type: 'select', label: '状态', options: [
      { label: '潜在', value: 'lead', color: 'gray' },
      { label: '活跃', value: 'active', color: 'green' },
      { label: '流失', value: 'churned', color: 'red' },
    ]},
    { name: 'assignee', type: 'relationship', relationTo: 'user', label: '负责人' },
    { name: 'notes', type: 'richText', label: '备注' },
  ],
  listView: {
    columns: ['name', 'phone', 'status', 'assignee', 'updatedAt'],
    defaultSort: 'updatedAt:desc',
    searchableFields: ['name', 'phone', 'email'],
    filterableFields: ['status', 'assignee'],
    batchActions: ['delete', 'archive'],
  },
  kanbanView: {
    statusField: 'status',
    cardTitle: 'name',
  },
  formView: {
    layout: [{ type: 'tabs', tabs: [
      { label: '基本信息', fields: ['name', 'phone', 'email', 'status', 'assignee'] },
      { label: '详情', fields: ['notes'] },
    ]}],
  },
};
```

## 字段类型

| 类型 | 说明 | 关键属性 |
|------|------|---------|
| `text` | 单行文本 | `maxLength`, `placeholder` |
| `textarea` | 多行文本 | `rows` |
| `number` | 数字 | `min`, `max`, `precision` |
| `email` | 邮箱 | 自动格式校验 |
| `date` | 日期 | `format` |
| `select` | 下拉选择 | `options`, `multiple` |
| `checkbox` | 复选框 | — |
| `relationship` | 关联字段 | `relationTo`, `hasMany` |
| `richText` | 富文本 | Lexical 编辑器 |
| `upload` | 文件上传 | `accept`, `maxSize`, `maxCount` |
| `formula` | 计算字段 | `expression`, `resultType` |
| `subtable` | 子表明细 | `childEntity`, `columns` |

## 条件可见性

字段根据其他字段值动态显示/隐藏/只读：

```typescript
{ name: 'rejectReason', type: 'textarea',
  visibleWhen: { field: 'status', operator: 'eq', value: 'rejected' },
  requiredWhen: { field: 'status', operator: 'eq', value: 'rejected' },
}
```

## 表达式上下文（FieldContext）

条件表达式中可引用运行时值：

```typescript
// 引用当前记录字段
{ field: 'status', operator: 'eq', value: 'draft' }

// 引用用户信息
{ field: '$user.role', operator: 'in', value: ['admin', 'hr'] }

// 动态关联过滤（省→市级联）
{ name: 'city', type: 'relationship', relationTo: 'city',
  optionsFrom: {
    dynamicFilter: { province_id: '$record.province' }
  }
}

// 动态默认值
{ name: 'assignee', type: 'relationship', defaultValue: '$user.id' }
```

## Mixin 复用

预定义 Mixin 消除重复字段定义：

```typescript
// 使用内置 Mixin
{
  slug: 'order',
  mixins: ['timestamp', 'audit', 'softDelete', 'org'],
  fields: [/* 仅业务字段 */],
}
```

内置 Mixin：`timestamp`（创建/更新时间）、`audit`（创建/更新人）、`softDelete`（软删除）、`org`（多租户）。

## 自定义覆盖

### 整个视图覆盖

```typescript
{
  slug: 'workflow',
  overrides: {
    listView: WorkflowCustomList, // 自定义 React 组件
  },
}
```

### 单个字段覆盖

```typescript
{ name: 'color', type: 'text',
  components: {
    Field: ColorPicker,    // 表单中用自定义组件
    Cell: ColorSwatch,     // 列表中用自定义组件
  }
}
```

## 动态注册（无代码）

EntityDef 支持存储在数据库中，运行时加载：

```text
用户/AI 创建 EntityDef → 保存到 sys_entity_def 表
  → 后端自动 CREATE TABLE + 注册 API
  → 前端加载配置 → ViewEngine 渲染
  → 全程无需写代码、无需重启
```

通过管理界面或 AI 对话创建：

```
用户：帮我创建一个项目管理模块，包含项目名称、状态、负责人、截止日期
AI：[生成 EntityDef JSON] → 保存 → 立即可用
```

## 权限配置

```typescript
{
  slug: 'salary',
  access: {
    read: true,
    create: false,
    fieldAccess: {
      amount: { visible: false, editable: false }, // 仅特定角色可见
    },
  },
  dataAccess: [{
    name: '只看本部门',
    roles: ['manager'],
    condition: { field: 'department_id', operator: 'in', value: '$user.departments' },
  }],
}
```
