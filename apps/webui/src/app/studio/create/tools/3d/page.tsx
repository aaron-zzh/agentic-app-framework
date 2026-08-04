/**
 * 工具-3D 模型生成。
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Layers } from "lucide-react"
import { useCallback, useState } from "react"
import { GlassCard, GlassCardBody, GlowButton } from "@/components/studio"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { request } from "@/lib/api/rest/entity"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { notify } from "@/lib/notification"

const TEXTURE_OPTIONS = [
  { value: "none", label: "无贴图 ¥2.1起" },
  { value: "standard", label: "标清贴图 ¥2.8起" },
  { value: "detailed", label: "高清贴图 ¥3.5起" }
] as const

type TextureQuality = (typeof TEXTURE_OPTIONS)[number]["value"]

export default function Model3dToolPage() {
  const [prompt, setPrompt] = useState("")
  const [textureQuality, setTextureQuality] = useState<TextureQuality>("none")
  const [tasks, setTasks] = useState<AigcTaskEvent[]>([])

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
    onSuccess: () => {
      setPrompt("")
      notify.success("3D 生成任务已提交")
    },
    onError: () => notify.error("提交失败")
  })

  useAigcTaskStream({
    onCreated: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "MODEL_3D") return
      setTasks((prev) => [task, ...prev.filter((item) => item.id !== task.id)])
    }, []),
    onCompleted: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "MODEL_3D") return
      setTasks((prev) => prev.map((item) => (item.id === task.id ? task : item)))
      notify.success("3D 模型生成完成，媒体已入库")
    }, []),
    onFailed: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "MODEL_3D") return
      setTasks((prev) => prev.map((item) => (item.id === task.id ? task : item)))
      notify.error(task.errorMsg ?? "生成失败")
    }, [])
  })

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6 p-6">
      <header className="flex items-center gap-2">
        <Layers className="text-violet-400" />
        <div>
          <h1 className="font-semibold text-xl">3D 模型生成</h1>
          <p className="text-muted-foreground text-sm">生成基础 3D 媒体，并可按需保存为资产</p>
        </div>
      </header>

      <GlassCard glow="violet">
        <GlassCardBody>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label htmlFor="model-3d-prompt">模型描述</Label>
              <Textarea
                id="model-3d-prompt"
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                placeholder="描述你想要生成的 3D 模型（如：一只可爱的小猫，卡通风格）"
                className="min-h-28 resize-none"
                maxLength={3000}
              />
            </div>
            <div className="flex items-end gap-3">
              <div className="flex flex-col gap-2">
                <Label htmlFor="texture-quality">贴图质量</Label>
                <Select
                  value={textureQuality}
                  onValueChange={(value) => value && setTextureQuality(value as TextureQuality)}
                >
                  <SelectTrigger id="texture-quality" className="w-48">
                    {TEXTURE_OPTIONS.find((option) => option.value === textureQuality)?.label}
                  </SelectTrigger>
                  <SelectContent>
                    {TEXTURE_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <p className="ml-auto text-muted-foreground text-xs">{prompt.length}/3000</p>
              <GlowButton
                tone="violet"
                size="sm"
                disabled={isPending || !prompt.trim()}
                onClick={() => submit()}
              >
                <Layers />
                {isPending ? "提交中..." : "生成 3D"}
              </GlowButton>
            </div>
          </div>
        </GlassCardBody>
      </GlassCard>

      <GenerationResultCard tasks={tasks} mediaType="MODEL_3D" />
    </div>
  )
}
