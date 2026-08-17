/**
 * 视频生成 Composer。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ImagePlus, Wand2 } from "lucide-react"
import { useCallback, useRef, useState } from "react"
import { ModelParamsPopover } from "@/components/common/ModelParamsPopover"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { BrandProfilePicker } from "@/features/aigc/generation/BrandProfilePicker"
import { PromptTemplateDialog } from "@/features/aigc/generation/PromptTemplateDialog"
import { SkillPickerContent } from "@/features/aigc/generation/SkillPicker"
import {
  buildPromptWithSnippets,
  SnippetPickerDialog
} from "@/features/aigc/generation/SnippetPickerDialog"
import { ImageUploadChip } from "@/features/studio/home/ImageUploadChip"
import { MediaComposerShell } from "@/features/studio/media-generation/components/MediaComposerShell"
import { useVideoGenerationController } from "@/features/studio/media-generation/hooks/use-video-generation-controller"
import type {
  MediaGenerationComposerProps,
  MediaTaskSubmission,
  VideoInputMode
} from "@/features/studio/media-generation/types"
import type { AigcSnippet } from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils"

interface VideoImageUploadButtonProps {
  label: string
  multiple?: boolean
  disabled: boolean
  onSelect: (files: File[]) => void
}

/** 图标下直接展示上传类型的图片选择入口。 */
function VideoImageUploadButton({
  label,
  multiple = false,
  disabled,
  onSelect
}: VideoImageUploadButtonProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  return (
    <>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={disabled}
        className="flex size-14 shrink-0 flex-col items-center justify-center gap-1 rounded-md border border-foreground/15 border-dashed bg-foreground/[0.03] text-muted-foreground transition-colors hover:border-primary/40 hover:bg-primary/5 hover:text-primary disabled:cursor-not-allowed disabled:opacity-50"
        aria-label={`上传${label}`}
      >
        <ImagePlus className="size-4" />
        <span className="text-[10px]">{label}</span>
      </button>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple={multiple}
        className="hidden"
        disabled={disabled}
        onChange={(event) => {
          const files = Array.from(event.target.files ?? [])
          if (files.length > 0) onSelect(files)
          event.target.value = ""
        }}
      />
    </>
  )
}

function VideoImageAttachment({
  label,
  attachment,
  progress,
  onRemove
}: {
  label: string
  attachment: { name: string; previewSrc: string }
  progress: number
  onRemove: () => void
}) {
  return (
    <div className="flex min-w-14 flex-col items-center gap-1">
      <ImageUploadChip
        name={`${label} · ${attachment.name}`}
        progress={progress}
        previewSrc={attachment.previewSrc}
        onRemove={onRemove}
      />
      <span className="max-w-16 truncate text-center text-[10px] text-muted-foreground leading-none">
        {label}
      </span>
    </div>
  )
}

function VideoPendingImageAttachment({
  label,
  attachment,
  progress
}: {
  label: string
  attachment: { name: string; previewSrc: string }
  progress: number
}) {
  const normalizedProgress = Math.min(100, Math.max(0, Math.round(progress)))

  return (
    <div
      className="flex min-w-14 flex-col items-center gap-1"
      title={`${label} · ${attachment.name}`}
    >
      <div className="relative size-14 overflow-hidden rounded-md border border-foreground/12 bg-foreground/6">
        {/* biome-ignore lint/performance/noImgElement: 上传中的本地 blob 预览 */}
        <img src={attachment.previewSrc} alt={label} className="size-full object-cover" />
        <div className="absolute inset-0 flex items-center justify-center bg-black/45 text-white text-xs tabular-nums backdrop-blur-[1px]">
          {normalizedProgress}%
        </div>
      </div>
      <span className="max-w-16 truncate text-center text-[10px] text-muted-foreground leading-none">
        {label}
      </span>
    </div>
  )
}

