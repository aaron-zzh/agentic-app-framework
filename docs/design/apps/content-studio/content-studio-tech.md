---
level: Practice
layer: Model
purpose: 定义 Content Studio 与原 AIGC 模块一次性整合后的模块边界、领域模型、数据库表、文件生命周期、迁移步骤与验收标准
status: draft
version: 1.0.0
date: 2026-08-04
author: AaronZZH & Kiro
tags:
  - Content Studio
  - AIGC
  - Project
  - Media
  - Asset
  - Work
  - Flyway
scope:
  includes:
    - Content Studio 与 AIGC 的单模块整合
    - 唯一 Project 聚合与项目对象图谱
    - 素材、资产、交付物与作品边界
    - sys_file、媒体版本与物理文件生命周期
    - 目标数据库表和旧表收敛映射
    - 一次性重构步骤与验收标准
  excludes:
    - 模型供应商采购与参数配置
    - 文档、知识库、记忆、工作流模块内部表结构
    - 专业非线性剪辑器和自动投放实现
gains:
  - 能按唯一 Project 聚合实施 Content Studio 与 AIGC 整合
  - 能建立 sys_file、MediaVersion、Asset 和 Work 的单一所有权关系
  - 能判断旧表应合并、改名、保留还是删除
  - 能按无兼容层的一次性步骤完成数据库、后端、前端和数据迁移
  - 能验证项目、执行、文件删除和作品发布不存在双真理源
dependencies:
  - ./content-studio-design.md
  - ./content-studio-capability-concept-map.md
  - ../../../reference/dev/architecture-constraints.md
related:
  - ./content-studio-competitor-analysis.md
  - ../service/module-structure.md
---

# Content Studio 技术设计

> 本文是 [Content Studio 产品设计](./content-studio-design.md) 的工程落地方案。产品设计定义业务概念和产品边界，本文定义模块、数据、接口、迁移和验证方式。发生冲突时，先修正产品设计或本文，再修改代码，禁止用兼容层维持两套真理源。

## 设计结论

- Content Studio 是原 AIGC 模块的重构增强，不建立平行 `module.content` 业务模块。
- 系统只有一个 `Project` 聚合、一个项目 ID 空间和一个项目 API 体系。
- 保留现有 `aigc_project` 作为唯一项目主表，将 `cs_project` 字段和行为合并后删除 `cs_project`。
- `ProjectGraph` 是 `ProjectObject + ProjectRelation` 的领域门面，不建立独立图节点表，也不保存 React Flow 节点数组。
- `ExecutionRun` 是所有 Agent、Tool、Workflow 动作的统一执行记录；`AigcTask` 只承载媒体生成子任务。
- 所有持久媒体先形成 `Media + MediaVersion`；用户明确标记可复用后才创建 `Asset` 资产库登记。
- `Work` 只引用已采用、发布或归档的 Deliverable/ObjectVersion，不复制媒体文件。
- `sys_file` 管物理存储对象，`MediaVersion` 管媒体文件版本，业务表不持久化 URL 作为文件身份。
- 删除业务对象只解除引用和软删除；只有异步文件垃圾回收器可以删除 `sys_file` 与对象存储文件。
- AAF 未发布 v1.0，本次采用一次性切换，不增加 fallback、旧 API 适配、双写或双读。

## 模块边界

后端统一归入：

```text
com.xuejiai.aaf.module.ai.aigc
├─ config       项目类型、蓝图、领域扩展、渠道、执行绑定
├─ project      Project、ProjectObject、ProjectRelation、ObjectVersion
├─ brand        BrandProfile 与版本引用
├─ media        Media、MediaVersion、项目媒体引用
├─ asset        Asset、分类、标签、集合和派生关系
├─ execution    ExecutionRun、ActionCommand、AigcTask 关联
├─ work         Work 与渠道发布记录
├─ timeline     TimelineComposition、Track、Clip、StoryboardExport
├─ task         媒体生成子任务
├─ image        图像专业能力
├─ video        视频专业能力
├─ voice        音频和音色能力
└─ model3d      三维内容能力
```

以下包在重构完成后删除：

```text
com.xuejiai.aaf.module.content
```

AIGC 业务包访问文件、文档、知识库、Assistant、Workflow 等其他业务包时，只依赖对方 `api` 接口或领域事件，禁止直接访问对方 Entity、Repository 或 Service 实现。

