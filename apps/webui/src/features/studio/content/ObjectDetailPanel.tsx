/**
 * Content Studio 对象详情、候选比较、RunTree 与作品面板。
 * @author AaronZZH & Kiro
 */

"use client"

import { GitCompareArrows } from "lucide-react"
import { useEffect, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { ApiError } from "@/lib/api/errors"
import type { AigcObjectVersionStatus, AigcProject, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import { useAdoptAigcObjectVersion, useAigcExecutionRuns, useAigcObjectVersions, useRejectAigcObjectVersion } from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"
import { CandidateComparisonDialog } from "./CandidateComparisonDialog"
import { ExecutionRunTree } from "./ExecutionRunTree"
import { OBJECT_STATUS_LABELS, OBJECT_TYPE_LABELS } from "./graph/graph-projection"
import { ObjectWorkPanel } from "./ObjectWorkPanel"
import { TimelinePanel } from "./TimelinePanel"

const VERSION_STATUS_LABELS: Record<AigcObjectVersionStatus, string> = {
  candidate: "候选",
  adopted: "已采用",
  rejected: "已否决",
  superseded: "已被取代"
}
const VERSION_STATUS_VARIANT = { candidate: "outline", adopted: "default", rejected: "destructive", superseded: "secondary" } as const

export interface ObjectDetailPanelProps {
  open: boolean
  project: AigcProject
  object?: AigcProjectObject
  lifecycleWritable: boolean
  canAction: boolean
  canAdopt: boolean
  canTimelineRead: boolean
  canTimelineCreate: boolean
  canTimelineUpdate: boolean
  canTimelineDelete: boolean
  onOpenChange: (open: boolean) => void
}

export function ObjectDetailPanel({
  open,
  project,
  object,
  lifecycleWritable,
  canAction,
  canAdopt,
  canTimelineRead,
  canTimelineCreate,
  canTimelineUpdate,
  canTimelineDelete,
  onOpenChange
}: ObjectDetailPanelProps) {
  const [compareIds, setCompareIds] = useState<number[]>([])
  const [compareOpen, setCompareOpen] = useState(false)
  const [adoptId, setAdoptId] = useState<number | null>(null)
  const [rejectId, setRejectId] = useState<number | null>(null)
  const [reason, setReason] = useState("")
  const [runPage, setRunPage] = useState(1)
  const objectId = object?.id ?? null
  const versionsQuery = useAigcObjectVersions(object?.projectId ?? null, objectId)
  const runsQuery = useAigcExecutionRuns(
    { projectId: project.id, objectId: objectId ?? undefined, rootOnly: true, pageNo: runPage, pageSize: 20 },
    object !== undefined
  )
  const adopt = useAdoptAigcObjectVersion()
  const reject = useRejectAigcObjectVersion()
  const versions = versionsQuery.data ?? []
  const runTrees = runsQuery.data?.list ?? []
  const runTotal = runsQuery.data?.total ?? 0
  const canRunAction = canAction && lifecycleWritable
  const canAdoptVersion = canAdopt && lifecycleWritable
  const selectedAdopt = versions.find((version) => version.id === adoptId)
  const replacing = object?.adoptedVersionId !== undefined && object.adoptedVersionId !== adoptId
  const timelineAvailable = object?.objectType === "video_deliverable" && canTimelineRead

  useEffect(() => {
    setCompareIds([])
    setCompareOpen(false)
    setAdoptId(null)
    setRejectId(null)
    setReason("")
    setRunPage(1)
  }, [objectId])

  function conflict(error: unknown) {
    if (error instanceof ApiError && error.code === 409) {
      notify.error("权威状态已变化，已刷新项目与候选版本，请重新选择")
      return
    }
    notify.error(error instanceof Error ? error.message : "操作失败")
  }

  function submitAdopt() {
    if (!canAdoptVersion || !object || adoptId === null || (replacing && !reason.trim())) return
    adopt.mutate({
      projectId: project.id,
      objectId: object.id,
      versionId: adoptId,
      expectedAdoptedVersionId: object.adoptedVersionId,
      expectedProjectVersion: project.version,
      confirmedReplacement: replacing,
      reason: replacing ? reason.trim() : undefined,
      idempotencyKey: crypto.randomUUID()
    }, {
      onSuccess: () => {
        setAdoptId(null)
        setReason("")
        notify.success(replacing ? "已替换采用版本" : "候选版本已采用")
      },
      onError: conflict
    })
  }

  function submitReject() {
    if (!canAdoptVersion || !object || rejectId === null || !reason.trim()) return
    reject.mutate({
      projectId: project.id,
      objectId: object.id,
      versionId: rejectId,
      expectedProjectVersion: project.version,
      reason: reason.trim(),
      idempotencyKey: crypto.randomUUID()
    }, {
      onSuccess: () => {
        setRejectId(null)
        setReason("")
        notify.success("候选版本已否决并记录原因")
      },
      onError: conflict
    })
  }

  function toggleCompare(id: number, checked: boolean) {
    setCompareIds((current) => checked ? [...current.filter((item) => item !== id), id].slice(-2) : current.filter((item) => item !== id))
  }

  return (
    <Sheet open={open && object !== undefined} onOpenChange={onOpenChange}>
      <SheetContent className="w-[96vw] sm:max-w-3xl">
        <SheetHeader>
          <SheetTitle>{object?.title || (object ? OBJECT_TYPE_LABELS[object.objectType] : "对象详情")}</SheetTitle>
          <SheetDescription>{object ? `${OBJECT_TYPE_LABELS[object.objectType]} · #${object.stableKey}${lifecycleWritable ? "" : ` · ${project.status} 阶段只读`}` : "查看项目对象"}</SheetDescription>
        </SheetHeader>

        {object ? (
          <Tabs defaultValue="detail" className="min-h-0 flex-1 px-4 pb-4">
            <TabsList className="w-full">
              <TabsTrigger value="detail">详情</TabsTrigger>
              <TabsTrigger value="versions">候选与版本</TabsTrigger>
              <TabsTrigger value="executions">RunTree</TabsTrigger>
              {object.objectType === "deliverable_set" ? <TabsTrigger value="work">作品</TabsTrigger> : null}
              {timelineAvailable ? <TabsTrigger value="timeline">时间线</TabsTrigger> : null}
            </TabsList>

            <TabsContent value="detail" className="min-h-0">
              <ScrollArea className="h-full pr-3">
                <dl className="grid grid-cols-[7rem_minmax(0,1fr)] gap-x-3 gap-y-3 rounded-xl border p-4 text-sm">
                  <dt className="text-muted-foreground">对象 ID</dt><dd>{object.id}</dd>
                  <dt className="text-muted-foreground">稳定 Key</dt><dd>{object.stableKey}</dd>
                  <dt className="text-muted-foreground">模板实例</dt><dd>{object.blueprintTemplateKey ? `${object.blueprintTemplateKey} / ${object.instanceNo ?? "-"}` : "自定义对象"}</dd>
                  <dt className="text-muted-foreground">合同角色</dt><dd>{object.contractRole ?? "-"}</dd>
                  <dt className="text-muted-foreground">状态</dt><dd>{OBJECT_STATUS_LABELS[object.status]}</dd>
                  <dt className="text-muted-foreground">采用版本</dt><dd>{object.adoptedVersionId ? `#${object.adoptedVersionId}` : "尚未采用"}</dd>
                  <dt className="text-muted-foreground">摘要</dt><dd>{object.summary || "暂无摘要"}</dd>
                </dl>
                <pre className="mt-4 max-h-80 overflow-auto rounded-xl border bg-muted/40 p-3 text-xs">{JSON.stringify(object.payload ?? {}, null, 2)}</pre>
              </ScrollArea>
            </TabsContent>

            <TabsContent value="versions" className="min-h-0">
              <ScrollArea className="h-full pr-3">
                {versionsQuery.isLoading ? <div className="flex flex-col gap-2"><Skeleton className="h-24" /><Skeleton className="h-24" /></div> : versions.length === 0 ? (
                  <Empty className="min-h-56"><EmptyHeader><EmptyTitle>暂无对象版本</EmptyTitle><EmptyDescription>执行成功后会先产生不可变候选版本。</EmptyDescription></EmptyHeader></Empty>
                ) : (
                  <div className="flex flex-col gap-3">
                    <div className="flex items-center justify-between gap-3 rounded-xl border bg-muted/20 p-3">
                      <p className="text-muted-foreground text-sm">勾选任意两个候选或采用版进行比较（{compareIds.length}/2）</p>
                      <Button type="button" variant="outline" size="sm" disabled={compareIds.length !== 2} onClick={() => setCompareOpen(true)}><GitCompareArrows />比较</Button>
                    </div>
                    {versions.map((version) => (
                      <div key={version.id} className="flex flex-col gap-2 rounded-xl border p-3">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div className="flex items-center gap-2">
                            <Checkbox checked={compareIds.includes(version.id)} aria-label={`选择版本 ${version.versionNo} 比较`} onCheckedChange={(checked) => toggleCompare(version.id, checked === true)} />
                            <p className="font-medium text-sm">版本 {version.versionNo}</p>
                            <Badge variant={VERSION_STATUS_VARIANT[version.status]}>{VERSION_STATUS_LABELS[version.status]}</Badge>
                          </div>
                          {version.status === "candidate" && canAdopt ? (
                            <div className="flex gap-2">
                              <Button type="button" variant="outline" size="xs" disabled={!canAdoptVersion || reject.isPending} onClick={() => { if (!canAdoptVersion) return; setReason(""); setRejectId(version.id) }}>否决</Button>
                              <Button type="button" size="xs" disabled={!canAdoptVersion || adopt.isPending} onClick={() => { if (!canAdoptVersion) return; setReason(""); setAdoptId(version.id) }}>{object.adoptedVersionId ? "替换采用" : "采用"}</Button>
                            </div>
                          ) : null}
                        </div>
                        <p className="text-muted-foreground text-sm">{version.summary || "暂无版本摘要"}</p>
                        <div className="flex flex-wrap gap-x-4 text-muted-foreground text-xs"><span>Run #{version.executionRunId ?? "-"}</span><span>{new Date(version.createTime).toLocaleString("zh-CN")}</span><span>{version.mediaVersionIds.length} 个 MediaVersion</span></div>
                      </div>
                    ))}
                  </div>
                )}
              </ScrollArea>
            </TabsContent>

            <TabsContent value="executions" className="min-h-0">
              <ScrollArea className="h-full pr-3">
                {runsQuery.isLoading ? <Skeleton className="h-52" /> : runTrees.length === 0 ? <Empty className="min-h-56"><EmptyHeader><EmptyTitle>暂无执行记录</EmptyTitle><EmptyDescription>该对象还没有关联 ExecutionRun。</EmptyDescription></EmptyHeader></Empty> : <div className="flex flex-col gap-3"><ExecutionRunTree roots={runTrees} canAction={canRunAction} /><div className="flex items-center justify-between"><span className="text-muted-foreground text-xs">Root {Math.min((runPage - 1) * 20 + 1, runTotal)}–{Math.min(runPage * 20, runTotal)} / {runTotal}</span><div className="flex gap-2"><Button type="button" size="xs" variant="outline" disabled={runPage <= 1 || runsQuery.isFetching} onClick={() => setRunPage((page) => Math.max(1, page - 1))}>上一页</Button><Button type="button" size="xs" variant="outline" disabled={runPage * 20 >= runTotal || runsQuery.isFetching} onClick={() => setRunPage((page) => page + 1)}>下一页</Button></div></div></div>}
              </ScrollArea>
            </TabsContent>

            {object.objectType === "deliverable_set" ? <TabsContent value="work" className="min-h-0"><ObjectWorkPanel project={project} object={object} /></TabsContent> : null}
            {timelineAvailable ? <TabsContent value="timeline" className="min-h-0"><TimelinePanel project={project} object={object} lifecycleWritable={lifecycleWritable} canCreate={canTimelineCreate} canUpdate={canTimelineUpdate} canDelete={canTimelineDelete} /></TabsContent> : null}
          </Tabs>
        ) : null}
      </SheetContent>

      {object ? <CandidateComparisonDialog open={compareOpen} projectId={project.id} objectId={object.id} leftVersionId={compareIds[0] ?? null} rightVersionId={compareIds[1] ?? null} onOpenChange={setCompareOpen} /> : null}

      <Dialog open={adoptId !== null && canAdoptVersion} onOpenChange={(next) => { if (!next) { setAdoptId(null); setReason("") } }}>
        <DialogContent><DialogHeader><DialogTitle>{replacing ? "确认替换采用版本" : "采用候选版本"}</DialogTitle><DialogDescription>{replacing ? `当前采用版 #${object?.adoptedVersionId} 将被版本 ${selectedAdopt?.versionNo ?? ""} 替换；CAS 冲突会刷新权威状态。` : "采用只移动当前对象的采用指针，不修改历史候选。"}</DialogDescription></DialogHeader>
          {replacing ? <Textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder="填写替换原因（必填）" /> : null}
          <DialogFooter><Button type="button" variant="outline" onClick={() => setAdoptId(null)}>取消</Button><Button type="button" disabled={adopt.isPending || (replacing && !reason.trim())} onClick={submitAdopt}>确认采用</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={rejectId !== null && canAdoptVersion} onOpenChange={(next) => { if (!next) { setRejectId(null); setReason("") } }}>
        <DialogContent><DialogHeader><DialogTitle>否决候选版本</DialogTitle><DialogDescription>否决不会删除版本，原因将进入审计记录。</DialogDescription></DialogHeader>
          <Textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder="填写否决原因（必填）" />
          <DialogFooter><Button type="button" variant="outline" onClick={() => setRejectId(null)}>取消</Button><Button type="button" variant="destructive" disabled={reject.isPending || !reason.trim()} onClick={submitReject}>确认否决</Button></DialogFooter>
        </DialogContent>
      </Dialog>
    </Sheet>
  )
}
