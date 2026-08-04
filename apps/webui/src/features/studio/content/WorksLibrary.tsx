/**
 * AIGC Work 作品库与 Publication 最小管理界面。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { Archive, ExternalLink, Library, Plus, Send } from "lucide-react"
import Link from "next/link"
import { useId, useState } from "react"
import { GlassCard, GlassCardBody, SectionHaze } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import type { AigcPublicationStatus, AigcWork, AigcWorkPublication } from "@/lib/api/rest/ai/aigc"
import {
  useAigcChannelSpecs,
  useAigcProjectChannelRefs,
  useAigcWorkPublications,
  useAigcWorks,
  useArchiveAigcWork,
  usePublishAigcWork,
  useUpdateAigcPublication
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"
import { getChannelLabel } from "./project-type-config"

const PUBLICATION_VARIANT = {
  pending: "outline",
  scheduled: "secondary",
  publishing: "default",
  published: "secondary",
  failed: "destructive",
  canceled: "outline"
} as const

const RESULT_STATUSES: AigcPublicationStatus[] = ["publishing", "published", "failed", "canceled"]

function PublicationEditor({ publication }: { publication: AigcWorkPublication }) {
  const [status, setStatus] = useState<AigcPublicationStatus>(
    RESULT_STATUSES.includes(publication.status) ? publication.status : "publishing"
  )
  const [externalUrl, setExternalUrl] = useState(publication.externalUrl ?? "")
  const update = useUpdateAigcPublication()

  return (
    <div className="flex flex-col gap-3 rounded-lg border p-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <p className="font-medium text-sm">{getChannelLabel(publication.channelCode)}</p>
          <p className="text-muted-foreground text-xs">Publication #{publication.id}</p>
        </div>
        <Badge variant={PUBLICATION_VARIANT[publication.status]}>{publication.status}</Badge>
      </div>
      {publication.externalUrl ? (
        <Button
          nativeButton={false}
          render={<Link href={publication.externalUrl} target="_blank" />}
          variant="link"
          className="w-fit px-0"
        >
          <ExternalLink /> 查看已发布内容
        </Button>
      ) : null}
      {publication.status !== "published" ? (
        <div className="grid gap-2 sm:grid-cols-[10rem_minmax(0,1fr)_auto]">
          <Select
            value={status}
            onValueChange={(value) => value && setStatus(value as AigcPublicationStatus)}
          >
            <SelectTrigger>
              <SelectValue>{status}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {RESULT_STATUSES.map((item) => (
                  <SelectItem key={item} value={item}>
                    {item}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          <Input
            value={externalUrl}
            onChange={(event) => setExternalUrl(event.target.value)}
            placeholder="渠道结果 URL（可选）"
          />
          <Button
            type="button"
            disabled={update.isPending}
            onClick={() =>
              update.mutate(
                {
                  workId: publication.workId,
                  publicationId: publication.id,
                  status,
                  externalUrl: externalUrl.trim() || undefined
                },
                { onSuccess: () => notify.success("发布结果已更新") }
              )
            }
          >
            保存结果
          </Button>
        </div>
      ) : null}
    </div>
  )
}

function WorkPublicationPanel({ work }: { work: AigcWork }) {
  const dialogTitleId = useId()
  const [open, setOpen] = useState(false)
  const [channelSpecVersionId, setChannelSpecVersionId] = useState<number>()
  const { data: publications = [], isLoading } = useAigcWorkPublications(work.id)
  const { data: channelPage } = useAigcChannelSpecs({ status: "published" })
  const { data: channelRefs = [] } = useAigcProjectChannelRefs(work.projectId)
  const publish = usePublishAigcWork()
  const boundChannelIds = new Set(channelRefs.map((reference) => reference.channelSpecId))
  const channels = (channelPage?.list ?? []).filter(
    (channel) => channel.status === "published" && boundChannelIds.has(channel.id)
  )

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3">
        <p className="font-medium text-sm">发布记录</p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => setOpen(true)}
          disabled={work.status === "archived"}
        >
          <Plus /> 新建发布
        </Button>
      </div>
      {isLoading ? (
        <Skeleton className="h-24 w-full" />
      ) : publications.length === 0 ? (
        <p className="rounded-lg border border-dashed p-4 text-center text-muted-foreground text-sm">
          尚无 Publication
        </p>
      ) : (
        publications.map((publication) => (
          <PublicationEditor key={publication.id} publication={publication} />
        ))
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle id={dialogTitleId}>创建发布记录</DialogTitle>
            <DialogDescription>
              仅可选择项目已绑定的已发布渠道规格；后端会进行最终校验。
            </DialogDescription>
          </DialogHeader>
          <Select
            value={channelSpecVersionId ? String(channelSpecVersionId) : ""}
            onValueChange={(value) => setChannelSpecVersionId(value ? Number(value) : undefined)}
          >
            <SelectTrigger className="w-full">
              <SelectValue>选择渠道规格</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {channels.map((channel) => (
                  <SelectItem key={channel.id} value={String(channel.id)}>
                    {channel.name} · {channel.specVersion}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button
              type="button"
              disabled={!channelSpecVersionId || publish.isPending}
              onClick={() => {
                if (!channelSpecVersionId) return
                publish.mutate(
                  { workId: work.id, channelSpecVersionId },
                  {
                    onSuccess: () => {
                      setOpen(false)
                      notify.success("发布记录已创建")
                    }
                  }
                )
              }}
            >
              <Send /> 创建
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function WorkCard({ work }: { work: AigcWork }) {
  const confirmArchive = useBoolean()
  const archive = useArchiveAigcWork()

  return (
    <>
      <GlassCard>
        <GlassCardBody className="flex flex-col gap-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h2 className="font-medium">{work.title}</h2>
              <p className="text-muted-foreground text-sm">
                Project #{work.projectId} · ObjectVersion #{work.adoptedObjectVersionId}
              </p>
            </div>
            <Badge variant="secondary">{work.status}</Badge>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              nativeButton={false}
              render={
                <Link
                  href={`/studio/projects/${work.projectId}?focus=${work.deliverableObjectId}`}
                />
              }
              variant="outline"
            >
              返回项目对象
            </Button>
            {work.status !== "archived" ? (
              <Button type="button" variant="ghost" onClick={confirmArchive.onTrue}>
                <Archive /> 归档作品
              </Button>
            ) : null}
          </div>
          <WorkPublicationPanel work={work} />
        </GlassCardBody>
      </GlassCard>
      <ConfirmDialog
        open={confirmArchive.value}
        onOpenChange={confirmArchive.setValue}
        title="归档作品"
        description="归档后不能再创建或回写 Publication，但项目仍保留作品引用。"
        confirmText="确认归档"
        onConfirm={() =>
          archive.mutate(
            { workId: work.id, expectedVersion: work.version },
            { onSuccess: () => notify.success("作品已归档") }
          )
        }
      />
    </>
  )
}

export function WorksLibrary() {
  const { data, isLoading } = useAigcWorks({ pageSize: 100 })
  const works = data?.list ?? []

  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="violet" />
      <div className="relative mx-auto flex max-w-6xl flex-col gap-6 p-6">
        <header>
          <h1 className="font-semibold text-xl">我的作品</h1>
          <p className="text-muted-foreground text-sm">
            已采用并通过审核的项目成果及渠道 Publication。
          </p>
        </header>
        {isLoading ? (
          <div className="grid gap-4 lg:grid-cols-2">
            <Skeleton className="h-64" />
            <Skeleton className="h-64" />
          </div>
        ) : works.length === 0 ? (
          <GlassCard glow="none">
            <Empty className="min-h-72">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Library />
                </EmptyMedia>
                <EmptyTitle>还没有收录作品</EmptyTitle>
                <EmptyDescription>
                  项目审核通过后，从 adopted Deliverable 对象详情收录 Work。
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          </GlassCard>
        ) : (
          <div className="grid items-start gap-4 lg:grid-cols-2">
            {works.map((work) => (
              <WorkCard key={work.id} work={work} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
