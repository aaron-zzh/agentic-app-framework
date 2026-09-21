/**
 * AIGC 项目定时任务 Sheet 面板。
 * @author Kiro
 */

"use client"

import { CalendarClock, ExternalLink, Pause, Play, Plus, Trash2 } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle
} from "@/components/ui/sheet"
import { Textarea } from "@/components/ui/textarea"
import { cn } from "@/lib/utils"

interface ProjectSchedule {
  id: number
  executeAt: string // ISO 8601
  timezone: string
  prompt: string
  status: "PENDING" | "DISPATCHING" | "TRIGGERED" | "DEFERRED" | "CANCELLED" | "FAILED"
  linkedTaskId?: string
  lastError?: string
  failCount: number
  createdAt: string
}

interface ProjectScheduleSheetProps {
  projectId: number
  conversationId: string
  assistantId: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

const STATUS_CONFIG = {
  PENDING: { label: "等待触发", tone: "slate" as const },
  DISPATCHING: { label: "调度中", tone: "blue" as const },
  TRIGGERED: { label: "已触发", tone: "green" as const },
  DEFERRED: { label: "延期", tone: "orange" as const },
  CANCELLED: { label: "已取消", tone: "gray" as const },
  FAILED: { label: "失败", tone: "red" as const }
}

export function ProjectScheduleSheet({
  projectId,
  conversationId,
  assistantId,
  open,
  onOpenChange
}: ProjectScheduleSheetProps) {
  const [schedules, setSchedules] = useState<ProjectSchedule[]>([])
  const [loading, setLoading] = useState(false)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)
  const [formData, setFormData] = useState({
    executeAt: "",
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    prompt: ""
  })

  // Load schedules when sheet opens
  const loadSchedules = async () => {
    setLoading(true)
    try {
      const res = await fetch(`/api/aigc/projects/${projectId}/schedules`)
      const data = await res.json()
      if (data.success) {
        setSchedules(data.data)
      }
    } catch (_error) {
      toast.error("加载失败", {
        description: "无法加载定时任务列表"
      })
    } finally {
      setLoading(false)
    }
  }

