---
level: Practice
layer: Product
purpose: 定义 ModBot 数字人的大模型语义表演计划与多模态驱动融合方案
status: draft
version: 1.2.0
date: 2026-09-17
author: Kiro
changelog:
  - 2026-09-17 | v1.2.0 第二节点调整为 AvatarDriveFusionEngine，接入边缘检测与视觉大模型融合的 SceneState
  - 2026-09-17 | v1.1.0 明确首版采用 TTS viseme/音素时间戳驱动口型，补充协同发音、表情融合与量化验收
  - 2026-09-17 | v1.0.0 创建数字人语义计划与驱动转换双节点设计
tags:
  - ModBot
  - 数字人
  - 大模型
  - 表演计划
  - 多模态驱动
dependencies:
  - ./product-concept.md
  - ../../framework/intelligent/skill/skill.md
  - ../../framework/intelligent/action-governance.md
related:
  - ./qt-robot-communication.md
  - ./visual-perception-fusion.md
scope:
  includes:
    - 大模型生成回复与数字人表演语义计划的节点合同
    - 大模型计划、TTS、实时场景状态到多通道时序参数的融合
    - 首版 TTS viseme/音素口型同步与表情融合
    - 开发板目标检测和视觉大模型融合结果的数字人使用边界
    - 复杂连续动作的视频替代边界
    - 与大模型直接实时生成底层驱动参数的方案对比
  excludes:
    - 具体数字人模型、骨骼和动画资产的制作流程
    - 具体渲染引擎、TTS 厂商和设备协议适配
    - 目标检测与视觉大模型的具体模型选型和部署
    - 机器人硬实时运动控制与实体安全联锁实现
gains:
  - 能实现回复正文与数字人表演计划的一次性结构化生成
  - 能融合大模型计划、TTS 和 SceneState 生成多通道驱动参数
  - 能实现以 TTS viseme 为主、音频节奏为辅的首版口型同步
  - 能用开发板检测和视觉大模型协同支撑注视、指向和场景反应
  - 能判断复杂动作应使用实时驱动、动画片段还是视频
  - 能解释为何生产主链不采用大模型逐帧生成驱动参数
---

# 数字人语义计划与多模态驱动融合设计

> ModBot 数字人采用“大模型生成表演语义计划 + 多模态驱动融合引擎”的双节点架构。口型来自 TTS，视觉来自开发板检测与视觉大模型融合的 SceneState；第二节点统一完成时序、资产、视觉目标、通道约束和本地高频驱动。

## 决策

生产主链保持两个核心节点，视觉作为受治理的场景状态支撑，不增加直接控制数字人的第三个模型节点：

```text
开发板目标检测/跟踪 ─┐
视觉大模型按需理解 ───┴→ VisualSceneFusion → SceneState
                                                ├→ 冻结 SceneSnapshot
用户消息与对话上下文 ───────────────────────────┘
  → 节点一 AvatarPerformancePlanner
      大模型生成回复正文与表演语义计划
  → AvatarPerformancePlan

AvatarPerformancePlan + TtsArtifact + 实时 SceneState
+ AvatarProfile + AssetManifest + RuntimePolicy
  → 节点二 AvatarDriveFusionEngine
      多源融合并生成多通道时序驱动参数
  → CompiledDriveBundle / RuntimeDriveDelta
  → 本地播放器按 30/60 FPS 插值执行
```

核心分工：

- 大模型决定数字人“表达什么”：回复正文、情绪、语气、语义动作、注视和场景表现意图。
- TTS 决定数字人“如何发音”：最终音频、语句边界、音素、viseme、停顿和重音。
- SceneState 提供数字人“面向什么”：用户、目标物、位置、关系、可见性和新鲜度。
- 融合引擎决定数字人“如何准确执行”：时间、口型、表情曲线、视觉目标、动画资产、资源锁、过渡、打断和底层参数。
- 通用大模型、视觉大模型都不直接持续生成骨骼、BlendShape、坐标和逐帧控制值。
- 缺少常规资产的复杂长尾动作可由专用动作模型离线或短前瞻生成动画片段，验证后仍由融合引擎播放。

## 设计原则

### 语义与驱动分层

大模型只输出与具体数字人解耦的语义意图，不感知骨骼名称、BlendShape 名称、动画文件名、关节角度、引擎 API 或设备协议。同一语义计划可映射到写实人物、二次元角色、卡通机器人或只有眼睛和嘴巴的屏幕形象。

