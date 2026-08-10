/**
 * 3D 模型生成 Composer。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { MediaComposerShell } from "@/features/studio/media-generation/components/MediaComposerShell"
import {
  TEXTURE_OPTIONS,
  type TextureQuality,
  useModel3dGenerationController
} from "@/features/studio/media-generation/hooks/use-model-3d-generation-controller"
import type { MediaGenerationComposerProps } from "@/features/studio/media-generation/types"

/** 渲染 3D 模型描述和贴图质量控件。 */
export function Model3dGenerationComposer(props: MediaGenerationComposerProps) {
  const controller = useModel3dGenerationController(props)

  return (
    <MediaComposerShell
      prompt={controller.prompt}
      onPromptChange={controller.setPrompt}
      onSubmit={controller.submit}
      placeholder="描述你想生成的 3D 模型..."
      canSubmit={controller.canSubmit}
      isSubmitting={controller.isSubmitting}
      creditEstimate={controller.creditEstimate}
      leadingTools={props.leadingTools}
      appearance={props.appearance}
      className={props.className}
      tools={
        <select
          value={controller.textureQuality}
          onChange={(event) =>
            controller.setTextureQuality(event.target.value as TextureQuality)
          }
          aria-label="贴图质量"
          className="h-8 shrink-0 rounded-lg border border-foreground/8 bg-background px-2.5 text-xs"
        >
          {TEXTURE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      }
    />
  )
}
