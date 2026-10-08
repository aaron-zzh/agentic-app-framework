---
level: Practice
layer: Model
purpose: 说明数字人系统的业务定位、三大模块方案选型、关键技术决策和实施路径
status: published
version: 1.1.0
date: 2026-10-08
author: AaronZZH & Kiro
tags:
  - 数字人
  - AIGC Blueprint
  - 设计方案
related:
  - ./api.md
  - ./frontend.md
  - ./video-generation/design.md
gains:
  - 能说明数字人资产生成、管理、营销视频三个模块为何选用 AIGC Blueprint 及其边界
  - 能找到数据库与接口的权威来源，不依赖本文中的历史草稿
---

> **阅读提示**：本文记录方案选型与决策过程。数据库表结构见 `v19__digital_human_schema.sql`，接口见 [api.md](./api.md)；视频路由阈值以 15 秒为准（早期草稿写作 30 秒）。

# 数字人系统设计方案

## 一、系统概述

### 1.1 业务定位

数字人系统是一个端到端的 AI 数字人资产管理与营销视频生成平台，包含三个核心模块：

1. **数字人资产生成** - 基于提示词或参考图生成数字人形象和音色
2. **数字人管理** - 资产库 CRUD、大模型配置、音色切换、助理关联
3. **营销视频生成** - 基于数字人生成营销视频内容

### 1.2 与现有系统关系

```
AIGC 项目系统 (AAF-113)
├── Blueprint 机制
│   ├── CATALOG Blueprint（预置模板）
│   └── ASSISTANT_GENERATED Blueprint（动态生成）
│
├── ProjectObject 对象
│   ├── concept（概念稿）
│   ├── image（图像）
│   ├── video（视频）
│   └── digital_human（数字人）← 新增对象类型
│
└── 数字人系统集成点
    ├── 模块1：资产生成 → 可选 AIGC Blueprint 或快速工具
    ├── 模块2：资产管理 → 独立管理界面
    └── 模块3：视频生成 → 内置 AIGC Blueprint

Assistant 助理系统
├── Assistant Profile
│   ├── name, description, instructions
│   ├── model（大模型配置）
│   └── digital_human_id（关联数字人）← 新增字段
```

---

## 二、模块一：数字人资产生成

### 2.1 需求分析

**输入**:
- 提示词（如："25岁职业女性，短发，商务装，温柔微笑"）
- 参考图（上传照片）

**输出**:
- 数字人形象资产（3D 模型或 2D 形象）
- 数字人音色资产（TTS 音色克隆）

**生成方式**:
- 形象生成：调用图像生成 API（如 MidJourney/DALL-E）
- 音色生成：调用 TTS 克隆 API（如 ElevenLabs/Azure）

### 2.2 方案对比

#### 方案 A：基于 AIGC Blueprint（推荐）

**优势**:
- ✅ 复用现有 Blueprint 机制
- ✅ 统一项目管理和审核流程
- ✅ 积分消耗透明
- ✅ Assistant 可协助优化提示词
- ✅ 版本管理和候选治理

**Blueprint 定义**:

> 下面的 YAML 是选型阶段的示意，字段（`boundExecutionId`、`inputMapping`、`requiredContext` 等）和执行目标（`image_generation_midjourney`、`tts_clone_elevenlabs`）并未落地，平台不识别这些写法。实际落地的 Blueprint 以 `v24__digital_human_seed.sql` 为准：动作只声明 `actionKey`，形象生成复用 `image.generate` 绑定，音色使用预设音色而不做克隆。落地细节见 [Blueprint 部署](./video-generation/blueprint-deployment.md)。

```yaml
displayName: 数字人资产生成
category: digital_human
version: "1.0.0"

projectSpecification:
  requiredContext:
    - id: appearance_prompt
      role: user_input
      label: 形象描述
      type: text
    - id: reference_image
      role: user_input
      label: 参考图片（可选）
      type: image
      optional: true
    - id: voice_sample
      role: user_input
      label: 音色样本（可选）
      type: audio
      optional: true

slotTemplates:
  - key: avatar_image
    label: 数字人形象
    objectType: image
    semanticRole: digital_human_avatar
    outputContract:
      format: image/png
      minResolution: 1024x1024
    readinessCriteria:
      dependencies: []
      policy: immediate
  
  - key: voice_model
    label: 数字人音色
    objectType: audio_model
    semanticRole: digital_human_voice
    outputContract:
      format: tts_model
    readinessCriteria:
      dependencies: [avatar_image]
      policy: after_dependency

actions:
  - key: generate_avatar
    targetSlot: avatar_image
    boundExecutionId: image_generation_midjourney
    inputMapping:
      prompt: "{{appearance_prompt}}, professional digital human portrait, high quality, 4K"
      referenceImage: "{{reference_image}}"
  
  - key: clone_voice
    targetSlot: voice_model
    boundExecutionId: tts_clone_elevenlabs
    inputMapping:
      voiceSample: "{{voice_sample}}"
      targetName: "{{project.name}}_voice"

deliverableSets:
  - key: digital_human_asset
    label: 数字人资产包
    includedSlots: [avatar_image, voice_model]
```