### 回复正文唯一

`AvatarPerformancePlan.utterances[].text` 是聊天气泡、字幕和 TTS 的唯一正文。禁止模型在结构化计划之外再输出第二份用户回复，避免正文、字幕、语音和动作语义不一致。

### 低频决策与高频执行分离

大模型按每次回复或语句生成一次计划；转换引擎生成关键帧和离散事件；本地播放器负责 30/60 FPS 插值。服务端不逐帧调用大模型，也不逐帧传输驱动值。

### 确定性约束优先

时间换算、动作冲突、幅度限制、资源互斥、过渡、打断和恢复均由版本化规则处理。相同计划、资产画像和编译器版本应产生相同驱动结果。

## 节点一：大模型生成表演语义计划

### 定位

`AvatarPerformancePlanner` 是大模型节点，通过专用 Skill 和结构化 Tool 合同生成一次完整回复及其表演语义。建议使用：

```text
Skill: builtin-avatar-performance-planning
Tool:  avatar.performance.submit
Output: AvatarPerformancePlan
```

该 Skill 只在 ModBot 或启用数字人输出的 Assistant 范围内激活，不作为所有助理的全局能力。

### 输入

| 输入 | 内容 |
|---|---|
| 用户消息 | 本轮目标、问题或指令 |
| 对话上下文 | 与本轮表达有关的最小历史上下文 |
| 助理人格 | 性格、说话风格、表达强度和禁用行为 |
| 场景上下文 | 闲聊、教学、提醒、告警、庆祝或演示等业务场景 |
| `SceneSnapshot` | 本轮冻结的可见实体、关系、置信度和新鲜度 |
| 抽象能力摘要 | 是否支持表情、注视、上半身动作、全身动作、位移、灯光、屏幕和预制复杂动画 |

抽象能力摘要只告诉大模型“能做什么”，不暴露底层资产和驱动实现。`SceneSnapshot` 只暴露来自 [视觉感知融合设计](./visual-perception-fusion.md) 的稳定 `entityId` 和受控语义，不提供原始视频流、像素坐标或设备驱动字段。模型可生成 `LOOK_AT_OBJECT(entityId)`、`POINT_AT_OBJECT(entityId)` 等意图，但引用目标必须存在于本轮冻结快照。

### 结构化输出

```json
{
  "version": "avatar-performance-plan/1.0",
  "utterances": [
    {
      "id": "u1",
      "text": "欢迎回来，很高兴再次见到你！",
      "speechAct": "GREETING",
      "emotion": {
        "type": "HAPPY",
        "intensity": 0.75,
        "valence": 0.8,
        "arousal": 0.55
      },
      "voice": {
        "style": "WARM",
        "pace": 1.0,
        "pitch": 0.1,
        "volume": 0.9
      }
    },
    {
      "id": "u2",
      "text": "今天有什么需要我帮助的吗？",
      "speechAct": "QUESTION",
      "emotion": {
        "type": "CURIOUS",
        "intensity": 0.45,
        "valence": 0.4,
        "arousal": 0.35
      },
      "voice": {
        "style": "FRIENDLY",
        "pace": 0.95,
        "pitch": 0.05,
        "volume": 0.85
      }
    }
  ],
  "cues": [
    {
      "id": "c1",
      "anchor": {
        "utteranceId": "u1",
        "relation": "DURING"
      },
      "channel": "BODY",
      "intent": "WELCOME",
      "intensity": 0.7
    },
    {
      "id": "c2",
      "anchor": {
        "utteranceId": "u2",
        "relation": "DURING"
      },
      "channel": "GAZE",
      "intent": "LOOK_AT_USER",
      "intensity": 0.5
    }
  ],
  "scene": {
    "presentation": "REALTIME",
    "energy": 0.55
  }
}
```

Schema 使用闭世界字段，拒绝未知属性。执行身份、设备、授权、风险、优先级和幂等键来自可信运行上下文，不允许模型填写。

### 语义模型

情绪同时使用离散类别与连续维度：

```text
离散类别：NEUTRAL、HAPPY、SAD、ANGRY、SURPRISED、CURIOUS、FOCUSED、
         CONFUSED、CONCERNED、EXCITED、CALM、TIRED、ALERT、GRATEFUL、
         APOLOGETIC、ENCOURAGING、EMBARRASSED
连续维度：valence、arousal、dominance、intensity，范围 0..1
```

