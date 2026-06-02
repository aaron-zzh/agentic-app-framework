/**
 * 文档管理页面——左侧分组文档树 + 右侧内容/关系图谱
 * @author AaronZZH & Kiro
 */
"use client"

import { useQueryClient } from "@tanstack/react-query"
import { Bot, Edit, FileText, Plus, RefreshCw } from "lucide-react"
import { useCallback, useMemo, useState } from "react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { toast } from "sonner"
import { PageContainer } from "@/components/common/PageContainer"
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger
} from "@/components/ui/accordion"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { KiroAgentDrawer } from "@/features/livechat/kiro/KiroAgentDrawer"
import { RichTextEditor } from "@/features/rich-text-editor"
import { useDocEvents } from "@/lib/hooks/use-doc-events"
import {
  docKeys,
  useDocTree,
  useDocument,
  useImportDocs,
  useUpdateDocument
} from "@/lib/queries/use-documents"
import type { DocTreeNode } from "@/lib/types/document"
import { DocCreateDialog } from "./DocCreateDialog"
import { DocRelationGraph } from "./DocRelationGraph"

/** 文档类型分组配置 */
const DOC_TYPE_GROUPS = [
  { key: "spec", label: "规格" },
  { key: "design", label: "设计" },
  { key: "task", label: "任务" },
  { key: "guide", label: "指南" },
  { key: "reference", label: "参考" },
  { key: "explanation", label: "说明" }
] as const