**前端流程**:
```
用户点击"创建数字人" 
  ↓
进入 AIGC 项目创建页面
  - 选择 Blueprint: "数字人资产生成"
  - 填写项目名称
  - 输入形象描述（提示词）
  - 上传参考图（可选）
  - 上传音色样本（可选）
  ↓
提交后进入项目详情页
  - 显示两个对象：avatar_image（EMPTY）, voice_model（EMPTY）
  - 自动触发 generate_avatar 执行
  - avatar_image 生成完成后显示候选
  - 用户采用后，自动触发 clone_voice
  ↓
两个对象都采用后，点击"完成并创建数字人"
  ↓
系统创建 DigitalHuman 实体，关联：
  - avatarImageUrl: avatar_image.adoptedVersion.url
  - voiceModelId: voice_model.adoptedVersion.externalId
```

---

#### 方案 B：快速工具页面

**优势**:
- ✅ 轻量快速，无需创建 AIGC 项目
- ✅ 适合批量快速生成

**劣势**:
- ❌ 无版本管理
- ❌ 无候选治理
- ❌ 无 Assistant 协助
- ❌ 积分消耗不透明

**页面布局**:
```
┌─────────────────────────────────────────────────────┐
│ 快速生成数字人                                        │
├─────────────────────────────────────────────────────┤
│                                                     │
│ 步骤 1: 形象生成                                      │
│ ┌─────────────────────────────────────────────────┐ │
│ │ 形象描述（提示词）                                  │ │
│ │ ┌───────────────────────────────────────────────┐ │ │
│ │ │ 25岁职业女性，短发，商务装，温柔微笑              │ │ │
│ │ └───────────────────────────────────────────────┘ │ │
│ │                                                   │ │
│ │ 参考图片（可选）                                   │ │
│ │ [上传图片]                                        │ │
│ │                                                   │ │
│ │ [生成形象]                                        │ │
│ └─────────────────────────────────────────────────┘ │
│                                                     │
│ 步骤 2: 音色克隆                                      │
│ ┌─────────────────────────────────────────────────┐ │
│ │ 音色样本（可选）                                   │ │
│ │ [上传音频]                                        │ │
│ │                                                   │ │
│ │ 或选择预设音色：                                   │ │
│ │ ○ 温柔女声  ○ 专业男声  ○ 活泼女声                │ │
│ │                                                   │ │
│ │ [克隆音色]                                        │ │
│ └─────────────────────────────────────────────────┘ │
│                                                     │
│ [保存为数字人资产]                                    │
└─────────────────────────────────────────────────────┘
```

### 2.3 推荐方案

**推荐采用方案 A（AIGC Blueprint）**

**理由**:
1. 与现有系统深度集成
2. 完整的项目管理能力
3. 支持 Assistant 协助优化
4. 版本管理和审核流程
5. 积分消耗透明可追溯

**保留方案 B 作为"快速入口"**:
- 在数字人管理页面提供"快速生成"按钮
- 底层仍调用 AIGC Blueprint，但隐藏项目详情页
- 生成完成后直接跳转到数字人管理页面

---

## 三、模块二：数字人管理

### 3.1 数据模型

```typescript
// 数字人实体
interface DigitalHuman {
  id: string
  userId: string
  organizationId: string
  
  // 基础信息
  name: string                    // 数字人名称
  description?: string            // 描述
  tags?: string[]                 // 标签（如：营销、客服、讲师）
  
  // 形象资产
  avatarImageUrl: string          // 形象图片 URL
  avatarModelUrl?: string         // 3D 模型 URL（可选）
  appearance: {
    age?: number
    gender: 'male' | 'female' | 'neutral'
    style: string                 // 如："职业装"
    prompt: string                // 原始提示词
  }
  
  // 音色资产
  voiceModelId: string            // TTS 音色模型 ID（第三方 API 返回）
  voiceConfig: {
    provider: 'elevenlabs' | 'azure' | 'custom'
    stability?: number            // 0-1
    similarity?: number           // 0-1
    sampleAudioUrl?: string       // 原始音色样本
  }
  
  // 大模型配置
  modelConfig: {
    provider: 'openai' | 'anthropic' | 'custom'
    model: string                 // 如："claude-sonnet-5"
    temperature?: number
    maxTokens?: number
    systemPrompt?: string         // 数字人人设提示词
  }
  
  // 关联助理
  assistantId?: string            // 关联的 Assistant ID
  
  // 使用统计
  stats: {
    videoCount: number            // 生成视频数量
    totalDuration: number         // 累计视频时长（秒）
    creditsConsumed: number       // 累计消耗积分
    lastUsedAt?: Date
  }
  
  // 状态
  status: 'active' | 'archived' | 'failed'
  generationSource: 'aigc_project' | 'quick_tool'  // 生成来源
  aigcProjectId?: string          // 关联的 AIGC 项目 ID（方案 A）
  
  // 元数据
  createdAt: Date
  updatedAt: Date
}

// 预设音色库
interface PresetVoice {
  id: string
  name: string                    // 如："温柔女声"
  description: string
  gender: 'male' | 'female'
  sampleAudioUrl: string          // 试听音频
  provider: string
  externalVoiceId: string         // 第三方 API 音色 ID
  isActive: boolean
}
```

