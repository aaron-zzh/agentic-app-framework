/**
 * Studio 项目基础信息编辑弹窗。
 * @author AaronZZH & Kiro
 */

"use client"

import { ImageIcon, Sparkles, Upload, X } from "lucide-react"
import { useEffect, useId, useRef, useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Progress } from "@/components/ui/progress"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import {
  type AigcProject,
  type AigcProjectCoverStatus,
  type AigcProjectUpdateInput,
  useUpdateAigcProject
} from "@/lib/api/rest/ai/aigc"
import { useMediaByVersionId } from "@/lib/api/rest/media/media-asset"
import { type UploadResult, useFileUpload } from "@/lib/hooks/use-file-upload"
import { getProjectTypeConfig } from "./project-type-config"

interface ProjectBasicInfoDialogProps {
  open: boolean
  project: AigcProject
  onOpenChange: (open: boolean) => void
}

type CoverEditMode = "KEEP" | "UPLOAD" | "AI_GENERATE" | "REMOVE"
type ProjectCoverUpload = Pick<UploadResult, "fileId" | "url" | "name">

const COVER_STATUS_LABELS: Record<AigcProjectCoverStatus, string> = {
  NONE: "无封面",
  READY: "READY · 已就绪",
  PENDING: "PENDING · 生成中",
  FAILED: "FAILED · 生成失败"
}

const PRODUCTION_MODE_LABELS: Record<string, string> = {
  standard: "标准内容",
  short_drama: "短剧",
  motion_comic: "漫剧"
}

function createCoverIdempotencyKey(): string {
  return `project-cover-${globalThis.crypto.randomUUID()}`
}

function coverStatusVariant(
  status: AigcProjectCoverStatus
): "outline" | "secondary" | "destructive" {
  if (status === "FAILED") return "destructive"
  if (status === "PENDING") return "secondary"
  return "outline"
}

