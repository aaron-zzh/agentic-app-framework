/**
 * 知识库详情页——统计、文档管理、图谱和检索闭环
 * @author AaronZZH & Kiro
 */

"use client"

import {
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clock,
  Database,
  Edit2,
  ExternalLink,
  FileText,
  HardDrive,
  Layers,
  Loader2,
  type LucideIcon,
  RefreshCw,
  Trash2,
  XCircle
} from "lucide-react"
import Link from "next/link"
import { use, useId, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { TypographyH1 } from "@/components/ui/typography"
import { DocumentLibraryImport } from "@/features/knowledge/components/DocumentLibraryImport"
import { DocumentUpload } from "@/features/knowledge/components/DocumentUpload"
import { KnowledgeGraph } from "@/features/knowledge/components/KnowledgeGraph"
import { KnowledgeSettings } from "@/features/knowledge/components/KnowledgeSettings"
import { SearchTestPanel } from "@/features/knowledge/components/SearchTestPanel"
import {
  KNOWLEDGE_AI_SUMMARY_COPY,
  KNOWLEDGE_DESCRIPTION_COPY
} from "@/features/knowledge/constants"
import { useStudioHeader } from "@/layouts/studio/header-store"
import {
  useDeleteKnowledgeDocument,
  useKnowledgeBase,
  useKnowledgeBaseStats,
  useKnowledgeChunks,
  useKnowledgeDocument,
  useKnowledgeDocuments,
  useRebuildKnowledgeGraph,
  useRetryKnowledgeDocument,
  useSetChunkEnabled,
  useUpdateKnowledgeBase
} from "@/lib/api/rest/knowledge/knowledge"
import { paths } from "@/lib/constants/paths"
import { notify } from "@/lib/notification"
import type {
  KnowledgeChunk,
  KnowledgeDocument,
  KnowledgeDocumentStatus
} from "@/lib/types/knowledge"
import { cn } from "@/lib/utils/index"
import { formatTimeAgo } from "@/lib/utils/time"

const STATUS_MAP: Record<
  KnowledgeDocumentStatus,
  { label: string; icon: LucideIcon; spin?: boolean; className: string }
> = {
  0: {
    label: "待处理",
    icon: Clock,
    className: "bg-muted text-muted-foreground"
  },
  1: {
    label: "处理中",
    icon: Loader2,
    spin: true,
    className: "bg-sky-500/10 text-sky-600 dark:text-sky-400"
  },
  2: {
    label: "已完成",
    icon: CheckCircle2,
    className: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
  },
  3: {
    label: "失败",
    icon: XCircle,
    className: "bg-destructive/10 text-destructive"
  }
}

function ChunkRow({
  kbId,
  documentId,
  chunk
}: {
  kbId: string
  documentId: number
  chunk: KnowledgeChunk
}) {
  const [expanded, setExpanded] = useState(false)
  const { mutate: setEnabled, isPending: toggling } = useSetChunkEnabled(kbId, documentId)

  return (
    <div
      className={`flex flex-col gap-2 rounded-lg border p-3 ${chunk.enabled ? "bg-muted/30" : "bg-muted/10 opacity-60"}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="flex min-w-0 items-center gap-2 text-muted-foreground text-xs">
          <span>
            #{chunk.chunkIndex + 1} · {chunk.tokenCount} tokens
          </span>
          {chunk.sectionPath ? <span className="truncate">{chunk.sectionPath}</span> : null}
          {chunk.pageNo !== null ? <span>第 {chunk.pageNo} 页</span> : null}
          {chunk.enabled ? null : <Badge variant="outline">已停用</Badge>}
        </span>
        <Switch
          checked={chunk.enabled}
          disabled={toggling}
          aria-label={chunk.enabled ? "停用分块" : "启用分块"}
          onCheckedChange={(enabled) =>
            setEnabled(
              { chunkId: chunk.id, enabled },
              { onSuccess: () => notify.success(enabled ? "分块已启用" : "分块已停用") }
            )
          }
          className="h-4 w-7"
        />
      </div>
      <button
        type="button"
        onClick={() => setExpanded((value) => !value)}
        className="text-left"
        aria-expanded={expanded}
      >
        <p
          className={`whitespace-pre-wrap text-sm leading-relaxed ${expanded ? "" : "line-clamp-3"}`}
        >
          {chunk.content}
        </p>
      </button>
    </div>
  )
}
function DocumentRow({ doc, kbId }: { doc: KnowledgeDocument; kbId: string }) {
  const [expanded, setExpanded] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const { data: documentDetail, isLoading: detailLoading } = useKnowledgeDocument(
    kbId,
    doc.id,
    expanded
  )
  const { data: chunkData, isLoading: chunkLoading } = useKnowledgeChunks(kbId, doc.id, expanded)
  const { mutate: deleteDocument, isPending: deleting } = useDeleteKnowledgeDocument(kbId)
  const { mutate: retryDocument, isPending: retrying } = useRetryKnowledgeDocument(kbId)

  const detail = documentDetail ?? doc
  const chunks = chunkData?.list ?? []
  const chunkTotal = chunkData?.total ?? 0
  const status = STATUS_MAP[doc.status]

  function handleDelete() {
    deleteDocument(doc.id, {
      onSuccess: () => notify.success("文档已删除")
    })
  }

  function handleRetry() {
    retryDocument(doc.id, {
      onSuccess: () => notify.success("已重新提交处理")
    })
  }

  return (
    <>
      <TableRow className="cursor-pointer hover:bg-muted/50" onClick={() => setExpanded((v) => !v)}>
        <TableCell className="max-w-md whitespace-normal">
          <span className="flex items-center gap-1.5 font-medium">
            <Button
              size="icon-sm"
              variant="ghost"
              aria-label={expanded ? "收起文档详情" : "查看文档详情"}
              aria-expanded={expanded}
              onClick={(event) => {
                event.stopPropagation()
                setExpanded((value) => !value)
              }}
            >
              {expanded ? <ChevronDown /> : <ChevronRight />}
            </Button>
            {doc.title}
          </span>
          {doc.summary ? (
            <p className="line-clamp-2 pl-8 text-muted-foreground/80 text-xs" title={doc.summary}>
              摘要：{doc.summary}
            </p>
          ) : null}
        </TableCell>
        <TableCell>{doc.fileType}</TableCell>
        <TableCell>{formatSize(doc.fileSize ?? 0)}</TableCell>
        <TableCell>{doc.chunkCount}</TableCell>
        <TableCell>
          <Badge variant="ghost" className={cn("hover:bg-inherit", status.className)}>
            <status.icon className={status.spin ? "animate-spin" : undefined} />
            {status.label}
          </Badge>
        </TableCell>
        <TableCell>{new Date(doc.createTime).toLocaleDateString()}</TableCell>
        <TableCell>
          <div className="flex items-center justify-end gap-1">
            {doc.sourceDocumentId !== null ? (
              <Button
                size="icon-sm"
                variant="ghost"
                aria-label="查看来源文档详情"
                title="查看来源文档详情"
                nativeButton={false}
                render={
                  <Link
                    href={paths.studio.knowledgeDoc(
                      doc.sourceDocumentId,
                      doc.sourceDocumentVersionId
                    )}
                  />
                }
                onClick={(event) => event.stopPropagation()}
              >
                <ExternalLink />
              </Button>
            ) : null}
            {doc.status === 3 ? (
              <Button
                size="icon-sm"
                variant="ghost"
                aria-label="重试文档处理"
                disabled={retrying}
                onClick={(event) => {
                  event.stopPropagation()
                  handleRetry()
                }}
              >
                <RefreshCw className={retrying ? "animate-spin" : undefined} />
              </Button>
            ) : null}
            <Button
              size="icon-sm"
              variant="ghost"
              aria-label="删除文档"
              disabled={deleting}
              onClick={(event) => {
                event.stopPropagation()
                setDeleteOpen(true)
              }}
            >
              <Trash2 className="text-destructive" />
            </Button>
          </div>
        </TableCell>
      </TableRow>

      {expanded ? (
        <TableRow>
          <TableCell colSpan={7} className="bg-muted/20 p-4">
            {detailLoading ? (
              <Skeleton className="h-24" />
            ) : (
              <div className="flex flex-col gap-4">
                <dl className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-3">
                  <div>
                    <dt className="text-muted-foreground">来源</dt>
                    <dd className="break-all">{detail.sourceUri ?? detail.sourceKey ?? "暂无"}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">来源文档版本</dt>
                    <dd>
                      {detail.sourceDocumentId !== null && detail.sourceDocumentVersionId !== null
                        ? `#${detail.sourceDocumentId} / Version #${detail.sourceDocumentVersionId}`
                        : "暂无"}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">活动运行 ID</dt>
                    <dd className="break-all font-mono text-xs">{detail.activeRunId ?? "暂无"}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">更新时间</dt>
                    <dd>{new Date(detail.updateTime).toLocaleString()}</dd>
                  </div>
                  <div className="sm:col-span-2 lg:col-span-3">
                    <dt className="text-muted-foreground">内容哈希</dt>
                    <dd className="break-all font-mono text-xs">{detail.contentHash ?? "暂无"}</dd>
                  </div>
                </dl>

                {detail.errorMessage ? (
                  <p className="rounded-md bg-destructive/10 p-3 text-destructive text-sm">
                    {detail.errorMessage}
                  </p>
                ) : null}

                <div className="flex flex-col gap-2">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <h3 className="font-medium text-sm">
                      文档分块{chunkTotal > 0 ? `（${chunkTotal}）` : ""}
                    </h3>
                    <p className="text-muted-foreground text-xs">
                      分块内容只读；停用后不参与检索，相关事实从图谱移除。修改内容请编辑源文档后重新导入。
                    </p>
                  </div>
                  {chunkLoading ? (
                    Array.from({ length: 3 }).map((_, index) => (
                      <Skeleton key={`chunk-${doc.id}-${index}`} className="h-16" />
                    ))
                  ) : chunks.length === 0 ? (
                    <p className="text-muted-foreground text-sm">
                      暂无分块（文档未处理完成或处理失败）
                    </p>
                  ) : (
                    <>
                      {chunks.map((chunk) => (
                        <ChunkRow key={chunk.id} kbId={kbId} documentId={doc.id} chunk={chunk} />
                      ))}
                      {chunkTotal > chunks.length ? (
                        <p className="text-muted-foreground text-xs">
                          仅显示前 {chunks.length} 个分块，共 {chunkTotal} 个
                        </p>
                      ) : null}
                    </>
                  )}
                </div>
              </div>
            )}
          </TableCell>
        </TableRow>
      ) : null}

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="删除文档"
        description={`确定删除「${doc.title}」吗？关联分块、向量和图谱数据将同步清理。`}
        confirmText="删除"
        variant="destructive"
        onConfirm={handleDelete}
      />
    </>
  )
}

