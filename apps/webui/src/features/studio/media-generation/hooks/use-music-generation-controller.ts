/**
 * 音乐生成 controller。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import type { MediaGenerationControllerOptions } from "@/features/studio/media-generation/types"
import { request } from "@/lib/api/rest/entity"

interface MusicSubmissionInput {
  prompt: string
  lyrics: string
  gender: string
}

export function useMusicGenerationController({
  projectId = null,
  initialDraft,
  onTaskSubmitted
}: MediaGenerationControllerOptions = {}) {
  const [prompt, setPrompt] = useState(initialDraft?.prompt ?? "")
  const [lyrics, setLyrics] = useState("")
  const [gender, setGender] = useState("female")
  const generateMusic = useMutation({
    mutationFn: (input: MusicSubmissionInput) =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "MUSIC",
          prompt: input.lyrics || input.prompt,
          projectId,
          params: {
            lyrics: input.lyrics || undefined,
            gender: input.gender
          }
        })
      })
  })

  const submit = useCallback(async () => {
    const normalizedPrompt = prompt.trim()
    const normalizedLyrics = lyrics.trim()
    if (!normalizedPrompt && !normalizedLyrics) {
      toast.error("请输入创作描述或歌词")
      return
    }

    try {
      const taskId = await generateMusic.mutateAsync({
        prompt: normalizedPrompt,
        lyrics: normalizedLyrics,
        gender
      })
      toast.success(`音乐任务已提交（#${taskId}）`)
      setPrompt("")
      setLyrics("")
      onTaskSubmitted?.({ mode: "music", taskId })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "提交失败，请重试")
    }
  }, [gender, generateMusic, lyrics, onTaskSubmitted, prompt])

  return {
    prompt,
    setPrompt,
    lyrics,
    setLyrics,
    gender,
    setGender,
    submit,
    isSubmitting: generateMusic.isPending,
    canSubmit:
      !generateMusic.isPending && (prompt.trim().length > 0 || lyrics.trim().length > 0)
  }
}