前端产品壳继续使用 `/studio`，领域能力统一到 AIGC API：

```text
/studio/projects/{id}       项目工作台
/studio/assets/materials    项目或工作区素材视图
/studio/assets/assets       可复用资产库
/studio/assets/works        作品库

/api/aigc/projects
/api/aigc/project-objects
/api/aigc/project-relations
/api/aigc/object-versions
/api/aigc/media
/api/aigc/assets
/api/aigc/execution-runs
/api/aigc/works
```

删除 `/api/content/**`，不保留转发控制器。

## 核心领域模型

```text
Workspace
├─ BrandProfile[]
│  └─ BrandProfileVersion[]
├─ Project[]
│  ├─ ProjectConfigurationSnapshot
│  ├─ ProjectProfileRef[]
│  ├─ ProjectObject[]
│  │  └─ ObjectVersion[]
│  ├─ ProjectRelation[]
│  ├─ ProjectDocumentRef[]
│  ├─ ProjectResourceRef[]
│  ├─ ProjectMediaRef[]
│  ├─ ExecutionRun[]
│  │  └─ AigcTask[]
│  └─ TimelineComposition[]
├─ Media[]
│  └─ MediaVersion[]
│     └─ sys_file
├─ Asset[]
└─ Work[]
   └─ WorkPublication[]
```

### 权威所有权

| 信息 | 权威所有者 | 禁止做法 |
|---|---|---|
| 物理文件 key、存储配置、大小、哈希和删除状态 | `sys_file` | 业务表从 URL 反解存储 key |
| 素材身份、来源和当前版本 | `aigc_media` | 用 Asset 或 Work 代替素材身份 |
| 不可变媒体文件版本 | `aigc_media_version` | 在多个业务表重复保存 URL |
| 跨项目可复用登记 | `aigc_asset` | 标记资产时复制媒体文件 |
| 项目内媒体用途 | `aigc_project_media_ref` | 在 Media 上固定写死 COVER/BGM 等用途 |
| 项目对象、父级、顺序和状态 | `aigc_project_object` | Storyboard、结构视图和图谱各存一份状态 |
| 对象候选与采用版本 | `aigc_object_version` | 生成成功后直接覆盖已采用内容 |
| 执行状态、成本、重试和输入输出 | `aigc_execution_run` | 用 AigcTask 表示所有执行 |
| 已采用或发布成果 | `aigc_work` | 把 `aiGenerated=true` 当作作品 |
| 长文本正文 | `DocumentVersion` | 将长文正文存入 Media 或 Work |

### 素材、资产、交付物和作品

```text
上传或生成文件
→ Media + MediaVersion
→ 在项目中通过 ProjectMediaRef 使用
→ 用户标记“可复用”后创建 Asset
→ Deliverable 采用 ObjectVersion
→ 用户收录或发布后创建 Work
```

- 素材 `Media`：项目产生或使用的全部持久媒体。
- 资产 `Asset`：被整理并允许跨项目复用的素材登记。
- 交付物 `Deliverable`：项目内需要完成、比较、采用和审核的内容对象。
- 作品 `Work`：作品库对已采用、发布或归档交付物的引用。

产品设计中的 `AssetVersion` 在工程模型中落实为 `MediaVersion`：不可变文件版本由 MediaVersion 持有，Asset 只是可复用登记。该命名细化不改变“文件版本只有一个权威所有者、资产和作品不复制媒体”的产品约束。

上传和 AI 生成只是 Media 的 `sourceType`，不能决定它是不是作品。

## 目标数据库表

以下清单是统一 AIGC/Content Studio 目标态。文档、知识库、记忆、Assistant、Workflow 与文件存储的内部表由各自模块管理，本模块只保存稳定引用。

### 项目定义与配置

| 表 | 职责 | 关键字段或关系 |
|---|---|---|
| `aigc_project_type` | 项目业务目标 | `code`、`definition_version`、默认渠道、默认生产模式、发布状态 |
| `aigc_project_blueprint` | 版本化项目骨架 | `code`、`blueprint_version`、对象规格、关系规格、交付物、`action_keys` |
| `aigc_project_type_package` | 兼容配置组合 | 类型、蓝图、领域、渠道、生产模式和执行绑定版本集合 |
| `aigc_domain_extension` | 版本化领域扩展 | 字段、规则、Validator、动作约束和迁移声明 |
| `aigc_channel_spec` | 版本化渠道规格 | 尺寸、时长、文案结构、必要声明和导出格式 |
| `aigc_execution_binding` | 动作到执行目标的绑定 | `action_key`、上下文条件、`AGENT/TOOL/WORKFLOW`、目标版本 |
| `aigc_snippet` | 轻量创作片段 | 文本、参考媒体、变量槽位和适用范围 |

