---
level: Practice
layer: Product
purpose: AAF 数据字典与选项来源设计
status: draft
version: 1.0.0
date: 2026-05-12
author: AaronZZH
---

# 数据字典与选项来源设计

> 关联文档：[结构化视图模式](./interaction-mode-structured-view.md)

## AAF 数据字典

### 三种来源

| 来源 | 后端 | 前端配置 | 何时用 |
|------|------|---------|--------|
| 硬编码 | 无 | `options: [...]` | 状态枚举等永不变的值 |
| 字典 | `sys_dictionary` 单表 | `optionsFrom: { type: 'dictionary', group: 'xxx' }` | 运维可维护的小选项集 |
| 关联实体 | 独立业务表 | `type: 'relationship'` 或 `optionsFrom: { type: 'entity' }` | 有独立生命周期的业务对象 |

### 判断标准

```text
选项会变吗？
  ├─ 不会 → 硬编码 options
  └─ 会 → 选项有自己的属性/关系吗？
        ├─ 有（如用户有邮箱/角色）→ 关联实体
        └─ 没有（纯 label+value）→ 字典
```

## 双表设计

```sql
-- 字典类型（分组元数据）
CREATE TABLE sys_dict_type (
  id          BIGINT PRIMARY KEY,
  code        VARCHAR(64) NOT NULL UNIQUE,  -- 分组编码 'doc_type'
  name        VARCHAR(128) NOT NULL,        -- 分组名称 '文档类型'
  builtin     BOOLEAN DEFAULT FALSE,        -- 系统内置（不可删除）
  extensible  BOOLEAN DEFAULT TRUE,         -- 是否允许用户扩展选项
  remark      VARCHAR(256),
  enabled     BOOLEAN DEFAULT TRUE,
  created_at  TIMESTAMP,
  updated_at  TIMESTAMP,
  deleted     BOOLEAN DEFAULT FALSE
);

-- 字典数据（具体选项）
CREATE TABLE sys_dict_data (
  id          BIGINT PRIMARY KEY,
  type_code   VARCHAR(64) NOT NULL,         -- 归属分组编码（FK 逻辑关联）
  label       VARCHAR(128) NOT NULL,        -- 显示名
  value       VARCHAR(128) NOT NULL,        -- 存储值
  sort        INT DEFAULT 0,               -- 排序
  color       VARCHAR(32),                  -- 前端标签颜色
  enabled     BOOLEAN DEFAULT TRUE,
  remark      VARCHAR(256),
  created_at  TIMESTAMP,
  updated_at  TIMESTAMP,
  deleted     BOOLEAN DEFAULT FALSE,
  UNIQUE(type_code, value)
);
```

双表理由：
- 分组有独立属性（是否内置、是否可扩展、状态），单表无处放
- 分组列表查询直接 `SELECT * FROM sys_dict_type`，无需 DISTINCT
- 职责清晰，符合第三范式
- 与芋道/若依一致，成熟方案

## 前端：统一抽象

### FieldDef 扩展

```typescript
interface SelectField extends BaseField {
  type: 'select'
  options?: { label: string; value: string; color?: string }[]   // 硬编码
  optionsFrom?: {
    type: 'dictionary' | 'entity'
    group?: string              // 字典分组
    entity?: string             // 关联实体 slug
    labelField?: string         // 默认 'label'(字典) / 'name'(实体)
    valueField?: string         // 默认 'value'(字典) / 'id'(实体)
    filter?: Record<string, any>
  }
}
```

### 渲染统一

三种来源渲染组件相同（`<SelectInput>`），区别仅在数据获取和缓存：

| 来源 | 请求 | 缓存 |
|------|------|------|
| 硬编码 | 无 | 无需 |
| 字典 | `GET /api/dict-data?typeCode=x` | staleTime 10min（很少变） |
| 关联实体 | `GET /api/{entity}?fields=id,name` | staleTime 1min |

数据量大的关联实体（>50 条）自动切换为异步搜索模式（`<RelationshipPicker>`）。

## 示例

```typescript
// 硬编码：状态（代码逻辑依赖这些值，不应外部修改）
{ name: 'status', type: 'select',
  options: [{label:'草稿',value:'draft'}, {label:'已发布',value:'published'}] }

// 字典：文档类型（运维可加减，无业务逻辑依赖）
{ name: 'docType', type: 'select',
  optionsFrom: { type: 'dictionary', group: 'doc_type' } }

// 关联实体：作者（有独立属性和 CRUD）
{ name: 'author', type: 'relationship', relationTo: 'user' }
```
