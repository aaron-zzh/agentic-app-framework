/**
 * 项目文档面板：管理项目对现有文档的引用，并复用共享文档编辑器。
 * @author AaronZZH & Kiro
 */

"use client"

import { BookOpen, FilePlus2, FileText, Link2, Loader2, Trash2 } from "lucide-react"
import { useEffect, useId, useMemo, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle
} from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import type { AigcProject, AigcProjectDocumentRef } from "@/lib/api/rest/ai/aigc"
import {
  useAigcProjectDocumentRefs,
  useAttachAigcProjectDocument,
  useDetachAigcProjectDocument
} from "@/lib/api/rest/ai/aigc"
import { useCreateDocument, useDocList } from "@/lib/api/rest/system/document"
import { cn } from "@/lib/utils/index"
import { DocumentEditor } from "./DocumentEditor"

interface ProjectDocumentPanelProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  project: AigcProject
  readOnly: boolean
}

/** 展示并管理当前项目关联的外部文档。 */
export function ProjectDocumentPanel({
  open,
  onOpenChange,
  project,
  readOnly
}: ProjectDocumentPanelProps) {
  const { data: references = [], isLoading: refsLoading } = useAigcProjectDocumentRefs(project.id)
  const { data: documents = [], isLoading: docsLoading } = useDocList()
  const attachDocument = useAttachAigcProjectDocument()
  const detachDocument = useDetachAigcProjectDocument()
  const createDocument = useCreateDocument()
  const uid = useId()
  const [selectedDocumentId, setSelectedDocumentId] = useState<number | null>(null)
  const [documentToAttach, setDocumentToAttach] = useState("")
  const [referenceToDetach, setReferenceToDetach] = useState<AigcProjectDocumentRef | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [newDocTitle, setNewDocTitle] = useState("")

  const referencedIds = useMemo(
    () => new Set(references.map((reference) => reference.documentVersionId)),
    [references]
  )
  const availableDocuments = useMemo(
    () => documents.filter((document) => !referencedIds.has(document.id)),
    [documents, referencedIds]
  )
  const documentsById = useMemo(
    () => new Map(documents.map((document) => [document.id, document])),
    [documents]
  )

  useEffect(() => {
    if (!open) return
    if (
      selectedDocumentId !== null &&
      references.some((reference) => reference.documentVersionId === selectedDocumentId)
    ) {
      return
    }
    setSelectedDocumentId(references.at(0)?.documentVersionId ?? null)
  }, [open, references, selectedDocumentId])

  function handleAttach() {
    if (readOnly) return
    const documentId = Number(documentToAttach)
    if (!Number.isFinite(documentId) || documentId <= 0) return
    attachDocument.mutate(
      {
        projectId: project.id,
        documentVersionId: documentId,
        role: "project",
        expectedProjectVersion: project.version
      },
      {
        onSuccess: () => {
          setDocumentToAttach("")
          setSelectedDocumentId(documentId)
          toast.success("文档已关联到项目")
        },
        onError: (error) =>
          toast.error(`关联失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  function handleDetach() {
    if (readOnly || !referenceToDetach) return
    const detachedDocumentId = referenceToDetach.documentVersionId
    detachDocument.mutate(
      {
        projectId: project.id,
        refId: referenceToDetach.id,
        expectedProjectVersion: project.version
      },
      {
        onSuccess: () => {
          if (selectedDocumentId === detachedDocumentId) setSelectedDocumentId(null)
          setReferenceToDetach(null)
          toast.success("已解除项目文档关联")
        },
        onError: (error) =>
          toast.error(`解除失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  function handleCreateDocument(e: React.FormEvent) {
    e.preventDefault()
    if (readOnly) return
    const title = newDocTitle.trim()
    if (!title) return
    createDocument.mutate(
      { title, filePath: `docs/project/${project.id}/${Date.now()}.md`, docType: "reference" },
      {
        onSuccess: (created) => {
          setCreateOpen(false)
          setNewDocTitle("")
          const createdId = created.id
          if (createdId === null) {
            toast.success("文档创建成功")
            return
          }
          attachDocument.mutate(
            {
              projectId: project.id,
              documentVersionId: createdId,
              role: "project",
              expectedProjectVersion: project.version
            },
            {
              onSuccess: () => {
                setSelectedDocumentId(createdId)
                toast.success("文档已创建并关联到项目")
              },
              onError: (error) =>
                toast.error(`关联失败：${error instanceof Error ? error.message : "未知错误"}`)
            }
          )
        },
        onError: (error) =>
          toast.error(`创建失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  return (
    <>
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="w-[min(96vw,1870px)] gap-0 p-0 sm:max-w-[1870px]">
          <SheetHeader className="border-b pr-12">
            <SheetTitle className="flex items-center gap-2">
              <BookOpen />
              项目文档
            </SheetTitle>
            <SheetDescription>
              当前关联 {references.length} 篇文档；正文仍由文档模块统一管理。
            </SheetDescription>
          </SheetHeader>

          <div className="grid min-h-0 flex-1 grid-cols-1 md:grid-cols-[280px_minmax(0,1fr)]">
            <aside className="flex min-h-0 flex-col border-b md:border-r md:border-b-0">
              {!readOnly ? (
                <div className="flex gap-2 border-b p-3">
                  <Select
                    value={documentToAttach}
                    onValueChange={(value) => setDocumentToAttach(value ?? "")}
                  >
                    <SelectTrigger className="min-w-0 flex-1" aria-label="选择要关联的文档">
                      <SelectValue placeholder={docsLoading ? "加载文档..." : "选择现有文档"} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {availableDocuments.map((document) => (
                          <SelectItem key={document.id} value={String(document.id)}>
                            {document.title}
                          </SelectItem>
                        ))}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                  <Button
                    size="icon"
                    aria-label="关联所选文档"
                    disabled={!documentToAttach || attachDocument.isPending}
                    onClick={handleAttach}
                  >
                    {attachDocument.isPending ? <Loader2 className="animate-spin" /> : <Link2 />}
                  </Button>
                  <Button
                    variant="outline"
                    size="icon"
                    aria-label="新建文档"
                    onClick={() => setCreateOpen(true)}
                  >
                    <FilePlus2 />
                  </Button>
                </div>
              ) : null}

              <div className="min-h-0 flex-1 overflow-auto p-2">
                {refsLoading ? (
                  <div className="flex flex-col gap-2 p-1">
                    {Array.from({ length: 4 }, (_, index) => (
                      <Skeleton key={`project-doc-${index}`} className="h-10" />
                    ))}
                  </div>
                ) : references.length === 0 ? (
                  <Empty className="h-full border-0">
                    <EmptyHeader>
                      <EmptyMedia variant="icon">
                        <FileText />
                      </EmptyMedia>
                      <EmptyTitle>暂无项目文档</EmptyTitle>
                      <EmptyDescription>
                        {readOnly ? "项目当前为只读状态" : "从上方选择现有文档进行关联"}
                      </EmptyDescription>
                    </EmptyHeader>
                  </Empty>
                ) : (
                  <div className="flex flex-col gap-1">
                    {references.map((reference) => {
                      const document = documentsById.get(reference.documentVersionId)
                      const selected = selectedDocumentId === reference.documentVersionId
                      return (
                        <div
                          key={reference.id}
                          className={cn(
                            "group flex items-center gap-1 rounded-lg",
                            selected ? "bg-accent" : "hover:bg-accent/60"
                          )}
                        >
                          <button
                            type="button"
                            className="flex min-w-0 flex-1 items-center gap-2 px-3 py-2 text-left"
                            onClick={() => setSelectedDocumentId(reference.documentVersionId)}
                          >
                            <FileText className="shrink-0 text-muted-foreground" />
                            <span className="truncate text-sm">
                              {document?.title ?? `文档 #${reference.documentVersionId}`}
                            </span>
                          </button>
                          {!readOnly ? (
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className="mr-1 opacity-0 group-hover:opacity-100"
                              aria-label={`解除文档「${document?.title ?? reference.documentVersionId}」的关联`}
                              onClick={() => setReferenceToDetach(reference)}
                            >
                              <Trash2 />
                            </Button>
                          ) : null}
                        </div>
                      )
                    })}
                  </div>
                )}
              </div>
            </aside>

            <section className="min-h-0 overflow-hidden">
              {selectedDocumentId ? (
                <DocumentEditor
                  key={selectedDocumentId}
                  docId={selectedDocumentId}
                  initialView="preview"
                  allowEditing
                />
              ) : (
                <Empty className="h-full border-0">
                  <EmptyHeader>
                    <EmptyMedia variant="icon">
                      <BookOpen />
                    </EmptyMedia>
                    <EmptyTitle>选择项目文档</EmptyTitle>
                    <EmptyDescription>选择左侧文档后可预览或修改正文。</EmptyDescription>
                  </EmptyHeader>
                </Empty>
              )}
            </section>
          </div>
        </SheetContent>
      </Sheet>

      <ConfirmDialog
        open={referenceToDetach !== null}
        onOpenChange={(nextOpen) => {
          if (!nextOpen) setReferenceToDetach(null)
        }}
        title="解除项目文档关联"
        description="解除后文档本身不会被删除，仍可在文档管理中查看。"
        confirmText="解除关联"
        variant="destructive"
        onConfirm={handleDetach}
      />

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>新建文档</DialogTitle>
            <DialogDescription>创建后将自动关联到当前项目。</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateDocument} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor={`${uid}-new-doc-title`}>标题</Label>
              <Input
                id={`${uid}-new-doc-title`}
                value={newDocTitle}
                onChange={(e) => setNewDocTitle(e.target.value)}
                placeholder="文档标题"
                required
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                取消
              </Button>
              <Button type="submit" disabled={createDocument.isPending || attachDocument.isPending}>
                {createDocument.isPending || attachDocument.isPending ? "创建中..." : "创建并关联"}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}
