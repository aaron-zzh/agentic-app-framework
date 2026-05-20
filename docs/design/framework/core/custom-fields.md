---
level: Practice
layer: Product
purpose: AAF 用户自定义字段设计——运行时动态扩展实体字段
status: draft
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 用户自定义字段（Custom Fields）

> 允许用户（非开发者）在运行时为实体添加自定义字段，无需修改代码或重新部署。
> 所属体系：[结构化视图模式](../apps/webui/interaction-mode-structured-view.md) | [元引擎](../framework/meta-engine.md)

## 一、定位

用户可根据业务需要，在管理界面为任意实体动态添加字段。系统自动完成数据库加列、API 扩展、UI 渲染，全程无需开发介入。

```text
用户点击 [+ 添加字段]
  → 选择字段类型 + 配置属性
  → 后端：ALTER TABLE ADD COLUMN + 注册元数据
  → 前端：EntityDef 刷新 → UI 自动渲染新字段
```

## 二、技术方案

### 方案选型

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| ALTER TABLE（直接加列） | 查询标准、ORM 原生支持、可索引 | 需 DDL 权限 | ✅ 采用 |
| JSONB 列 | 零 DDL | 查询非标准、无数据库约束 | ❌ |
| EAV 模式 | 最灵活 | 查询性能差、无约束 | ❌ |

**选择 ALTER TABLE 的理由**：PostgreSQL 11+ 加列（NULLABLE）瞬间完成、不锁表、不重写数据。查询完全标准化，JPA/QueryDSL/报表/BI 无需适配。

### 安全约束

- 自定义字段一律 **NULLABLE**（required 由应用层校验，不加数据库级 NOT NULL）
- 不支持修改已有列类型（ALTER TYPE 会锁表重写）
- 删除 = 逻辑隐藏（不 DROP COLUMN，数据保留可恢复）
- 字段名强制 `x_` 前缀，区分系统字段和用户自定义字段

## 三、元数据表

全局一张表，通过 `entity_slug` 区分所属实体：

```sql
CREATE TABLE custom_field_def (
  id          BIGSERIAL PRIMARY KEY,
  entity_slug VARCHAR(64) NOT NULL,     -- 所属实体（如 'document'）
  field_name  VARCHAR(64) NOT NULL,     -- 列名（x_ 前缀，如 'x_priority'）
  field_type  VARCHAR(32) NOT NULL,     -- text/number/date/select/relationship/checkbox...
  label       VARCHAR(128) NOT NULL,    -- 显示名称
  config      JSONB DEFAULT '{}',       -- 扩展配置
  sort_order  INT DEFAULT 0,            -- 排列顺序
  hidden      BOOLEAN DEFAULT FALSE,    -- 逻辑删除（隐藏）
  created_by  BIGINT NOT NULL,          -- 创建人
  created_at  TIMESTAMP DEFAULT NOW(),
  UNIQUE(entity_slug, field_name)
);
```

### config JSONB 示例

```json
// select 类型
{ "options": [{"label": "高", "value": "high"}, {"label": "低", "value": "low"}] }

// relationship 类型
{ "relationTo": "user", "hasMany": false }

// number 类型
{ "min": 0, "max": 100, "precision": 2 }

// 通用属性
{ "required": true, "placeholder": "请输入...", "defaultValue": "xxx" }
```

## 四、操作支持

| 操作 | 支持 | 实现方式 |
|------|------|---------|
| 添加字段 | ✅ | ALTER TABLE ADD COLUMN x_{name} {type} |
| 修改配置 | ✅ | 更新 custom_field_def.config（不改列类型） |
| 隐藏字段 | ✅ | UPDATE custom_field_def SET hidden = true |
| 恢复字段 | ✅ | UPDATE custom_field_def SET hidden = false |
| 物理删除列 | ❌ | 不支持（数据不可逆丢失） |

## 五、字段类型映射

| field_type | PostgreSQL 列类型 | 前端组件 |
|------------|------------------|---------|
| text | VARCHAR(500) | TextInput |
| textarea | TEXT | Textarea |
| number | NUMERIC | NumberInput |
| date | TIMESTAMP | DatePicker |
| checkbox | BOOLEAN | Checkbox |
| select | VARCHAR(64) | Select（options 从 config 读取） |
| relationship | BIGINT（FK） | RelationshipPicker |
| email | VARCHAR(255) | EmailInput |
| url | VARCHAR(500) | URLInput |

## 六、前后端协作流程

