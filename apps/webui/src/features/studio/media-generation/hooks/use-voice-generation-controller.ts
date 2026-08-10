/**
 * 配音生成 controller。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import { VOICES } from "@/features/aigc/voice-options"
import type { MediaGenerationControllerOptions } from "@/features/studio/media-generation/types"
import { request } from "@/lib/api/rest/entity"

interface VoiceSubmissionInput {
  prompt: string
  voiceId: string
}

export function useVoiceGenerationController({
  projectId = null,
  initialDraft,
  onTaskSubmitted
}: MediaGenerationControllerOptions = {}) {
  const [prompt, setPrompt] = useState(initialDraft?.prompt ?? "")
  const [voiceId, setVoiceId] = useState(VOICES[0].value)
  const generateVoice = useMutation({
    mutationFn: ({ prompt: text, voiceId: selectedVoice }: VoiceSubmissionInput) =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "VOICE",
          prompt: text,
          projectId,
          params: { voice: selectedVoice }
        })
      })
  })

  const submit = useCallback(async () => {
    const normalizedPrompt = prompt.trim()
    if (!normalizedPrompt) {
      toast.error("请输入配音文本")
      return
    }
    if (normalizedPrompt.length > 200) {
      toast.error("配音文本不超过 200 字")
      return
    }

    try {
      const taskId = await generateVoice.mutateAsync({ prompt: normalizedPrompt, voiceId })
      toast.success(`配音任务已提交（#${taskId}）`)
      setPrompt("")
      onTaskSubmitted?.({ mode: "voice", taskId })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "提交失败，请重试")
    }
  }, [generateVoice, onTaskSubmitted, prompt, voiceId])

  return {
    prompt,
    setPrompt,
    voiceId,
    setVoiceId,
    submit,
    isSubmitting: generateVoice.isPending,
    canSubmit: !generateVoice.isPending && prompt.trim().length > 0
  }
}