### 品牌与 IP

| 表 | 职责 | 关键字段或关系 |
|---|---|---|
| `aigc_brand_profile` | 品牌/IP 稳定身份 | 名称、类型、工作区、当前版本和状态 |
| `aigc_brand_profile_version` | 不可变品牌资料版本 | 定位、受众、语气、视觉、声明、禁用项和规则 |
| `aigc_brand_profile_media_ref` | 品牌版本引用媒体 | `brand_profile_version_id`、`media_version_id`、角色 |
| `aigc_brand_profile_document_ref` | 品牌版本引用文档 | `brand_profile_version_id`、`document_version_id`、角色 |

### 项目核心

| 表 | 职责 | 关键字段或关系 |
|---|---|---|
| `aigc_project` | 唯一项目聚合根 | 类型、蓝图、领域、生产/生成模式、预算、成本和图谱修订 |
| `aigc_project_config_snapshot` | 不可变项目配置快照 | 采用版本、兼容性结果和完整 JSON 快照 |
| `aigc_project_profile_ref` | 项目引用品牌资料版本 | 一主、多辅助及作用范围 |
| `aigc_project_channel_ref` | 项目引用渠道规格版本 | 渠道版本、主渠道和项目覆盖配置 |
| `aigc_resolved_domain_context` | 解析后领域上下文修订 | 输入事实、最终规则、解析结果和修订号 |
| `aigc_project_object` | 项目类型化对象 | 类型、稳定 key、父级、顺序、状态、Schema、采用版本 |
| `aigc_project_relation` | 项目对象语义关系 | 包含、约束、派生、引用、组成、顺序、变体和执行依赖 |
| `aigc_object_version` | 项目对象不可变版本 | 内容、文档版本、执行来源和候选/采用状态 |
| `aigc_project_revision` | 项目图谱逻辑修订 | 变更对象、关系、操作者、来源执行和摘要 |
| `aigc_project_document_ref` | 项目引用文档版本 | 来源、参考、脚本、证据和输出角色 |
| `aigc_project_resource_ref` | 项目引用外部能力 | Assistant、KnowledgeBase、Workflow、Skill、ModelPolicy |
| `aigc_project_media_ref` | 项目对象引用媒体版本 | `media_version_id`、对象、角色、顺序和采用状态 |

`Brief`、`CreativeConcept`、`DeliverableSet`、图片/视频/文案/文章交付物、Episode、Scene、Shot、ShotKeyframe、Character、Prop、Review 和 ClaimEvidence 都由 `aigc_project_object.object_type` 表达，不分别建立状态主表。

### 素材与资产

| 表 | 职责 | 关键字段或关系 |
|---|---|---|
| `aigc_media` | 素材逻辑身份 | 类型、来源、来源执行/任务、当前版本和原始项目 |
| `aigc_media_version` | 不可变媒体文件版本 | `file_id`、`thumbnail_file_id`、版本号、尺寸、时长和生成信息 |
| `aigc_asset` | 可复用资产库登记 | `media_id` 唯一、分类、范围、版权、状态和使用次数 |
| `aigc_asset_category` | 资产分类树 | 工作区、父级、名称和排序 |
| `aigc_asset_tag` | 资产标签定义 | 工作区、名称、颜色和使用次数 |
| `aigc_asset_tag_ref` | 资产标签关系 | `asset_id + tag_id` 唯一 |
| `aigc_asset_relation` | 媒体/资产派生关系 | 变体、裁剪、转码、提取、放大等及处理参数 |
| `aigc_asset_collection` | 用户维护的复用集合 | 品牌、角色、场景、产品或风格集合；MVP 可选 |
| `aigc_asset_collection_item` | 资产集合成员 | 集合、资产、角色和顺序；MVP 可选 |

### 执行与生成

| 表 | 职责 | 关键字段或关系 |
|---|---|---|
| `aigc_execution_run` | 统一业务执行记录 | 项目/对象、动作、目标、上下文、输入输出、成本、状态和重试 |
| `aigc_execution_task_ref` | ExecutionRun 与媒体子任务关系 | `execution_run_id + task_id + role` |
| `aigc_task` | 媒体生成子任务 | 图片、视频、音频、音乐、3D、处理、转码和合成 |

