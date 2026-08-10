/**
 * 3D 模型生成 controller。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { useCallback, useState } from "react"
import { toast } from "sonner"
import type { MediaGenerationControllerOptions } from "@/features/studio/media-generation/types"
import { request } from "@/lib/api/rest/entity"

export const TEXTURE_OPTIONS = [
  { value: "none", label: "无贴图" },
  { value: "standard", label: "标清贴图" },
  { value: "detailed", label: "高清贴图" }
] as const

export type TextureQuality = (typeof TEXTURE_OPTIONS)[number]["value"]

interface Model3dSubmissionInput {
  prompt: string
  textureQuality: TextureQuality
}

export function useModel3dGenerationController({
  projectId = null,
  initialDraft,
  onTaskSubmitted
}: MediaGenerationControllerOptions = {}) {
  const [prompt, setPrompt] = useState(initialDraft?.prompt ?? "")
  const [textureQuality, setTextureQuality] = useState<TextureQuality>("none")
  const generateModel3d = useMutation({
    mutationFn: (input: Model3dSubmissionInput) =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "MODEL_3D",
          prompt: input.prompt,
          projectId,
          params: { source: "text", textureQuality: input.textureQuality }
        })
      })
  })

  const submit = useCallback(async () => {
    const normalizedPrompt = prompt.trim()
    if (!normalizedPrompt) {
      toast.error("请输入创作描述")
      return
    }

    try {
      const taskId = await generateModel3d.mutateAsync({
        prompt: normalizedPrompt,
        textureQuality
      })
      toast.success(`3D 任务已提交（#${taskId}）`)
      setPrompt("")
      onTaskSubmitted?.({ mode: "model-3d", taskId })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "提交失败，请重试")
    }
  }, [generateModel3d, onTaskSubmitted, prompt, textureQuality])

  return {
    prompt,
    setPrompt,
    textureQuality,
    setTextureQuality,
    submit,
    isSubmitting: generateModel3d.isPending,
    canSubmit: !generateModel3d.isPending && prompt.trim().length > 0
  }
}
