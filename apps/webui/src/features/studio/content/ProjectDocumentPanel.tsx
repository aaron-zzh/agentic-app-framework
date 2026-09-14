/**
 * 项目文档面板：管理项目对现有文档的引用，并复用共享文档编辑器。
 * @author AaronZZH & Kiro
 */

"use client"

import { BookOpen, FilePlus2, FileText, Link2, Loader2, RefreshCw, Trash2 } from "lucide-react"
import { useEffect, useId, useMemo, useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
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
  useDetachAigcProjectDocument,
  useUpdateAigcProjectDocumentToCurrent
} from "@/lib/api/rest/ai/aigc"
import { useCreateDocument, useDocList, useDocument } from "@/lib/api/rest/system/document"
import { cn } from "@/lib/utils/index"
import { DocumentEditor } from "./DocumentEditor"

interface ProjectDocumentPanelProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  project: AigcProject
  readOnly: boolean
}

/** 展示并管理项目自有文档与外部固定版本引用。 */
export function ProjectDocumentPanel({
  open,
  onOpenChange,
  project,
  readOnly
}: ProjectDocumentPanelProps) {
  const {
    data: references = [],
    isLoading: refsLoading,
    refetch: refetchReferences
  } = useAigcProjectDocumentRefs(project.id)
  const { data: documents = [], isLoading: docsLoading } = useDocList()
  const attachDocument = useAttachAigcProjectDocument()
  const detachDocument = useDetachAigcProjectDocument()
  const updateDocumentToCurrent = useUpdateAigcProjectDocumentToCurrent()
  const createDocument = useCreateDocument()
  const uid = useId()
  const [selectedReferenceId, setSelectedReferenceId] = useState<number | null>(null)
  const [documentToAttach, setDocumentToAttach] = useState("")
  const [referenceToDetach, setReferenceToDetach] = useState<AigcProjectDocumentRef | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [newDocTitle, setNewDocTitle] = useState("")
  const documentToAttachId = Number(documentToAttach)
  const { data: documentToAttachDetail, isFetching: documentToAttachLoading } = useDocument(
    Number.isInteger(documentToAttachId) && documentToAttachId > 0 ? documentToAttachId : null
  )

  const referencedIds = useMemo(
    () => new Set(references.map((reference) => reference.documentId)),
    [references]
  )
  const availableDocuments = useMemo(
    () => documents.filter((document) => !referencedIds.has(document.id)),
    [documents, referencedIds]
  )
  const selectedReference = useMemo(
    () => references.find((reference) => reference.id === selectedReferenceId) ?? null,
    [references, selectedReferenceId]
  )

  useEffect(() => {
    if (!open) return
    if (
      selectedReferenceId !== null &&
      references.some((reference) => reference.id === selectedReferenceId)
    ) {
      return
    }
    setSelectedReferenceId(references.at(0)?.id ?? null)
  }, [open, references, selectedReferenceId])

  function handleAttach() {
    if (readOnly) return
    const documentId = Number(documentToAttach)
    if (!Number.isFinite(documentId) || documentId <= 0) return
    if (!documentToAttachDetail?.currentVersionId) {
      toast.error("所选文档尚无可关联版本")
      return
    }
    attachDocument.mutate(
      {
        projectId: project.id,
        referenceType: "EXTERNAL_REFERENCE",
        documentVersionId: documentToAttachDetail.currentVersionId,
        role: "project",
        expectedProjectVersion: project.version
      },
      {
        onSuccess: (reference) => {
          setDocumentToAttach("")
          setSelectedReferenceId(reference.id)
          toast.success("文档已关联到项目")
        },
        onError: (error) =>
          toast.error(`关联失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  function handleDetach() {
    if (readOnly || !referenceToDetach) return
    const detachedReferenceId = referenceToDetach.id
    detachDocument.mutate(
      {
        projectId: project.id,
        refId: referenceToDetach.id,
        expectedProjectVersion: project.version
      },
      {
        onSuccess: () => {
          if (selectedReferenceId === detachedReferenceId) setSelectedReferenceId(null)
          setReferenceToDetach(null)
          toast.success("已解除项目文档关联")
        },
        onError: (error) =>
          toast.error(`解除失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  function handleUpdateToCurrent(reference: AigcProjectDocumentRef) {
    if (readOnly || reference.referenceType !== "EXTERNAL_REFERENCE" || !reference.outdated) {
      return
    }
    updateDocumentToCurrent.mutate(
      {
        projectId: project.id,
        refId: reference.id,
        expectedProjectVersion: project.version
      },
      {
        onSuccess: () => toast.success("外部引用已更新到源文档当前版本"),
        onError: (error) =>
          toast.error(`更新失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  function handleCreateDocument(e: React.FormEvent) {
    e.preventDefault()
    if (readOnly) return
    const title = newDocTitle.trim()
    if (!title) return
    createDocument.mutate(
      { title, docType: "project" },
      {
        onSuccess: (created) => {
          setCreateOpen(false)
          setNewDocTitle("")
          const createdId = created.id
          if (createdId === null) {
            toast.error("文档已创建，但未返回文档身份")
            return
          }
          attachDocument.mutate(
            {
              projectId: project.id,
              referenceType: "PROJECT_DOCUMENT",
              documentId: createdId,
              role: "project",
              expectedProjectVersion: project.version
            },
            {
              onSuccess: (reference) => {
                setSelectedReferenceId(reference.id)
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
        <SheetContent
          side="right"
          className="w-[80vw] gap-0 p-0 data-[side=right]:w-[80vw] data-[side=right]:sm:max-w-[80vw]"
        >
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
                    disabled={
                      !documentToAttach ||
                      documentToAttachLoading ||
                      !documentToAttachDetail?.currentVersionId ||
                      attachDocument.isPending
                    }
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
                      const selected = selectedReferenceId === reference.id
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
                            onClick={() => setSelectedReferenceId(reference.id)}
                          >
                            <FileText className="shrink-0 text-muted-foreground" />
                            <span className="min-w-0 flex-1 truncate text-sm">
                              {reference.title}
                            </span>
                            <span className="flex shrink-0 items-center gap-1">
                              <Badge
                                variant={
                                  reference.referenceType === "PROJECT_DOCUMENT"
                                    ? "default"
                                    : "secondary"
                                }
                                className="text-[10px]"
                              >
                                {reference.referenceType === "PROJECT_DOCUMENT"
                                  ? "项目文档"
                                  : "外部引用"}
                              </Badge>
                              <span className="text-muted-foreground text-xs">
                                v{reference.effectiveVersionNo}
                              </span>
                              {reference.referenceType === "EXTERNAL_REFERENCE" &&
                              reference.outdated ? (
                                <Badge variant="outline" className="text-[10px]">
                                  有更新
                                </Badge>
                              ) : null}
                            </span>
                          </button>
                          {!readOnly ? (
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className="mr-1 opacity-0 group-hover:opacity-100"
                              aria-label={`解除文档「${reference.title}」的关联`}
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

            <section className="flex min-h-0 flex-col overflow-hidden">
              {selectedReference ? (
                <>
                  {selectedReference.referenceType === "EXTERNAL_REFERENCE" ? (
                    <div className="flex flex-wrap items-center gap-2 border-b px-4 py-2 text-sm">
                      <Badge variant="secondary">外部固定版本</Badge>
                      <span className="text-muted-foreground">
                        当前使用 v{selectedReference.effectiveVersionNo}
                        {selectedReference.outdated
                          ? `，源文档已更新至 v${selectedReference.currentVersionNo}`
                          : "，与源文档当前版本一致"}
                      </span>
                      {!readOnly && selectedReference.outdated ? (
                        <Button
                          className="ml-auto"
                          variant="outline"
                          size="sm"
                          disabled={updateDocumentToCurrent.isPending}
                          onClick={() => handleUpdateToCurrent(selectedReference)}
                        >
                          {updateDocumentToCurrent.isPending ? (
                            <Loader2 data-icon="inline-start" className="animate-spin" />
                          ) : (
                            <RefreshCw data-icon="inline-start" />
                          )}
                          更新到当前版本
                        </Button>
                      ) : null}
                    </div>
                  ) : (
                    <div className="flex items-center gap-2 border-b px-4 py-2 text-sm">
                      <Badge>项目文档</Badge>
                      <span className="text-muted-foreground">
                        自动使用当前版本 v{selectedReference.effectiveVersionNo}
                      </span>
                    </div>
                  )}
                  <div className="min-h-0 flex-1 overflow-hidden">
                    <DocumentEditor
                      key={`${selectedReference.id}:${selectedReference.effectiveDocumentVersionId}`}
                      docId={selectedReference.documentId}
                      initialView="preview"
                      allowEditing={!readOnly}
                      onSaved={() => void refetchReferences()}
                      pinnedVersionId={
                        selectedReference.referenceType === "EXTERNAL_REFERENCE"
                          ? selectedReference.effectiveDocumentVersionId
                          : undefined
                      }
                      sourceHref={
                        selectedReference.referenceType === "EXTERNAL_REFERENCE"
                          ? `/docs/${selectedReference.documentId}/edit`
                          : undefined
                      }
                    />
                  </div>
                </>
              ) : (
                <Empty className="h-full border-0">
                  <EmptyHeader>
                    <EmptyMedia variant="icon">
                      <BookOpen />
                    </EmptyMedia>
                    <EmptyTitle>选择项目文档</EmptyTitle>
                    <EmptyDescription>选择左侧文档后可查看对应的有效版本。</EmptyDescription>
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