离散类别便于理解和资产映射，连续维度用于平滑过渡和角色差异化。

动作只表达通用语义：

```text
HELLO、WELCOME、GOODBYE、THANK、APOLOGIZE、AGREE、DISAGREE
EMPHASIZE、EXPLAIN、POINT_USER、POINT_SCREEN、COUNT、COMPARE
THINK、LISTEN、ENCOURAGE、CELEBRATE、WARNING、DANCE、WALK_TO_TARGET
```

同一 `WELCOME` 可根据 AvatarProfile 映射为挥手、双手欢迎、点头微笑或屏幕动画。

### 正文输出合同

启用数字人计划时采用结构化终态：

```text
Assistant 调用一次 avatar.performance.submit
→ Tool 校验并冻结 AvatarPerformancePlan
→ UI、字幕和 TTS 投影 utterances[].text
→ Tool 成功后本轮不再生成自然语言总结
```

完成条件至少包括：

- 恰有一份合法计划；
- `utterance.id` 唯一且顺序稳定；
- 聊天正文 hash 与计划正文 hash 相同；
- Tool 调用后不存在第二份回复；
- Schema、文本长度和语义目录引用全部有效。

### 节点边界

节点一不负责：

- 计算毫秒级开始和结束时间；
- 生成音素、口型或逐帧曲线；
- 选择具体动画文件；
- 输出骨骼、BlendShape、关节、坐标或引擎参数；
- 处理动作冲突、播放、暂停、打断和恢复；
- 授予设备或外部动作权限。

## 节点二：融合多模态驱动参数

### 定位

`AvatarDriveFusionEngine` 是确定性多源融合与时序编排引擎。它不只转换大模型输出，而是融合 `AvatarPerformancePlan`、`TtsArtifact`、实时 `SceneState`、角色资产和运行策略，生成目标数字人可执行的 `CompiledDriveBundle`，并在播放期间生成受约束的 `RuntimeDriveDelta`。

### 输入

| 输入 | 内容 |
|---|---|
| `AvatarPerformancePlan` | 节点一生成的回复、情绪、动作、注视和场景表现语义 |
| `TtsArtifact` | 最终音频、语句边界、词边界、音素、viseme、停顿和重音 |
| `SceneState` | 开发板检测和视觉大模型融合后的实体、位置、关系、来源与 TTL |
| `AvatarProfile` | 目标数字人的通道能力、参数范围和并发限制 |
| `AssetManifest` | 情绪、动作、视频与语义意图的版本化映射 |
| `RuntimePolicy` | 动作密度、资源锁、视觉使用、过渡、打断和降级规则 |

示例 AvatarProfile：

```json
{
  "avatarId": "avatar-001",
  "version": "3",
  "channels": {
    "face": true,
    "viseme": true,
    "gaze": true,
    "head": true,
    "upperBody": true,
    "fullBody": true,
    "locomotion": false,
    "light": false,
    "screen": false
  },
  "limits": {
    "maxConcurrentGestures": 1,
    "minimumGestureGapMs": 1200,
    "expressionTransitionMs": 250
  }
}
```

### 融合流程

```text
校验并规范化 AvatarPerformancePlan
→ 按 utterance 生成 TTS 与稳定时间标记
→ 根据音素生成口型曲线
→ 将情绪和动作意图解析为目标资产
→ 用 SceneState 将 entityId 解析为当前注视/指向目标
→ 建立多通道时间轴
→ 计算资源锁、优先级、TTL 和冲突
→ 添加过渡及程序化微动作
→ 生成 CompiledDriveBundle
→ 播放期按新鲜 SceneState 生成有界 RuntimeDriveDelta
→ 本地播放器分块预缓冲并按 30/60 FPS 插值
```

### 视觉状态融合

融合引擎只消费 [视觉感知融合设计](./visual-perception-fusion.md) 产出的统一 `SceneState`，不直接读取原始帧、检测器响应或视觉大模型自由文本。视觉输入主要作用于：

- `LOOK_AT_USER`、`LOOK_AT_OBJECT(entityId)` 的眼神和头部目标；
- `POINT_AT_OBJECT(entityId)` 的虚拟角色指向；
- 用户移动时的平滑注视跟随；
- 目标丢失、遮挡或位置过期后的动作取消和中性复位；
- 场景变化导致可选表现失效时的停止或重新规划请求。