  const handleCreate = async () => {
    if (!formData.executeAt || !formData.prompt.trim()) {
      toast.error("表单不完整", {
        description: "请填写执行时间和任务提示词"
      })
      return
    }

    try {
      const res = await fetch(`/api/aigc/projects/${projectId}/schedules`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          executeAt: new Date(formData.executeAt).toISOString(),
          timezone: formData.timezone,
          prompt: formData.prompt.trim(),
          assistantId,
          conversationId
        })
      })

      const data = await res.json()
      if (data.success) {
        toast.success("定时任务已创建", {
          description: `将在 ${formData.executeAt} 触发`
        })
        setCreateDialogOpen(false)
        setFormData({ executeAt: "", timezone: formData.timezone, prompt: "" })
        loadSchedules()
      } else {
        throw new Error(data.message)
      }
    } catch (error) {
      toast.error("创建失败", {
        description: error instanceof Error ? error.message : "未知错误"
      })
    }
  }

  const handlePause = async (scheduleId: number) => {
    try {
      const res = await fetch(`/api/aigc/projects/${projectId}/schedules/${scheduleId}/pause`, {
        method: "PUT"
      })
      if (res.ok) {
        toast.success("已暂停")
        loadSchedules()
      }
    } catch (_error) {
      toast.error("暂停失败")
    }
  }

  const handleResume = async (scheduleId: number) => {
    try {
      const res = await fetch(`/api/aigc/projects/${projectId}/schedules/${scheduleId}/resume`, {
        method: "PUT"
      })
      if (res.ok) {
        toast.success("已恢复")
        loadSchedules()
      }
    } catch (_error) {
      toast.error("恢复失败")
    }
  }

  const handleCancel = async (scheduleId: number) => {
    try {
      const res = await fetch(`/api/aigc/projects/${projectId}/schedules/${scheduleId}`, {
        method: "DELETE"
      })
      if (res.ok) {
        toast.success("已取消")
        loadSchedules()
      }
    } catch (_error) {
      toast.error("取消失败")
    }
  }

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent side="right" className="w-full sm:max-w-xl">
          <SheetHeader>
            <SheetTitle className="flex items-center gap-2">
              <CalendarClock className="size-5" />
              定时任务
            </SheetTitle>
            <SheetDescription>为项目设置一次性 AI 定时任务</SheetDescription>
          </SheetHeader>

          <div className="mt-6 space-y-4">
            <Button
              variant="outline"
              size="sm"
              className="w-full"
              onClick={() => {
                setCreateDialogOpen(true)
                if (schedules.length === 0) loadSchedules()
              }}
            >
              <Plus className="size-4" />
              新建定时任务
            </Button>

            {loading ? (
              <p className="text-center text-muted-foreground text-sm">加载中...</p>
            ) : schedules.length === 0 ? (
              <div className="rounded-lg border border-dashed p-8 text-center">
                <CalendarClock className="mx-auto size-12 text-muted-foreground/50" />
                <p className="mt-4 text-muted-foreground text-sm">暂无定时任务</p>
              </div>
            ) : (
              <div className="space-y-3">
                {schedules.map((schedule) => (
                  <ScheduleCard
                    key={schedule.id}
                    schedule={schedule}
                    onPause={handlePause}
                    onResume={handleResume}
                    onCancel={handleCancel}
                  />
                ))}
              </div>
            )}
          </div>
        </SheetContent>
      </Sheet>

      <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建定时任务</DialogTitle>
            <DialogDescription>
              设置任务触发时间和提示词，Assistant 将在指定时间自动执行
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="executeAt">执行时间</Label>
              <Input
                id="executeAt"
                type="datetime-local"
                value={formData.executeAt}
                onChange={(e) => setFormData({ ...formData, executeAt: e.target.value })}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="timezone">时区</Label>
              <Input
                id="timezone"
                value={formData.timezone}
                onChange={(e) => setFormData({ ...formData, timezone: e.target.value })}
                placeholder="Asia/Shanghai"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="prompt">任务提示词</Label>
              <Textarea
                id="prompt"
                rows={4}
                value={formData.prompt}
                onChange={(e) => setFormData({ ...formData, prompt: e.target.value })}
                placeholder="生成本周的小红书种草内容"
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={handleCreate}>创建</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function ScheduleCard({
  schedule,
  onPause,
  onResume,
  onCancel
}: {
  schedule: ProjectSchedule
  onPause: (id: number) => void
  onResume: (id: number) => void
  onCancel: (id: number) => void
}) {
  const status = STATUS_CONFIG[schedule.status]
  const canPause = schedule.status === "PENDING" || schedule.status === "DEFERRED"
  const canResume = schedule.status === "PENDING" || schedule.status === "DEFERRED"
  const isTerminal =
    schedule.status === "TRIGGERED" ||
    schedule.status === "CANCELLED" ||
    schedule.status === "FAILED"

  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex items-center gap-2">
            <Badge variant="secondary" className={cn("text-xs", `bg-${status.tone}-500/10`)}>
              {status.label}
            </Badge>
            {schedule.linkedTaskId && (
              <Button
                variant="ghost"
                size="sm"
                className="h-6 gap-1 px-2 text-xs"
                onClick={() => {
                  // TODO: 跳转到关联 Task
                  window.open(`/studio/tasks/${schedule.linkedTaskId}`, "_blank")
                }}
              >
                <ExternalLink className="size-3" />
                Task
              </Button>
            )}
          </div>
          <p className="text-sm">{schedule.prompt}</p>
          <p className="text-muted-foreground text-xs">
            {new Date(schedule.executeAt).toLocaleString("zh-CN", {
              timeZone: schedule.timezone,
              year: "numeric",
              month: "2-digit",
              day: "2-digit",
              hour: "2-digit",
              minute: "2-digit"
            })}{" "}
            ({schedule.timezone})
          </p>
          {schedule.lastError && (
            <p className="text-destructive text-xs">错误: {schedule.lastError}</p>
          )}
        </div>

        {!isTerminal && (
          <div className="flex gap-1">
            {canPause && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="暂停"
                onClick={() => onPause(schedule.id)}
              >
                <Pause className="size-4" />
              </Button>
            )}
            {canResume && (
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="恢复"
                onClick={() => onResume(schedule.id)}
              >
                <Play className="size-4" />
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="取消"
              onClick={() => onCancel(schedule.id)}
            >
              <Trash2 className="size-4" />
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
