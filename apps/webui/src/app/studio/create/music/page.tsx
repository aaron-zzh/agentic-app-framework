/**
 * 创作-音乐生成
 *
 * 风格描述 + 可选歌词 + 音色 → 提交 MUSIC 任务 → SSE 监听进度 → 音频播放
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Music, Wand2 } from "lucide-react"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { request } from "@/lib/api/rest/entity/crud"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { notify } from "@/lib/notification"

const GENDERS = [
  { value: "female", label: "女声" },
  { value: "male", label: "男声" }
]

export default function MusicToolPage() {
  const [prompt, setPrompt] = useState("")
  const [lyrics, setLyrics] = useState("")
  const [gender, setGender] = useState("female")
  const [tasks, setTasks] = useState<AigcTaskEvent[]>([])

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "MUSIC",
          prompt: lyrics.trim() || prompt.trim(),
          projectId: null,

          params: { lyrics: lyrics.trim() || undefined, gender }
        })
      }),
    onSuccess: (taskId) => {
      setTasks((prev) => [
        {
          id: taskId,
          userId: 0,
          type: "MUSIC",
          prompt: lyrics.trim() || prompt.trim(),
          status: "PENDING",
          createTime: new Date().toISOString(),
          updateTime: new Date().toISOString()
        },
        ...prev
      ])
      setPrompt("")
      setLyrics("")
      notify.success("音乐生成任务已提交")
    },
    onError: () => notify.error("提交失败")
  })

  useAigcTaskStream({
    onProgress: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "MUSIC") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    onCompleted: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "MUSIC") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
      toast.success("音乐生成完成，素材已入库")
    }, []),
    onFailed: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "MUSIC") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
      toast.error("生成失败")
    }, [])
  })

  const canSubmit = !isPending && (prompt.trim().length > 0 || lyrics.trim().length > 0)
  const selectedGender = GENDERS.find((g) => g.value === gender)

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      {/* 标题区 */}
      <header className="space-y-2">
        <div className="flex items-center gap-2">
          <Music className="size-5 text-emerald-400" />
          <h1 className="font-semibold text-xl">AI 音乐生成</h1>
        </div>
        <p className="text-muted-foreground text-sm">AI 根据提示词作词作曲，生成完整歌曲</p>
      </header>

      {/* Composer 卡 */}
      <GlassCard glow="emerald">
        <div className="space-y-4 p-5">
          {/* 风格描述 */}
          <div className="space-y-2">
            <Label className="text-xs">音乐风格描述</Label>
            <Textarea
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="描述音乐风格、主题、情绪...（如：轻快的流行风格，关于夏天的歌曲）"
              className="max-h-[200px] min-h-[80px] resize-none overflow-y-auto border-foreground/[0.08] bg-foreground/[0.02]"
              maxLength={3000}
            />
          </div>

          {/* 歌词（可选） */}
          <div className="space-y-2">
            <Label className="text-xs">
              歌词
              <span className="ml-1 font-normal text-muted-foreground">
                （可选，填写后优先使用歌词）
              </span>
            </Label>
            <Textarea
              value={lyrics}
              onChange={(e) => setLyrics(e.target.value)}
              placeholder="在此输入歌词..."
              className="max-h-[200px] min-h-[80px] resize-none overflow-y-auto border-foreground/[0.08] bg-foreground/[0.02]"
              maxLength={3000}
            />
          </div>

          {/* 参数行 */}
          <div className="space-y-1.5">
            <Label className="text-muted-foreground text-xs">演唱音色</Label>
            <select
              value={gender}
              onChange={(e) => setGender(e.target.value)}
              className="w-full rounded-lg border border-foreground/[0.08] bg-background px-3 py-2 text-sm"
            >
              {GENDERS.map((g) => (
                <option key={g.value} value={g.value}>
                  {g.label}
                </option>
              ))}
            </select>
          </div>

          {/* 提交栏 */}
          <div className="flex items-center justify-between border-foreground/[0.06] border-t pt-3">
            <div className="flex items-center gap-2 text-muted-foreground text-xs">
              <span>当前音色：</span>
              {selectedGender ? (
                <NeonChip tone="emerald" size="sm">
                  {selectedGender.label}
                </NeonChip>
              ) : (
                <span className="opacity-50">—</span>
              )}
            </div>
            <GlowButton
              tone="emerald"
              size="default"
              onClick={() => submit()}
              disabled={!canSubmit}
            >
              <Wand2 className="size-4" />
              {isPending ? "提交中..." : "立即生成"}
            </GlowButton>
          </div>
        </div>
      </GlassCard>

      {/* 生成结果 */}
      <GenerationResultCard tasks={tasks} mediaType="AUDIO" />
    </div>
  )
}
