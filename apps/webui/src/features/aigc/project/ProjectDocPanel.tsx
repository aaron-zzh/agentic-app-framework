/**
 * 项目文档面板——prompt 输入 + 文档列表 + 文档内容预览
 * 从 StoryboardPanel header 的图标按钮触发，以 Sheet 形式展开
 * @author AaronZZH & Kiro
 */

"use client"

import { FileText, Loader2, Trash2, Upload } from "lucide-react"
import { useParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { RichTextEditor } from "@/features/rich-text-editor"
import {
  useAigcProject,
  useAigcProjectDocs,
  useImportProjectPdf,
  useLinkProjectDoc,
  useUnlinkProjectDoc,
  useUpdateAigcProject
} from "@/lib/queries/use-aigc-projects"
import { useCreateDocument, useDocument, useUpdateDocument } from "@/lib/queries/use-documents"
import { cn } from "@/lib/utils/cn"

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function ProjectDocPanel({ open, onOpenChange }: Props) {
  const params = useParams()
  const projectId = params?.projectId ? Number(params.projectId) : null

  const { data: project } = useAigcProject(projectId)
  const { data: projectDocs = [], isLoading: docsLoading } = useAigcProjectDocs(projectId)
  const { mutate: updateProject } = useUpdateAigcProject()
  const { mutate: unlinkDoc } = useUnlinkProjectDoc()
  const { mutate: linkDoc } = useLinkProjectDoc()
  const { mutate: createDoc, isPending: creating } = useCreateDocument()
  const { mutate: importPdf, isPending: importing } = useImportProjectPdf(projectId)

  const [prompt, setPrompt] = useState("")
  const [selectedDocId, setSelectedDocId] = useState<number | "new" | null>(null)
  const [promptInitialized, setPromptInitialized] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // project 首次加载完成后同步 prompt，之后不再覆盖用户输入
  useEffect(() => {
    if (project && !promptInitialized) {
      setPrompt(project.prompt ?? "")
      setPromptInitialized(true)
    }
  }, [project, promptInitialized])

  const firstDocId = projectDocs[0]?.docId ?? null
  const activeDocId = selectedDocId ?? firstDocId

  function handlePromptBlur() {
    if (!projectId || prompt === (project?.prompt ?? "")) return
    updateProject({ id: projectId, prompt })
  }

  function handlePdfUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    const form = new FormData()
    form.append("file", file)
    importPdf(form)
    e.target.value = ""
  }

  return (
    <Sheet
      open={open}
      onOpenChange={(v) => {
        if (!v) setPromptInitialized(false)
        onOpenChange(v)
      }}
    >
      <SheetContent side="left" className="!w-[60vw] !max-w-[60vw] flex flex-col p-0">
        <SheetHeader className="border-b px-4 py-3">
          <SheetTitle className="text-sm">项目规范</SheetTitle>
        </SheetHeader>

        <div className="flex min-h-0 flex-1">
          {/* 左栏：prompt + 文档列表 */}
          <div className="flex w-52 shrink-0 flex-col border-r">
            {/* Prompt 输入 */}
            <div className="border-b p-3">
              <p className="mb-1.5 font-medium text-muted-foreground text-xs">提示词</p>
              <Textarea
                placeholder="描述创作方向、风格、要求..."
                className="min-h-[80px] resize-none text-xs"
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                onBlur={handlePromptBlur}
              />
            </div>

            {/* 文档列表 */}
            <div className="flex items-center justify-between px-3 py-2">
              <p className="font-medium text-muted-foreground text-xs">知识库</p>
              <div className="flex items-center gap-1">
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-6"
                  title="上传 PDF"
                  disabled={importing}
                  onClick={() => fileInputRef.current?.click()}
                >
                  {importing ? (
                    <Loader2 className="size-3.5 animate-spin" />
                  ) : (
                    <Upload className="size-3.5" />
                  )}
                </Button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="application/pdf"
                  className="hidden"
                  onChange={handlePdfUpload}
                />
              </div>
            </div>

            <ScrollArea className="flex-1">
              {docsLoading ? (
                <div className="flex justify-center py-4">
                  <Loader2 className="size-4 animate-spin text-muted-foreground" />
                </div>
              ) : projectDocs.length === 0 ? (
                <div className="flex flex-col items-center gap-2 px-3 py-4">
                  <p className="text-muted-foreground text-xs">暂无关联文档</p>
                  <Button
                    variant="outline"
                    className="w-full"
                    onClick={() => setSelectedDocId("new")}
                  >
                    新建文档
                  </Button>
                </div>
              ) : (
                <ul className="space-y-0.5 p-2">
                  {projectDocs.map((pd) => (
                    <li key={pd.docId}>
                      <button
                        type="button"
                        onClick={() => setSelectedDocId(pd.docId)}
                        className={cn(
                          "group flex w-full items-center gap-1.5 rounded-md px-2 py-1.5 text-left text-xs transition-colors hover:bg-accent",
                          activeDocId === pd.docId && "bg-accent font-medium"
                        )}
                      >
                        <FileText className="size-3.5 shrink-0 text-blue-400" />
                        <span className="min-w-0 flex-1 truncate">
                          {pd.docTitle ?? `文档 ${pd.docId}`}
                        </span>
                        {pd.sourceFileId && (
                          <Badge variant="outline" className="shrink-0 px-1 py-0 text-[10px]">
                            PDF
                          </Badge>
                        )}
                        <Trash2
                          className="size-3 shrink-0 text-destructive opacity-0 transition-opacity group-hover:opacity-70"
                          onClick={(e) => {
                            e.stopPropagation()
                            if (projectId) unlinkDoc({ projectId, docId: pd.docId })
                          }}
                        />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </ScrollArea>
          </div>

          {/* 右栏：文档编辑器（新建/编辑统一） */}
          <div className="min-w-0 flex-1 overflow-hidden">
            {activeDocId === "new" ? (
              <DocEditor
                projectId={projectId}
                createDoc={createDoc}
                linkDoc={linkDoc}
                creating={creating}
                onCreated={(docId) => setSelectedDocId(docId)}
                onCancel={() => setSelectedDocId(null)}
              />
            ) : activeDocId ? (
              <DocEditor docId={activeDocId} />
            ) : (
              <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
                选择文档预览
              </div>
            )}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  )
}

/**
 * 统一文档编辑器——新建和编辑共用
 * - 新建模式：docId 为空，传入 createDoc/linkDoc
 * - 编辑模式：传入 docId，自动加载数据
 * - 标题行内编辑，内容区 RichTextEditor（易读/Markdown 双模式）
 */
function DocEditor({
  docId,
  projectId,
  createDoc,
  linkDoc,
  creating,
  onCreated,
  onCancel
}: {
  docId?: number
  projectId?: number | null
  createDoc?: (
    p: { title: string; content: string },
    opts: { onSuccess: (doc: { id: number }) => void }
  ) => void
  linkDoc?: (p: { projectId: number; docId: number }) => void
  creating?: boolean
  onCreated?: (docId: number) => void
  onCancel?: () => void
}) {
  const isNew = !docId
  const { data: doc, isLoading } = useDocument(isNew ? 0 : (docId ?? 0))
  const { mutate: updateDoc, isPending: saving } = useUpdateDocument()

  const [mode, setMode] = useState<"wysiwyg" | "markdown">("wysiwyg")
  const [title, setTitle] = useState("")
  const [content, setContent] = useState("")
  const [dirty, setDirty] = useState(false)
  const titleInputRef = useRef<HTMLInputElement>(null)

  // 新建模式自动聚焦标题
  useEffect(() => {
    if (isNew) titleInputRef.current?.focus()
  }, [isNew])

  // 加载已有文档数据
  useEffect(() => {
    if (doc) {
      setTitle(doc.title ?? "")
      setContent(doc.content ?? "")
      setDirty(false)
    }
  }, [doc])

  if (!isNew && isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="size-5 animate-spin text-muted-foreground" />
      </div>
    )
  }

  function handleSave() {
    if (isNew) {
      if (!projectId || !title.trim() || !createDoc || !linkDoc) return
      createDoc(
        { title: title.trim(), content },
        {
          onSuccess: (created) => {
            linkDoc({ projectId, docId: created.id })
            onCreated?.(created.id)
          }
        }
      )
    } else {
      updateDoc({ id: docId ?? 0, title, content })
      setDirty(false)
    }
  }

  return (
    <div className="flex h-full flex-col">
      {/* 顶栏：标题 + 模式切换 + 操作 */}
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
        <Tabs value={mode} onValueChange={(v) => setMode(v as typeof mode)}>
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
        <RichTextEditor
          value={content}
          onChange={(v) => {
            setContent(v)
            setDirty(true)
          }}
          preset="document"
          mode={mode === "markdown" ? "markdown" : "html"}
          fill
          className="h-full border-0"
        />
      </div>
    </div>
  )
}
