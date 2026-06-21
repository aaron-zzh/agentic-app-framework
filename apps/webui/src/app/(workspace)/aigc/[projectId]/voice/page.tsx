/**
 * 配音生成工作台——提交 TTS 配音任务，完成后素材自动入库并可在线预览播放
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Mic } from "lucide-react"
import { useParams } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { VOICE_TEXT_MAX_LEN as TEXT_MAX_LEN, VOICES } from "@/features/aigc/voice-options"
import { request } from "@/lib/api/rest/entity/crud"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"

interface TaskItem {
  id: number
  prompt: string
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAIL"
  ossUrl?: string
  errorMsg?: string
}

export default function VoiceGenerationPage() {
  const routeParams = useParams()
  const projectId = routeParams.projectId ? Number(routeParams.projectId) : null
  const [text, setText] = useState("")
  const [voice, setVoice] = useState<string>(VOICES[0].value)
  const [tasks, setTasks] = useState<TaskItem[]>([])

  const trimmed = text.trim()
  const overLimit = text.length > TEXT_MAX_LEN

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "VOICE",
          prompt: trimmed,
          projectId: projectId ?? null,
          params: { voice }
        })
      }),
    onSuccess: (taskId) => {
      setTasks((prev) => [{ id: taskId, prompt: trimmed, status: "PENDING" }, ...prev])
      setText("")
      toast.success("配音生成任务已提交")
    },
    onError: () => {}
  })

  useAigcTaskStream({
    onProgress: (task) => {
      if (task.type !== "VOICE") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? { ...t, status: "RUNNING" } : t)))
    },
    onCompleted: (task) => {
      if (task.type !== "VOICE") return
      setTasks((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: "SUCCESS", ossUrl: task.ossUrl } : t))
      )
      toast.success("配音生成完成，素材已入库")
    },
    onFailed: (task) => {
      if (task.type !== "VOICE") return
      setTasks((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: "FAIL", errorMsg: task.errorMsg } : t))
      )
    }
  })

  return (
    <div className="flex h-full flex-col gap-6 p-6">
      <div>
        <h1 className="font-bold text-2xl">配音生成</h1>
        <p className="text-muted-foreground text-sm">输入文本，AI 合成自然配音，完成后自动入库</p>
      </div>

      {/* 输入区 */}
      <Card className="space-y-4 p-5">
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <Label>配音文本</Label>
            <span
              className={overLimit ? "text-destructive text-xs" : "text-muted-foreground text-xs"}
            >
              {text.length}/{TEXT_MAX_LEN}
            </span>
          </div>
          <Textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={TEXT_MAX_LEN}
            placeholder="输入需要配音的文本内容（最多 200 字）..."
            className="min-h-[120px] resize-none"
          />
        </div>

        <div className="flex items-center gap-4">
          <div className="space-y-1">
            <Label className="text-muted-foreground text-xs">音色</Label>
            <Select value={voice} onValueChange={(v) => v && setVoice(v)}>
              <SelectTrigger className="h-8 w-40 text-xs">
                <span>{VOICES.find((v) => v.value === voice)?.label ?? voice}</span>
              </SelectTrigger>
              <SelectContent>
                {VOICES.map((v) => (
                  <SelectItem key={v.value} value={v.value}>
                    {v.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Button
            className="ml-auto gap-2"
            disabled={isPending || !trimmed || overLimit}
            onClick={() => submit()}
          >
            <Mic className="size-4" />
            {isPending ? "提交中..." : "生成配音"}
          </Button>
        </div>
      </Card>

      {/* 任务列表 */}
      {tasks.length > 0 && (
        <div className="space-y-3">
          <h2 className="font-medium text-muted-foreground text-sm">生成记录</h2>
          {tasks.map((task) => (
            <Card key={task.id} className="flex items-center gap-4 p-4">
              <Mic className="size-8 shrink-0 text-muted-foreground" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">{task.prompt}</p>
                <p className="mt-0.5 text-muted-foreground text-xs">
                  {task.status === "PENDING" && "等待中..."}
                  {task.status === "RUNNING" && "合成中..."}
                  {task.status === "SUCCESS" && "已完成，素材已入库"}
                  {task.status === "FAIL" && `失败：${task.errorMsg ?? "未知错误"}`}
                </p>
              </div>
              {task.status === "SUCCESS" && task.ossUrl && (
                <audio controls src={task.ossUrl} className="h-8 w-48 shrink-0">
                  <track kind="captions" />
                </audio>
              )}
              {(task.status === "PENDING" || task.status === "RUNNING") && (
                <div className="size-4 shrink-0 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
