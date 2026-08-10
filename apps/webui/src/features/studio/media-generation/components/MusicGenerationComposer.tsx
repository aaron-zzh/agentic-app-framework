/**
 * 音乐生成 Composer。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { MediaComposerShell } from "@/features/studio/media-generation/components/MediaComposerShell"
import { useMusicGenerationController } from "@/features/studio/media-generation/hooks/use-music-generation-controller"
import type { MediaGenerationComposerProps } from "@/features/studio/media-generation/types"

/** 渲染音乐描述、歌词和演唱音色控件。 */
export function MusicGenerationComposer(props: MediaGenerationComposerProps) {
  const controller = useMusicGenerationController(props)

  return (
    <MediaComposerShell
      prompt={controller.prompt}
      onPromptChange={controller.setPrompt}
      onSubmit={controller.submit}
      placeholder="描述你想生成的音乐..."
      canSubmit={controller.canSubmit}
      isSubmitting={controller.isSubmitting}
      leadingTools={props.leadingTools}
      appearance={props.appearance}
      className={props.className}
      extraInput={
        <textarea
          value={controller.lyrics}
          onChange={(event) => controller.setLyrics(event.target.value)}
          aria-label="歌词（可选）"
          placeholder="歌词（可选，填写后优先使用歌词）"
          maxLength={3000}
          disabled={controller.isSubmitting}
          className="min-h-16 w-full resize-none rounded-lg border border-foreground/8 bg-foreground/2 px-3 py-2 text-sm leading-6 outline-none placeholder:text-muted-foreground focus:border-primary/50 disabled:opacity-50"
        />
      }
      tools={
        <select
          value={controller.gender}
          onChange={(event) => controller.setGender(event.target.value)}
          aria-label="演唱音色"
          className="h-8 shrink-0 rounded-lg border border-foreground/8 bg-background px-2.5 text-xs"
        >
          <option value="female">女声</option>
          <option value="male">男声</option>
        </select>
      }
    />
  )
}