export default function KnowledgeDetailPage({ params }: { params: Promise<{ id: string }> }) {
  useStudioHeader({ back: { href: "/studio/knowledge", label: "返回知识库列表" } })
  const { id } = use(params)
  const { data: knowledgeBase, isLoading } = useKnowledgeBase(id)
  const { data: stats } = useKnowledgeBaseStats(id)
  const { data: documentData } = useKnowledgeDocuments(id)
  const { mutate: updateKnowledgeBase, isPending: saving } = useUpdateKnowledgeBase()
  const { mutate: rebuildGraph, isPending: rebuildingGraph } = useRebuildKnowledgeGraph(id)

  const [editing, setEditing] = useState(false)
  const [editName, setEditName] = useState("")
  const [editDescription, setEditDescription] = useState("")
  const [activeTab, setActiveTab] = useState("documents")
  const descriptionId = useId()

  const documents = documentData?.list ?? []

  function startEdit() {
    setEditName(knowledgeBase?.name ?? "")
    setEditDescription(knowledgeBase?.description ?? "")
    setEditing(true)
  }

  function handleSave() {
    updateKnowledgeBase(
      { id, data: { name: editName, description: editDescription } },
      {
        onSuccess: () => {
          setEditing(false)
          notify.success("已保存")
        }
      }
    )
  }

  if (isLoading) {
    return (
      <PageContainer>
        <Skeleton className="mb-4 h-8 w-48" />
        <div className="grid gap-4 sm:grid-cols-4">
          {Array.from({ length: 4 }).map((_, index) => (
            <Skeleton key={`stat-${index}`} className="h-24" />
          ))}
        </div>
      </PageContainer>
    )
  }

  if (!knowledgeBase) return null

  const statCards = [
    { icon: FileText, label: "文档数", value: stats?.documentCount ?? 0 },
    { icon: Layers, label: "分块数", value: stats?.chunkCount ?? 0 },
    { icon: Database, label: "向量数", value: stats?.embeddingCount ?? 0 },
    { icon: HardDrive, label: "总大小", value: formatSize(stats?.totalSize ?? 0) }
  ]

  return (
    <PageContainer>
      {editing ? (
        <div className="mb-6 flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <Input
              value={editName}
              onChange={(event) => setEditName(event.target.value)}
              className="h-10 w-80 font-bold text-xl"
            />
            <Button size="sm" onClick={handleSave} disabled={saving}>
              {saving ? <Loader2 data-icon="inline-start" className="animate-spin" /> : null}
              保存
            </Button>
            <Button size="sm" variant="outline" onClick={() => setEditing(false)}>
              取消
            </Button>
          </div>
          <label htmlFor={descriptionId} className="mt-2 font-medium text-sm">
            {KNOWLEDGE_DESCRIPTION_COPY.label}
          </label>
          <Textarea
            id={descriptionId}
            value={editDescription}
            onChange={(event) => setEditDescription(event.target.value)}
            placeholder={KNOWLEDGE_DESCRIPTION_COPY.placeholder}
            aria-describedby={`${descriptionId}-help`}
            rows={3}
            className="max-w-2xl"
          />
          <p id={`${descriptionId}-help`} className="max-w-2xl text-muted-foreground text-xs">
            {KNOWLEDGE_DESCRIPTION_COPY.help}
          </p>
        </div>
      ) : (
        <div className="mb-2 flex items-center gap-2">
          <TypographyH1 className="text-2xl">{knowledgeBase.name}</TypographyH1>
          <Button size="icon-sm" variant="ghost" onClick={startEdit} aria-label="编辑知识库">
            <Edit2 />
          </Button>
        </div>
      )}
      {editing ? null : (
        <div className="mb-6 grid gap-3 md:grid-cols-2">
          <section className="flex flex-col gap-1 rounded-lg border p-3">
            <h2 className="font-medium text-sm">{KNOWLEDGE_DESCRIPTION_COPY.label}</h2>
            {knowledgeBase.description ? (
              <p className="whitespace-pre-wrap text-sm">{knowledgeBase.description}</p>
            ) : (
              <p className="text-muted-foreground text-sm">未填写</p>
            )}
            <p className="text-muted-foreground text-xs">{KNOWLEDGE_DESCRIPTION_COPY.help}</p>
          </section>
          <section className="flex flex-col gap-1 rounded-lg border bg-muted/20 p-3">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="font-medium text-sm">{KNOWLEDGE_AI_SUMMARY_COPY.label}</h2>
              {knowledgeBase.aiSummaryUpdatedAt ? (
                <span className="text-muted-foreground text-xs">
                  更新于 {formatTimeAgo(knowledgeBase.aiSummaryUpdatedAt)}
                </span>
              ) : null}
            </div>
            {knowledgeBase.aiSummary ? (
              <p className="whitespace-pre-wrap text-muted-foreground text-sm">
                {knowledgeBase.aiSummary}
              </p>
            ) : (
              <p className="text-muted-foreground text-sm">{KNOWLEDGE_AI_SUMMARY_COPY.empty}</p>
            )}
            <p className="text-muted-foreground text-xs">{KNOWLEDGE_AI_SUMMARY_COPY.help}</p>
          </section>
        </div>
      )}

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {statCards.map((stat) => (
          <Card key={stat.label}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="font-medium text-muted-foreground text-sm">
                {stat.label}
              </CardTitle>
              <stat.icon className="text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="font-bold text-2xl">{stat.value}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Tabs value={activeTab} onValueChange={(value) => setActiveTab(String(value))}>
        <div className="flex items-center justify-between gap-2">
          <TabsList>
            <TabsTrigger value="documents">文档</TabsTrigger>
            <TabsTrigger value="graph">知识图谱</TabsTrigger>
            <TabsTrigger value="search">检索测试</TabsTrigger>
            <TabsTrigger value="settings">设置</TabsTrigger>
          </TabsList>
          {activeTab === "documents" ? <DocumentLibraryImport knowledgeBaseId={id} /> : null}
          {activeTab === "graph" ? (
            <Button
              size="sm"
              variant="outline"
              disabled={rebuildingGraph}
              onClick={() =>
                rebuildGraph(undefined, {
                  onSuccess: (status) =>
                    status.ready
                      ? notify.success("图谱已重建")
                      : notify.warning(`图谱重建未完成：${status.errorMessage ?? status.status}`)
                })
              }
            >
              <RefreshCw
                data-icon="inline-start"
                className={rebuildingGraph ? "animate-spin" : undefined}
              />
              {rebuildingGraph ? "重建中..." : "重建图谱"}
            </Button>
          ) : null}
        </div>

        <TabsContent value="documents" className="mt-4 flex flex-col gap-4">
          <DocumentUpload knowledgeBaseId={id} />
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>文件名</TableHead>
                <TableHead>类型</TableHead>
                <TableHead>大小</TableHead>
                <TableHead>分块数</TableHead>
                <TableHead>状态</TableHead>
                <TableHead>上传时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {documents.length > 0 ? (
                documents.map((document) => (
                  <DocumentRow key={document.id} doc={document} kbId={id} />
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    暂无文档
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </TabsContent>

        <TabsContent value="graph" className="mt-4">
          <KnowledgeGraph knowledgeBaseId={id} />
        </TabsContent>

        <TabsContent value="search" className="mt-4">
          <SearchTestPanel knowledgeBaseId={knowledgeBase.stableId} />
        </TabsContent>

        <TabsContent value="settings" className="mt-4">
          <KnowledgeSettings knowledgeBase={knowledgeBase} />
        </TabsContent>
      </Tabs>
    </PageContainer>
  )
}

function formatSize(bytes: number): string {
  if (bytes === 0) return "0 B"
  const units = ["B", "KB", "MB", "GB"]
  const index = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / 1024 ** index).toFixed(1)} ${units[index]}`
}