一次 ExecutionRun 可以关联多个 AigcTask。删除 `cs_execution_run.aigc_task_id` 单值设计。

### 作品与发布

| 表 | 职责 | 关键字段或关系 |
|---|---|---|
| `aigc_work` | 作品库登记 | 项目、Deliverable、采用对象版本、封面、状态和可见性 |
| `aigc_work_publication` | 多渠道发布记录 | 作品、渠道、外部 ID/URL、发布状态、计划/实际时间和响应 |

### 视频专业模型

| 表 | 职责 | 关键字段或关系 |
|---|---|---|
| `aigc_timeline_composition` | 轻时间线领域对象 | 项目、交付物、时长、帧率、分辨率、状态和采用修订 |
| `aigc_timeline_track` | 时间线轨道 | 视频、语音、音乐、字幕和叠加层 |
| `aigc_timeline_clip` | 时间线片段 | 媒体版本、对象来源、入出点、裁剪、转场和音量 |
| `aigc_storyboard_export` | 故事板只读导出快照 | 来源项目修订、交付物、导出媒体版本和格式 |

Storyboard 本身是 Shot、ShotKeyframe、ProjectRelation 和 ProjectMediaRef 的投影，不保留 `aigc_storyboard` 第二状态表。

## 文件存储设计

### sys_file 职责

`sys_file` 是物理存储对象的唯一登记表，负责稳定 key、存储配置、文件大小、哈希、状态和物理删除调度。业务模块不保存或解析存储路径。

目标字段：

```text
sys_file
├─ id
├─ storage_config_id       关联 sys_file_config
├─ file_key                存储后端中的稳定对象 key
├─ original_name
├─ mime_type
├─ size
├─ content_hash            SHA-256，用于完整性校验和可选去重
├─ storage_status          ACTIVE/PENDING_DELETE/DELETE_FAILED/DELETED/ORPHAN
├─ delete_after            允许物理删除的最早时间
├─ uploader_id
├─ org_id / workspace_id / owner_id
├─ create_time / update_time / delete_time
└─ deleted
```

本次在当前 `sys_file` 上增加：

```sql
storage_config_id BIGINT REFERENCES sys_file_config(id)
content_hash      VARCHAR(64)
storage_status    VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
delete_after      TIMESTAMP(6)
```

在文件服务完成数据库配置化前，`storage_config_id` 可短期允许空；进入生产前必须由上传服务写入实际存储配置。新上传文件必须计算 `content_hash`，历史或无法回读的记录允许异步补算。

`file_key` 是存储定位真理源。访问 URL 由文件查询 API 根据 `storage_config_id + file_key` 动态生成，不在 AIGC 业务表中持久化。

### sys_file_reference

新增全局文件引用登记：

```text
sys_file_reference
├─ id
├─ file_id
├─ ref_type
├─ ref_id
├─ ref_field
├─ purpose
├─ create_time
└─ deleted
```

约束：

```sql
UNIQUE (file_id, ref_type, ref_id, ref_field)
```

示例：

```text
AIGC_MEDIA_VERSION / 258 / FILE      / PRIMARY
AIGC_MEDIA_VERSION / 258 / THUMBNAIL / PREVIEW
DOCUMENT_VERSION   / 35  / SOURCE     / ORIGINAL
```

业务外键表达“媒体版本具体使用哪个文件”，`sys_file_reference` 表达“文件当前被哪些业务对象使用”。文件模块只通过引用登记判断能否进入物理删除流程，避免直接查询其他业务包 Repository。

### MediaVersion 与 sys_file

```text
aigc_media
  1 ── N aigc_media_version
              ├─ file_id ───────────→ sys_file.id
              └─ thumbnail_file_id ─→ sys_file.id
```

数据库约束：

```sql
FOREIGN KEY (file_id) REFERENCES sys_file(id) ON DELETE RESTRICT
FOREIGN KEY (thumbnail_file_id) REFERENCES sys_file(id) ON DELETE RESTRICT
UNIQUE (media_id, version_no)
```

`aigc_media` 和 `aigc_media_version` 不保存 URL、OSS 路径或 CDN 域名。缩略图是持久文件时登记独立 `sys_file`；只是 OSS 动态处理 URL 时不创建文件记录。