大模型计划提供语义目标，`SceneState` 提供当前事实，融合引擎负责在策略范围内解析两者。视觉状态不能凭空创建计划外的业务动作；视觉大模型不能直接生成坐标、骨骼、BlendShape、设备命令或动作授权。物理避障和人身安全继续由本地确定性算法与 Safety Supervisor 负责。

### TTS 与口型同步

#### 首版决策

首版不采用“仅按音频节奏控制张嘴”的方案，而采用以下组合：

```text
TTS viseme/音素时间戳  → 决定发音嘴形和切换时机
音频能量与重音         → 辅助下颌开合、力度和头部微动
AvatarPerformancePlan  → 决定情绪、嘴角、脸颊、眉眼和整体表演
```

音频振幅只能判断何时张嘴和张多大，无法可靠区分闭唇音、唇齿音、圆唇和展唇，不能作为写实 3D 数字人的主要口型来源。首版生产基线定为 `TTS_VISEME`；若 TTS 不直接提供 viseme，则使用已知正文与最终音频做音素强制对齐。仅音频能量驱动的 `AUDIO_ENVELOPE` 只允许用于远景、低精度卡通形象或显式低质量模式，不得静默宣称精确口型。

| 等级 | 方案 | 定位 |
|---|---|---|
| `AUDIO_ENVELOPE` | 音频能量控制下颌开合 | 原型或低精度模式 |
| `TTS_VISEME` | TTS viseme/音素时间戳 + 平滑 | 首版生产基线 |
| `COARTICULATED_VISEME` | viseme + 协同发音 + 情绪分层融合 | 首版应预留、推荐同步实现 |
| `NEURAL_AUDIO_FACE` | 神经音频模型生成高维面部曲线 | 后续高质量写实模式 |

#### TTS 主时间轴

节点一只表达“在某个 utterance 之前、期间或之后执行”，节点二将计划正文原样交给 TTS，并以最终播放音频的 sample clock 作为整场主时钟。`TtsArtifact` 至少冻结音频 hash、时长、语句边界和规范化 viseme：

```json
{
  "artifactId": "tts-01",
  "sourceTextHash": "sha256:...",
  "audioHash": "sha256:...",
  "durationMs": 5100,
  "utteranceMarks": [
    { "utteranceId": "u1", "startMs": 0, "endMs": 2150 },
    { "utteranceId": "u2", "startMs": 2300, "endMs": 5100 }
  ],
  "visemes": [
    { "type": "PP", "startMs": 180, "endMs": 260, "confidence": 1.0 },
    { "type": "AA", "startMs": 260, "endMs": 410, "confidence": 1.0 }
  ]
}
```

由此换算其余语义通道：

```text
u1            = 0..2150ms
u2            = 2300..5100ms
WELCOME       = 200..1900ms
HAPPY         = 0..2200ms
LOOK_AT_USER  = 2200..5100ms
VISEME        = 按最终 TTS 音素边界生成
```

TTS 文本必须来自 `AvatarPerformancePlan.utterances[].text`，且 `sourceTextHash` 与计划正文 hash 相同。TTS 适配层可以生成供应商参数或 SSML，但不能改写正文或回写语义计划。音频与所有口型事件必须引用同一个 `audioHash`，禁止音频重合成后继续复用旧口型时间线。

#### 统一 VisemeSet

平台定义独立于 TTS 厂商和数字人资产的 `VisemeSet v1`，首版建议包含：

```text
SIL、PP、FF、TH、DD、KK、CH、SS、NN、RR、AA、E、I、O、U
```

其中 `PP` 覆盖双唇闭合发音，`FF` 覆盖唇齿音，`AA/E/I/O/U` 表达主要元音口形。每个 TTS Adapter 将厂商音素或 viseme 映射到统一集合；每个 AvatarProfile 再将统一集合映射到本角色的 BlendShape 或口部骨骼。中文优先使用 TTS 返回的语言相关音素事件；缺失时按拼音声母、韵母或 IPA 转换并结合最终音频强制对齐，不由大模型猜测发音时间。

#### 协同发音

口型不会在音素边界瞬间切换。`LipSyncPolicy` 应版本化以下参数，并按语速和相邻口型动态计算：

