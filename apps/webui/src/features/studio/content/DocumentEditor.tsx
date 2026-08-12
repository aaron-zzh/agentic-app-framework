/**
 * Studio 共享文档预览与编辑器。
 * @author AaronZZH & Kiro
 */

"use client"

import { Eye, Loader2, Pencil } from "lucide-react"
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
}

/** 预览或编辑单篇当前用户文档。 */
export function DocumentEditor({
  docId,
  createDoc,
  creating = false,
  onCreated,
  onCancel,
  initialView = "edit",
  allowEditing = true
}: DocumentEditorProps) {
  const isNew = !docId
  const { data: doc, isLoading } = useDocument(isNew ? null : (docId ?? null))
  const { mutate: updateDoc, isPending: saving } = useUpdateDocument()
  const { mutate: publish } = usePublishDocument()
  const { mutate: unpublish } = useUnpublishDocument()

  const [view, setView] = useState<"preview" | "edit">(isNew ? "edit" : initialView)
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
    if (doc) {
      setTitle(doc.title ?? "")
      setContent(doc.content ?? "")
      setDirty(false)
    }
  }, [doc])

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
      { id: docId ?? 0, title, content: saveContent },
      {
        onSuccess: () => {
          setContent(saveContent)
          setDirty(false)
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

  if (!isNew && !doc) {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
        文档不存在或无权访问
      </div>
    )
  }

  const isPublished = doc?.publish === "published"

  if (view === "preview") {
    return (
      <div className="flex h-full min-h-0 flex-col">
        <div className="flex items-center gap-2 border-b px-4 py-2">
          <h3 className="min-w-0 flex-1 truncate font-medium text-sm">{title}</h3>
          <Badge variant={isPublished ? "default" : "outline"}>
            {isPublished ? "已发布" : "草稿"}
          </Badge>
          {allowEditing ? (
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
          isPublished ? (
            <Button variant="outline" size="sm" onClick={() => unpublish(doc.id)}>
              取消发布
            </Button>
          ) : (
            <Button variant="outline" size="sm" onClick={() => publish(doc.id)}>
              发布
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
