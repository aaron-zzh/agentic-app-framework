/**
 * 视频生成 Composer。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Plus, Wand2 } from "lucide-react"
import { useRef, useState } from "react"
import { ModelParamsPopover } from "@/components/common/ModelParamsPopover"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { PromptTemplateDialog } from "@/features/aigc/generation/PromptTemplateDialog"
import { SkillPickerContent } from "@/features/aigc/generation/SkillPicker"
import { ImageUploadChip } from "@/features/studio/home/ImageUploadChip"
import { MediaComposerShell } from "@/features/studio/media-generation/components/MediaComposerShell"
import { useVideoGenerationController } from "@/features/studio/media-generation/hooks/use-video-generation-controller"
import type { MediaGenerationComposerProps } from "@/features/studio/media-generation/types"
import type { VideoImageMode } from "@/lib/api/rest/ai"
import { cn } from "@/lib/utils"

/** 渲染视频生成输入、首尾帧、模型品牌、技能和参数控件。 */
export function VideoGenerationComposer(props: MediaGenerationComposerProps) {
  const controller = useVideoGenerationController(props)
  const referenceInputRef = useRef<HTMLInputElement>(null)
  const lastFrameInputRef = useRef<HTMLInputElement>(null)
  const [skillPickerOpen, setSkillPickerOpen] = useState(false)

  const attachment = controller.pendingImage ?? controller.referenceImage

  return (
    <MediaComposerShell
      prompt={controller.prompt}
      onPromptChange={controller.setPrompt}
      onSubmit={controller.submit}
      placeholder="描述你想生成的视频内容..."
      canSubmit={controller.canSubmit}
      isSubmitting={controller.isSubmitting}
      creditEstimate={controller.creditEstimate}
      leadingTools={props.leadingTools}
      headerTools={
        <PromptTemplateDialog
          type="VIDEO_GEN"
          hasReferenceImages={Boolean(controller.referenceImage)}
          onSelect={controller.setPrompt}
          triggerClassName="flex h-8 shrink-0 items-center gap-1 rounded-lg border border-foreground/8 px-2.5 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06]"
        />
      }
      appearance={props.appearance}
      className={props.className}
      attachments={
        <>
          {attachment ? (
            <ImageUploadChip
              name={attachment.name}
              progress={controller.referenceImage ? 100 : controller.uploadProgress}
              previewSrc={attachment.previewSrc}
              onRemove={controller.removeReferenceImage}
            />
          ) : null}
          {controller.imageMode === "REFERENCE" && controller.lastFrameImage ? (
            <ImageUploadChip
              name={`尾帧 · ${controller.lastFrameImage.name}`}
              progress={100}
              previewSrc={controller.lastFrameImage.previewSrc}
              onRemove={controller.removeLastFrameImage}
            />
          ) : null}
        </>
      }
      tools={
        <>
          <button
            type="button"
            onClick={() => referenceInputRef.current?.click()}
            disabled={controller.isSubmitting}
            className="flex size-8 shrink-0 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-foreground/[0.06] hover:text-foreground disabled:opacity-50"
            aria-label="添加参考图"
          >
            <Plus className="size-4" />
          </button>
          <input
            ref={referenceInputRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) void controller.uploadReferenceImage(file)
              event.target.value = ""
            }}
          />

          <select
            value={controller.imageMode}
            onChange={(event) => controller.setImageMode(event.target.value as VideoImageMode)}
            aria-label="视频生成模式"
            className="h-8 shrink-0 rounded-lg border border-foreground/8 bg-background px-2.5 text-xs"
          >
            <option value="T2V">文生视频</option>
            <option value="FIRST_FRAME">图生视频</option>
            <option value="REFERENCE">首尾帧</option>
          </select>

          {controller.imageMode === "REFERENCE" ? (
            <>
              <button
                type="button"
                onClick={() => lastFrameInputRef.current?.click()}
                disabled={controller.isSubmitting}
                className="flex h-8 shrink-0 items-center gap-1 rounded-lg border border-foreground/8 px-2.5 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06] disabled:opacity-50"
              >
                <Plus className="size-3" />
                尾帧
              </button>
              <input
                ref={lastFrameInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  if (file) void controller.uploadLastFrameImage(file)
                  event.target.value = ""
                }}
              />
            </>
          ) : null}

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
              <SkillPickerContent
                defaultCategory="VIDEO_GEN"
                onClose={() => setSkillPickerOpen(false)}
              />
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
