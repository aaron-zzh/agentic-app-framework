---
level: Practice
layer: Model
purpose: 元引擎外部数据源对接设计
status: draft
version: 0.1.0
date: 2026-05-08
author: AaronZZH
---

# 外部数据源对接设计

> 元引擎如何安全、统一地对接外部数据源（数据库/API/SDK/文件）
> 自动外部系统API集成功能?

## 1. 两种对接场景

| 场景 | 说明 | 适用 |
|------|------|------|
| **ETL 导入** | 定时/事件触发从外部拉数据，写入 AAF PostgreSQL | CRM 同步客户、ERP 同步订单 |
| **联邦查询** | 不落库，运行时实时查外部数据源，结果统一格式返回 | 报表聚合多源、大屏实时展示 |

## 2. 架构

```text
┌─────────────────────────────────────────┐
│  DSL 数据源定义                          │
│  type: external                         │
│  provider: jdbc | http | graphql | file │
│  connection: {加密配置}                  │
│  mapping: {字段映射到元引擎实体}          │
└─────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────┐
│  DataSource Adapter（统一适配层）         │
│  ├── JdbcAdapter    → 参数化查询         │
│  ├── HttpAdapter    → REST/GraphQL 调用  │
│  └── FileAdapter    → CSV/Excel 导入     │
└─────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────┐
│  DataSet（统一数据集结构）                │
│  { headers, rows, pagination, metadata }│
└─────────────────────────────────────────┘
```

## 3. 核心设计原则

- **DSL 驱动**：用户配置 DSL 定义数据源，元引擎生成参数化查询，不允许用户直接写 SQL
- **安全优先**：连接凭证加密存储（Jasypt），查询参数化防注入，结果脱敏
- **统一抽象**：不管数据来自哪里，下游（报表/大屏/AI）拿到的都是 `DataSet` 结构
- **按需连接**：连接池动态创建/销毁，不长期占用资源

## 4. DSL 示例

```yaml
datasource:
  name: erp-orders
  type: external
  provider: jdbc
  connection:
    url: jdbc:postgresql://erp-db:5432/erp
    username: readonly
    password: ${encrypted}
  query:
    table: orders
    fields: [order_id, customer_name, amount, status]
    filter: status = :status AND created_at > :since
  mapping:
    order_id: 订单编号
    customer_name: 客户名称
    amount: 金额
    status: 状态
```

## 5. DataSet 统一结构

```java
public record DataSet(
    List<DataHeader> headers,    // 列定义（名称、类型、显示名）
    List<DataRow> rows,          // 行数据
    Pagination pagination,       // 分页信息
    Map<String, Object> metadata // 元数据（数据源、查询耗时等）
) {}
```

## 6. 与 mfish-nocode 的区别

| 维度 | mfish | AAF |
|------|-------|-----|
| 用户输入 | 直接写 SQL | 配置 DSL |
| 安全性 | SQL 拼接，注入风险 | 参数化查询，DSL 白名单 |
| 数据源管理 | 动态多数据源连接池 | 按需创建，超时回收 |
| 结果结构 | MetaDataTable | DataSet（类型安全） |

## 7. 实现路径

- v0.2+：JdbcAdapter（PostgreSQL/MySQL 外部库）
- v0.3+：HttpAdapter（REST API 对接）
- v0.4+：FileAdapter（CSV/Excel 批量导入）
- 与元引擎实体运行时联动：外部数据可映射为虚拟实体，支持标准 CRUD 查询语义

## 8. 所在包

`com.xuejiai.aaf.framework.engine.datasource`