### 文件写入流程

平台决定保留的每个生成结果执行：

```text
供应商返回临时结果
→ 写入对象存储
→ 创建 sys_file
→ 创建 aigc_media
→ 创建 aigc_media_version(file_id)
→ 创建 sys_file_reference
→ 关联 ExecutionRun/AigcTask
```

生成结果默认只成为 Media，不自动创建 Asset。用户执行“保存到资产库”时只创建 `aigc_asset`，不复制文件。

文件上传 API 不再只返回 URL，改为返回：

```java
record StoredFile(
        Long fileId,
        String key,
        String originalName,
        String mimeType,
        long size,
        String contentHash) {}
```

文件 API 至少提供：

```java
StoredFile store(...);
void retain(Long fileId, FileReference reference);
void release(Long fileId, FileReference reference);
String getAccessibleUrl(Long fileId);
void requestDelete(Long fileId);
```

AIGC 业务包依赖 `FileApi`，不得直接调用 `FileRecordService`、`FileRecordRepository` 或 `StorageService`。

### 一致性与补偿

对象存储与 PostgreSQL 无法由本地事务统一提交，采用补偿流程：

- 物理上传成功但 `sys_file` 创建失败：立即补偿删除物理对象，操作失败。
- `sys_file` 创建成功但 MediaVersion 创建失败：将文件标记为 `ORPHAN` 或 `PENDING_DELETE`，由垃圾回收器清理。
- `sys_file_reference` 创建失败：MediaVersion 事务回滚，文件进入孤儿扫描。
- 禁止当前“sys_file 登记失败只记录 warn 并继续创建 Media”的行为。

### 删除与垃圾回收

删除操作按语义分层：

| 用户动作 | 删除内容 | 不删除内容 |
|---|---|---|
| 从项目移除素材 | `aigc_project_media_ref` | Media、MediaVersion、sys_file |
| 从资产库移除 | `aigc_asset` | Media、MediaVersion、sys_file |
| 删除作品 | Work/Publication 的业务登记 | Deliverable、MediaVersion、sys_file |
| 删除素材本体 | Media 进入回收站 | 保留期内的 MediaVersion、sys_file 和文件 |
| 彻底删除素材 | 无业务引用后释放文件引用 | 物理文件由异步 GC 删除 |

物理删除状态机：

```text
ACTIVE
→ PENDING_DELETE（无引用且达到 delete_after）
→ DELETED
  └─ 删除失败 → DELETE_FAILED → 重试
```

流程：

```text
Media 软删除
→ 回收站保留期结束
→ 检查 ProjectMediaRef/Asset/ObjectVersion/Work/AssetRelation
→ 删除 MediaVersion 对应 sys_file_reference
→ sys_file 无任何引用时设置 PENDING_DELETE + delete_after
→ 异步 GC 删除 OSS/MinIO/本地对象
→ 成功后 sys_file 标记 DELETED
```

普通业务 Service 和 HTTP 删除事务不得直接调用 `storageService.delete()`。数据库外键统一使用 `ON DELETE RESTRICT`，禁止从业务对象级联删除 `sys_file`。

## 旧表收敛映射

### 合并到唯一项目根

| 当前表 | 处理 |
|---|---|
| `aigc_project` | 保留并扩展为唯一项目表 |
| `cs_project` | 字段和数据合并到 `aigc_project` 后删除，禁止直接改名产生冲突 |

### Content Studio 表改名并迁入 AIGC

| 当前表 | 目标表 |
|---|---|
| `cs_project_type` | `aigc_project_type` |
| `cs_project_blueprint` | `aigc_project_blueprint` |
| `cs_domain_extension` | `aigc_domain_extension` |
| `cs_channel_spec` | `aigc_channel_spec` |
| `cs_project_profile_ref` | `aigc_project_profile_ref` |
| `cs_project_object` | `aigc_project_object` |
| `cs_project_relation` | `aigc_project_relation` |
| `cs_object_version` | `aigc_object_version` |
| `cs_execution_run` | `aigc_execution_run` |
| `cs_execution_binding` | `aigc_execution_binding` |
| `cs_snippet` | `aigc_snippet` |
| `cs_brand_profile` | 拆为 `aigc_brand_profile + aigc_brand_profile_version` |

### 素材与内容旧表

