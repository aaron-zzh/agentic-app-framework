/**
 * Studio 首页创作区：直接嵌入创作页的统一媒体生成工作台（图像、视频、配音、音乐、3D），在首页提交生成。
 *
 * 工作台自带发光外框，不再套座舱面板。嵌入模式下切换类型不跳转创作页，
 * 提交后的进度在右下角任务面板查看，结果出现在首页底部"最近生成"。
 * @author AaronZZH & Kiro
 */

"use client"

import { MediaGenerationWorkspace } from "@/features/studio/media-generation/MediaGenerationWorkspace"

export function HomeCreate() {
  return (
    <section aria-label="创作" className="min-w-0">
      <MediaGenerationWorkspace embedded />
    </section>
  )
}
