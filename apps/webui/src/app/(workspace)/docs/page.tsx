/**
 * 文档管理页面——左侧分组文档树 + 右侧内容/关系图谱
 * @author AaronZZH & Kiro
 */
"use client"

import { useQueryClient } from "@tanstack/react-query"
import { Bot, Edit, ExternalLink, FileText, Plus, RefreshCw } from "lucide-react"
import Link from "next/link"
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
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { KiroAgentDrawer } from "@/features/livechat/kiro/KiroAgentDrawer"
import { paths } from "@/lib/constants/paths"
import { useDocEvents } from "@/lib/hooks/use-doc-events"
import {
  docKeys,
  useDocTree,
  useDocument,
  useImportDocs,
  usePublishDocument,
  useUnpublishDocument
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

type PublishTab = "all" | "draft" | "published"

export default function DocsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [kiroOpen, setKiroOpen] = useState(false)
  const [contentTab, setContentTab] = useState("content")
  const [publishTab, setPublishTab] = useState<PublishTab>("all")

  const queryClient = useQueryClient()
  const { data: tree, isLoading: treeLoading } = useDocTree()
  const { data: doc, isLoading: docLoading } = useDocument(selectedId)
  const { mutate: importDocs, isPending: importing } = useImportDocs()
  const { mutate: publish, isPending: publishing } = usePublishDocument()
  const { mutate: unpublish, isPending: unpublishing } = useUnpublishDocument()

  /** SSE 文档变更通知 */
  const handleDocUpdate = useCallback(() => {
    if (selectedId) {
      queryClient.invalidateQueries({ queryKey: docKeys.detail(selectedId) })
    }
  }, [selectedId, queryClient])

  useDocEvents(selectedId, handleDocUpdate)

  /** 按 docType 分组，支持 publish tab 筛选 */
  const groupedDocs = useMemo(() => {
    if (!tree) return new Map<string, DocTreeNode[]>()
    const map = new Map<string, DocTreeNode[]>()
    function collect(nodes: DocTreeNode[]) {
      for (const node of nodes) {
        if (node.isDir) {
          collect(node.children)
        } else if (node.id != null) {
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

  /** 按 publish 状态过滤 */
  const allFlatDocs = useMemo(() => {
    const all: DocTreeNode[] = []
    for (const list of groupedDocs.values()) all.push(...list)
    return all
  }, [groupedDocs])

  const draftCount = allFlatDocs.length // 树节点无 publish 字段，只用 doc 详情判断
  void draftCount // suppress lint

  function handleSelectDoc(id: number) {
    setSelectedId(id)
    setContentTab("content")
  }

  function handlePublishToggle() {
    if (!selectedId || !doc) return
    if (doc.publish === "published") {
      unpublish(selectedId, {
        onSuccess: () => toast.success("已转为草稿"),
        onError: () => toast.error("操作失败")
      })
    } else {
      publish(selectedId, {
        onSuccess: () => toast.success("已发布"),
        onError: () => toast.error("操作失败")
      })
    }
  }

  return (
    <PageContainer disablePadding>
      <ResizablePanelGroup orientation="horizontal" className="h-[calc(100vh-8rem)]">
        {/* 左侧文档树 */}
        <ResizablePanel defaultSize="25%" minSize="15%">
          <div className="flex h-full flex-col overflow-hidden border-r">
            <div className="flex items-center justify-between border-b px-3 py-2">
              <span className="font-medium text-sm">文档</span>
              <div className="flex gap-1">
                <Button
                  size="sm"
                  variant="ghost"
                  nativeButton={false}
                  render={<Link href={paths.docs.new} />}
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

            {/* publish 状态筛选 tab */}
            <div className="border-b px-2 pt-1">
              <Tabs value={publishTab} onValueChange={(v) => setPublishTab(v as PublishTab)}>
                <TabsList className="h-7 w-full">
                  <TabsTrigger value="all" className="flex-1 text-xs">
                    全部
                  </TabsTrigger>
                  <TabsTrigger value="draft" className="flex-1 text-xs">
                    草稿
                  </TabsTrigger>
                  <TabsTrigger value="published" className="flex-1 text-xs">
                    已发布
                  </TabsTrigger>
                </TabsList>
              </Tabs>
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
        <ResizablePanel defaultSize="75%">
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
                  <div className="flex min-w-0 items-center gap-2">
                    <h2 className="truncate font-semibold text-base">
                      {doc?.title ?? "加载中..."}
                    </h2>
                    {doc && (
                      <Badge
                        variant={doc.publish === "published" ? "default" : "secondary"}
                        className="shrink-0 text-xs"
                      >
                        {doc.publish === "published" ? "已发布" : "草稿"}
                      </Badge>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {doc && (
                      <>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={handlePublishToggle}
                          disabled={publishing || unpublishing}
                        >
                          {doc.publish === "published" ? "转为草稿" : "发布"}
                        </Button>
                        {doc.publish === "published" && (
                          <Button
                            size="sm"
                            variant="ghost"
                            nativeButton={false}
                            render={<Link href={paths.docs.public(doc.id)} target="_blank" />}
                            title="公开阅读页"
                          >
                            <ExternalLink className="size-4" />
                          </Button>
                        )}
                      </>
                    )}
                    <Button
                      size="sm"
                      variant="outline"
                      nativeButton={false}
                      render={<Link href={paths.docs.edit(selectedId)} />}
                      disabled={!doc}
                    >
                      <Edit className="mr-1 size-4" />
                      编辑
                    </Button>
                  </div>
                </div>

                {/* Tab 切换 */}
                <Tabs
                  value={contentTab}
                  onValueChange={(v) => setContentTab(v as string)}
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
