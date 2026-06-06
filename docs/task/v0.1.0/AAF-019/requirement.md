---
level: Practice
layer: Model
purpose: 文档管理系统需求规格（内部开发文档管理）
status: active
version: 2.0.0
date: 2026-05-22
author: AaronZZH
changelog:
  - 2026-05-22 | v2.0 对齐双链语法、本地文件自动同步、关系图谱可视化、弹窗编辑
  - 2026-05-03 | v1.0 初始版本
---

# 文档管理系统

任务编号：AAF-019

## 背景

AAF 项目的 `docs/` 目录下有大量 Markdown 文档（规范、设计、任务、指南），目前只能通过编辑器管理。本功能提供 Web 界面，让开发者可以在线浏览、编辑、搜索文档，并自动识别文档间的引用关系，以关系图谱形式可视化。

存储分工：
- **PostgreSQL**：文档内容、元数据（Front Matter）、全文检索
- **Neo4j**：文档引用关系图谱（双链 + Markdown 链接）

## 用户故事

### US-1：文档查看与编辑

**作为** 框架开发者，**我希望** 通过 Web 页面查看和编辑项目的设计、规范、需求、任务文档，修改后同步到本地文件，**以便** 在线管理规范文档而不需要切换到编辑器。

#### 验收标准

```gherkin
Feature: 文档查看与编辑

  Scenario: 浏览文档目录
    Given 项目 docs/ 目录下有多个文档
    When 打开文档页面
    Then 左侧展示文档树结构（按 spec/design/task/guide/reference/explanation 分类）
    And 点击文档名称在右侧展示 Markdown 渲染内容

  Scenario: 弹窗编辑文档
    Given 正在查看一篇文档
    When 点击"编辑"按钮
    Then 弹出编辑弹窗，展示原始 Markdown 内容（textarea）
    And 保存后内容更新到数据库并同步写回本地 docs/ 对应文件

  Scenario: 导入本地文档（手动触发）
    Given 本地 docs/ 目录有新增或修改的文件
    When 调用 POST /api/docs/import
    Then 扫描 docs/ 目录，解析 Front Matter 元数据，新增或更新数据库记录
    And 提取文档中的双链 [[文档名]] 和 Markdown 链接（`[文本](路径)` 格式），写入 Neo4j

  Scenario: 启动时自动同步
    Given 服务启动
    When 应用启动完成
    Then 自动扫描 docs/ 目录，将本地文件同步到数据库（新增/更新）
```

### US-2：文档关系管理（双链 + 图谱）

**作为** 框架开发者，**我希望** 文档之间的引用关系能被自动识别并以图谱形式可视化，**以便** 快速理解文档间的依赖和影响范围。

#### 验收标准

```gherkin
Feature: 文档关系管理

  Scenario: 自动识别双链引用
    Given 文档 A 中包含 [[文档B]] 或 Markdown 相对链接（`[文本](相对路径)` 格式，如相对路径 `../path/to/B.md`）
    When 文档 A 被导入或保存
    Then 系统在 Neo4j 中创建 A → B 的 REFERENCES 关系边

  Scenario: 查看文档关系图谱
    Given 多篇文档之间存在引用关系
    When 打开某篇文档的关系视图
    Then 展示以该文档为中心的 React Flow 关系图（直接引用 + 被引用，深度 ≤ 2）
    And 点击图中节点弹出该文档的预览弹窗

  Scenario: 查询受影响文档
    Given 文档 B 即将被修改
    When 调用 GET /api/docs/{id}/relations
    Then 返回所有直接引用 B 的文档列表（source 和 target 两个方向）
```

### US-3：全文检索

**作为** 框架开发者，**我希望** 能通过关键词快速搜索所有文档内容，**以便** 在大量文档中快速定位相关信息。

#### 验收标准

```gherkin
Feature: 全文检索

  Scenario: 关键词搜索
    Given 系统中已有多篇文档
    When 调用 GET /api/docs/search?q=Agent生命周期
    Then 返回所有包含该关键词的文档列表（title + file_path + 匹配片段）
    And 结果按相关度排序（PostgreSQL tsvector）

  Scenario: 搜索无结果
    Given 输入不存在的关键词
    When 执行搜索
    Then 返回空列表
```

## 需求规格

### 存储架构

| 存储 | 职责 |
|------|------|
| PostgreSQL `doc_document` | 文档内容、Front Matter 元数据、全文检索索引 |
| Neo4j `DocNode` + `REFERENCES` | 文档引用关系图谱（双链 + 路径链接） |

### 数据模型

**doc_document**（已在 V1 迁移中存在，本期补充 `front_matter` 字段）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| title | varchar(200) | 标题（从 Front Matter 或 H1 提取） |
| file_path | varchar(500) | 相对于项目根目录的路径 |
| content | text | 文档全量 Markdown 内容 |
| front_matter | jsonb | 解析后的 Front Matter 元数据 |
| doc_type | varchar(50) | spec/design/task/guide/reference/explanation |
| status | varchar(20) | active/archived |
| create_time | timestamp | 创建时间 |
| update_time | timestamp | 更新时间 |

**doc_link**（新增，Neo4j 关系的 PG 镜像，用于快速查询）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | 主键 |
| source_id | bigint FK | 来源文档 ID |
| target_id | bigint FK | 目标文档 ID |
| link_type | varchar(20) | wikilink（双链）/ mdlink（Markdown 链接） |
| create_time | timestamp | 创建时间 |

**Neo4j 节点/关系**

- 节点：`DocNode {docId, title, filePath}`
- 关系：`(DocNode)-[:REFERENCES {linkType}]->(DocNode)`

### 接口定义

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/docs/tree | 获取文档树（按目录层级） |
| GET | /api/docs/{id} | 获取文档详情（含 content） |
| PUT | /api/docs/{id} | 更新文档内容（同步写回本地文件） |
| POST | /api/docs/import | 触发全量导入（扫描 docs/ 目录） |
| GET | /api/docs/{id}/relations | 获取文档关系图数据（nodes + edges） |
| GET | /api/docs/search | 全文检索（?q=关键词） |

### 约束

- 文档编辑为单用户模式，不处理并发冲突
- 本地文件同步：保存时写回文件（AAF → 本地），反向变更需手动触发导入
- 启动时自动扫描一次（`@EventListener(ApplicationStartedEvent.class)`）
- 双链解析：`[[文档名]]` 按 title 匹配；Markdown 链接按 file_path 匹配
- 关系图谱深度限制：前端展示深度 ≤ 2，后端接口返回直接关系（1 跳）
- 全文检索使用 PostgreSQL `tsvector`（中文分词暂用 `simple` 配置）

## 相关设计

- 后端技术选型：[tech-stack.md](../../../design/apps/service/tech-stack.md)
- 内容体系规范：[content-system](../../../reference/content-system/Readme.md)
