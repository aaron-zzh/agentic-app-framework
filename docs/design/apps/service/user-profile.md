---
title: 用户画像模块设计
status: approved
author: AaronZZH & Kiro
date: 2026-05-30
---

# 用户画像模块设计

## 定位

为每个用户维护结构化画像，供 AI Assistant 个性化回复、精准推荐、智能路由使用。
采用**维度注册表 + 维度值存储**架构，支持动态扩展任意业务场景（康养、电商、教育等）。

## 数据模型

### profile_dimension（画像维度定义）

定义"画像有哪些字段"，管理员可后台动态添加。

| 字段 | 类型 | 说明 |
|------|------|------|
| code | VARCHAR(64) UNIQUE | 维度编码（如 health.blood_pressure） |
| name | VARCHAR(100) | 显示名 |
| group_code | VARCHAR(32) | 分组编码 |
| value_type | VARCHAR(32) | 值类型：text/number/boolean/enum/tags/json |
| enum_options | JSONB | 枚举选项（value_type=enum 时） |
| unit | VARCHAR(32) | 单位 |
| source | VARCHAR(32) | 默认数据来源：manual/behavior/ai/device/import |
| sort_order | INT | 排序 |
| required | BOOLEAN | 是否必填 |
| searchable | BOOLEAN | 是否支持筛选 |
| ai_visible | BOOLEAN | 是否注入 AI 上下文 |

### profile_dimension_value（用户维度值）

存储"某用户在某维度的值"。

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 关联用户 |
| dimension_id | BIGINT | 关联维度定义 |
| value_text | TEXT | 文本/JSON 值 |
| value_number | DECIMAL | 数值型值 |
| value_tags | JSONB | 标签数组型值 |
| confidence | DECIMAL | AI 推断置信度（manual=1.0） |
| source | VARCHAR(32) | 本次值的来源 |
| expires_at | TIMESTAMP | 过期时间 |

### user_profile（画像主表，聚合摘要）

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT UNIQUE | 关联用户 |
| lifecycle_stage | VARCHAR(32) | 生命周期阶段 |
| ai_summary | TEXT | AI 生成的一句话画像摘要 |
| last_analyzed_at | TIMESTAMP | 最近分析时间 |

## 预置维度分组

| group_code | 名称 | 维度示例 |
|------------|------|---------|
| basic | 基础信息 | 年龄段、性别、职业、地区 |
| preference | 偏好 | 兴趣爱好、饮食偏好、沟通风格 |
| behavior | 行为 | 活跃度、使用频率、消费等级 |
| health | 健康 | 血压、血糖、用药、行动能力 |
| living | 生活 | 居住方式、出行方式、紧急联系人 |
| shopping | 消费 | 品牌偏好、价格敏感度、品类偏好 |
| social | 社交 | 社交活跃度、影响力 |
| personality | 性格 | MBTI、情绪倾向、沟通偏好 |

## 与 Assistant 集成

ai_visible=true 的维度值自动注入 Assistant 上下文：

```text
用户发消息 → 查询 ai_visible=true 的维度值
  → 拼接 system prompt 画像片段
  → AssistantExecutor.chat()
```

## 扩展机制

- 后台动态添加维度，前端按 value_type 自动渲染表单
- 批量导入（Excel/CSV）
- 设备对接（source=device）
- AI 自动填充（source=ai，带 confidence）
- 过期机制（expires_at，过期提醒重新采集）
- 按租户/应用启用不同维度分组

## 包结构

```text
module/system/profile/
├── domain/        UserProfile, ProfileDimension, ProfileDimensionValue
├── enums/         DimensionGroupEnum, DimensionValueTypeEnum, ProfileSourceEnum, LifecycleStageEnum
├── repository/    对应 Repository
├── service/       UserProfileService, ProfileDimensionService
├── vo/            VO + DTO
└── controller/    REST API
```
