/**
 * 音乐生成工作台——提交音乐生成任务，完成后素材自动入库
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Music } from "lucide-react"
import { useParams } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { request } from "@/lib/api/rest/entity/crud"
import { useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"

interface TaskItem {
  id: number
  prompt: string
  status: "PENDING" | "RUNNING" | "SUCCESS" | "FAIL"
  ossUrl?: string
  errorMsg?: string
}

export default function MusicGenerationPage() {
  const routeParams = useParams()
  const projectId = routeParams.projectId ? Number(routeParams.projectId) : null
  const [prompt, setPrompt] = useState("")
  const [lyrics, setLyrics] = useState("")
  const [gender, setGender] = useState("female")
  const [tasks, setTasks] = useState<TaskItem[]>([])

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "MUSIC",
          prompt: lyrics.trim() || prompt.trim(),
          projectId: projectId ?? null,
          params: { lyrics: lyrics.trim() || undefined, gender }
        })
      }),
    onSuccess: (taskId) => {
      setTasks((prev) => [
        { id: taskId, prompt: lyrics.trim() || prompt.trim(), status: "PENDING" },
        ...prev
      ])
      setPrompt("")
      setLyrics("")
      toast.success("音乐生成任务已提交")
    },
    onError: (err) => toast.error((err as Error).message ?? "提交失败")
  })

  useAigcTaskStream({
    onProgress: (task) => {
      if (task.type !== "MUSIC") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? { ...t, status: "RUNNING" } : t)))
    },
    onCompleted: (task) => {
      if (task.type !== "MUSIC") return
      setTasks((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: "SUCCESS", ossUrl: task.ossUrl } : t))
      )
      toast.success("音乐生成完成")
    },
    onFailed: (task) => {
      if (task.type !== "MUSIC") return
      setTasks((prev) =>
        prev.map((t) => (t.id === task.id ? { ...t, status: "FAIL", errorMsg: task.errorMsg } : t))
      )
    }
  })

  return (
    <div className="flex h-full flex-col gap-6 p-6">
      <div>
        <h1 className="font-bold text-2xl">音乐生成</h1>
        <p className="text-muted-foreground text-sm">AI 根据提示词作词作曲，生成完整歌曲</p>
      </div>

      {/* 输入区 */}
      <Card className="space-y-4 p-5">
        <div className="space-y-2">
          <Label>创作提示词</Label>
          <Textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="描述你想要的音乐风格、主题、情绪...（如：轻快的流行风格，关于夏天的歌曲）"
            className="min-h-[80px] resize-none"
          />
        </div>

        <div className="space-y-2">
          <Label>歌词（可选，填写后优先使用歌词）</Label>
          <Textarea
            value={lyrics}
            onChange={(e) => setLyrics(e.target.value)}
            placeholder="直接填写歌词内容，AI 将根据歌词生成音乐..."
            className="min-h-[80px] resize-none"
          />
        </div>

        <div className="flex items-center gap-4">
          <div className="space-y-1">
            <Label className="text-muted-foreground text-xs">演唱声音</Label>
            <Select value={gender} onValueChange={(v) => v && setGender(v)}>
              <SelectTrigger className="h-8 w-28 text-xs">
                <span>{gender === "female" ? "女声" : "男声"}</span>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="female">女声</SelectItem>
                <SelectItem value="male">男声</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Button
            className="ml-auto gap-2"
            disabled={isPending || (!prompt.trim() && !lyrics.trim())}
            onClick={() => submit()}
          >
            <Music className="size-4" />
            {isPending ? "提交中..." : "生成音乐"}
          </Button>
        </div>
      </Card>

      {/* 任务列表 */}
      {tasks.length > 0 && (
        <div className="space-y-3">
          <h2 className="font-medium text-muted-foreground text-sm">生成记录</h2>
          {tasks.map((task) => (
            <Card key={task.id} className="flex items-center gap-4 p-4">
              <Music className="size-8 shrink-0 text-muted-foreground" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">{task.prompt}</p>
                <p className="mt-0.5 text-muted-foreground text-xs">
                  {task.status === "PENDING" && "等待中..."}
                  {task.status === "RUNNING" && "生成中..."}
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
