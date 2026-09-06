/**
 * ExecutionRun 树与恢复操作。
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown, ChevronRight, CircleStop, RotateCcw } from "lucide-react"
import type { ReactNode } from "react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import type {
  AigcExecutionRun,
  AigcExecutionRunView,
  AigcExecutionStatus
} from "@/lib/api/rest/ai/aigc"
import {
  useAigcExecutionRunTree,
  useCancelAigcExecutionRun,
  useRetryAigcExecutionRun
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"

const LABELS: Record<AigcExecutionStatus, string> = {
  PENDING_BIND: "绑定中",
  PENDING: "等待中",
  RUNNING: "执行中",
  PARTIALLY_SUCCEEDED: "部分成功",
  SUCCEEDED: "已成功",
  FAILED: "失败",
  CANCELED: "已取消"
}
const VARIANTS = {
  PENDING_BIND: "outline",
  PENDING: "outline",
  RUNNING: "default",
  PARTIALLY_SUCCEEDED: "secondary",
  SUCCEEDED: "secondary",
  FAILED: "destructive",
  CANCELED: "outline"
} as const
const ACTIVE: AigcExecutionStatus[] = ["PENDING_BIND", "PENDING", "RUNNING"]
const RETRYABLE: AigcExecutionStatus[] = ["PARTIALLY_SUCCEEDED", "FAILED", "CANCELED"]

type DisplayRun = AigcExecutionRun | AigcExecutionRunView
type RunChildren = ReadonlyMap<number, AigcExecutionRunView[]>

function runCredit(run: DisplayRun): number | undefined {
  return "creditCost" in run ? run.creditCost : run.costCredits
}

function runOutput(run: DisplayRun): string | undefined {
  return "output" in run ? run.output : undefined
}

function RunNode({
  run,
  childrenByParent,
  canAction,
  treeAction,
  loadingChildren,
  childrenError
}: {
  run: DisplayRun
  childrenByParent: RunChildren
  canAction: boolean
  treeAction?: ReactNode
  loadingChildren?: boolean
  childrenError?: boolean
}) {
  const children = childrenByParent.get(run.id) ?? []
  const cancel = useCancelAigcExecutionRun()
  const retry = useRetryAigcExecutionRun()
  const output = runOutput(run)

  return (
    <li className="flex flex-col gap-2 border-l pl-3">
      <div className="flex flex-col gap-2 rounded-xl border p-3">
        <div className="flex flex-wrap items-start justify-between gap-2">
          <div>
            <p className="font-medium text-sm">
              {run.actionKey} · Run #{run.id}
            </p>
            <p className="text-muted-foreground text-xs">
              {run.runKind} · root #{run.rootExecutionRunId ?? run.id}
              {run.parentExecutionRunId ? ` · parent #${run.parentExecutionRunId}` : ""}
            </p>
          </div>
          <Badge variant={VARIANTS[run.status]}>{LABELS[run.status]}</Badge>
        </div>
        <div className="flex flex-wrap gap-x-4 gap-y-1 text-muted-foreground text-xs">
          <span>graph r{run.targetGraphRevision}</span>
          <span>{run.frozenProjectObjectIds.length} 个冻结目标</span>
          <span>{run.taskIds.length} 个媒体 Task</span>
          <span>{run.retryOfExecutionRunId ? `retry-of #${run.retryOfExecutionRunId}` : "原始执行"}</span>
          <span>费用 {runCredit(run) ?? "-"}</span>
        </div>
        {output ? <p className="break-words text-muted-foreground text-xs">{output}</p> : null}
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            size="xs"
            disabled={!canAction || !ACTIVE.includes(run.status) || cancel.isPending}
            onClick={() => {
              if (!canAction || !ACTIVE.includes(run.status)) return
              cancel.mutate(
                { id: run.id, reason: "用户从 RunTree 取消" },
                { onSuccess: () => notify.success("执行已取消") }
              )
            }}
          >
            <CircleStop />取消
          </Button>
          <Button
            type="button"
            variant="outline"
            size="xs"
            disabled={!canAction || !RETRYABLE.includes(run.status) || retry.isPending}
            onClick={() => {
              if (!canAction || !RETRYABLE.includes(run.status)) return
              retry.mutate(run.id, {
                onSuccess: () => notify.success("已创建关联重试 Run")
              })
            }}
          >
            <RotateCcw />重试
          </Button>
          {treeAction}
        </div>
      </div>
      {loadingChildren ? <Skeleton className="ml-3 h-20" /> : null}
      {childrenError ? (
        <p className="ml-3 text-destructive text-xs">执行树加载失败，请收起后重试。</p>
      ) : null}
      {children.length > 0 ? (
        <ul className="ml-3 flex flex-col gap-2">
          {children.map((child) => (
            <RunNode
              key={child.id}
              run={child}
              childrenByParent={childrenByParent}
              canAction={canAction}
            />
          ))}
        </ul>
      ) : null}
    </li>
  )
}

function LazyRunTree({ root, canAction }: { root: AigcExecutionRun; canAction: boolean }) {
  const [expanded, setExpanded] = useState(false)
  const treeQuery = useAigcExecutionRunTree(root.id, expanded)
  const childrenByParent = new Map<number, AigcExecutionRunView[]>()
  if (expanded && treeQuery.data) {
    for (const run of treeQuery.data.descendants) {
      if (run.parentExecutionRunId === undefined || run.parentExecutionRunId === null) continue
      const children = childrenByParent.get(run.parentExecutionRunId) ?? []
      children.push(run)
      childrenByParent.set(run.parentExecutionRunId, children)
    }
  }

  return (
    <RunNode
      run={treeQuery.data?.root ?? root}
      childrenByParent={childrenByParent}
      canAction={canAction}
      loadingChildren={expanded && treeQuery.isLoading}
      childrenError={expanded && treeQuery.isError}
      treeAction={
        <Button
          type="button"
          variant="ghost"
          size="xs"
          aria-expanded={expanded}
          onClick={() => setExpanded((current) => !current)}
        >
          {expanded ? <ChevronDown /> : <ChevronRight />}
          {expanded ? "收起树" : "展开树"}
        </Button>
      }
    />
  )
}

export function ExecutionRunTree({
  roots,
  canAction
}: {
  roots: AigcExecutionRun[]
  canAction: boolean
}) {
  return (
    <ul className="flex flex-col gap-3">
      {roots.map((root) => (
        <LazyRunTree key={root.id} root={root} canAction={canAction} />
      ))}
    </ul>
  )
}