### 3.2 管理页面设计

```
┌───────────────────────────────────────────────────────────┐
│ 数字人管理                        [+ 创建数字人] [快速生成] │
├───────────────────────────────────────────────────────────┤
│ 筛选：[全部] [营销] [客服] [讲师]    搜索：[___________]   │
├───────────────────────────────────────────────────────────┤
│                                                           │
│ 数字人列表（卡片视图）                                      │
│                                                           │
│ ┌────────────┐  ┌────────────┐  ┌────────────┐          │
│ │            │  │            │  │            │          │
│ │  [形象图]  │  │  [形象图]  │  │  [形象图]  │          │
│ │            │  │            │  │            │          │
│ ├────────────┤  ├────────────┤  ├────────────┤          │
│ │ 职业女性A   │  │ 专业讲师B   │  │ 客服助手C   │          │
│ │ 温柔女声    │  │ 沉稳男声    │  │ 活泼女声    │          │
│ │            │  │            │  │            │          │
│ │ 使用: 12次  │  │ 使用: 5次   │  │ 使用: 28次  │          │
│ │ 关联助理: ✓ │  │ 关联助理: - │  │ 关联助理: ✓ │          │
│ │            │  │            │  │            │          │
│ │ [编辑][...]│  │ [编辑][...]│  │ [编辑][...]│          │
│ └────────────┘  └────────────┘  └────────────┘          │
└───────────────────────────────────────────────────────────┘
```

### 3.3 编辑页面设计

```
┌───────────────────────────────────────────────────────────┐
│ 编辑数字人：职业女性A                     [保存] [预览视频] │
├───────────────────────────────────────────────────────────┤
│                                                           │
│ 左侧：预览区                                               │
│ ┌─────────────────────┐                                   │
│ │                     │                                   │
│ │    [形象大图]       │                                   │
│ │                     │                                   │
│ │                     │                                   │
│ └─────────────────────┘                                   │
│ [重新生成形象]                                             │
│                                                           │
├───────────────────────────────────────────────────────────┤
│ 右侧：配置区                                               │
│                                                           │
│ 基础信息                                                   │
│   名称：[职业女性A_______]                                 │
│   描述：[25岁职业女性，短发...___]                         │
│   标签：[营销] [客服] [+ 添加]                             │
│                                                           │
│ 音色配置                                                   │
│   当前音色：温柔女声                                        │
│   [🔊 试听]  [切换音色]                                   │
│   稳定性：[========>--] 0.8                               │
│   相似度：[=======>---] 0.7                               │
│                                                           │
│ 大模型配置                                                 │
│   模型：[Claude Sonnet 5 ▼]                              │
│   Temperature：[=====>-----] 0.7                         │
│   人设提示词：                                             │
│   ┌───────────────────────────────────────┐              │
│   │ 你是一位专业的营销顾问，擅长...          │              │
│   └───────────────────────────────────────┘              │
│                                                           │
│ 关联助理                                                   │
│   [选择助理 ▼]  当前：营销助手 A                           │
│   □ 同步助理的模型配置到数字人                              │
│                                                           │
│ 使用统计                                                   │
│   生成视频：12 次                                          │
│   累计时长：125 秒                                         │
│   消耗积分：3,200                                          │
│   最后使用：2026-09-20 14:30                              │
└───────────────────────────────────────────────────────────┘
```

### 3.4 音色切换流程

