---
level: Practice
layer: Product
purpose: 定义 ModBot 开发板目标检测与视觉大模型协同的视觉感知融合方案
status: draft
version: 1.0.0
date: 2026-09-17
author: Kiro
tags:
  - ModBot
  - 视觉感知
  - 目标检测
  - 视觉大模型
  - 多模态融合
dependencies:
  - ./product-concept.md
  - ./qt-robot-communication.md
related:
  - ./avatar-performance-driving.md
scope:
  includes:
    - 开发板目标检测与跟踪的高频事实输出
    - 视觉大模型按需补充场景语义
    - 几何事实与场景语义的状态融合
    - 视觉状态对数字人表演规划和驱动融合的支撑
    - 成本、隐私、时效和安全边界
  excludes:
    - 具体目标检测模型和视觉大模型选型
    - 摄像头、NPU 和开发板型号选型
    - 训练数据、模型微调和部署工具链
    - 机器人避障、导航和实体安全算法
    - 原始视频传输与编解码实现
gains:
  - 能划分开发板检测、视觉大模型和场景融合的职责
  - 能通过统一 SceneState 支撑数字人注视、指向和场景反应
  - 能按事件、预算和隐私策略控制视觉大模型成本
  - 能避免视觉大模型直接驱动 3D 参数或物理动作
---

# 视觉感知融合设计

> ModBot 采用“开发板高频检测与跟踪 + 视觉大模型按需语义理解 + 确定性场景状态融合”。视觉大模型具有图像和场景理解能力，但其成本、延迟和不确定性决定了它不能替代本地检测器，也不能进入实时控制闭环。

## 决策

```text
相机、深度与其他视觉传感器
  → EdgeDetectorTracker
      高频输出目标类别、位置、关键点、深度和 trackId
  → EdgeVisualObservation ─────────────┐
                                      ├→ VisualSceneFusion → SceneState
关键帧、目标 ROI 和任务问题            │
  → VisionLanguageModelPort            │
      按需输出物体属性、关系和场景语义 ─┘

SceneState
  ├→ AvatarPerformancePlanner 使用冻结 SceneSnapshot 规划回复与表演
  └→ AvatarDriveFusionEngine 使用实时状态修正注视、指向与反应
```

职责边界：

- 开发板检测器负责低延迟、连续、可定位的视觉事实。
- 视觉大模型负责低频、按需、成本较高的语义理解。
- `VisualSceneFusion` 负责来源、时效、置信度、冲突和实体关联。
- 两个数字人核心节点只读取统一 `SceneState`，不分别消费原始模型输出。
- 视觉大模型不直接输出 BlendShape、骨骼、动作名、设备命令或物理控制参数。

## 三层视觉能力

### 开发板检测与跟踪

`EdgeDetectorTracker` 常驻开发板或机器人核心计算单元，优先使用 NPU/GPU，处理连续视频帧。典型输出包括：

- 人、手、常见物体和屏幕等目标类别；
- 二维包围框、关键点和遮挡状态；
- 稳定 `trackId`、速度和运动方向；
- 双目或深度相机提供的距离和相对三维位置；
- 人体姿态、手势、头部朝向等专用模型结果；
- 采集时间、帧 ID、模型版本和置信度。

建议能力等级：

| 能力 | 建议频率 | 用途 |
|---|---:|---|
| 目标检测与跟踪 | 10..30Hz，可按开发板调整 | 注视跟随、目标存在和位置更新 |
| 人体/手部关键点 | 10..30Hz，按场景启用 | 手势、姿态和指向估计 |
| 深度与空间位置 | 10..30Hz | 目标距离和三维锚点 |
| 场景变化检测 | 1..10Hz | 触发视觉大模型或刷新语义 |

频率是容量规划建议，不是硬编码协议值。检测器只发布有界结构化事实，不上传连续原始视频到业务控制通道。

### 视觉大模型理解

`VisionLanguageModelPort` 用于目标检测器难以完成的语义任务，例如：

- “用户拿着的物品可能用于什么”；
- “桌面上哪些东西与当前问题相关”；
- “画面中的文字、图表或界面表达什么”；
- “用户说的‘这个’更可能指哪个已检测目标”；
- 物体属性、目标关系、场景概述和任务相关区域选择。

视觉大模型不承担：

- 高频目标跟踪；
- 精确几何定位；
- 碰撞、避障、限位和急停判断；
- 对人的真实身份、心理状态或敏感属性作无依据推断；
- 直接决定物理动作是否安全。

视觉大模型输出必须绑定输入证据：

