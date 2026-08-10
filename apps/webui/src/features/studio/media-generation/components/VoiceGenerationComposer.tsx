/**
 * 配音生成 Composer。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { VOICES } from "@/features/aigc/voice-options"
import { MediaComposerShell } from "@/features/studio/media-generation/components/MediaComposerShell"
import { useVoiceGenerationController } from "@/features/studio/media-generation/hooks/use-voice-generation-controller"
import type { MediaGenerationComposerProps } from "@/features/studio/media-generation/types"

/** 渲染配音文本和音色控件。 */
export function VoiceGenerationComposer(props: MediaGenerationComposerProps) {
  const controller = useVoiceGenerationController(props)

  return (
    <MediaComposerShell
      prompt={controller.prompt}
      onPromptChange={controller.setPrompt}
      onSubmit={controller.submit}
      placeholder="输入需要配音的文本..."
      maxLength={200}
      canSubmit={controller.canSubmit}
      isSubmitting={controller.isSubmitting}
      leadingTools={props.leadingTools}
      appearance={props.appearance}
      className={props.className}
      tools={
        <select
          value={controller.voiceId}
          onChange={(event) => controller.setVoiceId(event.target.value)}
          aria-label="音色"
          className="h-8 shrink-0 rounded-lg border border-foreground/8 bg-background px-2.5 text-xs"
        >
          {VOICES.map((voice) => (
            <option key={voice.value} value={voice.value}>
              {voice.label}
            </option>
          ))}
        </select>
      }
    />
  )
}
