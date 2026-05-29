/**
 * 计划任务管理页面
 * @author AaronZZH & Kiro
 */

"use client"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { TypographyH1 } from "@/components/ui/typography"
import type { ScheduledTaskStatus } from "@/lib/api/scheduled-task"
import {
  useScheduledTaskPause,
  useScheduledTaskResume,
  useScheduledTaskRun,
  useScheduledTasks
} from "@/lib/queries/use-scheduled-tasks"

/** 状态标签样式 */
const STATUS_VARIANT: Record<ScheduledTaskStatus, "default" | "secondary" | "destructive"> = {
  active: "default",
  paused: "secondary",
  failed: "destructive"
}

const STATUS_LABEL: Record<ScheduledTaskStatus, string> = {
  active: "运行中",
  paused: "已暂停",
  failed: "已失败"
}

export default function ScheduledTasksPage() {
  const { data, isLoading } = useScheduledTasks()
  const pauseMutation = useScheduledTaskPause()
  const resumeMutation = useScheduledTaskResume()
  const runMutation = useScheduledTaskRun()

  return (
    <PageContainer>
      <TypographyH1 className="mb-6">计划任务管理</TypographyH1>

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>任务名</TableHead>
            <TableHead>类型</TableHead>
            <TableHead>Cron</TableHead>
            <TableHead>上次执行</TableHead>
            <TableHead>下次执行</TableHead>
            <TableHead>状态</TableHead>
            <TableHead>操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {isLoading && (
            <TableRow>
              <TableCell colSpan={7} className="text-center text-muted-foreground">
                加载中...
              </TableCell>
            </TableRow>
          )}
          {data?.map((task) => (
            <TableRow key={task.id}>
              <TableCell className="font-medium">{task.name}</TableCell>
              <TableCell>{task.type}</TableCell>
              <TableCell className="font-mono text-sm">{task.cron}</TableCell>
              <TableCell className="text-muted-foreground">
                {task.lastRun ? new Date(task.lastRun).toLocaleString("zh-CN") : "-"}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {task.nextRun ? new Date(task.nextRun).toLocaleString("zh-CN") : "-"}
              </TableCell>
              <TableCell>
                <Badge variant={STATUS_VARIANT[task.status]}>{STATUS_LABEL[task.status]}</Badge>
              </TableCell>
              <TableCell>
                <div className="flex gap-2">
                  {task.status === "active" && (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={pauseMutation.isPending}
                      onClick={() => pauseMutation.mutate(task.id)}
                    >
                      暂停
                    </Button>
                  )}
                  {(task.status === "paused" || task.status === "failed") && (
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={resumeMutation.isPending}
                      onClick={() => resumeMutation.mutate(task.id)}
                    >
                      恢复
                    </Button>
                  )}
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={runMutation.isPending}
                    onClick={() => runMutation.mutate(task.id)}
                  >
                    立即执行
                  </Button>
                </div>
              </TableCell>
            </TableRow>
          ))}
          {!isLoading && (!data || data.length === 0) && (
            <TableRow>
              <TableCell colSpan={7} className="text-center text-muted-foreground">
                暂无计划任务
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </PageContainer>
  )
}
