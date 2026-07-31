/**
 * 知识库详情页——统计、文档管理、图谱和检索闭环
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ChevronDown,
  ChevronRight,
  Database,
  Edit2,
  FileText,
  HardDrive,
  Layers,
  Loader2,
  Plus,
  RefreshCw,
  Save,
  Trash2,
  X
} from "lucide-react"
import { use, useState } from "react"
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
import { DocumentUpload } from "@/features/knowledge/components/DocumentUpload"
import { KnowledgeGraph } from "@/features/knowledge/components/KnowledgeGraph"
import { KnowledgeSettings } from "@/features/knowledge/components/KnowledgeSettings"
import { SearchTestPanel } from "@/features/knowledge/components/SearchTestPanel"
import {
  useCreateSegment,
  useDeleteKnowledgeDocument,
  useDeleteSegment,
  useKnowledgeBase,
  useKnowledgeBaseStats,
  useKnowledgeDocument,
  useKnowledgeDocuments,
  useKnowledgeSegments,
  useRetryKnowledgeDocument,
  useToggleSegment,
  useUpdateKnowledgeBase,
  useUpdateSegment
} from "@/lib/api/rest/knowledge/knowledge"
import { notify } from "@/lib/notification"
import type {
  KnowledgeDocument,
  KnowledgeDocumentStatus,
  KnowledgeSegment
} from "@/lib/types/knowledge"

const STATUS_MAP: Record<
  KnowledgeDocumentStatus,
  { label: string; variant: "default" | "secondary" | "destructive" | "outline" }
> = {
  0: { label: "待处理", variant: "outline" },
  1: { label: "处理中", variant: "secondary" },
  2: { label: "已完成", variant: "default" },
  3: { label: "失败", variant: "destructive" }
}

function SegmentRow({
  kbId,
  documentId,
  segment
}: {
  kbId: string
  documentId: number
  segment: KnowledgeSegment
}) {
  const [editing, setEditing] = useState(false)
  const [content, setContent] = useState(segment.content)
  const { mutate: update, isPending: updating } = useUpdateSegment(kbId, documentId)
  const { mutate: del, isPending: deleting } = useDeleteSegment(kbId, documentId)
  const { mutate: toggle } = useToggleSegment(kbId, documentId)

  function handleSave() {
    update(
      { id: segment.id, content },
      {
        onSuccess: () => {
          setEditing(false)
          notify.success("已保存")
        }
      }
    )
  }

  return (
    <div className="flex flex-col gap-2 rounded-lg border bg-muted/30 p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-muted-foreground text-xs">
          #{segment.position} · {segment.wordCount} 字
        </span>
        <div className="flex items-center gap-1.5">
          <Switch
            checked={segment.enabled}
            onCheckedChange={(enabled) => toggle({ id: segment.id, enabled })}
            className="h-4 w-7"
          />
          {editing ? (
            <>
              <Button
                size="icon-xs"
                variant="ghost"
                onClick={handleSave}
                disabled={updating}
                aria-label="保存分块"
              >
                {updating ? <Loader2 className="animate-spin" /> : <Save />}
              </Button>
              <Button
                size="icon-xs"
                variant="ghost"
                onClick={() => {
                  setEditing(false)
                  setContent(segment.content)
                }}
                aria-label="取消编辑分块"
              >
                <X />
              </Button>
            </>
          ) : (
            <>
              <Button
                size="icon-xs"
                variant="ghost"
                onClick={() => setEditing(true)}
                aria-label="编辑分块"
              >
                <Edit2 />
              </Button>
              <Button
                size="icon-xs"
                variant="ghost"
                onClick={() => del(segment.id)}
                disabled={deleting}
                aria-label="删除分块"
              >
                <Trash2 className="text-destructive" />
              </Button>
            </>
          )}
        </div>
      </div>
      {editing ? (
        <Textarea
          value={content}
          onChange={(event) => setContent(event.target.value)}
          className="min-h-[80px] text-sm"
        />
      ) : (
        <p className="line-clamp-3 text-sm leading-relaxed">{segment.content}</p>
      )}
    </div>
  )
}

function DocumentRow({ doc, kbId }: { doc: KnowledgeDocument; kbId: string }) {
  const [expanded, setExpanded] = useState(false)
  const [adding, setAdding] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [newContent, setNewContent] = useState("")

  const { data: documentDetail, isLoading: detailLoading } = useKnowledgeDocument(
    kbId,
    doc.id,
    expanded
  )
  const { data: segmentData, isLoading: segmentLoading } = useKnowledgeSegments(
    kbId,
    doc.id,
    expanded
  )
  const { mutate: createSegment, isPending: creating } = useCreateSegment(kbId)
  const { mutate: deleteDocument, isPending: deleting } = useDeleteKnowledgeDocument(kbId)
  const { mutate: retryDocument, isPending: retrying } = useRetryKnowledgeDocument(kbId)

  const detail = documentDetail ?? doc
  const segments = segmentData?.list ?? []
  const status = STATUS_MAP[doc.status]

  function handleAddSegment() {
    if (!newContent.trim()) return
    createSegment(
      { documentId: doc.id, content: newContent.trim() },
      {
        onSuccess: () => {
          setAdding(false)
          setNewContent("")
        }
      }
    )
  }

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
        <TableCell>
          <span className="flex items-center gap-1.5 font-medium">
            {expanded ? <ChevronDown /> : <ChevronRight />}
            {doc.title}
          </span>
        </TableCell>
        <TableCell>{doc.fileType}</TableCell>
        <TableCell>{formatSize(doc.fileSize)}</TableCell>
        <TableCell>{doc.chunkCount}</TableCell>
        <TableCell>
          <Badge variant={status.variant}>{status.label}</Badge>
        </TableCell>
        <TableCell>{new Date(doc.createTime).toLocaleDateString()}</TableCell>
        <TableCell>
          <div className="flex items-center justify-end gap-1">
            <Button
              size="icon-sm"
              variant="ghost"
              aria-label={expanded ? "收起文档详情" : "查看文档详情"}
              onClick={(event) => {
                event.stopPropagation()
                setExpanded((value) => !value)
              }}
            >
              {expanded ? <ChevronDown /> : <ChevronRight />}
            </Button>
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
                    <dt className="text-muted-foreground">文件路径</dt>
                    <dd className="break-all">{detail.filePath}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">内容哈希</dt>
                    <dd className="break-all font-mono text-xs">{detail.contentHash || "暂无"}</dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">更新时间</dt>
                    <dd>{new Date(detail.updateTime).toLocaleString()}</dd>
                  </div>
                </dl>

                {detail.errorMessage ? (
                  <p className="rounded-md bg-destructive/10 p-3 text-destructive text-sm">
                    {detail.errorMessage}
                  </p>
                ) : null}

                <div className="flex flex-col gap-2">
                  <h3 className="font-medium text-sm">文档分块</h3>
                  {segmentLoading ? (
                    Array.from({ length: 3 }).map((_, index) => (
                      <Skeleton key={`segment-${doc.id}-${index}`} className="h-16" />
                    ))
                  ) : (
                    <>
                      {segments.map((segment) => (
                        <SegmentRow
                          key={segment.id}
                          kbId={kbId}
                          documentId={doc.id}
                          segment={segment}
                        />
                      ))}
                      {adding ? (
                        <div className="flex flex-col gap-2">
                          <Textarea
                            value={newContent}
                            onChange={(event) => setNewContent(event.target.value)}
                            placeholder="输入新分块内容..."
                            className="min-h-[80px] text-sm"
                            autoFocus
                          />
                          <div className="flex gap-2">
                            <Button size="sm" onClick={handleAddSegment} disabled={creating}>
                              {creating ? (
                                <Loader2 data-icon="inline-start" className="animate-spin" />
                              ) : null}
                              保存
                            </Button>
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => {
                                setAdding(false)
                                setNewContent("")
                              }}
                            >
                              取消
                            </Button>
                          </div>
                        </div>
                      ) : (
                        <Button size="sm" variant="outline" onClick={() => setAdding(true)}>
                          <Plus data-icon="inline-start" />
                          添加分块
                        </Button>
                      )}
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
  const { id } = use(params)
  const { data: knowledgeBase, isLoading } = useKnowledgeBase(id)
  const { data: stats } = useKnowledgeBaseStats(id)
  const { data: documentData } = useKnowledgeDocuments(id)
  const { mutate: updateKnowledgeBase, isPending: saving } = useUpdateKnowledgeBase()

  const [editing, setEditing] = useState(false)
  const [editName, setEditName] = useState("")
  const [editDescription, setEditDescription] = useState("")

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
          <Input
            value={editDescription}
            onChange={(event) => setEditDescription(event.target.value)}
            placeholder="简介（可选）"
            className="max-w-lg"
          />
        </div>
      ) : (
        <div className="mb-2 flex items-center gap-2">
          <TypographyH1 className="text-2xl">{knowledgeBase.name}</TypographyH1>
          <Button size="icon-sm" variant="ghost" onClick={startEdit} aria-label="编辑知识库">
            <Edit2 />
          </Button>
        </div>
      )}
      {!editing && knowledgeBase.description ? (
        <p className="mb-6 text-muted-foreground">{knowledgeBase.description}</p>
      ) : null}

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

      <Tabs defaultValue="documents">
        <TabsList>
          <TabsTrigger value="documents">文档</TabsTrigger>
          <TabsTrigger value="graph">知识图谱</TabsTrigger>
          <TabsTrigger value="search">检索测试</TabsTrigger>
          <TabsTrigger value="settings">设置</TabsTrigger>
        </TabsList>

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
          <SearchTestPanel knowledgeBaseId={id} />
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
