/**
 * Content Studio 对象详情、版本与执行诊断面板。
 *
 * 服务端对象、版本和运行记录只从 TanStack Query 读取；采用动作始终经过明确确认。
 * @author AaronZZH & Kiro
 */

"use client"

import { CircleStop, RefreshCw, RotateCcw } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle
} from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type {
  ContentExecutionStatus,
  ContentObjectVersionStatus,
  ContentProjectObjectVO
} from "@/lib/api/rest/content"
import {
  useAdoptContentObjectVersion,
  useCancelContentExecutionRun,
  useContentExecutionRuns,
  useContentObjectVersions,
  useRejectContentObjectVersion,
  useRetryContentExecutionRun
} from "@/lib/api/rest/content"
import { notify } from "@/lib/notification"
import { OBJECT_STATUS_LABELS, OBJECT_TYPE_LABELS } from "./graph/graph-projection"

const VERSION_STATUS_LABELS: Record<ContentObjectVersionStatus, string> = {
  candidate: "候选",
  adopted: "adopted",
  rejected: "已否决",
  superseded: "已被取代"
}

const VERSION_STATUS_VARIANT = {
  candidate: "outline",
  adopted: "default",
  rejected: "destructive",
  superseded: "secondary"
} as const

const EXECUTION_STATUS_LABELS: Record<ContentExecutionStatus, string> = {
  pending: "等待中",
  running: "执行中",
  succeeded: "已成功",
  failed: "失败",
  canceled: "已取消"
}

const EXECUTION_STATUS_VARIANT = {
  pending: "outline",
  running: "default",
  succeeded: "secondary",
  failed: "destructive",
  canceled: "outline"
} as const

export interface ObjectDetailPanelProps {
  open: boolean
  object?: ContentProjectObjectVO
  readOnly?: boolean
  onOpenChange: (open: boolean) => void
}

