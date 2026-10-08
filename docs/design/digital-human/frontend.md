---
level: Practice
layer: Model
purpose: 说明数字人前端的目录结构、组件职责、数据流和权限展示规则
status: published
version: 2.0.0
date: 2026-10-08
author: AaronZZH & Kiro
tags:
  - 数字人
  - 前端
  - React Query
related:
  - ./api.md
  - ./system.md
scope:
  includes:
    - features/digital-human 的组件与数据流
    - 数字人 API 客户端的使用约定
  excludes:
    - 接口定义（见 api.md）
    - 视频生成执行器（见 video-generation/design.md）
gains:
  - 能找到数字人各页面和对话框对应的组件及其调用的接口
  - 能按「创建者才可操作」规则正确控制按钮显示
---

# 数字人前端

> 本文只描述当前实现。早期设计稿中的 `VoiceSelector`、`useDigitalHumanStats` 等组件和 Hook 没有实现，已删除。

## 目录结构

```text
apps/webui/src/
├── features/digital-human/
│   ├── components/
│   │   ├── DigitalHumanCard.tsx        数字人卡片（头像、音色、标签、使用次数）
│   │   ├── VoiceChangeDialog.tsx       切换音色，含语速、音调、音量
│   │   └── AssistantLinkDialog.tsx     关联助理
│   ├── management/
│   │   ├── DigitalHumanList.tsx        列表页：名称搜索、分页、删除
│   │   └── DigitalHumanDetail.tsx      详情页：概览、视频历史、音色、外观、使用统计
│   ├── video/
│   │   ├── VideoGenerationDialog.tsx   发起视频生成（简单、高级两种模式）
│   │   ├── SceneEditor.tsx             多场景编辑（拖拽排序）
│   │   ├── VideoHistoryList.tsx        视频历史
│   │   └── VideoPlayer.tsx             播放与下载
│   └── types/digital-human.ts          与后端 VO 对齐的类型
└── lib/api/rest/
    ├── digital-human.ts                数字人 API 与 React Query Hooks
    └── digital-human-video.ts          视频历史 API 与 Hooks
```

## 数据流

- 服务端状态只放在 TanStack Query，组件内不复制到 Zustand。
- 列表使用 `useDigitalHumans({ pageNo, pageSize, name, status })`，返回 `PageResult`，页数由 `total / pageSize` 计算。
- 详情使用 `useDigitalHuman(id)`，查询键 `["digital-human", id]`；所有变更成功后失效详情，变更列表数据的操作同时失效 `["digital-humans", "list"]`。
- 预设音色使用 `usePresetVoices()`：请求 `GET /api/preset-voices?isActive=true`，通过 `select` 取出 `list`，缓存 5 分钟。
- 更新使用 `useUpdateDigitalHuman()`，入参必须带当前 `version` 作为 `expectedVersion`。

## 权限展示

后端 VO 的 `ownedByCurrentUser` 决定是否展示编辑、删除、归档、恢复、切换音色、关联助理等操作；组织内的其他成员只能查看。该字段只用于展示，真正的校验在后端。

卡片上「组织共享」标记在 `ownedByCurrentUser` 为 `false` 时显示。

## 视频历史

视频历史接口仍返回 Spring `Page` 结构（`content`、`totalElements`…），与数字人列表的 `PageResult` 不同，见 `digital-human-video.ts`。

## 已知问题

- `app/` 下没有 `/digital-humans` 路由，列表和详情组件尚未挂载到页面。
- 详情页「编辑」「关联助理」「切换音色」三个按钮跳转到 `/digital-humans/{id}/edit` 等路径，这些页面不存在；对话框组件已有，但没有页面使用它们。
- `VideoGenerationDialog` 使用原生 `fetch("/api/aigc/projects")` 创建项目，没有走 `backendApi`，请求不会带认证信息，请求体字段（`blueprintCode`、`initialSlots`）也需要与当前 AIGC 项目接口核对。
- Studio 创建页的「数字人」模式目前只是不可提交的占位（见 AAF-100）。
