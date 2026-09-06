/**
 * Work 与 Publication 正式状态管理。
 * 普通用户只可创建、取消、重试，不可写入渠道发布结果。
 * @author AaronZZH & Kiro
 */

"use client"

import { Archive, CircleStop, ExternalLink, Library, Plus, RotateCcw, Send } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { GlassCard, GlassCardBody, SectionHaze } from "@/components/studio"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import type { AigcWork, AigcWorkPublication } from "@/lib/api/rest/ai/aigc"
import {
  useAigcChannelSpecs,
  useAigcProject,
  useAigcProjectChannelRefs,
  useAigcWorkPublications,
  useAigcWorks,
  useArchiveAigcWork,
  useCancelAigcPublication,
  usePublishAigcWork,
  useRetryAigcPublication
} from "@/lib/api/rest/ai/aigc"
import {
  AIGC_AUTHORITY_CODES,
  hasEntityCapability,
  useEntityAccess
} from "@/lib/api/rest/user/permission"
import { notify } from "@/lib/notification"
import { getChannelLabel } from "./project-type-config"

const STATUS_LABEL = { PENDING: "等待发布", SCHEDULED: "已排期", PUBLISHING: "发布中", SUCCEEDED: "已成功", FAILED: "发布失败", CANCELED: "已取消" } as const
const STATUS_VARIANT = { PENDING: "outline", SCHEDULED: "secondary", PUBLISHING: "default", SUCCEEDED: "secondary", FAILED: "destructive", CANCELED: "outline" } as const

type PublicationAction = { kind: "cancel" | "retry"; publication: AigcWorkPublication }

