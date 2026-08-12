/**
 * 图像生成 Composer。
 *
 * 可用于完整媒体工作台，也可通过 embedded 外观独立嵌入画布图片节点。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Plus, Wand2 } from "lucide-react"
import { useRef, useState } from "react"
import { ModelParamsPopover } from "@/components/common/ModelParamsPopover"
import { ModelSelector } from "@/components/common/ModelSelector"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { BrandProfilePicker } from "@/features/aigc/generation/BrandProfilePicker"
import { PromptTemplateDialog } from "@/features/aigc/generation/PromptTemplateDialog"
import { SkillPickerContent } from "@/features/aigc/generation/SkillPicker"
import { SnippetPickerDialog } from "@/features/aigc/generation/SnippetPickerDialog"
import { ImageUploadChip } from "@/features/studio/home/ImageUploadChip"
import { MediaComposerShell } from "@/features/studio/media-generation/components/MediaComposerShell"
import { useImageGenerationController } from "@/features/studio/media-generation/hooks/use-image-generation-controller"
import type { MediaGenerationComposerProps } from "@/features/studio/media-generation/types"
import { cn } from "@/lib/utils"

/** 渲染图像生成输入、模型、技能和参数控件。 */
export function ImageGenerationComposer(props: MediaGenerationComposerProps) {
  const controller = useImageGenerationController(props)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [skillPickerOpen, setSkillPickerOpen] = useState(false)

  const attachment = controller.pendingImage ?? controller.referenceImage

  return (
    <MediaComposerShell
      prompt={controller.prompt}
      onPromptChange={controller.setPrompt}
      onSubmit={controller.submit}
      placeholder="描述你想生成的图像内容..."
      canSubmit={controller.canSubmit}
      isSubmitting={controller.isSubmitting}
      creditEstimate={controller.creditEstimate}
      leadingTools={props.leadingTools}
      headerTools={
        <div className="flex items-center gap-1.5">
          <PromptTemplateDialog
            type="IMAGE_GEN"
            hasReferenceImages={Boolean(controller.referenceImage)}
            onSelect={controller.setPrompt}
            triggerClassName="flex h-8 shrink-0 items-center gap-1 rounded-lg border border-foreground/8 px-2.5 text-muted-foreground text-xs transition-colors hover:bg-foreground/[0.06]"
          />
          <SnippetPickerDialog
            value={controller.prompt}
            onChange={controller.setPrompt}
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
        attachment ? (
          <ImageUploadChip
            name={attachment.name}
            progress={controller.referenceImage ? 100 : controller.uploadProgress}
            previewSrc={attachment.previewSrc}
            onRemove={controller.removeReferenceImage}
          />
        ) : null
      }
      tools={
        <>
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={controller.isSubmitting}
            className="flex size-8 shrink-0 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-foreground/[0.06] hover:text-foreground disabled:opacity-50"
            aria-label="添加参考图"
          >
            <Plus className="size-4" />
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) void controller.uploadReferenceImage(file)
              event.target.value = ""
            }}
          />

          <ModelSelector
            variant="dropdown"
            options={controller.modelOptions}
            value={controller.modelId}
            onChange={controller.setModelId}
            className="h-8 shrink-0 gap-1 rounded-lg border border-foreground/8 px-2.5 text-muted-foreground text-xs hover:bg-foreground/[0.06]"
          />

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
                defaultCategory="IMAGE_GEN"
                onClose={() => setSkillPickerOpen(false)}
              />
            </PopoverContent>
          </Popover>

          <ModelParamsPopover
            model={controller.currentModel}
            params={controller.params}
            onChangeParams={controller.onChangeParams}
            isEditMode={Boolean(
              controller.referenceImage && controller.currentModel?.imageConfig?.edit
            )}
          />
        </>
      }
    />
  )
}
