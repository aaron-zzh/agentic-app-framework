/**
 * /studio/knowledge/docs——文档管理（迁移自 workspace/docs，不 redirect）
 * 复用文档管理业务组件，外套 Studio 风格层
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { Bot, Edit, FileText, Plus, RefreshCw } from "lucide-react"
import Link from "next/link"
import { useCallback, useMemo, useState } from "react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { toast } from "sonner"
import {
  GlassCard,
  GlassCardBody,
  GlassCardHeader,
  GlassCardTitle,
  GlowButton,
  SectionHaze
} from "@/components/studio"
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

const DOC_TYPE_GROUPS = [
  { key: "spec", label: "规格" },
  { key: "design", label: "设计" },
  { key: "task", label: "任务" },
  { key: "guide", label: "指南" },
  { key: "reference", label: "参考" },
  { key: "explanation", label: "说明" }
] as const

type PublishTab = "all" | "draft" | "published"

/** 后端返回的 DocTreeNode 实际比共享类型宽（含 type/published/title），用扩展类型断言取 */
type DocTreeNodeExt = DocTreeNode & { type?: string; published?: boolean; title?: string }
/** 后端返回的 Document 实际比共享类型宽（含 published/type/path 兼容字段），用扩展类型断言取 */
type DocumentExt = {
  id: number
  title: string
  content: string | null
  docType: string
  publish: string
  filePath: string
  // 兼容老接口字段
  type?: string
  path?: string
  published?: boolean
}