function PublicationRow({ work, publication, projectVersion, canPublish }: { work: AigcWork; publication: AigcWorkPublication; projectVersion: number | null; canPublish: boolean }) {
  const [action, setAction] = useState<PublicationAction | null>(null)
  const [reason, setReason] = useState("")
  const cancel = useCancelAigcPublication()
  const retry = useRetryAigcPublication()
  const active = ["PENDING", "SCHEDULED", "PUBLISHING"].includes(publication.status)

  function execute() {
    if (!canPublish || !action || projectVersion === null) return
    if (action.kind === "cancel") {
      if (!reason.trim()) return
      cancel.mutate({ workId: work.id, publicationId: publication.id, expectedProjectVersion: projectVersion, expectedWorkVersion: work.version, expectedPublicationVersion: publication.version, reason: reason.trim(), idempotencyKey: crypto.randomUUID() }, { onSuccess: () => { setAction(null); setReason(""); notify.success("Publication 已取消") } })
    } else {
      retry.mutate({ workId: work.id, publicationId: publication.id, expectedProjectVersion: projectVersion, expectedWorkVersion: work.version, expectedPublicationVersion: publication.version, idempotencyKey: crypto.randomUUID() }, { onSuccess: () => { setAction(null); notify.success("已创建新的关联重试 Publication，旧失败记录保留") } })
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-lg border p-3">
      <div className="flex flex-wrap items-start justify-between gap-2"><div><p className="font-medium text-sm">{getChannelLabel(publication.channelCode)}</p><p className="text-muted-foreground text-xs">Publication #{publication.id}{publication.retryOfPublicationId ? ` · retry-of #${publication.retryOfPublicationId}` : ""} · 重试 {publication.retryCount} 次</p></div><Badge variant={STATUS_VARIANT[publication.status]}>{STATUS_LABEL[publication.status]}</Badge></div>
      {publication.externalUrl ? <Button nativeButton={false} render={<Link href={publication.externalUrl} target="_blank" />} variant="link" className="w-fit px-0"><ExternalLink />查看渠道内容</Button> : null}
      {publication.status === "FAILED" ? <Alert variant="destructive"><AlertTitle>{publication.failureCode || "渠道发布失败"}</AlertTitle><AlertDescription>{publication.failureMessage || "渠道未返回可展示的失败说明"}{publication.responsePayload ? <pre className="mt-2 max-h-32 overflow-auto text-xs">{JSON.stringify(publication.responsePayload, null, 2)}</pre> : null}</AlertDescription></Alert> : null}
      {publication.cancelReason ? <p className="text-muted-foreground text-xs">取消原因：{publication.cancelReason}</p> : null}
      <div className="flex gap-2">{canPublish && active ? <Button type="button" size="xs" variant="outline" onClick={() => { if (!canPublish) return; setReason(""); setAction({ kind: "cancel", publication }) }}><CircleStop />取消</Button> : null}{canPublish && publication.status === "FAILED" ? <Button type="button" size="xs" variant="outline" onClick={() => { if (!canPublish) return; setAction({ kind: "retry", publication }) }}><RotateCcw />重试为新记录</Button> : null}</div>

      <Dialog open={action !== null && canPublish} onOpenChange={(open) => { if (!open) { setAction(null); setReason("") } }}><DialogContent><DialogHeader><DialogTitle>{action?.kind === "cancel" ? "取消 Publication" : "重试 Publication"}</DialogTitle><DialogDescription>{action?.kind === "cancel" ? "正在发布的渠道任务将请求取消，原因会保留在历史。" : "重试将创建新的 Publication，并通过 retry-of 保留失败链路。"}</DialogDescription></DialogHeader>{action?.kind === "cancel" ? <Textarea value={reason} onChange={(event) => setReason(event.target.value)} placeholder="填写取消原因（必填）" /> : null}<DialogFooter><Button type="button" variant="outline" onClick={() => setAction(null)}>返回</Button><Button type="button" disabled={(action?.kind === "cancel" && !reason.trim()) || cancel.isPending || retry.isPending} onClick={execute}>确认</Button></DialogFooter></DialogContent></Dialog>
    </div>
  )
}

function WorkCard({ work, canPublish, canArchive }: { work: AigcWork; canPublish: boolean; canArchive: boolean }) {
  const [publishOpen, setPublishOpen] = useState(false)
  const [archiveOpen, setArchiveOpen] = useState(false)
  const [channelSpecVersionId, setChannelSpecVersionId] = useState<number | null>(null)
  const [archiveReason, setArchiveReason] = useState("")
  const { data: project } = useAigcProject(work.projectId)
  const { data: publications = [], isLoading } = useAigcWorkPublications(work.id)
  const { data: channelPage } = useAigcChannelSpecs({ status: "published" })
  const { data: refs = [] } = useAigcProjectChannelRefs(work.projectId)
  const publish = usePublishAigcWork()
  const archive = useArchiveAigcWork()
  const bound = new Set(refs.map((ref) => ref.channelSpecId))
  const channels = (channelPage?.list ?? []).filter((channel) => bound.has(channel.id))
  const publishEnabled = canPublish && project?.status === "DELIVERING" && work.status !== "ARCHIVED"
  const archiveEnabled = canArchive && project?.status === "DELIVERING" && work.status !== "ARCHIVED"

  return (
    <GlassCard><GlassCardBody className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-medium">{work.title}</h2><p className="text-muted-foreground text-sm">Project #{work.projectId} · DeliverableSet #{work.deliverableSetObjectId} · Manifest #{work.manifestObjectVersionId}</p></div><Badge variant="secondary">{work.status}</Badge></div>
      <div className="flex flex-wrap gap-2"><Button nativeButton={false} render={<Link href={`/studio/projects/${work.projectId}?focus=${work.deliverableSetObjectId}`} />} variant="outline">返回项目 Manifest</Button>{canPublish ? <Button type="button" disabled={!publishEnabled} onClick={() => { if (publishEnabled) setPublishOpen(true) }}><Plus />新建发布</Button> : null}{canArchive ? <Button type="button" variant="ghost" disabled={!archiveEnabled} onClick={() => { if (archiveEnabled) setArchiveOpen(true) }}><Archive />归档 Work</Button> : null}</div>
      <div className="flex flex-col gap-3"><p className="font-medium text-sm">Publication 历史</p>{isLoading ? <Skeleton className="h-24" /> : publications.length === 0 ? <p className="rounded-lg border border-dashed p-4 text-center text-muted-foreground text-sm">尚无 Publication</p> : publications.map((publication) => <PublicationRow key={publication.id} work={work} publication={publication} projectVersion={project?.version ?? null} canPublish={publishEnabled} />)}</div>

      <Dialog open={publishOpen && publishEnabled} onOpenChange={setPublishOpen}><DialogContent><DialogHeader><DialogTitle>创建 Publication</DialogTitle><DialogDescription>仅创建正式发布请求；渠道结果由后端执行器写入，普通用户不可伪造成功。</DialogDescription></DialogHeader><Select value={channelSpecVersionId ? String(channelSpecVersionId) : ""} onValueChange={(value) => setChannelSpecVersionId(value ? Number(value) : null)}><SelectTrigger className="w-full"><SelectValue>选择项目绑定渠道</SelectValue></SelectTrigger><SelectContent><SelectGroup>{channels.map((channel) => <SelectItem key={channel.id} value={String(channel.id)}>{channel.name} · {channel.specVersion}</SelectItem>)}</SelectGroup></SelectContent></Select><DialogFooter><Button type="button" variant="outline" onClick={() => setPublishOpen(false)}>取消</Button><Button type="button" disabled={!channelSpecVersionId || publish.isPending} onClick={() => { if (!project || !publishEnabled || !channelSpecVersionId) return; publish.mutate({ workId: work.id, expectedProjectVersion: project.version, expectedWorkVersion: work.version, channelSpecVersionId, idempotencyKey: crypto.randomUUID() }, { onSuccess: () => { setPublishOpen(false); notify.success("Publication 已创建") } }) }}><Send />创建</Button></DialogFooter></DialogContent></Dialog>
      <Dialog open={archiveOpen && archiveEnabled} onOpenChange={setArchiveOpen}><DialogContent><DialogHeader><DialogTitle>归档 Work</DialogTitle><DialogDescription>Work 和全部 Publication 历史会保留，归档后不可继续发布。</DialogDescription></DialogHeader><Textarea value={archiveReason} onChange={(event) => setArchiveReason(event.target.value)} placeholder="填写归档原因（必填）" /><DialogFooter><Button type="button" variant="outline" onClick={() => setArchiveOpen(false)}>取消</Button><Button type="button" variant="destructive" disabled={!archiveReason.trim() || archive.isPending} onClick={() => { if (!project || !archiveEnabled || !archiveReason.trim()) return; archive.mutate({ workId: work.id, expectedProjectVersion: project.version, expectedWorkVersion: work.version, reason: archiveReason.trim(), idempotencyKey: crypto.randomUUID() }, { onSuccess: () => { setArchiveOpen(false); setArchiveReason(""); notify.success("Work 已归档") } }) }}>确认归档</Button></DialogFooter></DialogContent></Dialog>
    </GlassCardBody></GlassCard>
  )
}

export function WorksLibrary() {
  const { data: workAccess } = useEntityAccess("work")
  const canPublish = hasEntityCapability(workAccess, AIGC_AUTHORITY_CODES.WORK_PUBLISH)
  const canArchive = hasEntityCapability(workAccess, AIGC_AUTHORITY_CODES.WORK_ARCHIVE)
  const { data, isLoading } = useAigcWorks({ pageSize: 100 })
  const works = data?.list ?? []
  return <div className="relative h-full overflow-y-auto"><SectionHaze variant="violet" /><div className="relative mx-auto flex max-w-6xl flex-col gap-6 p-6"><header><h1 className="font-semibold text-xl">我的作品</h1><p className="text-muted-foreground text-sm">固定 approved、non-stale Manifest 的 Work 及不可变 Publication 历史。</p></header>{isLoading ? <div className="grid gap-4 lg:grid-cols-2"><Skeleton className="h-64" /><Skeleton className="h-64" /></div> : works.length === 0 ? <GlassCard glow="none"><Empty className="min-h-72"><EmptyHeader><EmptyMedia variant="icon"><Library /></EmptyMedia><EmptyTitle>还没有收录作品</EmptyTitle><EmptyDescription>在项目交付面板冻结 Manifest、审核通过后精确收录。</EmptyDescription></EmptyHeader></Empty></GlassCard> : <div className="grid items-start gap-4 lg:grid-cols-2">{works.map((work) => <WorkCard key={work.id} work={work} canPublish={canPublish} canArchive={canArchive} />)}</div>}</div></div>
}
