"use client"
/**
 * AIGC 生成工具的内联 ToolUI——在对话气泡内渲染生成中占位和完成后的媒体内容
 *
 * 注册三个工具名：generate_image / generate_video / generate_music
 * 工具调用时显示占位骨架，任务完成后通过 useAigcTaskStream 更新为真实媒体
 */

import { useCallback, useState } from "react"
import { defineToolkit } from "@assistant-ui/react"
import { ImageIcon, MusicIcon, VideoIcon } from "lucide-react"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"

interface AigcToolResult {
  taskId?: number
  mediaType?: "image" | "video" | "music"
  status?: "PENDING" | "SUCCESS" | "FAIL"
  prompt?: string
  message?: string
  error?: string
}

/** 解析工具返回的 JSON 字符串 */
function parseResult(result: unknown): AigcToolResult | null {
  if (!result) return null
  try {
    const s = typeof result === "string" ? result : JSON.stringify(result)
    return JSON.parse(s) as AigcToolResult
  } catch {
    return null
  }
}

/** 单个 AIGC 任务卡片——监听 SSE 更新状态 */
function AigcTaskCard({ data }: { data: AigcToolResult }) {
  const [url, setUrl] = useState<string | undefined>()
  const [status, setStatus] = useState<"PENDING" | "SUCCESS" | "FAIL">(data.status ?? "PENDING")
  const taskId = data.taskId

  useAigcTaskStream({
    enabled: !!taskId && status === "PENDING",
    onCompleted: useCallback(
      (task) => {
        if (task.id === taskId) {
          setStatus("SUCCESS")
          setUrl(task.ossUrl ?? task.resultUrl)
        }
      },
      [taskId]
    ),
    onFailed: useCallback(
      (task) => {
        if (task.id === taskId) setStatus("FAIL")
      },
      [taskId]
    )
  })

  const mediaType = data.mediaType ?? "image"
  const prompt = data.prompt ?? ""

  if (status === "SUCCESS" && url) {
    if (mediaType === "image") {
      return (
        // biome-ignore lint/a11y/useAltText: 用户生成内容
        <img
          src={url}
          alt={prompt}
          className="my-2 max-h-80 w-auto rounded-lg object-contain shadow"
        />
      )
    }
    if (mediaType === "video") {
      return (
        // biome-ignore lint/a11y/useMediaCaption: 用户生成内容
        <video
          src={url}
          controls
          className="my-2 max-h-80 w-full rounded-lg shadow"
        />
      )
    }
    if (mediaType === "music") {
      return (
        <div className="my-2 flex items-center gap-2 rounded-lg bg-muted p-3">
          <MusicIcon className="size-4 shrink-0 text-muted-foreground" />
          {/* biome-ignore lint/a11y/useMediaCaption: 用户生成内容 */}
          <audio src={url} controls className="h-8 flex-1" />
        </div>
      )
    }
  }

  if (status === "FAIL") {
    return (
      <div className="my-2 flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-destructive text-sm">
        {mediaIcon(mediaType)}
        生成失败，请重试
      </div>
    )
  }

  // PENDING：骨架占位
  return (
    <div className="my-2 flex items-center gap-2 rounded-lg border bg-muted/50 px-3 py-2 text-muted-foreground text-sm">
      {mediaIcon(mediaType)}
      <span className="animate-pulse">{data.message ?? "生成中，请稍候…"}</span>
    </div>
  )
}

function mediaIcon(mediaType: string) {
  if (mediaType === "video") return <VideoIcon className="size-4 shrink-0" />
  if (mediaType === "music") return <MusicIcon className="size-4 shrink-0" />
  return <ImageIcon className="size-4 shrink-0" />
}

const renderAigcTool = ({ result }: { result?: unknown }) => {
  const data = parseResult(result)
  if (!data) return null
  return <AigcTaskCard data={data} />
}

export const aigcToolkit = defineToolkit({
  generate_image: { type: "backend", render: renderAigcTool },
  generate_video: { type: "backend", render: renderAigcTool },
  generate_music: { type: "backend", render: renderAigcTool }
})
