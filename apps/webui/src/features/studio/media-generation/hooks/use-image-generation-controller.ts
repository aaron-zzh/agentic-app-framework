/**
 * 图像生成 controller。
 *
 * 管理模型、参数、技能、参考图上传和任务提交，不依赖页面路由与结果展示。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"
import { toast } from "sonner"
import { useAigcStore } from "@/features/aigc/store"
import { useAiSkills, useGenerateImage } from "@/lib/api/rest/ai"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import type {
  MediaGenerationControllerOptions,
  MediaImageAttachment,
  PendingMediaImageAttachment
} from "@/features/studio/media-generation/types"

export function useImageGenerationController({
  projectId = null,
  initialDraft,
  onTaskSubmitted
}: MediaGenerationControllerOptions = {}) {
  const [prompt, setPrompt] = useState(initialDraft?.prompt ?? "")
  const [referenceImage, setReferenceImage] = useState<MediaImageAttachment | null>(() =>
    initialDraft?.referenceImageUrl
      ? {
          url: initialDraft.referenceImageUrl,
          previewSrc: initialDraft.referenceImageUrl,
          name: "参考图"
        }
      : null
  )
  const [pendingImage, setPendingImage] = useState<PendingMediaImageAttachment | null>(null)

  const { upload, uploading, progress } = useFileUpload()
  const selectedSkillId = useAigcStore((state) => state.selectedSkillId)
  const setSelectedSkillId = useAigcStore((state) => state.setSelectedSkillId)
  const { data: allSkills } = useAiSkills()
  const selectedSkill = allSkills?.find((skill) => skill.id === selectedSkillId) ?? null

  const {
    options: modelOptions,
    modelId,
    setModelId,
    currentModel
  } = useModelSelector("IMAGE_GEN", { defaultValue: initialDraft?.model })
  const { params, onChangeParams, resolvedSize } = useGenerationParams(currentModel)
  const generateImage = useGenerateImage()

  const uploadReferenceImage = useCallback(
    async (file: File) => {
      const previewSrc = URL.createObjectURL(file)
      setPendingImage({ name: file.name, previewSrc })
      setReferenceImage(null)
      try {
        const result = await upload(file)
        setReferenceImage({ url: result.url, previewSrc, name: file.name })
        setPendingImage(null)
      } catch {
        URL.revokeObjectURL(previewSrc)
        setPendingImage(null)
        toast.error("参考图上传失败")
      }
    },
    [upload]
  )

  const removeReferenceImage = useCallback(() => {
    setReferenceImage(null)
    setPendingImage(null)
  }, [])

  const submit = useCallback(async () => {
    const normalizedPrompt = prompt.trim()
    if (!normalizedPrompt && !referenceImage) {
      toast.error("请输入创作描述")
      return
    }

    try {
      const { width, height, sizePreset } = resolvedSize
      const imageConfig = currentModel?.imageConfig
      const taskId = await generateImage.mutateAsync({
        prompt: normalizedPrompt || "参考图生成",
        model: modelId ?? undefined,
        projectId,
        width,
        height,
        sizePreset,
        aspectRatio: imageConfig?.mode === "ratio" ? params.aspectRatio : undefined,
        imageCount: params.imageCount,
        promptExtend: params.promptExtend,
        quality: params.quality,
        format: params.format,
        imageUrls: referenceImage ? [referenceImage.url] : undefined,
        systemPrompt: selectedSkill?.systemPrompt ?? undefined
      })
      toast.success(`图像任务已提交（#${taskId}）`)
      setPrompt("")
      setReferenceImage(null)
      setPendingImage(null)
      onTaskSubmitted?.({ mode: "image", taskId })
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "提交失败，请重试")
    }
  }, [
    currentModel,
    generateImage,
    modelId,
    onTaskSubmitted,
    params,
    projectId,
    prompt,
    referenceImage,
    resolvedSize,
    selectedSkill
  ])

  return {
    prompt,
    setPrompt,
    referenceImage,
    pendingImage,
    uploadReferenceImage,
    removeReferenceImage,
    uploadProgress: progress,
    modelOptions,
    modelId,
    setModelId,
    currentModel,
    params,
    onChangeParams,
    selectedSkill,
    setSelectedSkillId,
    submit,
    isSubmitting: generateImage.isPending || uploading,
    canSubmit:
      !generateImage.isPending &&
      !uploading &&
      (prompt.trim().length > 0 || Boolean(referenceImage))
  }
}
