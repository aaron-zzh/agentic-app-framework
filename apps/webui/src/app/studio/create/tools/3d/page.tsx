/**
 * 工具-3D 模型生成
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Layers } from "lucide-react"
import { useState } from "react"
import { GlassCard, GlassCardBody, GlowButton } from "@/components/studio"
import { AssetCard } from "@/features/aigc/asset/AssetCard"
import type { MediaAssetVO } from "@/features/aigc/types"
import { request } from "@/lib/api/rest/entity/crud"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { notify } from "@/lib/notification"

interface TaskItem {
  id: number
  prompt: string
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAIL"
  ossUrl?: string
  errorMsg?: string
}

function taskToAsset(task: TaskItem): MediaAssetVO {
  return {
    id: task.id,
    name: task.prompt,
    type: "MODEL_3D",
    url: task.ossUrl ?? "",
    thumbnailUrl: null,
    createTime: new Date().toISOString(),
    generationParams: null,
    size: null,
    width: null,
    height: null,
    tags: null,
    groupId: null,
    groupName: null
  } as unknown as MediaAssetVO
}

const TEXTURE_OPTIONS = [
  { value: "none", label: "无贴图 ¥2.1起" },
  { value: "standard", label: "标清贴图 ¥2.8起" },
  { value: "detailed", label: "高清贴图 ¥3.5起" }
] as const

type TextureQuality = (typeof TEXTURE_OPTIONS)[number]["value"]

export default function Model3dToolPage() {
  const [prompt, setPrompt] = useState("")
  const [textureQuality, setTextureQuality] = useState<TextureQuality>("none")
  const [tasks, setTasks] = useState<TaskItem[]>([])

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "MODEL_3D",
          prompt: prompt.trim(),
          projectId: null,
          params: { source: "text", textureQuality }
        })
      }),
    onSuccess: (taskId) => {
      setTasks((prev) => [{ id: taskId, prompt: prompt.trim(), status: "PENDING" }, ...prev])
      setPrompt("")
      notify.success("3D 生成任务已提交")
    },
    onError: () => notify.error("提交失败")
  })

  useAigcTaskStream({
    onCompleted: (task) => {
      if (task.type !== "MODEL_3D") return
      setTasks((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: "SUCCESS", ossUrl: task.ossUrl } : t))
      )
      notify.success("3D 模型生成完成，素材已入库")
    },
    onFailed: (task) => {
      if (task.type !== "MODEL_3D") return
      setTasks((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: "FAIL", errorMsg: task.errorMsg } : t))
      )
    }
  })

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <header className="flex items-center gap-2">
        <Layers className="size-5 text-violet-400" />
        <h1 className="font-semibold text-xl">3D 模型生成</h1>
      </header>

      <GlassCard glow="violet">
        <GlassCardBody>
          <div className="space-y-4">
            <textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="描述你想要生成的 3D 模型（如：一只可爱的小猫，卡通风格）"
              className="min-h-[100px] w-full resize-none rounded-xl border border-foreground/[0.08] bg-foreground/[0.02] p-3 text-sm leading-6 outline-none placeholder:text-muted-foreground focus:border-violet-400/40"
              maxLength={3000}
            />
            <div className="flex items-center gap-3">
              <select
                value={textureQuality}
                onChange={(e) => setTextureQuality(e.target.value as TextureQuality)}
                className="rounded-lg border border-foreground/[0.08] bg-background px-3 py-2 text-sm"
              >
                {TEXTURE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
              <p className="ml-auto text-muted-foreground text-xs">{prompt.length}/3000</p>
              <GlowButton
                tone="violet"
                size="sm"
                disabled={isPending || !prompt.trim()}
                onClick={() => submit()}
              >
                <Layers className="size-4" />
                {isPending ? "提交中..." : "生成 3D"}
              </GlowButton>
            </div>
          </div>
        </GlassCardBody>
      </GlassCard>

      {tasks.length > 0 && (
        <div className="space-y-3">
          <p className="font-medium text-muted-foreground text-sm">生成记录</p>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
            {tasks.map((task) =>
              task.status === "SUCCESS" && task.ossUrl ? (
                <AssetCard
                  key={task.id}
                  asset={taskToAsset(task)}
                  onClick={() => {}}
                  onDelete={() => {}}
                  onRegenerate={() => {}}
                />
              ) : (
                <div
                  key={task.id}
                  className="flex aspect-square flex-col items-center justify-center gap-2 rounded-xl border border-foreground/[0.08] bg-foreground/[0.02] p-4 text-center"
                >
                  {task.status === "FAIL" ? (
                    <>
                      <span className="text-2xl text-destructive">✕</span>
                      <p className="line-clamp-2 text-destructive text-xs">
                        {task.errorMsg ?? "生成失败"}
                      </p>
                    </>
                  ) : (
                    <>
                      <div className="size-6 animate-spin rounded-full border-2 border-violet-400 border-t-transparent" />
                      <p className="line-clamp-2 text-muted-foreground text-xs">{task.prompt}</p>
                    </>
                  )}
                </div>
              )
            )}
          </div>
        </div>
      )}
    </div>
  )
}