```json
{
  "observationId": "vlm-01",
  "sourceFrameId": "frame-1842",
  "sourceTrackIds": ["track-person-1", "track-object-7"],
  "modelRef": "vision-model/version",
  "capturedAt": "2026-09-17T09:56:36.803Z",
  "semantics": [
    {
      "subjectTrackId": "track-object-7",
      "predicate": "LIKELY_TYPE",
      "value": "coffee-cup",
      "confidence": 0.86
    },
    {
      "subjectTrackId": "track-person-1",
      "predicate": "POINTING_TO",
      "objectTrackId": "track-object-7",
      "confidence": 0.78
    }
  ]
}
```

模型自报置信度只是融合输入，不能单独决定自动动作。服务端应结合模型历史校准、检测一致性、帧新鲜度和多帧证据计算最终置信度。

### 场景状态融合

`VisualSceneFusion` 将边缘几何事实与视觉大模型语义合并为统一 `SceneState`：

```json
{
  "sceneVersion": 238,
  "observedAt": "2026-09-17T09:56:36.900Z",
  "validUntil": "2026-09-17T09:56:37.400Z",
  "entities": [
    {
      "entityId": "entity-7",
      "trackId": "track-object-7",
      "type": "OBJECT",
      "geometry": {
        "bbox": [0.62, 0.41, 0.18, 0.22],
        "positionMeters": [0.42, -0.08, 0.91]
      },
      "semantics": {
        "likelyType": "coffee-cup"
      },
      "provenance": [
        { "source": "EDGE_DETECTOR", "confidence": 0.94 },
        { "source": "VISION_MODEL", "confidence": 0.86 }
      ],
      "freshness": {
        "geometryTtlMs": 500,
        "semanticsTtlMs": 5000
      }
    }
  ]
}
```

融合规则：

- `trackId` 和几何位置以本地检测与跟踪结果为准；视觉大模型只能补充语义，不能覆盖更新鲜的几何事实。
- 几何和语义分别设置 TTL；语义仍有效不代表旧位置仍有效。
- 每个结论保留来源、模型版本、输入帧、置信度和时间。
- 目标丢失、跟踪切换或证据冲突必须显式反映，不能继续沿用旧位置。
- 多帧稳定结果可以提升可信度，单帧推测不得伪装成持续事实。
- `SceneState` 是视觉运行状态的唯一消费合同，原始检测和模型响应不形成平行场景真理源。

## 与数字人双节点的关系

### 节点一读取冻结场景快照

`AvatarPerformancePlanner` 在生成回复和表演语义前读取一次冻结 `SceneSnapshot`，用于：

- 回复用户指向或展示的目标；
- 选择 `LOOK_AT_USER`、`LOOK_AT_OBJECT` 或 `POINT_AT_OBJECT` 等语义意图；
- 根据用户是否在场、是否靠近和当前任务场景调整表达；
- 对视觉结论不充分的情况提出澄清，而不是猜测。

大模型只能引用 `entityId` 等稳定语义标识，不能提交像素坐标、空间坐标或检测器内部字段。计划中引用的目标必须存在于冻结快照中。

### 节点二读取实时场景状态

`AvatarDriveFusionEngine` 同时接收：

```text
AvatarPerformancePlan
+ TtsArtifact
+ SceneState stream
+ AvatarProfile
+ AssetManifest
+ RuntimePolicy
→ CompiledDriveBundle / RuntimeDriveDelta
```

实时视觉只用于受约束的运行期调整：

- 将 `LOOK_AT_OBJECT(entity-7)` 解析为当前注视方向；
- 将 `POINT_AT_OBJECT(entity-7)` 解析为可达的虚拟角色指向目标；
- 用户移动时平滑更新眼神和头部跟随；
- 目标丢失时取消指向并回到中性注视；
- 用户进入或离开时触发经策略允许的低风险反应；
- 场景变化使原计划失效时停止相关可选动作并请求重新规划。

驱动融合引擎不能把视觉状态直接转成未经计划和策略允许的新业务动作。物理底盘、机械臂和摄像头动作仍需独立授权与 Safety Supervisor 校验。

## 成本控制

视觉大模型默认采用 `EDGE_VLM_ON_DEMAND`，而不是连续逐帧调用。支持三种模式：

| 模式 | 行为 | 适用 |
|---|---|---|
| `EDGE_ONLY` | 只运行本地检测、跟踪和专用视觉模型 | 低成本、隐私敏感、离线 |
| `EDGE_VLM_ON_DEMAND` | 事件或任务需要时提交关键帧/ROI | 默认生产模式 |
| `EDGE_VLM_PERIODIC` | 在严格预算下周期刷新复杂场景语义 | 固定展厅等少量场景 |

按需触发条件：

