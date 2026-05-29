/**
 * 业务文档管理页面（/workspace/docs）
 * 对接 /api/docs，基础 CRUD
 * @author AaronZZH & Kiro
 */
"use client"

import { Edit, FileText, Plus } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { PageContainer } from "@/components/common/PageContainer"
import { DocTree } from "@/components/docs/DocTree"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { RichTextEditor } from "@/features/rich-text-editor"
import { useDocTree, useDocument, useUpdateDocument } from "@/lib/queries/use-documents"
import { DocCreateDialog } from "./DocCreateDialog"

export default function DocsPage() {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  const [editContent, setEditContent] = useState("")
  const [createOpen, setCreateOpen] = useState(false)

  const { data: tree, isLoading: treeLoading } = useDocTree()
  const { data: doc, isLoading: docLoading } = useDocument(selectedId)
  const { mutate: update, isPending: saving } = useUpdateDocument()

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
      <div className="flex h-[calc(100vh-8rem)] gap-4 p-4">
        {/* 左侧文档树 */}
        <div className="w-64 shrink-0 overflow-y-auto rounded-lg border p-3">
          <div className="mb-3 flex items-center justify-between">
            <span className="font-medium text-sm">文档</span>
            <Button size="sm" variant="ghost" onClick={() => setCreateOpen(true)} title="新建文档">
              <Plus className="size-4" />
            </Button>
          </div>
          {treeLoading ? (
            <div className="space-y-2">
              {Array.from({ length: 6 }).map((_, i) => (
                <Skeleton key={i} className="h-6 w-full" />
              ))}
            </div>
          ) : (
            <DocTree nodes={tree ?? []} selectedId={selectedId} onSelect={setSelectedId} />
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
              <div className="flex items-center justify-between border-b px-4 py-3">
                <h2 className="truncate font-semibold text-base">{doc?.title ?? "加载中..."}</h2>
                <Button size="sm" variant="outline" onClick={handleEdit} disabled={!doc}>
                  <Edit className="mr-1 size-4" />
                  编辑
                </Button>
              </div>
              <div className="flex-1 overflow-y-auto p-4">
                {docLoading ? (
                  <div className="space-y-3">
                    {Array.from({ length: 8 }).map((_, i) => (
                      <Skeleton key={i} className="h-4 w-full" />
                    ))}
                  </div>
                ) : (
                  <pre className="whitespace-pre-wrap font-mono text-sm">{doc?.content}</pre>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 编辑弹窗 */}
      <Dialog open={editOpen} onOpenChange={setEditOpen}>
        <DialogContent className="sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>编辑：{doc?.title}</DialogTitle>
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

      <DocCreateDialog open={createOpen} onOpenChange={setCreateOpen} />
    </PageContainer>
  )
}