```text
1. 前端：用户在设置菜单点击 [+ 添加字段] → 弹窗配置
2. 前端：POST /api/entities/{slug}/custom-fields { fieldName, fieldType, label, config }
3. 后端：
   a. 校验字段名（x_ 前缀、不重复、合法标识符）
   b. 执行 ALTER TABLE {entity_table} ADD COLUMN x_{name} {pg_type}
   c. INSERT INTO custom_field_def
   d. 返回成功
4. 后端：发送 SSE 事件 { resource: "{slug}", type: "schema_changed" }
5. 前端：TanStack Query invalidate EntityDef → 重新获取字段列表 → UI 自动渲染新字段
```

### EntityDef API 合并逻辑

```text
GET /api/entities/{slug}/definition
  → 系统字段（代码中定义的 FieldDef[]）
  + 自定义字段（custom_field_def WHERE entity_slug = '{slug}' AND hidden = false）
  → 合并返回统一的 FieldDef[]
```

前端无需区分系统字段和自定义字段，统一按 EntityDef 渲染。

## 七、权限

- 仅**管理员角色**可添加/隐藏/恢复自定义字段
- 普通用户只能使用（填写/查询）自定义字段
- 字段级权限复用现有 RBAC 体系（可配置某角色对某字段只读/不可见）

## 八、前端入口

```text
表单视图 / 列表视图 → 右上角 ⚙️ 设置菜单
  → [自定义字段]
  → 弹窗：已有自定义字段列表 + [+ 添加字段] 按钮
  → 添加：选择类型 → 填写标签/配置 → 确认
  → 隐藏：字段行右侧 [隐藏] 按钮
  → 排序：拖拽调整顺序
```

## 九、EntityDef：静态 + 动态共存

### 设计原则

EntityDef 支持三种来源，按实体自由选择：

| 来源 | 适用场景 | 说明 |
|------|---------|------|
| 纯静态（代码定义） | 系统核心实体（user/role/permission） | 字段固定，不允许用户修改 |
| 静态 + 动态扩展 | 业务实体（document/task） | 系统字段代码定义 + 用户可追加自定义字段 |
| 纯动态（后端存储） | 用户自建实体 | 整个 EntityDef 由用户/AI 创建，无代码定义 |

```text
前端启动
  → GET /api/entities/{slug}/definition
  → 后端返回完整 EntityDef（无论来源是代码、数据库还是混合）
  → 前端统一按返回值渲染
```

纯动态实体从创建开始就完全由后端存储驱动——用户通过 UI 或 AI 对话创建实体、定义字段、配置视图，无需任何代码。

### 视图自动调整规则

| 视图 | 添加字段后的默认行为 | 用户手动调整 |
|------|-------------------|------------|
| 表单 | 新字段追加到末尾（按 sort_order） | 设置菜单 → 拖拽调整字段位置/分组 |
| 列表 | 不自动显示（避免列过多） | 列选择器中勾选启用 |
| 看板 | 不影响（看板只显示 cardTitle + statusField） | — |
| 筛选/排序 | 自动可用（出现在筛选器字段列表中） | — |

### AI 生成与调整

用户可通过自然语言让 AI 调整字段和视图配置：

```text
用户："给文档加一个优先级字段，高中低三个选项，列表中显示"
  → AI 生成：POST /api/entities/document/custom-fields
    { fieldName: "x_priority", fieldType: "select", label: "优先级",
      config: { options: [{label:"高",value:"high"}, {label:"中",value:"mid"}, {label:"低",value:"low"}] } }
  → AI 追加：PATCH /api/entities/document/view-config
    { listView: { columns: [...existing, "x_priority"] } }
  → 前端刷新 → 新字段出现在表单和列表中
```

这是 Magic-DSL "自然语言→DSL→执行"能力在自定义字段场景的具体应用。

## 十、实现范围

| 能力 | 说明 |
|------|------|
| 后端 API + ALTER TABLE + 元数据表 | 核心基础 |
| EntityDef 合并（系统字段 + 自定义字段） | 前后端统一 |
| 前端自定义字段管理弹窗 | 添加/隐藏/排序/配置 |
| 全类型支持 | text/number/date/select/checkbox/relationship/email/url |
| 视图配置调整 | 列表列选择、表单字段排序 |
| 字段级权限 | 复用 RBAC，按角色控制可见/只读 |
| AI 生成字段 + 调整视图 | 自然语言→DSL→执行 |
| 字段公式（计算字段） | 基于其他字段值自动计算 |