- 提前约 40..80ms 向下一口型过渡；
- 相邻 viseme 允许受控重叠；
- 元音保持较长，辅音闭合较短；
- 快速语音合并过短且视觉贡献低的口型；
- 静音阶段平滑回到自然闭嘴；
- 闭唇音必须达到闭合约束，不能被 Smile 等表情破坏；
- 曲线在本地 30/60 FPS 插值，不逐帧依赖服务端消息。

#### 表情融合

面部驱动按区域和优先级分层，而非无界相加：

```text
ARTICULATION_MASK  嘴唇、下颌和发音闭合，优先级最高
EXPRESSION_MASK    眉毛、眼睛、脸颊和受约束的嘴角偏置
PROCEDURAL_MASK    眨眼、呼吸和眼神微动
```

最终口部曲线由受约束的 `viseme articulation + audio jaw envelope + expression lip bias` 合成。情绪可以改变嘴角和脸颊，但不能阻止 `p/b/m` 等闭唇音闭合。后续神经音频面部模型也必须输出到该分层合同，不能绕过表情 mask、参数范围和资源冲突规则。

#### 缺失与降级

```text
TTS 直接提供 viseme/音素时间戳
→ 映射统一 VisemeSet
否则正文与最终音频可用于强制对齐
→ 执行 G2P/音素转换 + forced alignment
否则目标形象明确允许低质量模式
→ AUDIO_ENVELOPE + 结构化降级标记
否则
→ 口型编译失败，不启动错误同步的可见口播
```

降级选择由可信 RuntimePolicy 和 AvatarProfile 决定，不由大模型或 TTS Adapter 自行切换。

### 驱动通道

| 通道 | 职责 |
|---|---|
| `AUDIO` | TTS 音频、音量和停顿 |
| `VISEME` | 音素到口型权重曲线 |
| `FACE` | 面部表情和局部表情 |
| `EYE` | 眼球、眼睑和眨眼 |
| `GAZE` | 注视用户、屏幕或场景目标 |
| `HEAD` | 点头、摇头和头部朝向 |
| `BODY` | 身体姿态和手势动画 |
| `LOCOMOTION` | 虚拟角色位移 |
| `LIGHT` | 颜色、亮度、呼吸和闪烁 |
| `SCREEN` | 字幕、卡片和画面特效 |
| `SFX` | 提示音和环境音效 |
| `VIDEO` | 审核过的预制复杂动画视频 |

物理机器人底盘移动和摄像头采集不复用数字人的 `LOCOMOTION` 或画面通道，其授权和安全执行边界见 [Qt 机器人通信设计](./qt-robot-communication.md)。

### 连续曲线与离散事件

连续曲线用于 BlendShape、眼球方向、头部旋转、表情强度和灯光亮度：

```json
{
  "channel": "FACE",
  "parameter": "smile",
  "keyframes": [
    { "timeMs": 0, "value": 0.0 },
    { "timeMs": 250, "value": 0.75 },
    { "timeMs": 1800, "value": 0.75 },
    { "timeMs": 2200, "value": 0.0 }
  ],
  "interpolation": "CUBIC"
}
```

离散事件用于播放动画、视频、音效和画面切换：

```json
{
  "channel": "BODY",
  "event": "PLAY_ANIMATION",
  "startMs": 200,
  "durationMs": 1700,
  "assetId": "wave-right-v2",
  "blendInMs": 200,
  "blendOutMs": 250
}
```

### 程序化微动作

以下行为由引擎生成，不占用大模型输出预算：

- 自然眨眼和眼神微动；
- 呼吸和轻微身体摆动；
- 说话时的小幅点头；
- 表情淡入淡出；
- 动作间过渡；
- 动作结束后返回 Idle；
- 同一语义动作的已审核变体选择与重复抑制。

### 资源冲突

引擎为动作声明 `FACE`、`MOUTH`、`EYES`、`HEAD`、`LEFT_HAND`、`RIGHT_HAND`、`UPPER_BODY`、`FULL_BODY`、`AUDIO`、`SCREEN` 和 `VIDEO` 等资源。冲突按以下顺序处理：

```text
安全与用户打断
> 语音和口型
> 必需语义动作
> 可选手势、灯效和特效
> 眨眼、呼吸和待机微动作
```

同一骨骼或独占资源不能同时由两个动作控制。次要动作可延后；延后后失去语义时机则省略，并产生结构化降级记录。

