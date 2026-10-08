---
level: Practice
layer: Product
purpose: 说明数字人视频生成执行器的绑定方式、入参契约、路由规则与待接入的外部服务
status: published
version: 2.0.0
date: 2026-10-08
author: AaronZZH & Kiro
tags:
  - 数字人
  - 视频生成
  - Hyperframes
related:
  - ../api.md
  - ../status.md
  - ./blueprint-deployment.md
---

# 数字人视频生成执行器

> 本文取代原「Phase 4 实施方案」。原文把阿里云、Hyperframes 各配成一个执行绑定，并按 `code` 字段登记，与平台 `aigc_execution_binding` 的真实结构不符，已整体重写。前端场景编辑器与视频播放器的实现以 `apps/webui/src/features/digital-human/video/` 下的源码为准，不再在文档中复制代码。

## 与 AIGC 平台的衔接

AIGC 平台的执行链路是：Blueprint 动作只声明 `actionKey`，运行时按 `aigc_execution_binding` 的 `action_key`、`project_type_code`、`priority` 选出绑定，再由绑定的 `target_type` 与 `target_ref` 决定谁来执行。

数字人视频沿用这条链路，不另开通道：

| 环节 | 取值 |
|------|------|
| Blueprint 动作 | `video.generate`，目标槽位 `video`，依赖槽位 `script` |
| 执行绑定 | `action_key = video.generate`、`project_type_code = digital_human_video`、`target_type = tool`、`target_ref = digital_human_video_generation`、`priority = 10` |
| 通用绑定 | v17 中 `video.generate → aigc.video.generate`（`priority = 0`），其他项目类型继续使用 |
| 执行器 | `DigitalHumanVideoGenerationAdapter`，实现 `AigcToolExecutionPort`，`supports(targetRef)` 只认 `digital_human_video_generation` |

`AigcToolActionExecutor` 和 `AigcActionCommandService` 通过各 `AigcToolExecutionPort.supports` 判断目标是否有人负责，新增 Tool 目标不需要修改 aigc 模块的白名单。形象生成动作 `image.generate` 复用通用绑定，不需要数字人专属执行器。

## 入参契约

执行器从动作的 `actionArguments` 读取：

| 参数 | 必填 | 说明 |
|------|------|------|
| `digitalHumanId` | 是 | 数字人 ID。形象与音色由执行器按 ID 读取，不接受调用方传入头像地址 |
| `scenes` | 是 | 脚本产出的场景列表，按顺序生成 |
| `aspectRatio` | 否 | `16:9`（缺省）、`9:16`、`1:1` |

`scenes` 每项：`content`（必填）、`duration`（必填，1 到 15 秒的整数）、`title`、`order`、`sceneDescription`（可选，`order` 以列表顺序为准）。脚本阶段已经分好段，执行器不再拆分脚本；总时长为各场景时长之和，上限 600 秒，场景数上限 50。

## 数字人校验

执行器可能运行在没有登录态的调度线程上，所以不依赖线程上下文，而是用执行命令携带的组织与工作空间校验：

- 数字人必须存在，组织必须与执行命令一致。
- 数字人属于某个工作空间时，必须与执行命令的工作空间一致；组织级数字人（工作空间为空）组织内可用。
- 状态必须是 `active`，已归档的数字人不能生成视频。

## 路由规则

路由只在执行器内保留一套：

| 条件 | 行为 | 结果中的 `provider` |
|------|------|---------------------|
| 单个场景且总时长不超过 15 秒 | 调用阿里云出片 | `aliyun` |
| 其他情况 | 每个场景调用阿里云出一段，再调用 Hyperframes 按顺序合成 | `hyperframes` |

执行器同步返回 JSON：`videoUrl`、`duration`、`provider`、`aspectRatio`、`sceneCount`。

## 积分

积分以 AIGC 执行记录为准。绑定上的 `estimated_credits`（当前 50）只用于提交时的项目预算校验，不是实际扣费。视频动作的积分消耗同步到 `digital_human_credit_consumption` 与数字人统计字段的流程尚未实现，见 [实施状态](../status.md)。

## 待接入

- `generateAliyunClip`、`composeWithHyperframes` 目前只打日志并返回占位地址，调用、轮询、取回地址都未实现。
- 阿里云与 Hyperframes 的地址、超时、密钥属于运行配置，接入真实调用时放入 Spring 配置，不放在绑定表。
- 执行器同步返回文本候选，不产生媒体版本。接入真实服务后，要改为提交 AIGC 任务或写入媒体，视频才能作为 `MEDIA_VERSION` 交付物被采纳。