// 复用 workspace/docs/page.tsx 的核心业务逻辑，外壳用 Studio 风格
export default function StudioKnowledgeDocsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [kiroOpen, setKiroOpen] = useState(false)
  const [contentTab, setContentTab] = useState("content")
  const [publishTab, setPublishTab] = useState<PublishTab>("all")
  const queryClient = useQueryClient()

  const { data: tree, isLoading: treeLoading } = useDocTree()
  const { data: doc, isLoading: docLoading } = useDocument(selectedId)
  const { mutate: importDocs, isPending: importing } = useImportDocs()
  const { mutate: publish } = usePublishDocument()
  const { mutate: unpublish } = useUnpublishDocument()

  useDocEvents(selectedId, () => {
    queryClient.invalidateQueries({ queryKey: docKeys.tree })
    if (selectedId) queryClient.invalidateQueries({ queryKey: docKeys.detail(selectedId) })
  })

  // 按类型分组 + publishTab 过滤
  const grouped = useMemo(() => {
    const allNodes = (tree ?? []).filter((n) => !n.isDir)
    const ext = (n: DocTreeNode): DocTreeNodeExt => n as DocTreeNodeExt
    const filtered = allNodes.filter((n) => {
      const e = ext(n)
      if (publishTab === "draft") return !e.published
      if (publishTab === "published") return e.published
      return true
    })
    return DOC_TYPE_GROUPS.map((g) => ({
      ...g,
      nodes: filtered.filter((n) => ext(n).type === g.key)
    })).filter((g) => g.nodes.length > 0)
  }, [tree, publishTab])

  const handleImport = useCallback(() => {
    // mutate 的 variables 类型推断为 unknown，显式 undefined 转 never 避免 TS2554
    ;(importDocs as (v: unknown, opts: { onSuccess: () => void; onError: () => void }) => void)(
      undefined,
      {
        onSuccess: () => toast.success("文档导入成功"),
        onError: () => toast.error("导入失败")
      }
    )
  }, [importDocs])

  // 标准化 doc 视图字段
  const docExt = doc ? (doc as unknown as DocumentExt) : null
  const isPublished = docExt ? Boolean(docExt.published) || docExt.publish === "published" : false
  const docType = docExt?.docType ?? docExt?.type ?? ""
  const docPath = docExt?.filePath ?? docExt?.path ?? ""

  return (
    <div className="relative h-full">
      <SectionHaze variant="blend" />
      <div className="relative flex h-full flex-col">
        {/* 顶栏工具 */}
        <div className="flex items-center gap-2 border-foreground/[0.06] border-b px-4 py-3">
          <h2 className="font-medium">文档管理</h2>
          <div className="ml-auto flex items-center gap-2">
            <Button variant="ghost" size="sm" disabled={importing} onClick={handleImport}>
              <RefreshCw className={`mr-1.5 size-4 ${importing ? "animate-spin" : ""}`} />
              同步
            </Button>
            <GlowButton tone="violet" size="sm">
              <Plus className="size-4" />
              新建
            </GlowButton>
            <Button variant="ghost" size="sm" onClick={() => setKiroOpen(true)}>
              <Bot className="size-4" />
            </Button>
          </div>
        </div>

        {/* 发布状态过滤 */}
        <div className="border-foreground/[0.04] border-b px-4 py-2">
          <Tabs value={publishTab} onValueChange={(v) => setPublishTab(v as PublishTab)}>
            <TabsList className="h-8">
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
        </div>

        {/* 主体：左树 + 右内容 */}
        <ResizablePanelGroup orientation="horizontal" className="min-h-0 flex-1">
          {/* 文档树 */}
          <ResizablePanel defaultSize={30} minSize={20}>
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
                    <AccordionItem key={g.key} value={g.key} className="border-foreground/[0.06]">
                      <AccordionTrigger className="py-2 text-muted-foreground text-xs hover:no-underline">
                        {g.label} ({g.nodes.length})
                      </AccordionTrigger>
                      <AccordionContent>
                        <div className="space-y-0.5">
                          {g.nodes.map((node) => {
                            const extNode = node as DocTreeNodeExt
                            const nodeId = extNode.id ?? -1
                            return (
                              <button
                                key={`doc-${node.path}`}
                                type="button"
                                onClick={() => setSelectedId(nodeId)}
                                className={`flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors hover:bg-foreground/[0.05] ${selectedId === nodeId ? "bg-foreground/[0.08]" : ""}`}
                              >
                                <FileText className="size-3.5 shrink-0 text-muted-foreground" />
                                <span className="truncate">{extNode.title ?? extNode.name}</span>
                                {!extNode.published && (
                                  <Badge variant="outline" className="ml-auto shrink-0 text-[10px]">
                                    草稿
                                  </Badge>
                                )}
                              </button>
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

          {/* 内容区 */}
          <ResizablePanel defaultSize={70}>
            <div className="h-full overflow-y-auto p-4">
              {!selectedId ? (
                <div className="flex h-full items-center justify-center">
                  <p className="text-muted-foreground text-sm">← 选择左侧文档查看内容</p>
                </div>
              ) : docLoading ? (
                <div className="space-y-3">
                  <Skeleton className="h-8 w-48" />
                  <Skeleton className="h-64" />
                </div>
              ) : docExt ? (
                <GlassCard glow="none">
                  <GlassCardHeader>
                    <GlassCardTitle>{docExt.title}</GlassCardTitle>
                    <div className="flex items-center gap-2">
                      {isPublished ? (
                        <GlowButton tone="violet" size="sm" onClick={() => unpublish(docExt.id)}>
                          取消发布
                        </GlowButton>
                      ) : (
                        <GlowButton tone="violet" size="sm" onClick={() => publish(docExt.id)}>
                          发布
                        </GlowButton>
                      )}
                      <Link href={paths.workspace.record("document", String(docExt.id))}>
                        <Button variant="ghost" size="sm">
                          <Edit className="size-4" />
                        </Button>
                      </Link>
                    </div>
                  </GlassCardHeader>
                  <GlassCardBody>
                    <Tabs value={contentTab} onValueChange={setContentTab}>
                      <TabsList className="mb-4">
                        <TabsTrigger value="content">内容</TabsTrigger>
                        <TabsTrigger value="meta">元信息</TabsTrigger>
                      </TabsList>
                      <TabsContent value="content">
                        <div className="prose prose-sm dark:prose-invert max-w-none">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>
                            {docExt.content ?? ""}
                          </ReactMarkdown>
                        </div>
                      </TabsContent>
                      <TabsContent value="meta">
                        <dl className="space-y-3 text-sm">
                          <div>
                            <dt className="text-muted-foreground">类型</dt>
                            <dd>{docType}</dd>
                          </div>
                          <div>
                            <dt className="text-muted-foreground">路径</dt>
                            <dd className="font-mono text-xs">{docPath}</dd>
                          </div>
                          <div>
                            <dt className="text-muted-foreground">状态</dt>
                            <dd>{isPublished ? "已发布" : "草稿"}</dd>
                          </div>
                        </dl>
                      </TabsContent>
                    </Tabs>
                  </GlassCardBody>
                </GlassCard>
              ) : null}
            </div>
          </ResizablePanel>
        </ResizablePanelGroup>
      </div>

      <KiroAgentDrawer open={kiroOpen} onOpenChange={(o) => !o && setKiroOpen(false)} />
    </div>
  )
}