```
用户点击"切换音色"
  ↓
弹出音色选择器
┌──────────────────────────────────────────────┐
│ 选择音色                          [× 关闭]     │
├──────────────────────────────────────────────┤
│ Tab: [预设音色] [自定义克隆]                   │
│                                              │
│ 预设音色列表：                                 │
│ ┌────────────┐  ┌────────────┐              │
│ │ 温柔女声    │  │ 专业男声    │              │
│ │ [🔊试听]   │  │ [🔊试听]   │              │
│ │ [选择]     │  │ [选择]     │              │
│ └────────────┘  └────────────┘              │
│                                              │
│ 自定义克隆：                                   │
│   [上传音频样本]  最少 30 秒清晰语音            │
│   预计消耗：50 积分                            │
│   [开始克隆]                                  │
└──────────────────────────────────────────────┘
```

### 3.5 关联助理逻辑

**关联方式 A：数字人 → 助理（单向绑定）**
```typescript
// Assistant 实体新增字段
interface Assistant {
  // ... 现有字段
  digitalHumanId?: string  // 关联的数字人 ID（可选）
}

// 使用场景：
// 1. 用户与助理对话时，可选择"生成视频"
// 2. 系统使用该助理关联的数字人生成视频
// 3. 视频内容为助理的回复内容
```

**关联方式 B：双向绑定（推荐）**
```typescript
interface DigitalHuman {
  assistantId?: string     // 关联的助理 ID
  syncModelConfig: boolean // 是否同步助理的模型配置
}

interface Assistant {
  digitalHumanId?: string  // 关联的数字人 ID
}

// 使用场景：
// 1. 在助理详情页显示关联的数字人形象
// 2. 在数字人详情页显示关联的助理
// 3. 可以快速从一个页面跳转到另一个
```

---

## 四、模块三：营销视频生成

### 4.1 需求分析

**输入**:
- 选择数字人
- 视频主题/提示词
- 视频时长要求

**输出**:
- 营销视频（MP4）

**生成方式**:
- **简单视频**（≤ 15 秒，阿里云单次生成上限）：直接调用 TTS + 数字人驱动 API
- **复杂视频**（> 15 秒，多镜头）：按场景拆分后对接 Hyperframes 合成

### 4.2 方案：内置 AIGC Blueprint

**Blueprint 定义**:
```yaml
name: digital-human-marketing-video
displayName: 数字人营销视频
category: digital_human
version: "1.0.0"

projectSpecification:
  requiredContext:
    - id: digital_human
      role: reference
      label: 选择数字人
      type: digital_human_ref
    - id: video_topic
      role: user_input
      label: 视频主题
      type: text
    - id: target_duration
      role: user_input
      label: 目标时长
      type: number
      unit: seconds
    - id: video_style
      role: user_input
      label: 视频风格
      type: enum
      options: [professional, casual, energetic]

slotTemplates:
  - key: script
    label: 视频脚本
    objectType: document
    semanticRole: video_script
    outputContract:
      format: text/markdown
      sections: [opening, body, closing]
    readinessCriteria:
      dependencies: []
      policy: immediate
  
  - key: video
    label: 数字人视频
    objectType: video
    semanticRole: marketing_video
    outputContract:
      format: video/mp4
      minDuration: 10
      maxDuration: 300
    readinessCriteria:
      dependencies: [script]
      policy: after_dependency

actions:
  - key: generate_script
    targetSlot: script
    boundExecutionId: aigc_text_generation
    inputMapping:
      prompt: |
        创作一个{{target_duration}}秒的营销视频脚本，主题：{{video_topic}}
        风格：{{video_style}}
        要求：
        1. 分为开场、正文、结尾三部分
        2. 语言简洁有力
        3. 适合数字人播报
  
  - key: generate_video_simple
    targetSlot: video
    boundExecutionId: digital_human_video_simple
    condition: "{{target_duration}} <= 30"
    inputMapping:
      digitalHumanId: "{{digital_human.id}}"
      script: "{{script.adoptedVersion.content}}"
      voiceModelId: "{{digital_human.voiceModelId}}"
  
  - key: generate_video_hyperframes
    targetSlot: video
    boundExecutionId: hyperframes_integration
    condition: "{{target_duration}} > 30"
    inputMapping:
      digitalHumanId: "{{digital_human.id}}"
      script: "{{script.adoptedVersion.content}}"
      voiceModelId: "{{digital_human.voiceModelId}}"
      scenes: "{{script.adoptedVersion.sections}}"

deliverableSets:
  - key: marketing_video_package
    label: 营销视频包
    includedSlots: [script, video]
```

### 4.3 前端流程