export function ProjectBasicInfoDialog({
  open,
  project,
  onOpenChange
}: ProjectBasicInfoDialogProps) {
  const nameId = useId()
  const descriptionId = useId()
  const briefId = useId()
  const coverFileId = useId()
  const coverPromptId = useId()
  const wasOpen = useRef(false)
  const updateProject = useUpdateAigcProject()
  const {
    upload: uploadCover,
    uploading: coverUploading,
    progress: coverUploadProgress
  } = useFileUpload({ maxWidth: 1920, maxHeight: 1080, quality: 0.9 })
  const { data: coverMedia } = useMediaByVersionId(project.coverMediaVersionId)
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description ?? "")
  const [brief, setBrief] = useState(project.brief ?? "")
  const [coverMode, setCoverMode] = useState<CoverEditMode>("KEEP")
  const [coverUpload, setCoverUpload] = useState<ProjectCoverUpload | null>(null)
  const [coverPrompt, setCoverPrompt] = useState("")
  const [coverIdempotencyKey, setCoverIdempotencyKey] = useState(createCoverIdempotencyKey)
  const [coverError, setCoverError] = useState<string | null>(null)
  const archived = project.status === "archived"
  const projectTypeLabel = getProjectTypeConfig({ code: project.projectTypeCode, name: "" }).label

  useEffect(() => {
    if (open && !wasOpen.current) {
      setName(project.name)
      setDescription(project.description ?? "")
      setBrief(project.brief ?? "")
      setCoverMode("KEEP")
      setCoverUpload(null)
      setCoverPrompt("")
      setCoverIdempotencyKey(createCoverIdempotencyKey())
      setCoverError(null)
    }
    wasOpen.current = open
  }, [open, project.brief, project.description, project.name])

  function handleCoverModeChange(value: CoverEditMode) {
    if (value === "AI_GENERATE" && coverMode !== "AI_GENERATE") {
      setCoverIdempotencyKey(createCoverIdempotencyKey())
    }
    setCoverMode(value)
    setCoverError(null)
  }

  async function handleCoverFile(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith("image/")) {
      setCoverError("请选择图片文件")
      return
    }
    setCoverError(null)
    try {
      const result = await uploadCover(file)
      setCoverUpload({ fileId: result.fileId, url: result.url, name: result.name })
    } catch (error) {
      setCoverError(error instanceof Error ? error.message : "封面上传失败")
    }
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedName = name.trim()
    if (!trimmedName || archived || coverUploading) return
    if (coverMode === "UPLOAD" && !coverUpload) {
      setCoverError("请先上传封面图片")
      return
    }

    const normalizedDescription = description.trim()
    const normalizedBrief = brief.trim()
    const data: AigcProjectUpdateInput = { expectedVersion: project.version }
    if (trimmedName !== project.name) data.name = trimmedName
    if (normalizedDescription !== (project.description ?? "")) {
      data.description = normalizedDescription || null
    }
    if (normalizedBrief !== (project.brief ?? "")) data.brief = normalizedBrief || null

    if (coverMode === "UPLOAD" && coverUpload) {
      data.cover = { operation: "UPLOAD", fileId: coverUpload.fileId }
    } else if (coverMode === "AI_GENERATE") {
      data.cover = {
        operation: "AI_GENERATE",
        prompt: coverPrompt.trim() || undefined,
        idempotencyKey: coverIdempotencyKey
      }
    } else if (coverMode === "REMOVE") {
      data.cover = { operation: "REMOVE" }
    }

    if (Object.keys(data).length === 1) {
      onOpenChange(false)
      return
    }

    try {
      await updateProject.mutateAsync({ id: project.id, data })
      toast.success("项目基础信息已更新")
      onOpenChange(false)
    } catch {
      // API 客户端已统一提示请求错误
    }
  }

  const previewUrl = coverUpload?.url ?? coverMedia?.currentVersion.url ?? null

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => !updateProject.isPending && onOpenChange(nextOpen)}
    >
      <DialogContent className="flex max-h-[calc(100dvh-2rem)] flex-col gap-0 overflow-hidden p-0 sm:max-w-2xl">
        <form onSubmit={handleSubmit} className="flex min-h-0 flex-1 flex-col">
          <DialogHeader className="shrink-0 border-b px-5 py-4 pr-12">
            <DialogTitle>项目基础信息</DialogTitle>
            <DialogDescription>编辑展示信息与项目封面。已归档项目仅支持查看。</DialogDescription>
          </DialogHeader>

          <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto p-5">
            <dl className="grid gap-3 rounded-xl bg-muted/60 p-4 sm:grid-cols-3">
              <div className="flex flex-col gap-1">
                <dt className="text-muted-foreground text-xs">项目类型</dt>
                <dd className="font-medium text-sm">{projectTypeLabel}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-muted-foreground text-xs">蓝图版本</dt>
                <dd className="font-medium text-sm">{project.blueprintVersion ?? "—"}</dd>
              </div>
              <div className="flex flex-col gap-1">
                <dt className="text-muted-foreground text-xs">生产模式</dt>
                <dd className="font-medium text-sm">
                  {PRODUCTION_MODE_LABELS[project.productionMode] ?? project.productionMode}
                </dd>
              </div>
            </dl>

            <div className="flex flex-col gap-2">
              <label htmlFor={nameId} className="font-medium text-sm">
                项目名称 <span className="text-destructive">*</span>
              </label>
              <Input
                id={nameId}
                required
                maxLength={200}
                value={name}
                disabled={archived}
                onChange={(event) => setName(event.target.value)}
              />
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor={descriptionId} className="font-medium text-sm">
                项目描述
              </label>
              <Textarea
                id={descriptionId}
                maxLength={500}
                value={description}
                disabled={archived}
                onChange={(event) => setDescription(event.target.value)}
                className="min-h-20 resize-y"
              />
              <p className="text-right text-muted-foreground text-xs">{description.length}/500</p>
            </div>

            <div className="flex flex-col gap-2">
              <label htmlFor={briefId} className="font-medium text-sm">
                创作简报
              </label>
              <Textarea
                id={briefId}
                value={brief}
                disabled={archived}
                onChange={(event) => setBrief(event.target.value)}
                className="min-h-28 resize-y"
              />
            </div>

            <div className="flex flex-col gap-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium text-sm">项目封面</span>
                <Badge variant={coverStatusVariant(project.coverStatus)}>
                  {COVER_STATUS_LABELS[project.coverStatus]}
                </Badge>
              </div>

              <div className="flex min-h-36 items-center justify-center overflow-hidden rounded-xl border bg-muted/30">
                {previewUrl ? (
                  // biome-ignore lint/performance/noImgElement: 媒体 URL 由后端动态签名并用于详情即时预览
                  <img src={previewUrl} alt="项目封面" className="max-h-64 w-full object-contain" />
                ) : (
                  <div className="flex flex-col items-center gap-2 text-muted-foreground text-sm">
                    <ImageIcon className="size-8" />
                    暂无封面预览
                  </div>
                )}
              </div>

              {!archived ? (
                <ToggleGroup
                  value={[coverMode]}
                  onValueChange={(values: string[]) => {
                    const next = values.at(-1)
                    if (
                      next === "KEEP" ||
                      next === "UPLOAD" ||
                      next === "AI_GENERATE" ||
                      next === "REMOVE"
                    ) {
                      handleCoverModeChange(next)
                    }
                  }}
                  variant="outline"
                  className="flex w-full flex-wrap justify-start"
                >
                  <ToggleGroupItem value="KEEP" aria-label="保持当前封面">
                    保持不变
                  </ToggleGroupItem>
                  <ToggleGroupItem value="UPLOAD" aria-label="上传替换封面">
                    <Upload /> 上传替换
                  </ToggleGroupItem>
                  <ToggleGroupItem value="AI_GENERATE" aria-label="AI 重新生成封面">
                    <Sparkles /> AI 重生成
                  </ToggleGroupItem>
                  <ToggleGroupItem
                    value="REMOVE"
                    aria-label="移除封面"
                    disabled={
                      project.coverMediaVersionId === null && project.coverStatus === "NONE"
                    }
                  >
                    <X /> 移除
                  </ToggleGroupItem>
                </ToggleGroup>
              ) : null}

              {coverMode === "UPLOAD" && !archived ? (
                <div className="flex flex-col gap-3 rounded-xl border bg-muted/30 p-3">
                  <div className="flex items-center gap-3">
                    <Button
                      nativeButton={false}
                      // biome-ignore lint/a11y/noLabelWithoutControl: Button 通过 render 渲染为 label，文本内容由 children 提供
                      render={<label htmlFor={coverFileId} />}
                      variant="outline"
                      size="sm"
                      className="cursor-pointer"
                    >
                      选择图片
                    </Button>
                    <Input
                      id={coverFileId}
                      type="file"
                      accept="image/*"
                      className="sr-only"
                      disabled={coverUploading}
                      onChange={(event) => {
                        void handleCoverFile(event.target.files?.[0])
                        event.target.value = ""
                      }}
                    />
                    <span className="min-w-0 truncate text-muted-foreground text-sm">
                      {coverUpload?.name ?? "尚未选择图片"}
                    </span>
                  </div>
                  {coverUploading ? (
                    <div className="flex items-center gap-3 text-muted-foreground text-xs">
                      <Progress value={coverUploadProgress} className="flex-1" />
                      {coverUploadProgress}%
                    </div>
                  ) : null}
                </div>
              ) : null}

              {coverMode === "AI_GENERATE" && !archived ? (
                <div className="flex flex-col gap-2 rounded-xl border bg-muted/30 p-3">
                  <label htmlFor={coverPromptId} className="font-medium text-sm">
                    封面描述（可选）
                  </label>
                  <Textarea
                    id={coverPromptId}
                    value={coverPrompt}
                    onChange={(event) => setCoverPrompt(event.target.value)}
                    placeholder="留空时将根据项目名称、简报和类型生成"
                    className="min-h-20 resize-y"
                  />
                  <p className="text-muted-foreground text-xs">
                    保存后异步生成；状态将显示为 PENDING，并在完成后更新为 READY 或 FAILED。
                  </p>
                </div>
              ) : null}

              {coverMode === "REMOVE" && !archived ? (
                <p className="rounded-xl border border-destructive/30 bg-destructive/10 p-3 text-destructive text-sm">
                  保存后将移除当前项目封面。
                </p>
              ) : null}

              {coverError ? <p className="text-destructive text-sm">{coverError}</p> : null}
              <p className="text-muted-foreground text-xs">
                上传或 AI 生成的封面均为项目级素材，不会自动保存到资产库。
              </p>
            </div>
          </div>

          <DialogFooter className="mx-0 mb-0 shrink-0 rounded-none px-5 py-4">
            <Button
              type="button"
              variant="outline"
              disabled={updateProject.isPending}
              onClick={() => onOpenChange(false)}
            >
              {archived ? "关闭" : "取消"}
            </Button>
            {!archived ? (
              <Button
                type="submit"
                disabled={
                  !name.trim() ||
                  coverUploading ||
                  (coverMode === "UPLOAD" && !coverUpload) ||
                  updateProject.isPending
                }
              >
                {updateProject.isPending ? "正在保存" : "保存"}
              </Button>
            ) : null}
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
