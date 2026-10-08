---
level: Practice
layer: Model
purpose: 定义数字人模块的 REST 接口、权限、错误码和数据模型，作为前后端对接的唯一接口说明
status: published
version: 2.0.0
date: 2026-10-08
author: AaronZZH & Kiro
tags:
  - 数字人
  - API
  - CRUD 资源
related:
  - ./system.md
  - ./frontend.md
  - ./video-generation/design.md
scope:
  includes:
    - 数字人、预设音色、积分消耗记录的接口与权限
    - 数字人视频历史接口
  excludes:
    - 视频生成执行器内部实现（见 video-generation/design.md）
gains:
  - 能按接口说明对接数字人的增删改查、音色切换、助理关联和归档
  - 能判断一个请求会被哪条权限码和数据范围规则放行
---

# 数字人 API

> 接口以代码为准。标准增删改查由 `BaseCrudController` 提供，完整参数以 Swagger 为准；本文只记录数字人特有的约定。
> 表结构以 [`v19__digital_human_schema.sql`](../../../apps/service/aaf-api/src/main/resources/db/migration/v19__digital_human_schema.sql) 为准，不在本文重复。

## 通用约定

- 前缀 `/api`，响应统一包装为 `Result { code, data, message }`。
- 分页响应为 `PageResult { list, total, pageNo, pageSize, ids, queryToken, fieldSet, hasMore }`，页码从 1 开始，单页最大 200。
- 分页入参继承 `PageParam`：`pageNo`、`pageSize`、`sort`（`field:asc|desc`）、`search`（按名称模糊）。
- 更新是 Patch 语义：未传不改，显式 `null` 清空可空字段，有值覆盖；必须携带 `expectedVersion`（取详情中的 `version`），不一致返回版本冲突。
- 所有接口受许可特性 `AIGC` 约束，且需要登录。

## 资源与权限

| 资源 | 路径 | 资源键 | 权限命名空间 | 数据范围 |
|------|------|--------|--------------|----------|
| 数字人 | `/api/digital-humans` | `digital-human.digital-human` | `digital-human:digital-human` | 组织内共享可读，工作空间隔离；仅创建者可改删 |
| 预设音色 | `/api/preset-voices` | `digital-human.preset-voice` | `digital-human:preset-voice` | 全局共享，只读 |
| 积分消耗记录 | `/api/digital-human-credit-consumptions` | `digital-human.credit-consumption` | `digital-human:credit-consumption` | 仅本人可见，只读 |

权限码由命名空间加动作组成，命名空间必须是 `模块:资源` 两段（`CrudResourceDescriptor` 校验）：

- 数字人：`digital-human:digital-human:read|create|update|delete|export`，命令另有 `digital-human:digital-human:change-voice|link-assistant|archive|restore`。
- 预设音色、积分记录：`…:read`、`…:export`。

权限码由 `v24__digital_human_seed.sql` 写入，并按模块绑定给 `admin`、`super_admin`、`org_admin`、`member`。

「仅创建者可改」不是数据范围，由 `DigitalHumanService` 在更新、删除和命令前校验 `ownerId`，不满足返回 `DIGITAL_HUMAN_EDIT_FORBIDDEN`。

## 数字人

### 标准接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/digital-humans` | 分页列表，筛选：`name`（模糊）、`status` |
| GET | `/api/digital-humans/_query` | 查询窗口，供通用列表引擎使用 |
| GET | `/api/digital-humans/{id}` | 详情 |
| POST | `/api/digital-humans/_batch-read` | 按 ID 批量读取 |
| GET | `/api/digital-humans/_options` | 选择器选项 |
| POST | `/api/digital-humans` | 创建；AIGC Blueprint 的 `postDeliveryAction` 也调用此接口 |
| PUT | `/api/digital-humans/{id}` | Patch 更新 |
| DELETE | `/api/digital-humans/{id}` | 删除（软删除） |

不支持导入、恢复、归档的通用批量接口；归档和恢复使用下面的具名命令。

### 创建请求

```json
{
  "name": "艾佳",
  "description": "客服形象",
  "avatarImageUrl": "https://…/avatar.png",
  "tags": ["客服"],
  "appearance": { "age": 25, "gender": "female", "style": "商务装", "prompt": "…" },
  "voiceConfig": {
    "provider": "aliyun",
    "presetVoiceId": 1,
    "voiceName": "艾佳",
    "speechRate": 0,
    "pitchRate": 0,
    "volume": 50
  },
  "modelConfig": { "provider": "anthropic", "model": "claude-sonnet-5", "temperature": 0.7 },
  "generationSource": "aigc_project",
  "aigcProjectId": 1001
}
```

- `name`、`avatarImageUrl`、`voiceConfig.presetVoiceId` 必填。
- `presetVoiceId` 必须是启用的预设音色 ID；`voiceName` 未传时取预设音色名称。
- `provider` 默认 `aliyun`，`speechRate`/`pitchRate` 默认 0（范围 -500~500），`volume` 默认 50（范围 0~100）。
- 状态默认 `active`，统计字段默认 0；助理关联不能在创建时设置。