### 实时播放与打断

首版采用“完整计划、分句编译、短时预缓冲”：

```text
大模型生成完整计划
→ 每个 utterance 独立 TTS 和编译
→ 预缓冲 300..800ms 的可配置窗口
→ 本地播放器按 30/60 FPS 插值执行
```

每次播放使用独立 `playbackSessionId + playbackEpoch`。用户打断时停止未播放音频，取消未来事件，口型复位，表情和身体回到安全 Idle，并递增 epoch；旧数据块和迟到回调不得影响新播放。

## 复杂连续动作与视频

实时参数驱动适合普通对话、讲解、提醒、简单手势、表情、注视和口型。以下场景优先使用高质量动画 Clip 或审核视频：

- 舞蹈、武术和杂技；
- 多角色互动；
- 复杂道具操作；
- 布料、头发和粒子效果；
- 固定品牌开场和节日庆祝；
- 电影化镜头和复杂转场。

选择顺序：

```text
已有兼容的 3D 动画 Clip
→ 使用实时渲染播放 Clip
否则已有正文和角色精确匹配的审核视频
→ 播放视频
否则
→ 简化为基础表情、手势和 TTS
```

视频不是任意驱动失败的通用兜底。动态口播若无法保证视频的正文、声音、角色和口型精确匹配，只能使用无明显口型的动作片段、透明身体动画、口播前后插入片段，或返回实时 3D 表现。

## 与大模型直接实时生成的对比

本文方案本身仍使用大模型实时生成，但只生成高层表演决策。对比方案是让大模型持续生成骨骼旋转、BlendShape 权重、动作时间和逐帧控制值。

| 维度 | 语义计划 + 确定性转换 | 大模型直接实时生成底层驱动 |
|---|---|---|
| 创造力 | 较高，受语义目录和资产约束 | 理论上更开放 |
| 稳定性 | 高，规则和资产可冻结 | 输出容易漂移和抖动 |
| 音画同步 | 基于真实 TTS 时间精确计算 | 通用模型难以持续精确同步 |
| 帧率 | 本地稳定执行 30/60 FPS | 模型延迟和抖动不适合高频控制 |
| 成本 | 每次回复一次主要模型调用 | 成本随时长和更新频率增长 |
| 可测试性 | 可复现、回归和预览 | 同一输入可能得到不同轨迹 |
| 安全 | 可限幅、限速、互斥和打断 | 容易输出越界或冲突参数 |
| 多角色适配 | 同一计划映射不同 Avatar | 模型需理解每个角色底层结构 |
| 恢复 | 可按计划和 epoch 重放 | 难以断点恢复和对账 |
| 资产质量 | 使用审核资产，质量稳定 | 容易穿模、突变和不自然 |
| 初期成本 | 需要资产、画像和编译规则 | Demo 启动较快 |
| 长尾动作 | 需增加资产或生成 Clip | 可快速探索新动作 |

### 语义计划方案优势

- 生产可控：可测试、回放、审核、限幅和版本化。
- 角色解耦：一份计划可驱动不同形态数字人。
- 低延迟低成本：模型低频决策，本地高频执行。
- 易于治理：大模型不能越过资产、权限和运行时约束。
- 易于打断：确定性时间轴可取消未来事件并恢复安全状态。

### 语义计划方案不足

- 语义目录和资产不足时，表现范围受限。
- 需要维护 AvatarProfile、AssetManifest、冲突规则和过渡规则。
- 固定动作库可能重复，需要变体选择和最近使用抑制。
- 新角色接入需要完成语义到资产的映射和能力测试。

### 直接生成方案适用范围

大模型或生成式动作模型更适合：

- 快速原型和实验性角色；
- 离线生成动画草稿；
- 为未来数秒生成关键姿势或动作轨迹；
- 缺少现成资产的长尾动作；
- 非关键娱乐和创意探索。

它不适合作为通用大模型逐帧控制生产数字人的主链路。

## 演进方向

生产方案可演进为三级结构：

```text
通用大模型
  → 生成回复、情绪、动作意图和语义锚点
可选专用动作生成模型
  → 为缺失的复杂动作生成短动画 Clip 或关键姿势
多模态驱动融合引擎
  → 融合 TTS、视觉状态、口型、时间轴、资源锁、过渡、插值和播放
```