| 当前表 | 处理 |
|---|---|
| `media_asset` | 拆为 `aigc_media + aigc_media_version`，补建 sys_file 关联 |
| `media_category` | `aigc_asset_category` |
| `media_tag` | `aigc_asset_tag` |
| `media_asset_tag` | `aigc_asset_tag_ref` |
| `media_asset_variant` | `aigc_asset_relation` |
| `media_asset_group` | 删除，由 ExecutionRun/AigcTask 输出关系分组 |
| `media_element` | 删除，由 ProjectObject + ProjectMediaRef 表达 |
| `aigc_content` | 删除，由 Deliverable ProjectObject + ObjectVersion 表达 |
| `aigc_content_asset` | 删除，由 ProjectMediaRef 表达 |
| `aigc_storyboard` | 删除，改为 Storyboard 投影 |
| `aigc_shot` | 删除，改为 SHOT ProjectObject |
| `aigc_shot_asset` | 删除，改为 ProjectMediaRef |
| `aigc_timeline` | `aigc_timeline_composition` |
| `aigc_track` | `aigc_timeline_track` |
| `aigc_clip` | `aigc_timeline_clip` |
| `aigc_project_doc` | `aigc_project_document_ref` |
| `user_project_resource` | `aigc_project_resource_ref` |
| `user_project_template` | 删除，由 Blueprint、复制项目和 Remix 替代 |

## 一次性重构步骤

### 数据库基线

- 确认所有共享、测试和开发数据库是否可清空重建。
- 冻结新的 `cs_*` 表和 `/api/content/**` 代码提交。
- 先修改目标迁移脚本，再重置本地数据库验证完整建库。
- 所有项目相关外键只指向 `aigc_project.id`。
- 建立 `aigc_media_version.file_id → sys_file.id` 和 `sys_file_reference`。

### 后端收敛

- 将 `module.content` 类移动并重命名到 `module.ai.aigc` 对应子包。
- 合并 `ContentProject` 与 `AigcProject`，只保留一个 Entity、Repository、Service 和 Controller。
- 将 Content Action、ExecutionRun 和 ObjectVersion 能力接入唯一 Project。
- 将 AigcTask 的 `project_id` 明确指向唯一 `aigc_project`。
- 将媒体上传改为返回 `StoredFile.fileId`，禁止 URL 反解 key。
- 删除旧 Storyboard/Content/MediaElement/MediaAssetGroup CRUD。
- 保留 TimelineComposition 专业模型，并通过 ProjectObject `entityRef` 接入项目图谱。

### API 收敛

- 统一到 `/api/aigc/**`。
- 删除 `/api/content/**`、旧 `/media-assets` 和重复项目 API。
- 权限编码统一使用 `aigc:*`，删除 `content:*` 并同步权限种子。
- OpenAPI、前端客户端和查询 key 同步切换，不保留旧路径 fallback。

### 前端收敛

- `/studio/projects` 只查询唯一 AIGC Project API。
- 项目工作台继续提供结构/图谱双投影。
- “AI 生成/上传”改为 Media 来源筛选，不再命名为作品/素材。
- 素材页查询 Media；资产页查询 Asset；作品页查询 Work。
- Work 未实现前隐藏作品入口，不复用 AssetLibrary 伪装作品库。
- 任务历史查询 ExecutionRun，并按需展开 AigcTask 子任务。

### 删除平行实现

全链路切换并验证后一次性删除：

```text
module.content
cs_* 表与实体
/api/content/**
ContentProject 前端类型与 Query Key
旧 AigcProject 简化 DTO/API
MediaAssetGroup
MediaElement
AigcContent
持久化 Storyboard 第二状态
```

## Flyway 迁移文件决策

### 已选择方案：全库 rebaseline

人类已明确批准重写未发布环境的历史迁移。本次不再保留 v200/v201 收敛迁移，直接采用以下唯一基线：

```text
db/migration/v1__system_schema.sql
db/migration/v7__aigc_schema.sql
db/migration/v10__stats_views.sql
db/seed/v11__init_dict_data.sql
db/seed/v12__init_seed_data.sql
db/seed/v17__aigc_content_studio_seed.sql
```

最终决策：

