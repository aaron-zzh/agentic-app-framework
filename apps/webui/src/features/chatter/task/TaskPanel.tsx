/**
 * TaskPanel——展示当前对话的 canonical TaskDetails 安全投影。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import {
  Ban,
  ChevronDown,
  CircleCheckBig,
  CircleDashed,
  CircleX,
  ListChecks,
  ListTree,
  LoaderCircle,
  PauseCircle,
  ShieldAlert
} from "lucide-react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { Progress, ProgressLabel, ProgressValue } from "@/components/ui/progress"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import type { TaskListProgress } from "@/features/chatter/hooks/use-task-list"
import {
  isTaskTerminal,
  type TaskDetails,
  type TaskExecutionDetails,
  type TaskNodeDetails,
  type TaskStatus,
  taskApi,
  taskKeys
} from "@/lib/api/rest/ai"
import { cn } from "@/lib/utils"

const STATUS_LABEL: Record<TaskStatus, string> = {
  DRAFT: "草稿",
  PLANNING: "规划中",
  READY: "就绪",
  RUNNING: "执行中",
  VERIFYING: "验证中",
  AWAITING_AUTHORIZATION: "等待授权",
  AWAITING_CLARIFICATION: "等待澄清",
  PAUSING: "暂停中",
  PAUSED: "已暂停",
  CANCELING: "取消中",
  COMPLETED: "已完成",
  CANCELED: "已取消",
  FAILED: "失败"
}

function TaskStatusIcon({ status }: { status: TaskStatus }) {
  if (status === "COMPLETED") return <CircleCheckBig className="size-4 text-primary" />
  if (status === "FAILED") return <CircleX className="size-4 text-destructive" />
  if (status === "CANCELED") return <Ban className="size-4 text-muted-foreground" />
  if (status === "CANCELING" || status === "PAUSING")
    return <LoaderCircle className="size-4 animate-spin text-muted-foreground" />
  if (status === "PAUSED") return <PauseCircle className="size-4 text-muted-foreground" />
  if (status === "AWAITING_AUTHORIZATION" || status === "AWAITING_CLARIFICATION")
    return <ShieldAlert className="size-4 text-primary" />
  if (status === "RUNNING" || status === "VERIFYING" || status === "PLANNING")
    return <LoaderCircle className="size-4 animate-spin text-primary" />
  return <CircleDashed className="size-4 text-muted-foreground" />
}

function textStatus(status: string): string {
  return status.replaceAll("_", " ")
}

function NodeRow({ node }: { node: TaskNodeDetails }) {
  const executorSteps = useBoolean(false)
  const executorPlan = node.executorPlan

  return (
    <li className="flex flex-col gap-1 rounded-md bg-muted/40 p-2 text-xs">
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge variant="outline">{node.kind}</Badge>
        <span className="min-w-0 flex-1 font-medium">{node.description}</span>
        <Badge variant="secondary">{textStatus(node.status)}</Badge>
      </div>
      <p className="text-muted-foreground">
        节点 {node.nodeId} · 尝试 {node.attempts}/{node.maxAttempts}
      </p>
      {node.dependsOn.length > 0 ? (
        <p className="text-muted-foreground">依赖：{node.dependsOn.join(" → ")}</p>
      ) : null}
      {node.result ? <p className="break-words">结果：{node.result}</p> : null}
      {node.failure ? <p className="break-words text-destructive">失败：{node.failure}</p> : null}
      {executorPlan ? (
        <Collapsible open={executorSteps.value} onOpenChange={executorSteps.setValue}>
          <CollapsibleTrigger
            className="flex items-center gap-1 rounded-md py-1 text-muted-foreground hover:text-foreground"
            aria-label={executorSteps.value ? "收起节点执行步骤" : "展开节点执行步骤"}
          >
            <ListChecks className="size-3.5" />
            <span>{executorPlan.steps.length} 个执行步骤</span>
            <ChevronDown
              className={cn("size-3.5 transition-transform", executorSteps.value && "rotate-180")}
            />
          </CollapsibleTrigger>
          <CollapsibleContent className="flex flex-col gap-1.5 pt-1">
            <p className="text-muted-foreground">
              执行计划 r{executorPlan.revision} · {textStatus(executorPlan.status)}
            </p>
            <p className="break-words">目标：{executorPlan.goal}</p>
            <ol className="flex flex-col gap-1">
              {executorPlan.steps.map((step) => (
                <li
                  key={step.ordinal}
                  className="rounded-md border border-border/50 bg-background/60 p-2"
                >
                  <div className="flex items-center gap-1.5">
                    <span className="min-w-0 flex-1 font-medium">
                      {step.ordinal}. {step.title}
                    </span>
                    <Badge variant="outline">{textStatus(step.status)}</Badge>
                  </div>
                  {step.result ? <p className="mt-1 break-words">结果：{step.result}</p> : null}
                  {step.failure ? (
                    <p className="mt-1 break-words text-destructive">失败：{step.failure}</p>
                  ) : null}
                </li>
              ))}
            </ol>
          </CollapsibleContent>
        </Collapsible>
      ) : null}
    </li>
  )
}

function ExecutionRow({ execution }: { execution: TaskExecutionDetails }) {
  return (
    <li className="flex flex-col gap-1 rounded-md bg-muted/40 p-2 text-xs">
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge variant="outline">第 {execution.attemptNo} 次</Badge>
        <span className="min-w-0 flex-1 truncate" title={execution.executionId}>
          {execution.nodeId ?? execution.scope}
        </span>
        <Badge variant="secondary">{textStatus(execution.status)}</Badge>
      </div>
      <p className="text-muted-foreground">
        {execution.ownerKind} · 连续失败 {execution.consecutiveFailures} 次
      </p>
      {execution.latestDispatch ? (
        <p className="text-muted-foreground">
          Dispatch {execution.latestDispatch.status} · 投递{" "}
          {execution.latestDispatch.deliveryAttempts} 次 · 下次{" "}
          {new Date(execution.latestDispatch.nextRunAt).toLocaleString()}
        </p>
      ) : null}
    </li>
  )
}

function TaskItem({ task }: { task: TaskDetails }) {
  const details = useBoolean(false)
  const confirm = useBoolean(false)
  const pauseConfirm = useBoolean(false)
  const queryClient = useQueryClient()
  const pause = useMutation({
    mutationFn: () => taskApi.pause(task.taskId, "用户在任务面板暂停任务"),
    onSuccess: () => {
      toast.success("任务已进入暂停流程")
      void queryClient.invalidateQueries({ queryKey: taskKeys.all })
    },
    onError: () => toast.error("暂停任务失败，请重试")
  })
  const resume = useMutation({
    mutationFn: () => taskApi.resume(task.taskId),
    onSuccess: () => {
      toast.success("任务已恢复调度")
      void queryClient.invalidateQueries({ queryKey: taskKeys.all })
    },
    onError: () => toast.error("恢复任务失败，请重试")
  })
  const cancel = useMutation({
    mutationFn: () => taskApi.cancel(task.taskId, "用户在任务面板取消任务"),
    onSuccess: () => {
      toast.success("任务已进入取消流程")
      void queryClient.invalidateQueries({ queryKey: taskKeys.all })
    },
    onError: () => toast.error("取消任务失败，请重试")
  })

  return (
    <div className="border-border/50 border-b py-2 last:border-b-0">
      <Collapsible open={details.value} onOpenChange={details.setValue}>
        <div className="flex items-start gap-2">
          <TaskStatusIcon status={task.status} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm leading-tight" title={task.taskId}>
              {task.goal ?? `任务 ${task.taskId}`}
            </p>
            <p className="mt-0.5 text-muted-foreground text-xs">
              {task.ownerKind} · {task.controlMode} · 优先级 {task.priority}
            </p>
          </div>
          <Badge variant="outline">{STATUS_LABEL[task.status]}</Badge>
          <CollapsibleTrigger
            className="flex items-center gap-1 rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
            aria-label={details.value ? "收起任务详情" : "展开任务步骤"}
          >
            {task.plan ? (
              <>
                <ListTree className="size-4" />
                <span className="text-xs">{task.plan.nodes.length}</span>
              </>
            ) : null}
            <ChevronDown
              className={cn("size-4 transition-transform", details.value && "rotate-180")}
            />
          </CollapsibleTrigger>
        </div>

        <CollapsibleContent className="flex flex-col gap-2 pt-2 pl-6">
          <p className="text-muted-foreground text-xs">
            来源 {task.source} · 创建于 {new Date(task.createdAt).toLocaleString()}
          </p>
          {task.plan ? (
            <section className="flex flex-col gap-1.5" aria-label="任务计划">
              <div className="flex flex-wrap gap-1.5 text-xs">
                <Badge variant="secondary">Plan r{task.plan.revision}</Badge>
                <Badge variant="outline">{task.plan.status}</Badge>
                <span className="text-muted-foreground">
                  并行度 {task.plan.maxParallelism} · {task.plan.failurePolicy} ·{" "}
                  {task.plan.aggregationKind}
                </span>
              </div>
              <ul className="flex flex-col gap-1.5">
                {task.plan.nodes.map((node) => (
                  <NodeRow key={node.nodeId} node={node} />
                ))}
              </ul>
            </section>
          ) : null}
          {task.executions.length > 0 ? (
            <>
              <Separator />
              <section className="flex flex-col gap-1.5" aria-label="执行尝试">
                <p className="font-medium text-xs">执行尝试</p>
                <ul className="flex flex-col gap-1.5">
                  {task.executions.map((execution) => (
                    <ExecutionRow key={execution.executionId} execution={execution} />
                  ))}
                </ul>
              </section>
            </>
          ) : null}
          {!isTaskTerminal(task.status) && task.status !== "CANCELING" ? (
            <div className="flex justify-end gap-2">
              {task.status === "PAUSED" ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={resume.isPending}
                  onClick={() => resume.mutate()}
                >
                  <CircleDashed data-icon="inline-start" />
                  恢复任务
                </Button>
              ) : task.status !== "PAUSING" ? (
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={pause.isPending}
                  onClick={pauseConfirm.onTrue}
                >
                  <PauseCircle data-icon="inline-start" />
                  暂停任务
                </Button>
              ) : null}
              <Button
                type="button"
                size="sm"
                variant="outline"
                disabled={cancel.isPending}
                onClick={confirm.onTrue}
              >
                <Ban data-icon="inline-start" />
                取消任务
              </Button>
            </div>
          ) : null}
        </CollapsibleContent>
      </Collapsible>
      <ConfirmDialog
        open={pauseConfirm.value}
        onOpenChange={pauseConfirm.setValue}
        title="暂停任务"
        description="将立即关闭旧执行权并等待各执行保存恢复状态；保存失败或超时会安全降级为新的执行尝试。"
        confirmText="确认暂停"
        onConfirm={() => pause.mutate()}
      />
      <ConfirmDialog
        open={confirm.value}
        onOpenChange={confirm.setValue}
        title="取消任务"
        description="将立即关闭旧执行权并进入取消流程，由系统异步关闭节点与执行尝试；已完成事实仍会保留。"
        confirmText="确认取消"
        variant="destructive"
        onConfirm={() => cancel.mutate()}
      />
    </div>
  )
}

interface TaskPanelProps {
  tasks: TaskDetails[]
  progress: TaskListProgress
  isLoading?: boolean
}

export function TaskPanel({ tasks, progress, isLoading }: TaskPanelProps) {
  if (isLoading || tasks.length === 0) return null
  const completed = progress.byStatus.COMPLETED
  const percent = progress.total > 0 ? Math.round((completed / progress.total) * 100) : 0
  const groups = Object.entries(progress.byStatus).filter(([, count]) => count > 0) as [
    TaskStatus,
    number
  ][]

  return (
    <div className="border-border/50 border-t bg-muted/30 px-3 py-2">
      <Progress value={percent} className="mb-2">
        <ProgressLabel className="text-xs">
          持久任务 {completed}/{progress.total} 完成
        </ProgressLabel>
        <ProgressValue className="text-xs" />
      </Progress>
      <div className="mb-1 flex flex-wrap gap-1">
        {groups.map(([status, count]) => (
          <Badge key={status} variant="secondary">
            {STATUS_LABEL[status]} {count}
          </Badge>
        ))}
      </div>
      <ScrollArea className="h-72">
        <div className="pr-2">
          {tasks.map((task) => (
            <TaskItem key={task.taskId} task={task} />
          ))}
        </div>
      </ScrollArea>
    </div>
  )
}