export function ObjectDetailPanel({
  open,
  object,
  readOnly = false,
  onOpenChange
}: ObjectDetailPanelProps) {
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null)
  const [selectedRunId, setSelectedRunId] = useState<number | null>(null)
  const objectId = object?.id ?? null
  const { data: versions = [], isLoading: versionsLoading } = useContentObjectVersions(objectId)
  const { data: runPage, isLoading: runsLoading } = useContentExecutionRuns(
    {
      objectId: object?.id,
      projectId: object?.projectId
    },
    object !== undefined
  )
  const adoptVersion = useAdoptContentObjectVersion()
  const rejectVersion = useRejectContentObjectVersion()
  const cancelRun = useCancelContentExecutionRun()
  const retryRun = useRetryContentExecutionRun()
  const runs = runPage?.list ?? []
  const selectedRun = runs.find((run) => run.id === selectedRunId)
  const selectedVersion = versions.find((version) => version.id === selectedVersionId)

  function handleAdopt() {
    if (readOnly || !object || selectedVersionId === null) return
    adoptVersion.mutate(
      { objectId: object.id, versionId: selectedVersionId },
      {
        onSuccess: () => {
          setSelectedVersionId(null)
          notify.success("候选版本已采用")
        }
      }
    )
  }

  function handleReject(versionId: number) {
    if (readOnly || !object) return
    rejectVersion.mutate(
      { objectId: object.id, versionId },
      { onSuccess: () => notify.success("候选版本已否决") }
    )
  }

  return (
    <Sheet open={open && object !== undefined} onOpenChange={onOpenChange}>
      <SheetContent className="w-[92vw] sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle>
            {object?.title || (object ? OBJECT_TYPE_LABELS[object.objectType] : "对象详情")}
          </SheetTitle>
          <SheetDescription>
            {object
              ? readOnly
                ? `${OBJECT_TYPE_LABELS[object.objectType]} · #${object.objectKey} · 项目已归档，采用、否决、取消与重试均已禁用`
                : `${OBJECT_TYPE_LABELS[object.objectType]} · #${object.objectKey}`
              : "查看项目对象"}
          </SheetDescription>
        </SheetHeader>

        {object ? (
          <Tabs defaultValue="detail" className="min-h-0 flex-1 px-4 pb-4">
            <TabsList className="w-full">
              <TabsTrigger value="detail">详情</TabsTrigger>
              <TabsTrigger value="versions">版本</TabsTrigger>
              <TabsTrigger value="executions">执行详情</TabsTrigger>
            </TabsList>

            <TabsContent value="detail" className="min-h-0">
              <ScrollArea className="h-full pr-3">
                <dl className="grid grid-cols-[7rem_minmax(0,1fr)] gap-x-3 gap-y-3 rounded-xl border p-4 text-sm">
                  <dt className="text-muted-foreground">对象 ID</dt>
                  <dd>{object.id}</dd>
                  <dt className="text-muted-foreground">对象类型</dt>
                  <dd>{OBJECT_TYPE_LABELS[object.objectType]}</dd>
                  <dt className="text-muted-foreground">状态</dt>
                  <dd>{OBJECT_STATUS_LABELS[object.status]}</dd>
                  <dt className="text-muted-foreground">来源</dt>
                  <dd>{object.source}</dd>
                  <dt className="text-muted-foreground">采用版本</dt>
                  <dd>{object.adoptedVersionRef || "尚未采用"}</dd>
                  <dt className="text-muted-foreground">摘要</dt>
                  <dd>{object.summary || "暂无摘要"}</dd>
                </dl>
                <div className="mt-4 flex flex-col gap-2">
                  <p className="font-medium text-sm">Payload 摘要</p>
                  <pre className="max-h-80 overflow-auto rounded-xl border bg-muted/40 p-3 text-xs">
                    {JSON.stringify(object.payload ?? {}, null, 2)}
                  </pre>
                </div>
              </ScrollArea>
            </TabsContent>

            <TabsContent value="versions" className="min-h-0">
              <ScrollArea className="h-full pr-3">
                {versionsLoading ? (
                  <div className="flex flex-col gap-2">
                    <Skeleton className="h-24 w-full" />
                    <Skeleton className="h-24 w-full" />
                  </div>
                ) : versions.length === 0 ? (
                  <Empty className="min-h-56">
                    <EmptyHeader>
                      <EmptyTitle>暂无对象版本</EmptyTitle>
                      <EmptyDescription>执行成功后会先在这里产生候选版本。</EmptyDescription>
                    </EmptyHeader>
                  </Empty>
                ) : (
                  <div className="flex flex-col gap-3">
                    {versions.map((version) => (
                      <div key={version.id} className="flex flex-col gap-2 rounded-xl border p-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <p className="font-medium text-sm">版本 {version.versionNo}</p>
                            <Badge variant={VERSION_STATUS_VARIANT[version.status]}>
                              {VERSION_STATUS_LABELS[version.status]}
                            </Badge>
                          </div>
                          {version.status === "candidate" ? (
                            <div className="flex gap-2">
                              <Button
                                type="button"
                                variant="outline"
                                size="xs"
                                disabled={readOnly || rejectVersion.isPending}
                                onClick={() => handleReject(version.id)}
                              >
                                否决
                              </Button>
                              <Button
                                type="button"
                                size="xs"
                                disabled={readOnly || adoptVersion.isPending}
                                onClick={() => setSelectedVersionId(version.id)}
                              >
                                采用
                              </Button>
                            </div>
                          ) : null}
                        </div>
                        <p className="text-muted-foreground text-sm">
                          {version.summary || "暂无版本摘要"}
                        </p>
                        <div className="flex flex-wrap gap-x-4 gap-y-1 text-muted-foreground text-xs">
                          <span>来源 ExecutionRun #{version.executionRunId ?? "-"}</span>
                          <span>{new Date(version.createTime).toLocaleString("zh-CN")}</span>
                          {version.supersededByVersionId ? (
                            <span>被版本 #{version.supersededByVersionId} 取代</span>
                          ) : null}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </ScrollArea>
            </TabsContent>

            <TabsContent value="executions" className="min-h-0">
              <ScrollArea className="h-full pr-3">
                {runsLoading ? (
                  <Skeleton className="h-40 w-full" />
                ) : runs.length === 0 ? (
                  <Empty className="min-h-56">
                    <EmptyHeader>
                      <EmptyTitle>暂无执行记录</EmptyTitle>
                      <EmptyDescription>该对象还没有关联的 ExecutionRun。</EmptyDescription>
                    </EmptyHeader>
                  </Empty>
                ) : (
                  <div className="flex flex-col gap-3">
                    <div className="flex flex-col gap-2">
                      {runs.map((run) => (
                        <Button
                          key={run.id}
                          type="button"
                          variant={selectedRun?.id === run.id ? "secondary" : "outline"}
                          className="h-auto w-full justify-between px-3 py-2"
                          onClick={() => setSelectedRunId(run.id)}
                        >
                          <span className="min-w-0 text-left">
                            <span className="block truncate">{run.actionKey}</span>
                            <span className="block text-muted-foreground text-xs">
                              {run.targetType} · #{run.id}
                            </span>
                          </span>
                          <Badge variant={EXECUTION_STATUS_VARIANT[run.status]}>
                            {EXECUTION_STATUS_LABELS[run.status]}
                          </Badge>
                        </Button>
                      ))}
                    </div>

                    {selectedRun ? (
                      <div className="flex flex-col gap-3 rounded-xl border p-4">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div>
                            <p className="font-medium">ExecutionRun #{selectedRun.id}</p>
                            <p className="text-muted-foreground text-xs">
                              {selectedRun.targetType} · {selectedRun.targetRef || "未记录目标"}
                            </p>
                          </div>
                          <div className="flex gap-2">
                            <Button
                              type="button"
                              variant="outline"
                              size="xs"
                              disabled={
                                readOnly ||
                                !["pending", "running"].includes(selectedRun.status) ||
                                cancelRun.isPending
                              }
                              onClick={() =>
                                cancelRun.mutate(selectedRun.id, {
                                  onSuccess: () => notify.success("执行已取消")
                                })
                              }
                            >
                              <CircleStop />
                              取消
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="xs"
                              disabled={
                                readOnly ||
                                !["failed", "canceled"].includes(selectedRun.status) ||
                                retryRun.isPending
                              }
                              onClick={() =>
                                retryRun.mutate(selectedRun.id, {
                                  onSuccess: () => notify.success("已创建重试执行")
                                })
                              }
                            >
                              {retryRun.isPending ? (
                                <RefreshCw className="animate-spin" />
                              ) : (
                                <RotateCcw />
                              )}
                              重试
                            </Button>
                          </div>
                        </div>
                        <dl className="grid grid-cols-[6rem_minmax(0,1fr)] gap-x-3 gap-y-2 text-sm">
                          <dt className="text-muted-foreground">动作</dt>
                          <dd>{selectedRun.actionKey}</dd>
                          <dt className="text-muted-foreground">状态</dt>
                          <dd>{EXECUTION_STATUS_LABELS[selectedRun.status]}</dd>
                          <dt className="text-muted-foreground">费用</dt>
                          <dd>{selectedRun.costCredits ?? "未结算"}</dd>
                          <dt className="text-muted-foreground">模型</dt>
                          <dd>{selectedRun.selectedModelVersion || "自动路由"}</dd>
                          <dt className="text-muted-foreground">错误</dt>
                          <dd className="break-words text-destructive">
                            {selectedRun.errorMessage || "无"}
                          </dd>
                          <dt className="text-muted-foreground">开始时间</dt>
                          <dd>{selectedRun.startTime || "未开始"}</dd>
                          <dt className="text-muted-foreground">结束时间</dt>
                          <dd>{selectedRun.endTime || "未结束"}</dd>
                        </dl>
                      </div>
                    ) : (
                      <p className="rounded-xl border border-dashed p-4 text-center text-muted-foreground text-sm">
                        选择一条 ExecutionRun 查看诊断详情
                      </p>
                    )}
                  </div>
                )}
              </ScrollArea>
            </TabsContent>
          </Tabs>
        ) : null}
      </SheetContent>

      <ConfirmDialog
        open={selectedVersionId !== null}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) setSelectedVersionId(null)
        }}
        title="采用此候选版本"
        description={`采用版本 ${selectedVersion?.versionNo ?? ""} 后将更新对象采用指针；其他执行结果不会自动移动该指针。`}
        confirmText="确认采用"
        onConfirm={handleAdopt}
      />
    </Sheet>
  )
}
