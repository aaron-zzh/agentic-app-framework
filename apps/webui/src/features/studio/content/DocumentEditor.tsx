/**
 * Studio 共享文档预览与编辑器。
 * @author AaronZZH & Kiro
 */

"use client"

import { ExternalLink, Eye, Loader2, Pencil } from "lucide-react"
import Link from "next/link"
import { useEffect, useRef, useState } from "react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { RichTextEditorHandle } from "@/features/rich-text-editor"
import { RichTextEditor } from "@/features/rich-text-editor"
import {
  useDocument,
  useDocumentVersion,
  usePublishDocument,
  useUnpublishDocument,
  useUpdateDocument
} from "@/lib/api/rest/system/document"

interface DocumentEditorProps {
  docId?: number
  createDoc?: (
    params: { title: string; content: string },
    options: { onSuccess: (doc: { id: number }) => void }
  ) => void
  creating?: boolean
  onCreated?: (docId: number) => void
  onCancel?: () => void
  initialView?: "preview" | "edit"
  allowEditing?: boolean
  onSaved?: () => void
  pinnedVersionId?: number
  sourceHref?: string
}

/** 预览或编辑单篇当前用户文档。 */
export function DocumentEditor({
  docId,
  createDoc,
  creating = false,
  onCreated,
  onCancel,
  initialView = "edit",
  allowEditing = true,
  onSaved,
  pinnedVersionId,
  sourceHref
}: DocumentEditorProps) {
  const isNew = !docId
  const isPinned = pinnedVersionId !== undefined
  const { data: doc, isLoading: docLoading } = useDocument(
    isNew || isPinned ? null : (docId ?? null)
  )
  const { data: pinnedVersion, isLoading: pinnedVersionLoading } = useDocumentVersion(
    isPinned ? (docId ?? null) : null,
    isPinned ? (pinnedVersionId ?? null) : null
  )
  const displayedDocument = isPinned ? pinnedVersion : doc
  const isLoading = isPinned ? pinnedVersionLoading : docLoading
  const canEdit = allowEditing && !isPinned
  const { mutate: updateDoc, isPending: saving } = useUpdateDocument()
  const { mutate: publish } = usePublishDocument()
  const { mutate: unpublish } = useUnpublishDocument()

  const [view, setView] = useState<"preview" | "edit">(
    isNew ? "edit" : isPinned ? "preview" : initialView
  )
  const [mode, setMode] = useState<"wysiwyg" | "markdown">("wysiwyg")
  const [editorKey, setEditorKey] = useState(0)
  const [title, setTitle] = useState("")
  const [content, setContent] = useState("")
  const [dirty, setDirty] = useState(false)
  const titleInputRef = useRef<HTMLInputElement>(null)
  const editorRef = useRef<RichTextEditorHandle>(null)

  useEffect(() => {
    if (isNew) titleInputRef.current?.focus()
  }, [isNew])

  useEffect(() => {
    if (displayedDocument) {
      setTitle(displayedDocument.title ?? "")
      setContent(displayedDocument.content ?? "")
      setDirty(false)
    }
  }, [displayedDocument])

  function currentMarkdown() {
    return mode === "wysiwyg" ? (editorRef.current?.getContent("markdown") ?? content) : content
  }

  function handleModeChange(value: string) {
    if (value !== "wysiwyg" && value !== "markdown") return
    if (value === "markdown") {
      setContent(currentMarkdown())
    } else {
      setEditorKey((key) => key + 1)
    }
    setMode(value)
  }

  function handlePreview() {
    setContent(currentMarkdown())
    setView("preview")
  }

  function handleSave() {
    const saveContent = currentMarkdown()
    if (isNew) {
      if (!title.trim() || !createDoc) return
      createDoc(
        { title: title.trim(), content: saveContent },
        { onSuccess: (created) => onCreated?.(created.id) }
      )
      return
    }
    updateDoc(
      {
        id: docId ?? 0,
        title,
        content: saveContent,
        expectedCurrentVersionId: doc?.currentVersionId,
        changeSummary: "在项目文档编辑器中更新正文"
      },
      {
        onSuccess: () => {
          setContent(saveContent)
          setDirty(false)
          onSaved?.()
          toast.success("文档已保存")
        },
        onError: (error) =>
          toast.error(`保存失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  if (!isNew && isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="animate-spin text-muted-foreground" />
      </div>
    )
  }

  if (!isNew && !displayedDocument) {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
        {isPinned ? "固定版本不存在或无权访问" : "文档不存在或无权访问"}
      </div>
    )
  }

  const hasPublishedVersion = !isPinned && doc?.publishedVersionId != null
  const isCurrentVersionPublished =
    hasPublishedVersion && doc?.publishedVersionId === doc?.currentVersionId

  if (view === "preview") {
    return (
      <div className="flex h-full min-h-0 flex-col">
        <div className="flex items-center gap-2 border-b px-4 py-2">
          <h3 className="min-w-0 flex-1 truncate font-medium text-sm">{title}</h3>
          {isPinned ? (
            <Badge variant="secondary">固定版本 v{pinnedVersion?.versionNo}</Badge>
          ) : (
            <Badge variant={isCurrentVersionPublished ? "default" : "outline"}>
              {isCurrentVersionPublished
                ? "当前版本已发布"
                : hasPublishedVersion
                  ? "当前版本未发布"
                  : "草稿"}
            </Badge>
          )}
          {isPinned && sourceHref ? (
            <Button
              nativeButton={false}
              variant="outline"
              size="sm"
              render={<Link href={sourceHref} target="_blank" rel="noreferrer" />}
            >
              <ExternalLink data-icon="inline-start" />
              打开源文档
            </Button>
          ) : null}
          {canEdit ? (
            <Button variant="outline" size="sm" onClick={() => setView("edit")}>
              <Pencil data-icon="inline-start" />
              编辑
            </Button>
          ) : null}
        </div>
        <div className="min-h-0 flex-1 overflow-auto px-5 py-4">
          <article className="prose prose-sm dark:prose-invert max-w-none">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{content || "暂无内容"}</ReactMarkdown>
          </article>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex flex-wrap items-center gap-2 border-b px-4 py-2">
        <input
          ref={titleInputRef}
          className="min-w-40 flex-1 bg-transparent font-medium text-sm outline-none placeholder:text-muted-foreground"
          placeholder="文档标题..."
          value={title}
          onChange={(event) => {
            setTitle(event.target.value)
            setDirty(true)
          }}
        />
        {!isNew && doc ? (
          isCurrentVersionPublished ? (
            <Button variant="outline" size="sm" onClick={() => unpublish(doc.id)}>
              取消发布
            </Button>
          ) : (
            <Button
              variant="outline"
              size="sm"
              disabled={dirty || !doc.currentVersionId}
              onClick={() =>
                publish({ id: doc.id, expectedCurrentVersionId: doc.currentVersionId })
              }
            >
              发布当前版本
            </Button>
          )
        ) : null}
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
        {!isNew ? (
          <Button variant="ghost" size="sm" onClick={handlePreview}>
            <Eye data-icon="inline-start" />
            预览
          </Button>
        ) : null}
        {isNew ? (
          <Button variant="ghost" size="sm" onClick={onCancel}>
            取消
          </Button>
        ) : null}
        <Button
          size="sm"
          disabled={isNew ? !title.trim() || creating : !dirty || saving}
          onClick={handleSave}
        >
          {creating || saving ? (
            <Loader2 data-icon="inline-start" className="animate-spin" />
          ) : null}
          保存
        </Button>
      </div>

      <div className="min-h-0 flex-1 overflow-auto">
        {mode === "markdown" ? (
          <textarea
            className="h-full w-full resize-none bg-transparent p-3 font-mono text-sm outline-none"
            value={content}
            onChange={(event) => {
              setContent(event.target.value)
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
            initialValueMode="markdown"
            fill
            noBorder
            className="h-full"
          />
        )}
      </div>
    </div>
  )
}
