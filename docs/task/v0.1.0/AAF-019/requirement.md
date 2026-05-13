---
level: Practice
layer: Model
purpose: 文档管理系统需求规格
status: active
version: 1.0.0
date: 2026-05-03
author: AaronZZH
---

<!-- ⚠️ 早期需求，未经过六问分析。进入开发前由 product agent 补充需求分析章节 -->
<!-- scope_mode: hold -->

# 文档管理系统

任务编号：AAF-019

## 用户故事

### US-1：文档查看与编辑

**作为** 框架开发者，**我希望** 通过 Web 页面查看和编辑项目的设计、规范、需求、任务文档，修改后同步到本地文件，**以便** 在线管理规范文档而不需要切换到编辑器。

#### 验收标准

```gherkin
Feature: 文档查看与编辑

  Scenario: 浏览文档目录
    Given 项目 docs/ 目录下有多个文档
    When 打开文档页面
    Then 左侧展示文档树结构（按 spec/design/task/guide 分类）
    And 点击文档名称在右侧展示内容

  Scenario: 编辑并同步文档
    Given 正在查看一篇设计文档
    When 修改文档内容并点击保存
    Then 内容保存到数据库
    And 同步写回本地 docs/ 对应文件

  Scenario: 导入本地文档
    Given 本地 docs/ 目录有新增或修改的文件
    When 触发文档导入
    Then 扫描 docs/ 目录，新增或更新数据库中的文档记录
```

### US-2：文档关系管理

**作为** 框架开发者，**我希望** 文档之间的引用关系能被自动识别并可视化，**以便** 快速理解文档间的依赖和影响范围。

#### 验收标准

```gherkin
Feature: 文档关系管理

  Scenario: 自动识别文档引用关系
    Given 文档 A 中包含指向文档 B 的 Markdown 链接
    When 文档 A 被导入或保存
    Then 系统在 Neo4j 中创建 A → B 的引用关系边

  Scenario: 查看文档关系图
    Given 多篇文档之间存在引用关系
    When 打开某篇文档的关系视图
    Then 展示以该文档为中心的关系图（直接引用 + 被引用）

  Scenario: 查询受影响文档
    Given 文档 B 即将被修改
    When 查询"哪些文档引用了 B"
    Then 返回所有直接或间接引用 B 的文档列表
```

### US-3：全文检索

**作为** 框架开发者，**我希望** 能通过关键词快速搜索所有文档内容，**以便** 在大量文档中快速定位相关信息。

#### 验收标准

```gherkin
Feature: 全文检索

  Scenario: 关键词搜索
    Given 系统中已有多篇文档
    When 输入关键词 "Agent 生命周期"
    Then 返回所有包含该关键词的文档列表，高亮匹配片段
    And 结果按相关度排序

  Scenario: 搜索无结果
    Given 输入不存在的关键词
    When 执行搜索
    Then 返回空列表，提示"未找到相关文档"
```

## 需求规格

### 存储架构

- **PostgreSQL**：存储文档内容、元数据、版本快照
- **Neo4j**：存储文档间引用关系图谱，支持关系查询和影响分析
- **块状存储**：文档内容以块（Block）为最小单元存储，支持多层次嵌套网络结构，便于细粒度版本控制和局部更新

### 数据模型

**doc_document**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial PK | 主键 |
| title | varchar(200) | 标题 |
| file_path | varchar(500) | 本地文件相对路径 |
| content | text | 文档内容（全量，用于同步） |
| doc_type | varchar(50) | spec / design / task / guide / explanation |
| version | integer | 版本号 |
| status | varchar(20) | active / archived |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

**doc_block**（块状存储）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigserial PK | 主键 |
| document_id | bigint FK | 所属文档 |
| parent_id | bigint | 父块 ID（null 为根块） |
| block_type | varchar(50) | paragraph / heading / code / list 等 |
| content | text | 块内容 |
| sort_order | integer | 同级排序 |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

### 接口定义

**GET /api/docs/tree** — 获取文档树结构

**GET /api/docs/{id}** — 获取文档内容

**PUT /api/docs/{id}** — 更新文档内容（同步写回本地文件）

**POST /api/docs/import** — 触发本地文档导入

**GET /api/docs/{id}/relations** — 获取文档关系图

**GET /api/docs/search?q={keyword}** — 全文检索

### 约束

- 文档编辑为单用户模式，不处理并发冲突
- 文档同步仅支持 AAF → 本地文件方向的自动同步，反向需手动触发导入
- 版本快照在每次保存时自动创建


## 相关设计

- 迭代架构设计：[后端技术选型](../../../design/apps/service/tech-stack.md)