### 更新请求

可更新字段：`name`、`description`、`avatarImageUrl`、`tags`、`appearance`、`voiceConfig`、`modelConfig`、`syncModelConfig`，加必填的 `expectedVersion`。

`assistantId` 和 `status` 只能通过命令修改，通用更新传入返回 `DIGITAL_HUMAN_FIELD_COMMAND_ONLY`。`voiceConfig` 整体替换，同样校验预设音色。

### 响应

`DigitalHumanVO`：`id`、`version`、`name`、`description`、`avatarImageUrl`、`tags`、`appearance`、`voiceConfig`、`modelConfig`、`assistantId`、`syncModelConfig`、`videoCount`、`totalDuration`、`creditsConsumed`、`lastUsedAt`、`status`、`generationSource`、`aigcProjectId`、`createTime`、`updateTime`、`ownedByCurrentUser`。

空值字段不返回。`ownedByCurrentUser` 为 `true` 时前端才展示编辑、删除、归档等操作。

### 命令

| 方法 | 路径 | 权限码 | 请求体 | 说明 |
|------|------|--------|--------|------|
| POST | `/{id}/change-voice` | `digital-human:digital-human:change-voice` | `presetVoiceId`、`speechRate`、`pitchRate`、`volume` | 切换音色，其余音色字段未传取默认值 |
| POST | `/{id}/link-assistant` | `digital-human:digital-human:link-assistant` | `assistantId`、`syncModelConfig` | 关联助理 |
| DELETE | `/{id}/link-assistant` | `digital-human:digital-human:link-assistant` | 无 | 取消关联，同时关闭模型配置同步 |
| POST | `/{id}/archive` | `digital-human:digital-human:archive` | 无 | 状态改为 `archived`，幂等 |
| POST | `/{id}/restore` | `digital-human:digital-human:restore` | 无 | 状态改为 `active`，幂等 |

命令都返回最新的 `DigitalHumanVO`，并递增 `version`。

> 关联助理尚未校验助理是否存在、是否属于当前用户，也未回写助理侧的 `digitalHumanId`，代码中保留了 TODO。

### 视频历史

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/digital-humans/{id}/videos` | 视频历史，Spring `Page` 结构，支持 `projectName`、`page`、`size` |
| GET | `/api/digital-humans/{id}/videos/{videoId}` | 视频详情 |
| DELETE | `/api/digital-humans/{id}/videos/{videoId}` | 删除视频记录（软删除，不删文件） |

访问前先以 `getById` 校验当前用户可读该数字人。视频数据来自 AIGC 项目交付的 `aigc_asset`。

## 预设音色

`GET /api/preset-voices`：只读分页，筛选 `isActive`、`provider`、`gender`，默认按 `displayOrder` 升序。选择音色时传 `isActive=true`。音色库由种子数据维护，没有创建、更新、删除接口。

## 积分消耗记录

`GET /api/digital-human-credit-consumptions`：只读分页，筛选 `digitalHumanId`、`projectId`、`operation`（`avatar_generation`、`simple_video`、`complex_video`），仅返回本人记录。

## 错误码

模块错误码段 `8_000_000 ~ 8_999_999`，定义在 `ErrorCodeConstants`：

| 区间 | 子模块 | 示例 |
|------|--------|------|
| 8_000_xxx | 数字人 | `DIGITAL_HUMAN_NOT_FOUND`(404)、`DIGITAL_HUMAN_ACCESS_DENIED`(403)、`DIGITAL_HUMAN_EDIT_FORBIDDEN`(403)、`DIGITAL_HUMAN_VERSION_CONFLICT`(409) |
| 8_001_xxx | 预设音色 | `PRESET_VOICE_NOT_FOUND`(404)、`PRESET_VOICE_DISABLED` |
| 8_002_xxx | 视频 | `VIDEO_NOT_FOUND`(404)、`VIDEO_GENERATION_FAILED` |
| 8_003_xxx | 积分 | `CREDIT_INSUFFICIENT` 等 |

## 数据模型

| 表 | 实体 | 说明 |
|----|------|------|
| `digital_human` | `DigitalHuman` | 数字人；创建者为 `owner_id`，标签与各配置为 JSONB |
| `digital_human_preset_voice` | `PresetVoice` | 预设音色库，`org_id` 为空，实体标注 `@OrgIgnore` |
| `digital_human_credit_consumption` | `CreditConsumption` | 积分消耗明细，关联数字人和 AIGC 项目 |

三张表都具备 `BaseEntity` 的全部审计列。

## 已移除的接口

| 接口 | 原因 |
|------|------|
| `POST /api/digital-humans/{id}/usage-stats` | 没有调用方，且不校验访问权限。目前没有任何流程写入 `videoCount` 等统计字段，需在视频生成完成后由服务端补充 |
| `GET /api/preset-voices/all` | 由标准分页的 `isActive` 筛选取代 |
| 列表的 `tags` 筛选 | 标签为 JSONB，当前未实现包含查询 |
