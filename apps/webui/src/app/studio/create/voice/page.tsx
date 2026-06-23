/**
 * 创作-配音生成
 *
 * 输入文本 + 选音色 → 提交 VOICE 任务 → SSE 监听进度 → 音频播放
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Mic, Wand2 } from "lucide-react"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { GenerationResultCard } from "@/features/aigc/generation/GenerationResultCard"
import { VOICE_TEXT_MAX_LEN as TEXT_MAX_LEN, VOICES } from "@/features/aigc/voice-options"
import { request } from "@/lib/api/rest/entity/crud"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { notify } from "@/lib/notification"

export default function VoiceToolPage() {
  const [text, setText] = useState("")
  const [voice, setVoice] = useState<string>(VOICES[0].value)
  const [tasks, setTasks] = useState<AigcTaskEvent[]>([])

  const trimmed = text.trim()
  const overLimit = text.length > TEXT_MAX_LEN

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({ type: "VOICE", prompt: trimmed, projectId: null, params: { voice } })
      }),
    onSuccess: (taskId) => {
      setTasks((prev) => [
        {
          id: taskId,
          userId: 0,
          type: "VOICE",
          prompt: trimmed,
          status: "PENDING",
          createTime: new Date().toISOString(),
          updateTime: new Date().toISOString()
        },
        ...prev
      ])
      setText("")
      notify.success("配音生成任务已提交")
    },
    onError: () => notify.error("提交失败")
  })

  useAigcTaskStream({
    onProgress: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "VOICE") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    onCompleted: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "VOICE") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
      toast.success("配音生成完成，素材已入库")
    }, []),
    onFailed: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "VOICE") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
      toast.error("生成失败")
    }, [])
  })

  const selectedVoice = VOICES.find((v) => v.value === voice)

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      {/* 标题区 */}
      <header className="space-y-2">
        <div className="flex items-center gap-2">
          <Mic className="size-5 text-rose-400" />
          <h1 className="font-semibold text-xl">AI 配音生成</h1>
        </div>
        <p className="text-muted-foreground text-sm">输入文本，AI 合成自然配音，支持多种音色</p>
      </header>

      {/* Composer 卡 */}
      <GlassCard glow="rose">
        <div className="space-y-4 p-5">
          {/* 文本输入 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs">配音文本</Label>
              <span
                className={`text-xs ${overLimit ? "text-destructive" : "text-muted-foreground"}`}
              >
                {text.length}/{TEXT_MAX_LEN}
              </span>
            </div>
            <Textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              maxLength={TEXT_MAX_LEN}
              placeholder="输入需要配音的文本内容（最多 200 字）..."
              className="max-h-[200px] min-h-[120px] resize-none overflow-y-auto border-foreground/[0.08] bg-foreground/[0.02]"
            />
          </div>

          {/* 参数行 */}
          <div className="space-y-1.5">
            <Label className="text-muted-foreground text-xs">音色</Label>
            <select
              value={voice}
              onChange={(e) => setVoice(e.target.value)}
              className="w-full rounded-lg border border-foreground/[0.08] bg-background px-3 py-2 text-sm"
            >
              {VOICES.map((v) => (
                <option key={v.value} value={v.value}>
                  {v.label}
                </option>
              ))}
            </select>
          </div>

          {/* 提交栏 */}
          <div className="flex items-center justify-between border-foreground/[0.06] border-t pt-3">
            <div className="flex items-center gap-2 text-muted-foreground text-xs">
              <span>当前音色：</span>
              {selectedVoice ? (
                <NeonChip tone="rose" size="sm">
                  {selectedVoice.label}
                </NeonChip>
              ) : (
                <span className="opacity-50">—</span>
              )}
            </div>
            <GlowButton
              tone="rose"
              size="default"
              onClick={() => submit()}
              disabled={isPending || !trimmed || overLimit}
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