```
用户点击"生成营销视频"
  ↓
进入 AIGC 项目创建页面
  - 选择 Blueprint: "数字人营销视频"
  - 选择数字人（下拉列表）
  - 输入视频主题
  - 设置目标时长（秒）
  - 选择视频风格（专业/休闲/活力）
  ↓
提交后进入项目详情页
  - 显示两个对象：script（EMPTY）, video（EMPTY)
  - 自动触发 generate_script 执行
  - script 生成完成后显示候选
  - 用户可以编辑脚本内容
  - 用户采用后，根据时长自动选择执行：
    - ≤ 30 秒：generate_video_simple
    - > 30 秒：generate_video_hyperframes
  ↓
视频生成完成后
  - 显示视频播放器
  - 提供下载按钮
  - 可以重新生成（生成新 candidate）
  ↓
用户满意后点击"交付"
  - 创建 deliverable manifest
  - 项目状态变为 DELIVERING
```

### 4.4 简单视频生成 API

```typescript
// 简单视频生成执行器
interface SimpleVideoGenerationInput {
  digitalHumanId: string
  script: string
  voiceModelId: string
  resolution?: '1080P' | '4K'
  fps?: number
}

// 执行流程：
// 1. TTS 生成音频：script → audio.mp3
// 2. 数字人驱动：audio.mp3 + avatar → video.mp4
// 3. 返回 video URL
```

### 4.5 Hyperframes 集成 API

```typescript
// Hyperframes 集成执行器
interface HyperframesGenerationInput {
  digitalHumanId: string
  script: string
  voiceModelId: string
  scenes: Array<{
    title: string
    content: string
    duration: number
  }>
}

// 执行流程：
// 1. 将 script 分段为多个 scenes
// 2. 每个 scene 调用 Hyperframes API
// 3. 合成最终视频
// 4. 返回 video URL
```

---

## 五、数据库 Schema