专用模型生成的动画必须先经过关节范围、速度、碰撞、穿模、落脚和质量校验，再登记为受版本管理的资产，由节点二播放；生成模型不能绕过融合引擎直接控制运行中的数字人。

## 核心接口

```java
interface AvatarPerformancePlanningPort {
    AvatarPerformancePlan plan(AvatarPerformancePlanningRequest request);
}

interface AvatarPerformancePlanService {
    SubmittedPerformancePlan submit(
            AvatarPerformancePlan plan,
            PerformanceExecutionContext context);
}

interface TtsSynthesisPort {
    TtsArtifact synthesize(TtsSynthesisRequest request);
}

interface AvatarDriveFusionEngine {
    CompiledDriveBundle compile(
            AvatarPerformancePlan plan,
            TtsArtifact ttsArtifact,
            SceneSnapshot initialScene,
            AvatarProfile avatarProfile,
            AssetManifest assetManifest,
            RuntimePolicy runtimePolicy);

    RuntimeDriveDelta updateScene(
            SceneState sceneState,
            PlaybackFusionContext playbackContext);
}

interface AvatarDrivePlayer {
    PlaybackSession start(CompiledDriveBundle bundle);

    PlaybackSession apply(RuntimeDriveDelta delta);

    PlaybackSession cancel(String playbackSessionId, long playbackEpoch);
}
```

具体代码分层、持久化表和协议字段在进入实现任务时独立设计，本篇只固定双节点责任与跨节点合同。

## 验收基线

- 一轮数字人回复只有一份正文，聊天、字幕和 TTS 的文本 hash 一致。
- 节点一输出不含骨骼、BlendShape、动画文件、逐帧值或设备协议字段。
- 节点一只引用冻结 `SceneSnapshot` 中的稳定 `entityId`，不读取原始视觉流或生成坐标。
- 节点二只消费统一 `SceneState`，开发板检测结果和视觉大模型响应不能分别成为驱动源。
- 相同计划、TTS、初始场景快照、画像、资产和编译器版本产生字节级稳定的规范化编译结果。
- 实时 SceneState 只能在原计划与策略范围内生成有界增量，不能创建计划外业务动作。
- 几何位置过期或目标丢失后停止注视/指向更新并回到中性姿态，视觉大模型旧语义不能维持旧坐标。
- 视觉大模型输出不能直接生成骨骼、BlendShape、设备命令、物理动作授权或安全结论。
- 同一语义计划可在至少两个不同 AvatarProfile 上融合成功。
- 口型以真实 TTS viseme/音素时间为主，音频节奏只辅助下颌开合、力度和头部微动。
- 音频、viseme 与计划正文分别通过 `audioHash`、`sourceTextHash` 形成同源校验，重合成音频不得复用旧口型。
- 首版统一 VisemeSet 可跨 TTS Adapter 和至少两个不同 AvatarProfile 确定性映射。
- `p/b/m` 等闭唇音达到完整闭合，Smile 等情绪偏置不能破坏发音约束。
- 相邻 viseme 经过协同发音和平滑插值，不出现肉眼可见的逐帧跳变。
- viseme 事件相对音频 sample clock 的 P95 时间误差目标不超过 40ms，可见音画偏差目标不超过 80ms；生产值须按终端渲染与音频链路校准。
- 静音或用户打断后口型目标在 100ms 内停止推进，并在 150ms 内平滑回到安全闭嘴状态。
- 同一资源上的冲突动作不能并发生效，省略和降级有结构化原因。
- 播放被打断后不再执行未来事件，旧 epoch 数据不能影响新播放。
- 复杂动作视频只使用审核且兼容的资产，不以语义近似视频替代动态口播。
- 通用大模型调用频率不随 30/60 FPS 播放帧率增长。

## 实现状态

| 能力 | 状态 |
|---|---|
| 双节点架构与责任边界 | 已确认设计 |
| `AvatarPerformancePlan` Schema | 本文定义目标合同，尚未实现 |
| `AvatarDriveFusionEngine` | 目标态，尚未实现 |
| SceneSnapshot / SceneState 融合 | 目标态，尚未实现；视觉生产方见视觉感知融合设计 |
| AvatarProfile 与 AssetManifest | 目标态，尚未实现 |
| 多通道播放器与打断 | 目标态，尚未实现 |
| 专用动作生成模型 | 演进能力，首版非必需 |
