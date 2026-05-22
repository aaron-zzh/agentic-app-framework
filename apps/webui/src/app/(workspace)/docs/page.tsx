/**
 * 文档管理页面——文档树 + 内容展示 + 弹窗编辑 + 关系图谱
 * @author AaronZZH & Kiro
 */
"use client"

import { useState } from "react"
import { FileText, FolderOpen, Folder, RefreshCw, Edit } from "lucide-react"
import { PageContainer } from "@/components/common/PageContainer"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { useDocTree, useDocument, useUpdateDocument, useImportDocs } from "@/lib/queries/use-documents"
import type { DocTreeNode } from "@/lib/types/document"
import { DocRelationGraph } from "./DocRelationGraph"
import { RichTextEditor } from "@/features/rich-text-editor"
import { toast } from "sonner"

export default function DocsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editContent, setEditContent] = useState("")
  const [tab, setTab] = useState("content")

  const { data: tree, isLoading: treeLoading } = useDocTree()
  const { data: doc, isLoading: docLoading } = useDocument(selectedId)
  const { mutate: update, isPending: saving } = useUpdateDocument()
  const { mutate: importDocs, isPending: importing } = useImportDocs()

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
        onError: () => toast.error("保存失败"),
      }
    )
  }

  return (
    <PageContainer disablePadding>
      <div className="flex h-[calc(100vh-8rem)] gap-4 p-4">
        {/* 左侧文档树 */}
        <div className="w-64 shrink-0 overflow-y-auto rounded-lg border p-3">
          <div className="mb-3 flex items-center justify-between">
            <span className="text-sm font-medium">文档</span>
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
          {treeLoading ? (
            <div className="space-y-2">
              {Array.from({ length: 8 }).map((_, i) => (
                <Skeleton key={`sk-${i}`} className="h-6 w-full" />
              ))}
            </div>
          ) : (
            <DocTree nodes={tree ?? []} selectedId={selectedId} onSelect={handleSelectDoc} />
          )}
        </div>

        {/* 右侧内容区 */}
        <div className="flex-1 overflow-hidden rounded-lg border">
          {!selectedId ? (
            <div className="flex h-full items-center justify-center text-muted-foreground">
              <div className="text-center">
                <FileText className="mx-auto mb-2 size-12 opacity-30" />
                <p>选择左侧文档查看内容</p>
              </div>
            </div>
          ) : (
            <div className="flex h-full flex-col">
              {/* 标题栏 */}
              <div className="flex items-center justify-between border-b px-4 py-3">
                <h2 className="truncate text-base font-semibold">
                  {doc?.title ?? "加载中..."}
                </h2>
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
                        <Skeleton key={`sk-${i}`} className="h-4 w-full" />
                      ))}
                    </div>
                  ) : (
                    <pre className="whitespace-pre-wrap font-mono text-sm">
                      {doc?.content}
                    </pre>
                  )}
                </TabsContent>

                <TabsContent value="graph" className="flex-1 overflow-hidden">
                  {selectedId && (
                    <DocRelationGraph docId={selectedId} onSelectDoc={handleSelectDoc} />
                  )}
                </TabsContent>
              </Tabs>
            </div>
          )}
        </div>
      </div>

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
    </PageContainer>
  )
}

/** 文档树组件 */
function DocTree({
  nodes,
  selectedId,
  onSelect,
  depth = 0,
}: {
  nodes: DocTreeNode[]
  selectedId: number | null
  onSelect: (id: number) => void
  depth?: number
}) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  function toggle(path: string) {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(path)) next.delete(path)
      else next.add(path)
      return next
    })
  }

  return (
    <ul className="space-y-0.5">
      {nodes.map((node) => (
        <li key={node.path}>
          {node.isDir ? (
            <>
              <button
                type="button"
                className="flex w-full items-center gap-1.5 rounded px-2 py-1 text-left text-sm hover:bg-accent"
                style={{ paddingLeft: `${depth * 12 + 8}px` }}
                onClick={() => toggle(node.path)}
              >
                {expanded.has(node.path) ? (
                  <FolderOpen className="size-4 shrink-0 text-yellow-500" />
                ) : (
                  <Folder className="size-4 shrink-0 text-yellow-500" />
                )}
                <span className="truncate">{node.name}</span>
              </button>
              {expanded.has(node.path) && (
                <DocTree
                  nodes={node.children}
                  selectedId={selectedId}
                  onSelect={onSelect}
                  depth={depth + 1}
                />
              )}
            </>
          ) : (
            <button
              type="button"
              className={`flex w-full items-center gap-1.5 rounded px-2 py-1 text-left text-sm hover:bg-accent ${
                selectedId === node.id ? "bg-accent font-medium" : ""
              }`}
              style={{ paddingLeft: `${depth * 12 + 8}px` }}
              onClick={() => node.id != null && onSelect(node.id)}
            >
              <FileText className="size-4 shrink-0 text-blue-500" />
              <span className="truncate">{node.name}</span>
            </button>
          )}
        </li>
      ))}
    </ul>
  )
}