- `v1` 直接扩展 `sys_file`，并创建 `sys_file_reference`；文件存储配置、哈希、删除状态和引用关系从空库即为最终态。
- `v7` 直接定义统一 AIGC/Content Studio schema，不先创建再删除旧表，不出现 `cs_*`。
- `v7` 只有一个 `aigc_project`，项目对象、对象版本、执行、媒体、资产、作品和时间线全部引用该项目 ID 空间。
- `v7` 不定义 `media_asset`、`media_element`、`media_asset_group`、`aigc_storyboard`、`aigc_content`、`aigc_shot`、`user_project_template` 或 `user_project_resource`。
- `v17` 是统一 seed，只写最终 `aigc_*` 表、`aigc_*` 字典和 `aigc:*` 权限。
- 删除 `v200__content_studio_schema.sql` 与 `v201__content_studio_seed.sql`，禁止恢复为兼容迁移或平行模块。

### 同步调整

全库 rebaseline 同步修改历史依赖，避免后续迁移继续引用已删除模型：

| 文件 | 调整 |
|---|---|
| `v10__stats_views.sql` | 素材统计和仪表盘实体从 `media_asset` 改为 `aigc_media` |
| `v11__init_dict_data.sql` | 删除旧 AIGC 项目/内容/分镜/时间线字典，仅保留 `doc_type`；统一字典由 v17 提供 |
| `v12__init_seed_data.sql` | 删除 `user_project_template` seed，其余系统、生成模板、工作流模板和成长任务 seed 保留 |

### 环境约束

该方案是破坏性历史重写，不支持在已执行旧 v1/v7/v10/v11/v12/v200/v201 的数据库上增量升级：

- 开发、测试、CI 和个人数据库必须删除旧 `flyway_schema_history` 并完整重建。
- 禁止执行 `flyway repair` 掩盖 checksum 或结构差异。
- 如发现不可清空环境，必须暂停并重新设计正式前向迁移，不能恢复双写、双读、旧 API 适配或 `cs_*` 兼容表。
- 本次数据库设计阶段仅做 SQL 与文档静态一致性检查；数据库执行验证在后续获授权阶段单独完成。

## 验收标准

### 单一项目

- 数据库中不存在 `cs_project`，只有一个项目主表和一个项目 ID 空间。
- 后端不存在 `ContentProject` 与 `AigcProject` 两套聚合。
- 前端 `/studio/projects` 与所有生成任务使用同一个项目 ID。
- `AigcTask.project_id`、Media 来源项目和 ProjectObject 全部指向同一 `aigc_project.id`。

### 项目图谱与版本

- 结构视图和图谱视图查询同一 ProjectObject/ProjectRelation。
- Storyboard 只投影 Shot/ShotKeyframe/ProjectMediaRef，不保存第二状态。
- 执行成功只创建 candidate ObjectVersion/MediaVersion，明确采用后才移动采用指针。
- 失败、取消和重试不改变已采用版本。

### 文件一致性

- 每个内部持久化 MediaVersion 都有有效 `sys_file.id`。
- AIGC 业务表不保存 URL 作为文件身份，也不从 URL 解析存储 key。
- 上传后 `sys_file` 登记失败会补偿删除物理对象，不产生无记录文件。
- 删除项目引用、Asset 或 Work 不会删除仍被使用的物理文件。
- 有 `sys_file_reference` 的文件不能进入物理删除。
- 达到 `delete_after` 且引用为零的文件由 GC 删除，失败可重试。

### 素材、资产和作品

- 上传和 AI 生成只作为 Media 来源筛选。
- 标记资产只创建 Asset 记录，不复制 MediaVersion 或 sys_file。
- Work 引用已采用 Deliverable/ObjectVersion，不复制媒体。
- `/works` 与 `/materials` 不再渲染同一个无差异 AssetLibrary。

### 执行链

- 所有 Agent、Tool 和 Workflow 动作都有 ExecutionRun。
- 媒体生成时 ExecutionRun 可关联一个或多个 AigcTask。
- 一次生成多结果按 ExecutionRun 展示，不依赖 MediaAssetGroup。
- ExecutionRun 保存最终模型、Prompt、附件、工具、成本、输入输出和终态。

### 质量门禁

- 数据库从空库按 Flyway 顺序完整创建成功。
- 数据库结构中不存在 `cs_*` 表和遗留外键。
- 后端单元测试、迁移测试、跨用户权限测试和文件 GC 测试通过。
- 前端类型检查、项目创建、对象生成、素材引用、资产登记和作品发布流程通过。
- `pnpm check:affected` 全绿后才能进入验收。
