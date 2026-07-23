---
level: Practice
layer: Model
purpose: 定义通用 CRUD 筛选操作符、字段能力声明与 Odoo 式高级筛选的统一契约
status: draft
version: 0.1.0
date: 2026-07-17
author: AaronZZH & Kiro
tags:
  - CRUD
  - 筛选
  - 元数据
  - 实体引擎
scope:
  includes:
    - 操作符参数数量与字段类型能力矩阵
    - 资源级筛选能力元数据接口
    - 服务端解析的受控日期时间内置变量
    - 前端高级筛选交互边界
  excludes:
    - 任意字段的动态 SQL 查询
    - 旧 f_ 查询参数兼容
    - OR 逻辑
    - 用户或授权上下文变量
---

# 筛选操作符能力矩阵

## 设计边界

查询窗口固定为 `GET /{resource}/_query?filter=<base64url-json>`。`filter` 解码后必须是 `logic: "and"` 和 `conditions` 数组；不读取、不解码、不兼容旧 `f_*` 参数。每个条件为 `{ "field", "operator", "values" }`，其中 `values` 始终是字符串数组。

操作符是后端枚举，不是业务字典。它定义语义和参数数量；资源服务声明每个字段实际支持的操作符，并在执行谓词前再次验证字段、操作符和值。`/_meta` 仅驱动界面，不能扩大查询权限。

## 公共操作符

| 操作符 | 语义 | 最少值 | 最多值 | 适用字段类型 |
| --- | --- | ---: | ---: | --- |
| `eq` | 精确等于 | 1 | 1 | select、date、number、text |
| `in` | 属于任一值 | 1 | 不限 | select、relationship |
| `notIn` | 不属于任一值 | 1 | 不限 | select、relationship |
| `contains` | 包含子串 | 1 | 1 | text |
| `startsWith` | 以前缀开头 | 1 | 1 | text |
| `gt` | 大于 | 1 | 1 | date、number |
| `gte` | 大于等于 | 1 | 1 | date、number |
| `lt` | 小于 | 1 | 1 | date、number |
| `lte` | 小于等于 | 1 | 1 | date、number |
| `between` | 左闭右开区间 | 2 | 2 | date、number |
| `isNull` | 数据库值为 `NULL` | 0 | 0 | 可空字段 |
| `isNotNull` | 数据库值不是 `NULL` | 0 | 0 | 可空字段 |
| `isEmpty` | 文本值为 `NULL` 或空字符串 `''` | 0 | 0 | text |
| `isNotEmpty` | 文本值非 `NULL` 且非空字符串 | 0 | 0 | text |

`isNull` 与 `isEmpty` 有明确差异：前者仅测试数据库 `NULL`，后者仅用于文本并同时匹配 `NULL` 和 `''`。不进行空白字符裁剪，因此只含空格的值不是空值；`isBlank` 尚未实现。`between` 的两个值依次为起点和终点，资源以 `>= start` 且 `< end` 执行。`notIn` 将 `NULL` 视为不属于任何提交值，因此可空字段的 `notIn` 结果包含 `NULL`。

## Todo 资源能力

| 字段 | 操作符 | 值控件 | 执行约束 |
| --- | --- | --- | --- |
| `title` | `eq`、`contains`、`startsWith`、`isEmpty`、`isNotEmpty` | 文本或无值控件 | 文本匹配；空值语义见上表 |
| `status` | `eq`、`in`、`notIn` | 状态单选或多选 | 值仅限 Todo 状态枚举 |
| `category` | `eq`、`in`、`notIn`、`isNull`、`isNotNull` | 分类单选、多选或无值控件 | 值仅限 Todo 分类枚举 |
| `dueDate` | `between`、`gt`、`gte`、`lt`、`lte`、`isNull`、`isNotNull` | 日期时间、内置变量建议或无值控件 | 日期比较；`between` 为左闭右开 |

`dueDate` 的字段级变量为 `$now`、`$todayStart`、`$tomorrowStart` 和 `$nowPlus3Days`。它们由 Todo 服务端在每个日期条件中捕获一次当前时间后解析，分别代表当前时刻、当天起点、次日起点和当前时刻后三天。变量不是字典值，且不公开 `$user.id` 等用户或授权变量。

“即将到期”快捷筛选保持为复合条件：

```json
{
  "logic": "and",
  "conditions": [
    { "field": "dueDate", "operator": "between", "values": ["$now", "$nowPlus3Days"] },
    { "field": "status", "operator": "eq", "values": ["pending"] }
  ]
}
```

## 筛选能力元数据

每个 CRUD 资源通过既有 `GET /{resource}/_meta` 返回实际能力。操作符的 `minValues`、`maxValues` 反映公共参数契约；`variables` 只列出该字段可被服务端解析的变量。

```json
{
  "entitySlug": "todo",
  "filterFields": [
    {
      "field": "title",
      "operators": [
        { "value": "contains", "minValues": 1, "maxValues": 1 },
        { "value": "isEmpty", "minValues": 0, "maxValues": 0 }
      ],
      "variables": []
    },
    {
      "field": "dueDate",
      "operators": [
        { "value": "between", "minValues": 2, "maxValues": 2 },
        { "value": "isNull", "minValues": 0, "maxValues": 0 }
      ],
      "variables": ["$now", "$todayStart", "$tomorrowStart", "$nowPlus3Days"]
    }
  ]
}
```

未声明 `filterFields` 的资源不支持高级条件筛选。新增变量时必须同步资源解析器、字段元数据、前端值控件和文档；变量不可绕过数据访问规则。

## 前端交互模型

Toolbar 保留轻量全文搜索；“添加筛选”打开 Odoo 式字段—操作符—值条件构建器。字段取本地 `EntityDef` 与服务端 `filterFields` 的交集，操作符只取当前字段的服务器声明，多个条件以 AND 组合并写入唯一的 `filter` URL 参数。

单值操作符使用对应的 select、文本、数字或日期控件；`in` 和 `notIn` 在 select 字段使用多选；`between` 使用两个输入。零值操作符不渲染值控件。拥有 `variables` 的日期字段使用文本输入和字段级建议，因此用户可输入 ISO 日期时间或服务端已授权的变量；界面不会维护全局静态变量表。

## 演进规则

新增操作符必须同步完成：`CrudFilterOperator` 参数数量、共享解析校验、资源谓词与值校验、资源 `/_meta` 声明、前端值控件和测试。新增变量必须同步完成服务端单次时间捕获与解析、字段范围授权、前端建议和文档。任一环节缺失时，不得公开该操作符或变量。