表结构以 [`v19__digital_human_schema.sql`](../../../apps/service/aaf-api/src/main/resources/db/migration/v19__digital_human_schema.sql) 为准，字段与实体说明见 [api.md](./api.md#数据模型)。原有的 DDL 草稿已删除，避免与迁移脚本出现两份真理源。

与设计阶段草稿的主要差异：

- 创建者统一用 `owner_id`，不再有 `user_id`；组织、工作空间用 `org_id`、`workspace_id`。
- `tags` 为 JSONB，不是 `TEXT[]`。
- 预设音色表更名为 `digital_human_preset_voice`，ID 为自增数字，不是 `VARCHAR(64)`；积分消耗表更名为 `digital_human_credit_consumption`。
- 三张表都带 `BaseEntity` 的全部审计列。

---

## 六、API 设计

接口以 [api.md](./api.md) 为准。原有的接口草稿（`/api/digital-human/generation-projects` 等）已删除：资产生成和视频生成走 AIGC 项目接口，数字人管理走标准 CRUD 资源。

---

## 七、前端组件树

```typescript
// apps/webui/src/features/digital-human/

// 模块一：资产生成
generation/
├── GenerationProjectCreator.tsx    // AIGC 项目创建入口
├── QuickGenerationTool.tsx         // 快速生成工具
└── GenerationProjectDetail.tsx     // 项目详情（复用 AIGC 组件）

// 模块二：数字人管理
management/
├── DigitalHumanList.tsx            // 列表页
│   ├── DigitalHumanCard.tsx        // 卡片组件
│   └── FilterBar.tsx               // 筛选栏
├── DigitalHumanDetail.tsx          // 详情/编辑页
│   ├── PreviewPanel.tsx            // 左侧预览
│   ├── BasicInfoSection.tsx        // 基础信息
│   ├── VoiceConfigSection.tsx      // 音色配置
│   │   └── VoiceSelector.tsx       // 音色选择器
│   ├── ModelConfigSection.tsx      // 大模型配置
│   ├── AssistantLinkSection.tsx    // 助理关联
│   └── StatsSection.tsx            // 使用统计
└── components/
    ├── VoicePlayer.tsx             // 音色试听
    └── RegenerateAvatarDialog.tsx  // 重新生成形象

// 模块三：视频生成
video/
├── VideoProjectCreator.tsx         // 视频项目创建入口
├── VideoProjectDetail.tsx          // 项目详情（复用 AIGC 组件）
└── VideoPlayer.tsx                 // 视频播放器
```

---

## 八、实施计划

### Phase 1：基础设施（1周）
- ✅ 数据库表结构
- ✅ 数字人实体 CRUD API
- ✅ Assistant 关联字段
- ✅ 预设音色库初始化

### Phase 2：模块一 - 资产生成（2周）
- ✅ AIGC Blueprint 定义: digital-human-asset-generation
- ✅ 图像生成执行器集成（MidJourney/DALL-E）
- ✅ TTS 克隆执行器集成（ElevenLabs）
- ✅ 快速生成工具页面（可选）

### Phase 3：模块二 - 数字人管理（1周）
- ✅ 列表页 + 卡片视图
- ✅ 详情/编辑页
- ✅ 音色切换功能
- ✅ 助理关联功能

### Phase 4：模块三 - 视频生成（2周）
- ✅ AIGC Blueprint 定义: digital-human-marketing-video
- ✅ 简单视频生成执行器
- ✅ Hyperframes 集成执行器
- ✅ 视频项目创建与管理

### Phase 5：优化与集成（1周）
- ✅ 积分消耗统计
- ✅ 使用统计图表
- ✅ 权限控制
- ✅ 测试与修复

**总计：约 7 周**

---

## 九、关键技术决策

### 9.1 为什么模块一推荐 AIGC Blueprint？

1. **版本管理**：用户可以生成多个候选，选择最满意的
2. **审核流程**：组织可以审核数字人资产
3. **Assistant 协助**：AI 可以优化提示词
4. **积分透明**：清晰展示每个步骤消耗
5. **统一体验**：与其他 AIGC 项目一致

### 9.2 为什么模块三必须用 AIGC Blueprint？

1. **多阶段生成**：脚本生成 → 视频生成
2. **依赖管理**：视频依赖脚本
3. **候选治理**：可以重新生成脚本或视频
4. **复杂编排**：简单视频 vs Hyperframes 条件分支

### 9.3 数字人 vs 助理的关系

**数字人**：形象 + 音色 + 大模型配置  
**助理**：对话能力 + 工具调用 + 知识库

**关联后**：
- 助理有了"人格化"的形象
- 数字人有了"智能"的对话能力
- 用户可以在助理对话中生成视频
- 视频内容使用助理的回复

---

## 十、架构决策记录（ADR）

### 10.1 模块一：资产生成

1. **✅ 图像生成 API**：复用现有 AIGC 图像生成能力（已对接阿里云）
2. **✅ TTS 能力**：使用阿里云 TTS 预设音色（无需音色克隆）
3. **✅ 3D 模型**：暂不支持，仅使用 2D 头像图片
4. **✅ 快速工具**：不保留，统一使用 AIGC Blueprint 流程

### 10.2 模块二：数字人管理

5. **✅ 预设音色库**：初始 5-6 个最常用音色（艾佳、艾彤、小云、艾诚、艾伟）
6. **✅ 助理关联**：双向绑定（DigitalHuman ↔ Assistant）
7. **✅ 组织共享**：支持（organizationId 字段 + 权限控制）
8. **✅ 分组方式**：仅使用标签（tags），不需要文件夹

### 10.3 模块三：视频生成

9. **✅ Hyperframes 对接**：已有 API 访问权限，直接对接
10. **✅ 简单视频生成**：使用阿里云视频生成接口（提示词 + 参考图）
11. **✅ 视频时长约束**：
    - 单段最大 15 秒
    - 拼接总长最大 10 分钟（600 秒）
    - 最多 40 段
12. **✅ 场景编排**：支持完整场景编排
    - 视觉：数字人镜头角度、背景图/视频、叠加图片
    - 文字：字幕、文字叠加、动画效果
    - 音频：配音、背景音乐、音效
    - 转场：淡入淡出、切换、滑动等
    - **取决于 Hyperframes 支持能力**，先本地保存配置

### 10.4 积分与权限

13. **✅ 积分消耗**：按实际 API 成本计费，不预设标准
    - 由执行器返回实际消耗
    - 记录成本明细（API 提供商、时长、场景数、元素）
14. **✅ 权限控制**：
    - 支持组织级共享
    - 无需审批流程（创建后直接可用）
    - 权限规则：
      - 创建者：编辑、删除
      - 组织成员：查看、使用

---

## 十一、更新的技术方案

### 11.1 模块一：资产生成 Blueprint

```yaml
name: digital-human-asset-generation
displayName: 数字人资产生成
category: digital_human
version: "1.0.0"

projectSpecification:
  requiredContext:
    - id: appearance_prompt
      role: user_input
      label: 形象描述
      type: text
      placeholder: "25岁职业女性，短发，商务装，温柔微笑"
    
    - id: reference_image
      role: user_input
      label: 参考图片（可选）
      type: image
      optional: true
    
    - id: preset_voice
      role: user_input
      label: 选择音色
      type: enum
      options: [aijia, aitong, xiaoyun, aicheng, aiwei]
      default: aijia

slotTemplates:
  - key: avatar_image
    label: 数字人形象
    objectType: image
    semanticRole: digital_human_avatar
    outputContract:
      format: image/png
      aspectRatio: "1:1"
      minResolution: 1024x1024
    readinessCriteria:
      dependencies: []
      policy: immediate

actions:
  - key: generate_avatar
    targetSlot: avatar_image
    boundExecutionId: aliyun_image_generation
    inputMapping:
      prompt: "{{appearance_prompt}}, professional digital human portrait, high quality, 4K, front facing, neutral background, suitable for video generation"
      referenceImage: "{{reference_image}}"
      aspectRatio: "1:1"
      quality: "hd"

deliverableSets:
  - key: digital_human_asset
    label: 数字人资产包
    includedSlots: [avatar_image]
    postDeliveryAction:
      type: create_digital_human
      mapping:
        name: "{{project.name}}"
        avatarImageUrl: "{{avatar_image.adoptedVersion.url}}"
        voiceConfig:
          provider: aliyun
          presetVoiceId: "{{preset_voice}}"
        appearance:
          prompt: "{{appearance_prompt}}"
        generationSource: aigc_project
        aigcProjectId: "{{project.id}}"
```

### 11.2 模块三：视频生成 Blueprint

```yaml
name: digital-human-marketing-video
displayName: 数字人营销视频
category: digital_human
version: "1.0.0"

projectSpecification:
  requiredContext:
    - id: digital_human
      role: reference
      label: 选择数字人
      type: digital_human_ref
    
    - id: video_topic
      role: user_input
      label: 视频主题
      type: text
      placeholder: "介绍我们的产品特点和优势"
    
    - id: target_duration
      role: user_input
      label: 目标时长（秒）
      type: number
      validation:
        min: 5
        max: 600
      default: 30
    
    - id: video_style
      role: user_input
      label: 视频风格
      type: enum
      options: [professional, casual, energetic]
      default: professional
    
    - id: aspect_ratio
      role: user_input
      label: 画面比例
      type: enum
      options: ["16:9", "9:16", "1:1"]
      default: "16:9"

slotTemplates:
  - key: script
    label: 视频脚本
    objectType: document
    semanticRole: video_script
    outputContract:
      format: application/json
      schema:
        scenes: array
        totalDuration: number
    readinessCriteria:
      dependencies: []
      policy: immediate
  
  - key: video
    label: 数字人视频
    objectType: video
    semanticRole: marketing_video
    outputContract:
      format: video/mp4
      minDuration: 5
      maxDuration: 600
    readinessCriteria:
      dependencies: [script]
      policy: after_dependency

actions:
  - key: generate_script
    targetSlot: script
    boundExecutionId: aigc_text_generation
    inputMapping:
      prompt: |
        创作一个{{target_duration}}秒的营销视频脚本，主题：{{video_topic}}
        风格：{{video_style}}
        
        要求：
        1. 根据时长自动分段（单段不超过15秒）
        2. 每段包含：标题、内容、时长、场景描述
        3. 语言简洁有力，适合数字人播报
        4. 返回 JSON 格式：
        {
          "totalDuration": 数字,
          "scenes": [
            {
              "order": 1,
              "title": "开场",
              "content": "脚本内容",
              "duration": 15,
              "sceneDescription": "场景描述"
            }
          ]
        }
      model: "claude-sonnet-5"
      temperature: 0.8
  
  - key: generate_video_simple
    targetSlot: video
    boundExecutionId: aliyun_video_generation
    condition: "{{target_duration}} <= 15"
    inputMapping:
      prompt: "数字人播报视频，内容：{{script.adoptedVersion.content.scenes[0].content}}"
      referenceImage: "{{digital_human.avatarImageUrl}}"
      voiceConfig:
        presetVoiceId: "{{digital_human.voiceConfig.presetVoiceId}}"
        speechRate: "{{digital_human.voiceConfig.speechRate}}"
        pitchRate: "{{digital_human.voiceConfig.pitchRate}}"
      duration: "{{target_duration}}"
      aspectRatio: "{{aspect_ratio}}"
  
  - key: generate_video_hyperframes
    targetSlot: video
    boundExecutionId: hyperframes_integration
    condition: "{{target_duration}} > 15"
    inputMapping:
      digitalHumanId: "{{digital_human.id}}"
      avatarImageUrl: "{{digital_human.avatarImageUrl}}"
      voiceConfig: "{{digital_human.voiceConfig}}"
      scenes: "{{script.adoptedVersion.content.scenes}}"
      aspectRatio: "{{aspect_ratio}}"
      resolution: "1080p"

deliverableSets:
  - key: marketing_video_package
    label: 营销视频包
    includedSlots: [script, video]
    postDeliveryAction:
      type: update_digital_human_stats
      mapping:
        digitalHumanId: "{{digital_human.id}}"
        videoCount: "+1"
        totalDuration: "+{{target_duration}}"
```

### 11.3 场景配置数据结构（完整版）

```typescript
// 存储在 script slot 的 metadata 中
interface VideoScriptContent {
  totalDuration: number
  scenes: VideoScene[]
}

interface VideoScene {
  order: number
  title: string
  content: string                    // 播报内容
  duration: number                   // 单段时长（≤15秒）
  
  // 场景描述（用于生成）
  sceneDescription?: string
  
  // 视觉配置（Hyperframes 支持的部分）
  visual?: {
    digitalHuman: {
      cameraAngle?: 'front-close' | 'front-medium' | 'side' | 'three-quarter'
      expression?: 'neutral' | 'smile' | 'serious'
    }
    background?: {
      type: 'image' | 'video' | 'color'
      url?: string
      color?: string
    }
    overlayImages?: Array<{
      url: string
      position: string
      size: string
      timing: { start: number, end: number }
    }>
  }
  
  // 文字配置
  text?: {
    subtitles: {
      enabled: boolean
      style?: string
      position?: string
    }
    overlays?: Array<{
      text: string
      position: { x: number, y: number }
      style: {
        fontSize: number
        color: string
        fontWeight?: string
      }
      animation?: string
      timing: { start: number, end: number }
    }>
  }
  
  // 音频配置
  audio?: {
    backgroundMusic?: {
      url: string
      volume: number
      fadeIn?: boolean
      fadeOut?: boolean
    }
    soundEffects?: Array<{
      url: string
      triggerTime: number
      volume: number
    }>
  }
  
  // 转场
  transition?: {
    type: string
    duration: number
  }
}

// Hyperframes 执行器输入
interface HyperframesExecutionInput {
  digitalHumanId: string
  avatarImageUrl: string
  voiceConfig: VoiceConfig
  scenes: VideoScene[]
  aspectRatio: string
  resolution: string
  
  // 全局配置
  globalConfig?: {
    backgroundMusic?: {
      url: string
      volume: number
      loop: boolean
    }
    brandElements?: {
      logo?: string
      watermark?: string
      endCard?: string
    }
  }
}
```

### 11.4 数据库 Schema

表结构以 [`v19__digital_human_schema.sql`](../../../apps/service/aaf-api/src/main/resources/db/migration/v19__digital_human_schema.sql) 为准，实体与字段说明见 [api.md](./api.md#数据模型)。该章节原有的 DDL 草稿已删除，避免与迁移脚本出现两份真理源。

---

## 十二、实施计划（更新）

### Phase 1：基础设施（1周）
- ✅ 数据库表结构
- ✅ 预设音色库初始化（5个音色）
- ✅ Assistant 双向关联字段
- ✅ 权限控制中间件（组织共享逻辑）

### Phase 2：模块一 - 资产生成（1.5周）
- ✅ Blueprint 定义: `digital-human-asset-generation`
- ✅ 复用现有阿里云图像生成执行器
- ✅ `postDeliveryAction`: 创建 DigitalHuman 实体
- ✅ 前端：项目创建表单（选择音色下拉框）

### Phase 3：模块二 - 数字人管理（1.5周）
- ✅ REST API: CRUD + 权限控制
- ✅ 列表页：卡片视图 + 标签筛选
- ✅ 详情页：
  - 左侧预览（头像大图 + 音色试听）
  - 右侧配置（基础信息、音色切换、模型配置、助理关联、统计）
- ✅ 音色切换弹窗（预设音色列表 + 试听）
- ✅ 助理关联（双向显示 + 跳转）

### Phase 4：模块三 - 视频生成（2.5周）
- ✅ Blueprint 定义: `digital-human-marketing-video`
- ✅ 脚本生成执行器（Claude + JSON 输出）
- ✅ 简单视频执行器（≤15秒，阿里云）
- ✅ Hyperframes 集成执行器（>15秒）
  - 场景配置 JSON → Hyperframes API
  - 支持完整场景编排（视觉、文字、音频、转场）
- ✅ 前端：
  - 项目创建表单（选择数字人）
  - 脚本编辑器（场景列表 + 配置面板）
  - 视频预览播放器

### Phase 5：优化与测试（1周）
- ✅ 积分消耗统计和记录
- ✅ 数字人使用统计更新
- ✅ 错误处理和重试机制
- ✅ 端到端测试
- ✅ 文档完善

**总计：约 7.5 周**

---

## 十三、下一步行动

✅ **架构设计已完成，所有决策已确认**

准备生成以下详细文档：

1. **PRD 产品需求文档** - 功能详细说明 + 用户故事
2. **API 接口规范文档** - REST API + 数据模型
3. **数据库迁移脚本** - 完整 SQL 脚本
4. **前端组件详细设计** - 组件树 + Props + 状态管理
5. **Blueprint 配置文件** - 完整 YAML

请确认是否现在生成这些文档，还是需要进一步讨论其他方面？

---

**文档版本**: v2.0  
**最后更新**: 2026-09-22  
**状态**: 架构设计完成 ✅
