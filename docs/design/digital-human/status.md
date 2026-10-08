---
level: Practice
layer: Product
purpose: 记录数字人模块当前已实现范围、已知缺口和待办，替代原先分散的阶段交付与总结文档
status: published
version: 1.1.0
date: 2026-10-08
author: AaronZZH & Kiro
tags:
  - 数字人
  - 实施状态
related:
  - ./system.md
  - ./api.md
  - ./video-generation/design.md
gains:
  - 能一眼判断数字人模块哪些能力已落地、哪些仍是占位
  - 能按待办清单继续补齐外部接口、统计和前端页面
---

# 数字人实施状态

> 本文取代原 `implementation`、`summary` 和 `phase4-summary/final/complete/delivery` 六份交付记录。它们互相重复，且「测试通过」「全部完成」等结论没有可复核的依据，已删除，可从 git 历史查看。设计阶段的 `digital-human-migration.sql` 草稿同样已删除，表结构以 `v19` 为准。
> 下列状态均来自 2026-10-08 对代码的核对，未运行应用。

## 已实现

| 范围 | 内容 |
|------|------|
| 数据 | `v19` 建表：`digital_human`、`digital_human_preset_voice`、`digital_human_credit_consumption`，统计视图 `v_digital_human_stats`，`ai_assistant.digital_human_id` 双向关联列 |
| 种子 | `v24`：两个 Blueprint、项目类型、权限码及角色绑定、一条视频执行绑定、字典 |
| 后端 CRUD | 数字人继承 `BaseCrudService`，含授权、工作空间隔离、Patch 更新、乐观锁；预设音色、积分记录为只读资源 |
| 后端命令 | 切换音色、关联/取消关联助理、归档、恢复 |
| Blueprint 衔接 | 形象生成复用通用 `image.generate` 绑定；视频只有一个 `video.generate` 动作，数字人视频项目下绑定到 `digital_human_video_generation` |
| 视频执行器 | `digital_human_video_generation`：按 ID 读取并校验数字人，消费脚本的 `scenes`，单场景且不超过 15 秒走阿里云，其余逐段出片后合成；路由只在执行器内保留一套，详见 [video-generation/design.md](./video-generation/design.md) |
| 视频历史 | 列表、详情、删除接口 |
| 前端组件 | 卡片、列表、详情、音色对话框、助理对话框、场景编辑器、视频播放器、视频历史 |

接口细节见 [api.md](./api.md)，前端结构见 [frontend.md](./frontend.md)。

## 已知缺口

按影响从高到低：

1. **外部视频接口是占位。** 阿里云出片和 Hyperframes 合成只打日志并返回写死的 `mock-*.mp4`；调用、轮询状态、取回地址都未实现，API 密钥与地址也未配置。执行器同步返回文本候选，不产生媒体版本，接入真实服务时要改为提交 AIGC 任务或写入媒体。
2. **没有脚本生成动作。** 平台没有可执行的文本生成绑定：v17 的 agent 绑定 `target_ref` 写成 `aigc.copy.generate`，而 `FrameworkAigcAgentExecutionAdapter` 要求 `agentId@version`；`AigcActionCommandService` 目前也只放行专业媒体动作。营销视频 Blueprint 因此不再声明 `generate_script`，脚本由人工或助理产出并采纳。补齐需要 Agent 定义、`agentId@version` 绑定，并放宽该白名单。
3. **积分没有接入。** `DigitalHumanCreditService` 的计算、扣减、退还没有任何调用方；`digital_human_credit_consumption` 没有写入方，所以积分记录接口永远为空。设计方向是以 AIGC 执行记录的消耗为准，由监听器同步写入。
4. **使用统计没有写入方。** `videoCount`、`totalDuration`、`creditsConsumed`、`lastUsedAt` 不会变化。原 `usage-stats` 接口已移除，设计方向是监听 AIGC 执行完成事件后直接调用 `DigitalHumanService`。
5. **交付后创建数字人未实现。** 两个 Blueprint 的交付集合里仍带 `postDeliveryAction`，Java 侧没有任何代码读取它，字段引用的也是旧表单键；营销视频那段还指向已删除的 `/usage-stats`。设计方向是监听 AIGC 交付事件，由监听器自己处理用户、组织、工作空间上下文。
6. **视频历史查询的 SQL 引用了不存在的表。** `DigitalHumanVideoRepository` 关联了 `aigc_project_slot`，迁移脚本中没有这张表，查询会失败。
7. **前端未挂载。** `app/` 下没有 `/digital-humans` 路由；详情页按钮跳转的编辑、关联助理、切换音色页面也不存在。`VideoGenerationDialog` 用原生 `fetch` 调 `/api/aigc/projects`，不带认证信息，请求体（`blueprintCode`、`initialSlots`）也不是 AIGC 项目接口的格式。
8. **关联助理未校验。** 不检查助理是否存在、是否属于当前用户，也未回写助理侧关联（代码中有 TODO）。
9. **Blueprint 初始化表单是 JSON 字段。** AIGC 项目初始化只允许 `description`、`styleGuidance`、`deliveryRequirements` 等固定字段，所以数字人 ID、目标时长、音色等放在 `deliveryRequirements` 的 JSON 里，没有专门的选择控件，也没有数字人引用选择器。
10. **种子字典与表约束不一致。** 字典里生成来源有 `manual/ai_generated/imported/template`，而表约束只允许 `aigc_project`。
11. **标签筛选未实现。** 标签为 JSONB，列表接口暂不支持按标签筛选。

## 本轮调整的依据

核对 AIGC 平台代码后修正的 v24 种子问题：

- 动作里的 `boundExecutionId`、`inputMapping`、`condition`、`costTracking` 没有任何读取方，已删除；执行目标改由 `aigc_execution_binding` 的 `action_key` 解析。
- 原三个执行器的 `INSERT` 使用了不存在的列（`code`、`name`、`config` 等），并依赖不存在的唯一约束，空库上会失败；已改为按 v7 列结构写入一条绑定。阿里云与 Hyperframes 不再各占一条绑定，它们是执行器内部的调用，地址与密钥属于运行配置。
- 槽位 `semanticRole` 使用了平台不认的值，交付集合的根不是 `COMPOSITE_MANIFEST`，项目初始化字段使用了非固定键，三者都会让 `AigcCatalogBlueprintCompiler` 拒绝，已改为平台约定。
- 项目类型与 Blueprint 的 `ON CONFLICT (code)` 无法匹配部分唯一索引，已改为不带列的 `ON CONFLICT DO NOTHING`。
- `AigcToolExecutionPort` 新增 `supports(targetRef)`，`AigcToolActionExecutor` 与 `AigcActionCommandService` 改为按端口判断目标，替换原来写死四个 `aigc.*` 目标的白名单。此前数字人执行器作为第二个 `AigcToolExecutionPort` Bean 会让单例注入失败。

## 未验证

- 应用启动，尤其是 `AigcToolActionExecutor` 的端口列表注入，以及 `CrudResourceCompiler` 对三个新资源定义的校验结果。
- `v19`、`v24` 在空库上的执行结果；Blueprint 经 `AigcCatalogBlueprintCompiler` 编译与发布校验的结果。
- 数字人专属 `video.generate` 绑定在创建项目时被选中（`priority = 10`），以及运行时 `digitalHumanId` 与 `scenes` 经 `actionArguments` 到达执行器。
- 后端单元测试与前端 `check:affected`。`AigcActionCommandServiceTest` 的构造参数已随新依赖调整，未运行。

补齐外部视频接口之前，不应把「视频生成」当作可用能力对外说明。
