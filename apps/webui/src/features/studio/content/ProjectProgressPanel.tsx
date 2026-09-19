/**
 * 项目阶段一分层进度：Assistant Task、专业 Run、candidate、交付与实际积分互不合并。
 * @author AaronZZH & Kiro
 */

"use client"

import { AlertTriangle, CheckCircle2, Coins, ListChecks, PlayCircle, Shapes } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import type { AigcProjectProgress } from "@/lib/api/rest/ai/aigc"

const CANDIDATE_LABELS = {
  candidate: "待决策",
  adopted: "已采用",
  rejected: "已否决",
  superseded: "已取代"
} as const

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border bg-muted/20 p-3">
      <p className="text-muted-foreground text-xs">{label}</p>
      <p className="mt-1 font-semibold text-lg tabular-nums">{value}</p>
    </div>
  )
}

export function ProjectProgressPanel({ progress }: { progress: AigcProjectProgress }) {
  return (
    <section className="rounded-xl border">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
        <div>
          <h2 className="font-medium">分层进度证据</h2>
          <p className="text-muted-foreground text-xs">
            projection v{progress.projectionVersion} · project v{progress.projectVersion} · graph r
            {progress.graphRevision}
          </p>
        </div>
        <time className="text-muted-foreground text-xs" dateTime={progress.updatedAt}>
          {new Date(progress.updatedAt).toLocaleString("zh-CN")}
        </time>
      </header>

      <div className="grid gap-4 p-4 xl:grid-cols-2">
        <section className="rounded-xl border p-3">
          <h3 className="flex items-center gap-2 font-medium text-sm">
            <ListChecks /> Assistant Task
          </h3>
          {progress.currentTask === null ? (
            <Empty className="min-h-28 py-4">
              <EmptyHeader>
                <EmptyTitle>阶段一没有 Assistant Task</EmptyTitle>
                <EmptyDescription>项目不会伪造 Task，也不会自动推进项目状态。</EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : null}
        </section>

        <section className="rounded-xl border p-3">
          <h3 className="flex items-center gap-2 font-medium text-sm">
            <PlayCircle /> 专业 ExecutionRun
          </h3>
          {progress.executions.length === 0 ? (
            <p className="mt-3 text-muted-foreground text-sm">暂无专业 Run。</p>
          ) : (
            <div className="mt-3 flex flex-col gap-2">
              {progress.executions.map((run) => (
                <div key={run.executionRunId} className="rounded-lg border p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-medium text-sm">
                      {run.actionKey} · Run #{run.executionRunId}
                    </p>
                    <Badge variant="outline">{run.status}</Badge>
                  </div>
                  <p className="mt-1 text-muted-foreground text-xs">
                    对象 #{run.projectObjectId ?? "-"} · {run.targetType} · 预计{" "}
                    {run.estimatedCredits} · 预留 {run.reservedCredits}
                  </p>
                  {run.failureReason ? (
                    <p className="mt-1 text-destructive text-xs">{run.failureReason}</p>
                  ) : null}
                  {run.tasks.length > 0 ? (
                    <div className="mt-2 border-t pt-2">
                      <p className="mb-1 text-muted-foreground text-xs">专业媒体任务</p>
                      {run.tasks.map((task) => (
                        <p key={task.taskId} className="text-xs">
                          Task #{task.taskId} · {task.taskType} · {task.status} · MediaVersion #
                          {task.mediaVersionId ?? "-"}
                          {task.failureReason ? ` · ${task.failureReason}` : ""}
                        </p>
                      ))}
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="rounded-xl border p-3">
          <h3 className="flex items-center gap-2 font-medium text-sm">
            <Shapes /> Candidate 决策
          </h3>
          {progress.candidates.length === 0 ? (
            <p className="mt-3 text-muted-foreground text-sm">暂无 candidate。</p>
          ) : (
            <div className="mt-3 flex flex-col gap-2">
              {progress.candidates.map((candidate) => (
                <div
                  key={candidate.candidateVersionId}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-2 text-sm"
                >
                  <span>
                    ObjectVersion #{candidate.candidateVersionId} · 对象 #{candidate.objectId}
                  </span>
                  <Badge variant={candidate.status === "candidate" ? "outline" : "secondary"}>
                    {CANDIDATE_LABELS[candidate.status]}
                  </Badge>
                  <span className="w-full text-muted-foreground text-xs">
                    来源 Run #{candidate.executionRunId ?? "-"} · DocumentVersion #
                    {candidate.documentVersionId ?? "-"} · {candidate.mediaVersionIds.length}{" "}
                    个媒体版本
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="rounded-xl border p-3">
          <h3 className="flex items-center gap-2 font-medium text-sm">
            {progress.delivery.completionSatisfied ? <CheckCircle2 /> : <AlertTriangle />}
            交付状态
          </h3>
          <div className="mt-3 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
            <Metric label="Review" value={progress.delivery.reviewCount} />
            <Metric label="待审核" value={progress.delivery.pendingReviewCount} />
            <Metric label="Active Work" value={progress.delivery.activeWorkCount} />
            <Metric label="Publication" value={progress.delivery.publicationCount} />
          </div>
          {progress.delivery.blockers.length > 0 ? (
            <div className="mt-2 rounded-lg border border-destructive/40 bg-destructive/5 p-2 text-destructive text-xs">
              {progress.delivery.blockers.map((blocker) => (
                <p key={blocker}>• {blocker}</p>
              ))}
            </div>
          ) : null}
        </section>
      </div>

      <section className="border-t p-4">
        <h3 className="mb-3 flex items-center gap-2 font-medium text-sm">
          <Coins /> 实际积分证据
        </h3>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
          <Metric label="estimated" value={progress.credits.estimated} />
          <Metric label="reserved" value={progress.credits.reserved} />
          <Metric label="settled" value={progress.credits.settled} />
          <Metric label="refunded" value={progress.credits.refunded} />
          <Metric label="netActual" value={progress.credits.netActual} />
        </div>
        {progress.credits.unknownTaskIds.length > 0 ? (
          <p className="mt-2 text-amber-600 text-xs">
            尚无结算证据的成功任务：{progress.credits.unknownTaskIds.join("、")}；unknown =
            {progress.credits.unknown ?? "未知"}
          </p>
        ) : null}
      </section>
    </section>
  )
}
