/**
 * /studio/knowledge/docs——文档管理
 * 左侧分类树 + 右侧 DocEditor 内联编辑（复用 ProjectDocPanel 的 DocEditor 模式）
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { FileText, Loader2, Plus } from "lucide-react"
import { useEffect, useMemo, useRef, useState } from "react"
import { SectionHaze } from "@/components/studio"
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
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { RichTextEditorHandle } from "@/features/rich-text-editor"
import { RichTextEditor } from "@/features/rich-text-editor"
import type { DocListItem } from "@/lib/api/rest/system/document"
import { useDocEvents } from "@/lib/hooks/use-doc-events"
import {
  docKeys,
  useCreateDocument,
  useDocList,
  useDocument,
  usePublishDocument,
  useUnpublishDocument,
  useUpdateDocument
} from "@/lib/queries/use-documents"

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
  const queryClient = useQueryClient()

  const { data: list, isLoading: treeLoading } = useDocList()
  const { mutate: createDocMutate, isPending: creating } = useCreateDocument()

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
                              <button
                                key={`doc-${node.id}`}
                                type="button"
                                onClick={() => setSelectedId(nodeId)}
                                className={`flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm transition-colors hover:bg-foreground/[0.05] ${selectedId === nodeId ? "bg-foreground/[0.08]" : ""}`}
                              >
                                <FileText className="size-3.5 shrink-0 text-muted-foreground" />
                                <span className="truncate">{node.title}</span>
                                {node.publish !== "published" && (
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

          {/* 右侧编辑区 */}
          <ResizablePanel defaultSize={72}>
            <div className="h-full overflow-hidden">
              {selectedId === "new" ? (
                <DocEditor
                  createDoc={createDoc}
                  creating={creating}
                  onCreated={(id) => setSelectedId(id)}
                  onCancel={() => setSelectedId(null)}
                />
              ) : selectedId ? (
                <DocEditor key={selectedId} docId={selectedId} />
              ) : (
                <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
                  选择左侧文档
                </div>
              )}
            </div>
          </ResizablePanel>
        </ResizablePanelGroup>
      </div>
    </div>
  )
}

/** 文档编辑器（与 ProjectDocPanel 的 DocEditor 同构） */
function DocEditor({
  docId,
  createDoc,
  creating,
  onCreated,
  onCancel
}: {
  docId?: number
  createDoc?: (
    p: { title: string; content: string },
    opts: { onSuccess: (doc: { id: number }) => void }
  ) => void
  creating?: boolean
  onCreated?: (docId: number) => void
  onCancel?: () => void
}) {
  const isNew = !docId
  const { data: doc, isLoading } = useDocument(isNew ? null : (docId ?? null))
  const { mutate: updateDoc, isPending: saving } = useUpdateDocument()
  const { mutate: publish } = usePublishDocument()
  const { mutate: unpublish } = useUnpublishDocument()

  const [mode, setMode] = useState<"wysiwyg" | "markdown">("wysiwyg")
  const [editorKey, setEditorKey] = useState(0)
  const [initMode, setInitMode] = useState<"html" | "markdown">("markdown")
  const [title, setTitle] = useState("")
  const [content, setContent] = useState("")
  const [dirty, setDirty] = useState(false)
  const titleInputRef = useRef<HTMLInputElement>(null)
  const editorRef = useRef<RichTextEditorHandle>(null)

  useEffect(() => {
    if (isNew) titleInputRef.current?.focus()
  }, [isNew])

  useEffect(() => {
    if (doc) {
      setTitle(doc.title ?? "")
      setContent(doc.content ?? "")
      setDirty(false)
    }
  }, [doc])

  function handleModeChange(v: string) {
    const newMode = v as "wysiwyg" | "markdown"
    if (newMode === "markdown") {
      const md = editorRef.current?.getContent("markdown") ?? content
      setContent(md)
    } else {
      setInitMode("markdown")
      setEditorKey((k) => k + 1)
    }
    setMode(newMode)
  }

  function handleSave() {
    const saveContent =
      mode === "wysiwyg" ? (editorRef.current?.getContent("markdown") ?? content) : content
    if (isNew) {
      if (!title.trim() || !createDoc) return
      createDoc(
        { title: title.trim(), content: saveContent },
        { onSuccess: (created) => onCreated?.(created.id) }
      )
    } else {
      updateDoc({ id: docId ?? 0, title, content: saveContent })
      setDirty(false)
    }
  }

  const isPublished = doc?.publish === "published"

  if (!isNew && isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col">
      {/* 顶栏 */}
      <div className="flex items-center gap-2 border-b px-4 py-2">
        <input
          ref={titleInputRef}
          className="min-w-0 flex-1 bg-transparent font-medium text-sm outline-none placeholder:text-muted-foreground"
          placeholder="文档标题..."
          value={title}
          onChange={(e) => {
            setTitle(e.target.value)
            setDirty(true)
          }}
        />
        {!isNew &&
          doc &&
          (isPublished ? (
            <Button
              variant="outline"
              size="sm"
              className="text-xs"
              onClick={() => unpublish(doc.id)}
            >
              取消发布
            </Button>
          ) : (
            <Button variant="outline" size="sm" className="text-xs" onClick={() => publish(doc.id)}>
              发布
            </Button>
          ))}
        <Tabs value={mode} onValueChange={handleModeChange}>
          <TabsList className="h-7">
            <TabsTrigger value="wysiwyg" className="px-2 text-xs">
              易读
            </TabsTrigger>
            <TabsTrigger value="markdown" className="px-2 text-xs">
              Markdown
            </TabsTrigger>
          </TabsList>
        </Tabs>
        {isNew && (
          <Button variant="ghost" size="sm" className="text-xs" onClick={onCancel}>
            取消
          </Button>
        )}
        <Button
          size="sm"
          className="text-xs"
          disabled={isNew ? !title.trim() || !!creating : !dirty || saving}
          onClick={handleSave}
        >
          {(creating || saving) && <Loader2 className="mr-1 size-3 animate-spin" />}
          保存
        </Button>
      </div>

      {/* 内容区 */}
      <div className="min-h-0 flex-1 overflow-auto">
        {mode === "markdown" ? (
          <textarea
            className="h-full w-full resize-none bg-transparent p-3 font-mono text-sm outline-none"
            value={content}
            onChange={(e) => {
              setContent(e.target.value)
              setDirty(true)
            }}
            spellCheck={false}
          />
        ) : (
          <RichTextEditor
            key={editorKey}
            ref={editorRef}
            value={content}
            onChange={() => setDirty(true)}
            preset="document"
            mode="html"
            initialValueMode={initMode}
            fill
            noBorder
            className="h-full"
          />
        )}
      </div>
    </div>
  )
}