export default function DocsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editContent, setEditContent] = useState("")
  const [createOpen, setCreateOpen] = useState(false)
  const [kiroOpen, setKiroOpen] = useState(false)
  const [tab, setTab] = useState("content")

  const queryClient = useQueryClient()
  const { data: tree, isLoading: treeLoading } = useDocTree()
  const { data: doc, isLoading: docLoading } = useDocument(selectedId)
  const { mutate: update, isPending: saving } = useUpdateDocument()
  const { mutate: importDocs, isPending: importing } = useImportDocs()

  /** SSE 文档变更通知 */
  const handleDocUpdate = useCallback(() => {
    if (selectedId) {
      queryClient.invalidateQueries({ queryKey: docKeys.detail(selectedId) })
    }
  }, [selectedId, queryClient])

  useDocEvents(selectedId, handleDocUpdate)

  /** 按 docType 分组文档（扁平化树中的文件节点） */
  const groupedDocs = useMemo(() => {
    if (!tree) return new Map<string, DocTreeNode[]>()
    const map = new Map<string, DocTreeNode[]>()
    function collect(nodes: DocTreeNode[]) {
      for (const node of nodes) {
        if (node.isDir) {
          collect(node.children)
        } else if (node.id != null) {
          // 从路径推断 docType
          const docType = inferDocType(node.path)
          const list = map.get(docType) ?? []
          list.push(node)
          map.set(docType, list)
        }
      }
    }
    collect(tree)
    return map
  }, [tree])

  function handleSelectDoc(id: number) {
    setSelectedId(id)
    setTab("content")
  }

  function handleEdit() {
    if (!doc) return
    setEditContent(doc.content ?? "")
    setEditOpen(true)
  }

  function handleSave() {
    if (!selectedId) return
    update(
      { id: selectedId, content: editContent },
      {
        onSuccess: () => {
          setEditOpen(false)
          toast.success("文档已保存")
        },
        onError: () => toast.error("保存失败")
      }
    )
  }

  return (
    <PageContainer disablePadding>
      <ResizablePanelGroup direction="horizontal" className="h-[calc(100vh-8rem)]">
        {/* 左侧文档树 */}
        <ResizablePanel defaultSize={25} minSize={15}>
          <div className="flex h-full flex-col overflow-hidden border-r">
            <div className="flex items-center justify-between border-b px-3 py-2">
              <span className="font-medium text-sm">文档</span>
              <div className="flex gap-1">
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setCreateOpen(true)}
                  title="新建文档"
                >
                  <Plus className="size-4" />
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => importDocs()}
                  disabled={importing}
                  title="导入本地文档"
                >
                  <RefreshCw className={`size-4 ${importing ? "animate-spin" : ""}`} />
                </Button>
              </div>
            </div>
            <div className="flex-1 overflow-y-auto p-2">
              {treeLoading ? (
                <div className="space-y-2">
                  {Array.from({ length: 8 }).map((_, i) => (
                    <Skeleton key={`sk-${i.toString()}`} className="h-6 w-full" />
                  ))}
                </div>
              ) : (
                <Accordion multiple>
                  {DOC_TYPE_GROUPS.map((group) => {
                    const docs = groupedDocs.get(group.key) ?? []
                    if (docs.length === 0) return null
                    return (
                      <AccordionItem key={group.key} value={group.key}>
                        <AccordionTrigger className="py-1.5 font-medium text-muted-foreground text-xs uppercase">
                          {group.label}（{docs.length}）
                        </AccordionTrigger>
                        <AccordionContent>
                          <ul className="space-y-0.5">
                            {docs.map((node) => (
                              <li key={node.path}>
                                <button
                                  type="button"
                                  className={`flex w-full items-center gap-1.5 rounded px-2 py-1 text-left text-sm hover:bg-accent ${
                                    selectedId === node.id ? "bg-accent font-medium" : ""
                                  }`}
                                  onClick={() => node.id != null && handleSelectDoc(node.id)}
                                >
                                  <FileText className="size-3.5 shrink-0 text-blue-500" />
                                  <span className="truncate">{node.name}</span>
                                </button>
                              </li>
                            ))}
                          </ul>
                        </AccordionContent>
                      </AccordionItem>
                    )
                  })}
                </Accordion>
              )}
            </div>
          </div>
        </ResizablePanel>

        <ResizableHandle withHandle />

        {/* 右侧内容区 */}
        <ResizablePanel defaultSize={75}>
          <div className="flex h-full flex-col overflow-hidden">
            {!selectedId ? (
              <div className="flex h-full items-center justify-center text-muted-foreground">
                <div className="text-center">
                  <FileText className="mx-auto mb-2 size-12 opacity-30" />
                  <p>选择左侧文档查看内容</p>
                </div>
              </div>
            ) : (
              <>
                {/* 标题栏 */}
                <div className="flex items-center justify-between border-b px-4 py-3">
                  <h2 className="truncate font-semibold text-base">{doc?.title ?? "加载中..."}</h2>
                  <Button size="sm" variant="outline" onClick={handleEdit} disabled={!doc}>
                    <Edit className="mr-1 size-4" />
                    编辑
                  </Button>
                </div>

                {/* Tab 切换 */}
                <Tabs
                  value={tab}
                  onValueChange={(v) => setTab(v as string)}
                  className="flex flex-1 flex-col overflow-hidden"
                >
                  <TabsList className="mx-4 mt-2">
                    <TabsTrigger value="content">内容</TabsTrigger>
                    <TabsTrigger value="graph">关系图谱</TabsTrigger>
                  </TabsList>

                  <TabsContent value="content" className="flex-1 overflow-y-auto p-4">
                    {docLoading ? (
                      <div className="space-y-3">
                        {Array.from({ length: 10 }).map((_, i) => (
                          <Skeleton key={`sk-${i.toString()}`} className="h-4 w-full" />
                        ))}
                      </div>
                    ) : (
                      <div className="prose prose-sm dark:prose-invert max-w-none">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>
                          {doc?.content ?? ""}
                        </ReactMarkdown>
                      </div>
                    )}
                  </TabsContent>

                  <TabsContent value="graph" className="flex-1 overflow-hidden">
                    {selectedId && (
                      <DocRelationGraph docId={selectedId} onSelectDoc={handleSelectDoc} />
                    )}
                  </TabsContent>
                </Tabs>
              </>
            )}
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>

      {/* 新建文档弹窗 */}
      <DocCreateDialog open={createOpen} onOpenChange={setCreateOpen} />

      {/* 编辑弹窗 */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>编辑文档：{doc?.title}</DialogTitle>
          </DialogHeader>
          <RichTextEditor
            value={editContent}
            onChange={setEditContent}
            preset="document"
            mode="markdown"
            minHeight={400}
          />
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setEditOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Kiro Agent 悬浮按钮 */}
      <div className="fixed right-6 bottom-6 z-50">
        <Button size="icon" className="rounded-full shadow-lg" onClick={() => setKiroOpen(true)}>
          <Bot className="size-5" />
        </Button>
      </div>
      <KiroAgentDrawer open={kiroOpen} onOpenChange={setKiroOpen} />
    </PageContainer>
  )
}

/** 从文件路径推断文档类型 */
function inferDocType(path: string): string {
  if (path.includes("/prd/") || path.includes("/spec/")) return "spec"
  if (path.includes("/design/")) return "design"
  if (path.includes("/task/")) return "task"
  if (path.includes("/guide/")) return "guide"
  if (path.includes("/reference/")) return "reference"
  if (path.includes("/explanation/")) return "explanation"
  return "reference"
}