- 用户明确询问画面、物体、文字或关系；
- 检测器类别未知或多个目标存在指代歧义；
- 目标、场景或任务阶段发生显著变化；
- 当前任务需要 OCR、图表、界面或复杂关系理解；
- 既有语义已过期且仍影响回复或动作。

成本优化：

- 只发送关键帧、目标 ROI 或低分辨率缩略图；
- 使用感知哈希和 scene-change 去除重复帧；
- 优先复用未过期的对象语义，不重复解释静态场景；
- 设置每分钟调用次数、像素、Token、并发和超时预算；
- 批量解释同一关键帧中的多个目标，避免逐目标调用；
- 硬件允许时使用本地小型视觉语言模型处理常见语义。

预算耗尽时保留 `EDGE_ONLY` 事实并标记语义不可用，不得把旧视觉大模型结论当作新事实。

## 隐私与安全

- 摄像头物理遮挡或视觉禁用状态是最高优先级事实；关闭后不采集、不缓存、不调用视觉大模型。
- 原始视频默认本地处理，不进入 MQTT、A2A、CAN-FD 或公共任务事件正文。
- 向远端视觉大模型提交图片必须经过用户/租户策略，采用最小帧、ROI、降分辨率和必要脱敏。
- 默认不做人脸身份识别、跨会话人员追踪和敏感属性推断；需要时必须独立设计授权、告知、留存与删除策略。
- 原始帧、裁剪图、模型响应和 SceneState 分别设置留存策略；普通交互默认不长期保存原始图像。
- 视觉大模型输出视为不可信观察数据，不能包含或执行指令，不能扩大 Tool 权限。
- 避障、碰撞和人身安全只依赖本地确定性传感器、算法与硬件联锁，不依赖视觉大模型。

## 降级与失效

```text
视觉大模型超时、限流或预算耗尽
→ 保留新鲜 EDGE_ONLY 几何事实，复杂语义标记 unavailable

目标检测短暂丢帧
→ 在短 TTL 内按跟踪策略预测，超时后目标失效

目标已丢失或位置过期
→ 停止注视/指向更新并回到中性姿态

摄像头遮挡或用户关闭视觉
→ 立即停止采集与视觉调用，清除运行期位置状态

视觉结论影响高风险动作
→ 不自动执行，进入独立工具授权和安全校验
```

禁止在视觉大模型失败时自动上传更大范围、更高分辨率或更长视频，除非策略预先允许且仍在预算内。

## 核心接口

```java
interface EdgeVisualObservationPort {
    EdgeVisualObservation latest();
}

interface VisionLanguageModelPort {
    VisualSemanticObservation understand(VisualUnderstandingRequest request);
}

interface VisualSceneFusion {
    SceneState fuse(
            EdgeVisualObservation edgeObservation,
            List<VisualSemanticObservation> semanticObservations,
            VisualFusionPolicy policy);
}

interface SceneStatePort {
    SceneSnapshot snapshot();

    SceneStateSubscription subscribe();
}
```

具体模型、NPU Runtime、帧缓冲和传输实现由后续硬件 PoC 决定，本篇只固定职责与跨节点合同。

## 验收基线

- 开发板检测器可在不调用视觉大模型时持续输出有界目标、位置和跟踪事实。
- 视觉大模型只按事件、任务和预算触发，不随摄像头帧率逐帧调用。
- 每条融合结论均可追溯到来源、版本、输入帧、时间和置信度。
- 几何与语义 TTL 分离，过期位置不能继续驱动注视和指向。
- 节点一只引用冻结 SceneSnapshot 中的稳定 `entityId`，不生成坐标。
- 节点二只在原计划与策略范围内使用实时 SceneState，不从视觉结果创造新业务动作。
- 视觉大模型输出不能直接生成 3D 驱动参数、设备命令或动作授权。
- 视觉大模型不可用时保留 EDGE_ONLY 能力，并明确标记语义降级。
- 物理遮挡或视觉关闭后不产生新帧、远端调用或运行期位置更新。
- 原始图片、视频和不可信模型响应不进入公共任务事件。
- 避障、碰撞和急停不依赖视觉大模型。

## 实现状态

| 能力 | 状态 |
|---|---|
| 边缘检测、视觉大模型与场景融合边界 | 已确认设计 |
| `EdgeVisualObservation` | 目标态，尚未实现 |
| `VisualSemanticObservation` | 目标态，尚未实现 |
| `SceneState` 与 TTL 融合 | 目标态，尚未实现 |
| 数字人双节点接入 SceneState | 目标态，尚未实现 |
| 视觉大模型成本和隐私策略 | 目标态，尚未实现 |
