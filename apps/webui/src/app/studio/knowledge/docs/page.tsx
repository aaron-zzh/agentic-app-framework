/**
 * /studio/knowledge/docs——文档管理
 * 左侧分类树 + 右侧 DocEditor 内联编辑（复用 ProjectDocPanel 的 DocEditor 模式）
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { FileText, Plus, Trash2 } from "lucide-react"
import { useMemo, useState } from "react"
import { toast } from "sonner"
import { SectionHaze } from "@/components/studio"
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger
} from "@/components/ui/accordion"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { DocumentEditor } from "@/features/studio/content/DocumentEditor"
import type { DocListItem } from "@/lib/api/rest/system/document"
import {
  docKeys,
  useCreateDocument,
  useDeleteDocument,
  useDocList
} from "@/lib/api/rest/system/document"
import { useDocEvents } from "@/lib/hooks/use-doc-events"

const DOC_TYPE_GROUPS = [
  { key: "spec", label: "规格" },
  { key: "design", label: "设计" },
  { key: "task", label: "任务" },
  { key: "guide", label: "指南" },
  { key: "reference", label: "参考" },
  { key: "explanation", label: "说明" },
  { key: "copywriting", label: "文案" }
] as const

type PublishTab = "all" | "draft" | "published"

export default function StudioKnowledgeDocsPage() {
  const [selectedId, setSelectedId] = useState<number | "new" | null>(null)
  const [publishTab, setPublishTab] = useState<PublishTab>("all")
  const [deletingNode, setDeletingNode] = useState<DocListItem | null>(null)
  const queryClient = useQueryClient()

  const { data: list, isLoading: treeLoading } = useDocList()
  const { mutate: createDocMutate, isPending: creating } = useCreateDocument()
  const { mutate: deleteDoc } = useDeleteDocument()

  const createDoc = (
    p: { title: string; content: string },
    opts: { onSuccess: (doc: { id: number }) => void }
  ) => createDocMutate({ title: p.title, content: p.content, filePath: "", docType: "guide" }, opts)

  useDocEvents(typeof selectedId === "number" ? selectedId : null, () => {
    queryClient.invalidateQueries({ queryKey: docKeys.list })
    if (typeof selectedId === "number")
      queryClient.invalidateQueries({ queryKey: docKeys.detail(selectedId) })
  })

  const grouped = useMemo(() => {
    const allNodes: DocListItem[] = []
    function collect(nodes: DocListItem[]) {
      for (const n of nodes) allNodes.push(n)
    }
    collect(list ?? [])
    const filtered = allNodes.filter((n) => {
      if (publishTab === "draft") return n.publish !== "published"
      if (publishTab === "published") return n.publish === "published"
      return true
    })
    const knownKeys = new Set(DOC_TYPE_GROUPS.map((g) => g.key as string))
    const groups = DOC_TYPE_GROUPS.map((g) => ({
      ...g,
      nodes: filtered.filter((n) => n.docType === g.key)
    })).filter((g) => g.nodes.length > 0)
    const others = filtered.filter((n) => !knownKeys.has(n.docType ?? ""))
    if (others.length > 0)
      groups.push({ key: "other" as never, label: "其他" as never, nodes: others })
    return groups
  }, [list, publishTab])

  return (
    <div className="relative h-full">
      <SectionHaze variant="blend" />
      <div className="relative flex h-full flex-col">
        {/* 顶栏 */}
        <div className="flex items-center gap-2 border-foreground/6 border-b px-4 py-3">
          <h2 className="font-medium">文档管理</h2>
          <Tabs
            value={publishTab}
            onValueChange={(v) => setPublishTab(v as PublishTab)}
            className="ml-2"
          >
            <TabsList className="h-7">
              <TabsTrigger value="all" className="h-6 text-xs">
                全部
              </TabsTrigger>
              <TabsTrigger value="draft" className="h-6 text-xs">
                草稿
              </TabsTrigger>
              <TabsTrigger value="published" className="h-6 text-xs">
                已发布
              </TabsTrigger>
            </TabsList>
          </Tabs>
          <div className="ml-auto flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => setSelectedId("new")}>
              <Plus className="mr-1 size-4" />
              新建
            </Button>
          </div>
        </div>

        {/* 主体 */}
        <ResizablePanelGroup orientation="horizontal" className="min-h-0 flex-1">
          {/* 左侧文档树 */}
          <ResizablePanel defaultSize={28} minSize={20}>
            <div className="h-full overflow-y-auto p-3">
              {treeLoading ? (
                <div className="space-y-2">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <Skeleton key={`dt-${i}`} className="h-8" />
                  ))}
                </div>
              ) : grouped.length === 0 ? (
                <p className="py-8 text-center text-muted-foreground text-xs">暂无文档</p>
              ) : (
                <Accordion multiple defaultValue={grouped.map((g) => g.key as string)}>
                  {grouped.map((g) => (
                    <AccordionItem key={g.key} value={g.key} className="border-foreground/6">
                      <AccordionTrigger className="py-2 text-muted-foreground text-xs hover:no-underline">
                        {g.label} ({g.nodes.length})
                      </AccordionTrigger>
                      <AccordionContent>
                        <div className="space-y-0.5">
                          {g.nodes.map((node) => {
                            const nodeId = node.id ?? -1
                            return (
                              <div
                                key={`doc-${node.id}`}
                                className={`group flex w-full items-center gap-2 rounded-lg px-2 py-1.5 transition-colors hover:bg-foreground/[0.05] ${selectedId === nodeId ? "bg-foreground/[0.08]" : ""}`}
                              >
                                <button
                                  type="button"
                                  onClick={() => setSelectedId(nodeId)}
                                  className="flex min-w-0 flex-1 items-center gap-2 text-left text-sm"
                                >
                                  <FileText className="size-3.5 shrink-0 text-muted-foreground" />
                                  <span className="truncate">{node.title}</span>
                                </button>
                                {node.publish !== "published" && (
                                  <Badge variant="outline" className="shrink-0 text-[10px]">
                                    草稿
                                  </Badge>
                                )}
                                <button
                                  type="button"
                                  onClick={(e) => {
                                    e.stopPropagation()
                                    setDeletingNode(node)
                                  }}
                                  className="shrink-0 rounded p-1 text-muted-foreground opacity-0 transition-opacity hover:bg-destructive/10 hover:text-destructive group-hover:opacity-100"
                                  aria-label={`删除文档「${node.title}」`}
                                >
                                  <Trash2 className="size-3.5" />
                                </button>
                              </div>
                            )
                          })}
                        </div>
                      </AccordionContent>
                    </AccordionItem>
                  ))}
                </Accordion>
              )}
            </div>
          </ResizablePanel>

          <ResizableHandle />

          {/* 右侧编辑区 */}
          <ResizablePanel defaultSize={72}>
            <div className="h-full overflow-hidden">
              {selectedId === "new" ? (
                <DocumentEditor
                  createDoc={createDoc}
                  creating={creating}
                  onCreated={(id) => setSelectedId(id)}
                  onCancel={() => setSelectedId(null)}
                />
              ) : selectedId ? (
                <DocumentEditor key={selectedId} docId={selectedId} />
              ) : (
                <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
                  选择左侧文档
                </div>
              )}
            </div>
          </ResizablePanel>
        </ResizablePanelGroup>
      </div>

      <ConfirmDialog
        open={deletingNode !== null}
        onOpenChange={(open) => {
          if (!open) setDeletingNode(null)
        }}
        title="删除文档"
        description={`确定要删除「${deletingNode?.title}」吗？此操作不可撤销。`}
        confirmText="删除"
        variant="destructive"
        onConfirm={() => {
          if (deletingNode?.id == null) return
          const { id, title } = deletingNode
          deleteDoc(id, {
            onSuccess: () => {
              toast.success(`文档「${title}」已删除`)
              if (selectedId === id) setSelectedId(null)
            },
            onError: (err) =>
              toast.error(`删除失败：${err instanceof Error ? err.message : "未知错误"}`)
          })
        }}
      />
    </div>
  )
}