/** 渲染视频生成输入、多参考图、首尾帧、模型品牌、技能和参数控件。 */
export function VideoGenerationComposer(props: MediaGenerationComposerProps) {
  const [prompt, setPrompt] = useState(props.initialDraft?.prompt ?? "")
  const [selectedSnippets, setSelectedSnippets] = useState<AigcSnippet[]>([])
  const handleTaskSubmitted = useCallback(
    (submission: MediaTaskSubmission) => {
      setPrompt("")
      setSelectedSnippets([])
      props.onTaskSubmitted?.(submission)
    },
    [props.onTaskSubmitted]
  )
  const controller = useVideoGenerationController({
    projectId: props.projectId,
    initialDraft: props.initialDraft,
    onTaskSubmitted: handleTaskSubmitted
  })
  const [skillPickerOpen, setSkillPickerOpen] = useState(false)

  const handlePromptChange = useCallback(
    (nextPrompt: string) => {
      setPrompt(nextPrompt)
      controller.setPrompt(buildPromptWithSnippets(nextPrompt, selectedSnippets))
    },
    [controller.setPrompt, selectedSnippets]
  )
  const handleSelectedSnippetsChange = useCallback(
    (nextSnippets: AigcSnippet[]) => {
      setSelectedSnippets(nextSnippets)
      controller.setPrompt(buildPromptWithSnippets(prompt, nextSnippets))
    },
    [controller.setPrompt, prompt]
  )
  const handleRemoveSnippet = useCallback(
    (snippetId: number) => {
      handleSelectedSnippetsChange(selectedSnippets.filter((snippet) => snippet.id !== snippetId))
    },
    [handleSelectedSnippetsChange, selectedSnippets]
  )

  const referenceCount = controller.referenceImages.length + (controller.pendingImage ? 1 : 0)
  const hasReferenceImages =
    controller.referenceImages.length > 0 ||
    Boolean(controller.firstFrameImage || controller.lastFrameImage)

  return (
    <MediaComposerShell
      prompt={prompt}
      onPromptChange={handlePromptChange}
      onSubmit={controller.submit}
      placeholder="描述你想生成的视频内容..."
      canSubmit={controller.canSubmit}
      isSubmitting={controller.isSubmitting}
      creditEstimate={controller.creditEstimate}
      selectedSnippets={selectedSnippets}
      onRemoveSnippet={handleRemoveSnippet}
      leadingTools={props.leadingTools}
      headerTools={
        <div className="flex items-center gap-1.5">
          <PromptTemplateDialog
            type="VIDEO_GEN"
            hasReferenceImages={hasReferenceImages}
            onSelect={handlePromptChange}
            triggerClassName="flex h-8 shrink-0 items-center gap-1 rounded-lg border border-foreground/8 px-2.5 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06]"
          />
          <SnippetPickerDialog
            selectedSnippets={selectedSnippets}
            onSelectedSnippetsChange={handleSelectedSnippetsChange}
            triggerClassName="flex h-8 shrink-0 items-center gap-1 rounded-lg border border-foreground/8 px-2.5 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06]"
          />
          <BrandProfilePicker
            value={controller.selectedBrandProfile}
            onChange={controller.setSelectedBrandProfile}
            triggerClassName="flex h-8 shrink-0 items-center gap-1 rounded-lg border border-foreground/8 px-2.5 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06]"
          />
        </div>
      }
      appearance={props.appearance}
      className={props.className}
      attachments={
        controller.imageMode === "T2V" ? undefined : controller.imageMode === "REFERENCE" ? (
          <>
            {controller.referenceImages.map((image, index) => (
              <VideoImageAttachment
                key={`${image.url}-${index}`}
                label={`参考图 ${index + 1}`}
                attachment={image}
                progress={100}
                onRemove={() => controller.removeReferenceImage(index)}
              />
            ))}
            {controller.pendingImage ? (
              <VideoPendingImageAttachment
                label={`参考图 ${controller.referenceImages.length + 1}`}
                attachment={controller.pendingImage}
                progress={controller.uploadProgress}
              />
            ) : null}
            {referenceCount < controller.maxReferenceImages ? (
              <VideoImageUploadButton
                label="参考图"
                multiple
                disabled={controller.isSubmitting}
                onSelect={(files) => void controller.uploadReferenceImages(files)}
              />
            ) : null}
          </>
        ) : (
          <>
            {controller.firstFrameImage ? (
              <VideoImageAttachment
                label="首帧图"
                attachment={controller.firstFrameImage}
                progress={100}
                onRemove={controller.removeFirstFrameImage}
              />
            ) : controller.pendingImage ? (
              <VideoPendingImageAttachment
                label="首帧图"
                attachment={controller.pendingImage}
                progress={controller.uploadProgress}
              />
            ) : (
              <VideoImageUploadButton
                label="首帧图"
                disabled={controller.isSubmitting}
                onSelect={([file]) => {
                  if (file) void controller.uploadFirstFrameImage(file)
                }}
              />
            )}
            {controller.lastFrameImage ? (
              <VideoImageAttachment
                label="尾帧图"
                attachment={controller.lastFrameImage}
                progress={100}
                onRemove={controller.removeLastFrameImage}
              />
            ) : controller.pendingLastFrameImage ? (
              <VideoPendingImageAttachment
                label="尾帧图"
                attachment={controller.pendingLastFrameImage}
                progress={controller.uploadProgress}
              />
            ) : (
              <VideoImageUploadButton
                label="尾帧图"
                disabled={controller.isSubmitting}
                onSelect={([file]) => {
                  if (file) void controller.uploadLastFrameImage(file)
                }}
              />
            )}
          </>
        )
      }
      tools={
        <>
          <select
            value={controller.imageMode}
            onChange={(event) => controller.setImageMode(event.target.value as VideoInputMode)}
            disabled={controller.isSubmitting}
            aria-label="视频生成模式"
            className="h-8 shrink-0 rounded-lg border border-foreground/8 bg-background px-2.5 text-xs disabled:opacity-50"
          >
            <option value="T2V">文生视频</option>
            <option value="REFERENCE">图生视频</option>
            <option value="FIRST_LAST_FRAME">首尾帧</option>
          </select>

          {controller.brands.map((brand) => (
            <button
              key={brand.provider}
              type="button"
              onClick={() => controller.selectBrand(brand.provider)}
              className={cn(
                "flex h-8 shrink-0 items-center rounded-lg border px-2.5 text-xs transition-colors",
                controller.selectedBrand === brand.provider
                  ? "border-cyan-500/40 bg-cyan-500/10 text-cyan-400"
                  : "border-foreground/8 text-muted-foreground hover:bg-foreground/6"
              )}
            >
              {brand.label}
            </button>
          ))}

          <Popover open={skillPickerOpen} onOpenChange={setSkillPickerOpen}>
            <PopoverTrigger
              render={
                <button
                  type="button"
                  className={cn(
                    "flex h-8 shrink-0 items-center gap-1 rounded-lg border px-2.5 text-xs transition-colors",
                    controller.selectedSkill
                      ? "border-primary/40 bg-primary/10 text-primary"
                      : "border-foreground/8 text-muted-foreground hover:bg-foreground/6"
                  )}
                />
              }
            >
              <Wand2 className="size-3" />
              {controller.selectedSkill ? controller.selectedSkill.name : "技能"}
            </PopoverTrigger>
            <PopoverContent align="start" className="w-80 p-0" sideOffset={6}>
              <SkillPickerContent onClose={() => setSkillPickerOpen(false)} />
            </PopoverContent>
          </Popover>

          <ModelParamsPopover
            model={controller.currentModel}
            params={controller.params}
            onChangeParams={controller.onChangeParams}
          />
        </>
      }
    />
  )
}
