/**
 * 知识库详情页——统计卡片 + 文档列表 + Tab 切换
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
  Save,
  Trash2,
  X
} from "lucide-react"
import { use, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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
import { notify } from "@/lib/notification"
import {
  useCreateSegment,
  useDeleteSegment,
  useKnowledgeBase,
  useKnowledgeBaseStats,
  useKnowledgeDocuments,
  useKnowledgeSegments,
  useToggleSegment,
  useUpdateKnowledgeBase,
  useUpdateSegment
} from "@/lib/queries/use-knowledge"
import type { KnowledgeDocument } from "@/lib/types/knowledge"

const STATUS_MAP: Record<
  string,
  { label: string; variant: "default" | "secondary" | "destructive" | "outline" }
> = {
  pending: { label: "待处理", variant: "outline" },
  processing: { label: "处理中", variant: "secondary" },
  completed: { label: "已完成", variant: "default" },
  failed: { label: "失败", variant: "destructive" }
}

// ── 分块行组件 ────────────────────────────────────────────────────────────────
function SegmentRow({
  kbId,
  documentId,
  segment
}: {
  kbId: string
  documentId: string
  segment: { id: string; content: string; position: number; wordCount: number; enabled: boolean }
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
    <div className="space-y-2 rounded-lg border bg-muted/30 p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-muted-foreground text-xs">
          #{segment.position} · {segment.wordCount} 字
        </span>
        <div className="flex items-center gap-1.5">
          <Switch
            checked={segment.enabled}
            onCheckedChange={(v) => toggle({ id: segment.id, enabled: v })}
            className="h-4 w-7"
          />
          {editing ? (
            <>
              <Button
                size="icon"
                variant="ghost"
                className="size-6"
                onClick={handleSave}
                disabled={updating}
              >
                {updating ? (
                  <Loader2 className="size-3 animate-spin" />
                ) : (
                  <Save className="size-3" />
                )}
              </Button>
              <Button
                size="icon"
                variant="ghost"
                className="size-6"
                onClick={() => {
                  setEditing(false)
                  setContent(segment.content)
                }}
              >
                <X className="size-3" />
              </Button>
            </>
          ) : (
            <>
              <Button
                size="icon"
                variant="ghost"
                className="size-6"
                onClick={() => setEditing(true)}
              >
                <Edit2 className="size-3" />
              </Button>
              <Button
                size="icon"
                variant="ghost"
                className="size-6 text-destructive"
                onClick={() => del(segment.id)}
                disabled={deleting}
              >
                <Trash2 className="size-3" />
              </Button>
            </>
          )}
        </div>
      </div>
      {editing ? (
        <Textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          className="min-h-[80px] text-sm"
        />
      ) : (
        <p className="line-clamp-3 text-sm leading-relaxed">{segment.content}</p>
      )}
    </div>
  )
}

// ── 文档行（可展开分块）────────────────────────────────────────────────────────
function DocumentRow({ doc, kbId }: { doc: KnowledgeDocument; kbId: string }) {
  const [expanded, setExpanded] = useState(false)
  const [adding, setAdding] = useState(false)
  const [newContent, setNewContent] = useState("")

  const { data: segData, isLoading: segLoading } = useKnowledgeSegments(kbId, doc.id, expanded)
  const segments = segData?.list ?? []
  const { mutate: createSeg, isPending: creating } = useCreateSegment(kbId)
  const st = STATUS_MAP[doc.status] ?? STATUS_MAP.pending

  function handleAddSegment() {
    if (!newContent.trim()) return
    createSeg(
      { documentId: doc.id, content: newContent.trim() },
      {
        onSuccess: () => {
          setAdding(false)
          setNewContent("")
        }
      }
    )
  }

  return (
    <>
      <TableRow className="cursor-pointer hover:bg-muted/50" onClick={() => setExpanded((v) => !v)}>
        <TableCell>
          <span className="flex items-center gap-1.5 font-medium">
            {expanded ? (
              <ChevronDown className="size-3.5" />
            ) : (
              <ChevronRight className="size-3.5" />
            )}
            {doc.name}
          </span>
        </TableCell>
        <TableCell>{doc.type}</TableCell>
        <TableCell>{formatSize(doc.size)}</TableCell>
        <TableCell>{doc.chunkCount}</TableCell>
        <TableCell>
          <Badge variant={st.variant}>{st.label}</Badge>
        </TableCell>
        <TableCell>{new Date(doc.createdAt).toLocaleDateString()}</TableCell>
      </TableRow>
      {expanded && (
        <TableRow>
          <TableCell colSpan={6} className="bg-muted/20 p-4">
            {segLoading ? (
              <div className="space-y-2">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-16" />
                ))}
              </div>
            ) : (
              <div className="space-y-2">
                {segments.map((seg) => (
                  <SegmentRow key={seg.id} kbId={kbId} documentId={doc.id} segment={seg} />
                ))}
                {adding ? (
                  <div className="space-y-2">
                    <Textarea
                      value={newContent}
                      onChange={(e) => setNewContent(e.target.value)}
                      placeholder="输入新分块内容..."
                      className="min-h-[80px] text-sm"
                      autoFocus
                    />
                    <div className="flex gap-2">
                      <Button size="sm" onClick={handleAddSegment} disabled={creating}>
                        {creating ? <Loader2 className="mr-1 size-3 animate-spin" /> : null}
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
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={(e) => {
                      e.stopPropagation()
                      setAdding(true)
                    }}
                  >
                    <Plus className="mr-1 size-3" />
                    添加分块
                  </Button>
                )}
              </div>
            )}
          </TableCell>
        </TableRow>
      )}
    </>
  )
}

// ── 主页面 ────────────────────────────────────────────────────────────────────
export default function KnowledgeDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const { data: kb, isLoading } = useKnowledgeBase(id)
  const { data: stats } = useKnowledgeBaseStats(id)
  const { data: docsData } = useKnowledgeDocuments(id)
  const { mutate: updateKb, isPending: saving } = useUpdateKnowledgeBase()

  const [editing, setEditing] = useState(false)
  const [editName, setEditName] = useState("")
  const [editDesc, setEditDesc] = useState("")

  const documents = docsData?.list ?? []

  function startEdit() {
    setEditName(kb?.name ?? "")
    setEditDesc(kb?.description ?? "")
    setEditing(true)
  }

  function handleSave() {
    updateKb(
      { id, data: { name: editName, description: editDesc } },
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
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={`sk-${i}`} className="h-24" />
          ))}
        </div>
      </PageContainer>
    )
  }

  if (!kb) return null

  const statCards = [
    { icon: FileText, label: "文档数", value: stats?.documentCount ?? 0 },
    { icon: Layers, label: "分块数", value: stats?.chunkCount ?? 0 },
    { icon: Database, label: "向量数", value: stats?.vectorCount ?? 0 },
    { icon: HardDrive, label: "总大小", value: formatSize(stats?.totalSize ?? 0) }
  ]

  return (
    <PageContainer>
      {/* 标题区 + 编辑 */}
      {editing ? (
        <div className="mb-6 space-y-2">
          <div className="flex items-center gap-2">
            <Input
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              className="h-10 w-80 font-bold text-xl"
            />
            <Button size="sm" onClick={handleSave} disabled={saving}>
              {saving ? <Loader2 className="mr-1 size-3 animate-spin" /> : null}
              保存
            </Button>
            <Button size="sm" variant="outline" onClick={() => setEditing(false)}>
              取消
            </Button>
          </div>
          <Input
            value={editDesc}
            onChange={(e) => setEditDesc(e.target.value)}
            placeholder="简介（可选）"
            className="max-w-lg"
          />
        </div>
      ) : (
        <div className="mb-2 flex items-center gap-2">
          <TypographyH1 className="text-2xl">{kb.name}</TypographyH1>
          <Button size="icon" variant="ghost" className="size-7" onClick={startEdit}>
            <Edit2 className="size-3.5" />
          </Button>
        </div>
      )}
      {!editing && kb.description && <p className="mb-6 text-muted-foreground">{kb.description}</p>}

      {/* 统计卡片 */}
      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {statCards.map((s) => (
          <Card key={s.label}>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <CardTitle className="font-medium text-muted-foreground text-sm">{s.label}</CardTitle>
              <s.icon className="size-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="font-bold text-2xl">{s.value}</div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Tab 区域 */}
      <Tabs defaultValue="documents">
        <TabsList>
          <TabsTrigger value="documents">文档</TabsTrigger>
          <TabsTrigger value="graph">知识图谱</TabsTrigger>
          <TabsTrigger value="search">检索测试</TabsTrigger>
          <TabsTrigger value="settings">设置</TabsTrigger>
        </TabsList>

        <TabsContent value="documents" className="mt-4 space-y-4">
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
              </TableRow>
            </TableHeader>
            <TableBody>
              {documents.map((doc) => (
                <DocumentRow key={doc.id} doc={doc} kbId={id} />
              ))}
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
          <KnowledgeSettings knowledgeBase={kb} />
        </TabsContent>
      </Tabs>
    </PageContainer>
  )
}

function formatSize(bytes: number): string {
  if (bytes === 0) return "0 B"
  const units = ["B", "KB", "MB", "GB"]
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / 1024 ** i).toFixed(1)} ${units[i]}`
}
