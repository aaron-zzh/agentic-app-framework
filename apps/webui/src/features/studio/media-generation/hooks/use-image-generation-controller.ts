/**
 * 图像生成 controller。
 *
 * 管理模型、参数、技能、参考图上传和任务提交，不依赖页面路由与结果展示。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useMemo, useState } from "react"
import { toast } from "sonner"
import { useAigcStore } from "@/features/aigc/store"
import type {
  MediaGenerationControllerOptions,
  MediaImageAttachment,
  PendingMediaImageAttachment
} from "@/features/studio/media-generation/types"
import { useAiSkills, useGenerateImage } from "@/lib/api/rest/ai"
import {
  type AigcBrandProfileSelection,
  mergeAigcSystemPrompts,
  useRunAigcAction
} from "@/lib/api/rest/ai/aigc"
import { mediaApi } from "@/lib/api/rest/media"
import { useEstimateAigcCredits } from "@/lib/hooks/use-estimate-aigc-credits"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"

export function useImageGenerationController({
  projectTarget,
  initialDraft,
  onTaskSubmitted
}: MediaGenerationControllerOptions = {}) {
  const [prompt, setPrompt] = useState(initialDraft?.prompt ?? "")
  const [selectedBrandProfile, setSelectedBrandProfile] =
    useState<AigcBrandProfileSelection | null>(null)
  const [referenceImage, setReferenceImage] = useState<MediaImageAttachment | null>(() =>
    initialDraft?.referenceImageFileId
      ? {
          fileId: initialDraft.referenceImageFileId,
          url: initialDraft.referenceImagePreviewUrl ?? "",
          previewSrc: initialDraft.referenceImagePreviewUrl ?? "",
          name: "参考图",
          source: "UPLOAD"
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
  } = useModelSelector("IMAGE_GEN", { defaultValue: initialDraft?.model ?? "n1n:gpt-image-2" })
  const { params, onChangeParams, resolvedSize } = useGenerationParams(currentModel)
  const generateImage = useGenerateImage()
  const runAction = useRunAigcAction()
  const estimateParams = useMemo(
    () => ({
      imageCount: params.imageCount ?? 1,
      width: resolvedSize.width,
      height: resolvedSize.height,
      quality: params.quality
    }),
    [params.imageCount, params.quality, resolvedSize.height, resolvedSize.width]
  )
  const creditEstimate = useEstimateAigcCredits({
    type: "IMAGE",
    model: modelId,
    prompt,
    params: estimateParams,
    enabled: Boolean(modelId)
  })

  const uploadReferenceImage = useCallback(
    async (file: File) => {
      const previewSrc = URL.createObjectURL(file)
      setPendingImage({ name: file.name, previewSrc })
      setReferenceImage(null)
      try {
        const result = await upload(file)
        if (projectTarget) {
          const media = await mediaApi.materializeUploadedImage({
            fileId: result.fileId,
            name: file.name,
            originalProjectId: projectTarget.projectId
          })
          setReferenceImage({
            fileId: media.currentVersion.fileId,
            url: media.currentVersion.url,
            previewSrc: media.currentVersion.thumbnailUrl ?? media.currentVersion.url,
            name: media.name,
            source: "UPLOAD",
            mediaVersionId: media.currentVersion.id
          })
          URL.revokeObjectURL(previewSrc)
        } else {
          setReferenceImage({
            fileId: result.fileId,
            url: result.url,
            previewSrc,
            name: file.name,
            source: "UPLOAD"
          })
        }
        setPendingImage(null)
      } catch {
        URL.revokeObjectURL(previewSrc)
        setPendingImage(null)
        toast.error("参考图上传失败")
      }
    },
    [projectTarget, upload]
  )

  const selectReferenceImage = useCallback((attachment: MediaImageAttachment) => {
    setPendingImage(null)
    setReferenceImage(attachment)
  }, [])

  const removeReferenceImage = useCallback(() => {
    setReferenceImage(null)
    setPendingImage(null)
  }, [])

  const submit = useCallback(async () => {
    const normalizedPrompt = prompt.trim()
    if (!normalizedPrompt && !referenceImage?.fileId) {
      toast.error("请输入创作描述")
      return
    }

    try {
      const { width, height, sizePreset } = resolvedSize
      const imageConfig = currentModel?.imageConfig
      if (projectTarget) {
        const run = await runAction.mutateAsync({
          projectId: projectTarget.projectId,
          data: {
            objectId: projectTarget.objectId,
            actionKey: projectTarget.actionKey,
            prompt: normalizedPrompt || "参考图生成",
            requestedModelId: modelId ?? undefined,
            actionArguments: {
              width,
              height,
              imageCount: params.imageCount,
              promptExtend: params.promptExtend,
              quality: params.quality,
              format: params.format,
              background: params.background,
              contentModeration: params.contentModeration,
              seed: params.seed && params.seed > 0 ? params.seed : undefined
            },
            attachmentMediaVersionIds: referenceImage?.mediaVersionId
              ? [referenceImage.mediaVersionId]
              : [],
            confirmed: true,
            idempotencyKey: crypto.randomUUID()
          }
        })
        toast.success(`项目图像执行已提交（#${run.id}）`)
        setPrompt("")
        setReferenceImage(null)
        setPendingImage(null)
        onTaskSubmitted?.({ mode: "image", executionRunId: run.id })
        return
      }

      const taskId = await generateImage.mutateAsync({
        prompt: normalizedPrompt || "参考图生成",
        model: modelId ?? undefined,
        width,
        height,
        sizePreset,
        aspectRatio: imageConfig?.mode === "ratio" ? params.aspectRatio : undefined,
        imageCount: params.imageCount,
        promptExtend: params.promptExtend,
        quality: params.quality,
        format: params.format,
        background: params.background,
        contentModeration: params.contentModeration,
        seed: params.seed && params.seed > 0 ? params.seed : undefined,
        imageFileIds: referenceImage?.fileId ? [referenceImage.fileId] : undefined,
        systemPrompt: mergeAigcSystemPrompts(
          selectedSkill?.currentVersion?.content,
          selectedBrandProfile?.systemPrompt
        )
      })
      toast.success(`图像任务已提交（#${taskId}）`)
      setPrompt("")
      setReferenceImage(null)
      setPendingImage(null)
      onTaskSubmitted?.({ mode: "image", taskId })
    } catch (error) {
      if (projectTarget) {
        toast.error(error instanceof Error ? error.message : "提交失败，请重试")
      }
    }
  }, [
    currentModel,
    generateImage,
    modelId,
    onTaskSubmitted,
    params,
    projectTarget,
    prompt,
    referenceImage,
    resolvedSize,
    runAction,
    selectedBrandProfile,
    selectedSkill
  ])

  return {
    prompt,
    setPrompt,
    referenceImage,
    pendingImage,
    uploadReferenceImage,
    selectReferenceImage,
    removeReferenceImage,
    uploadProgress: progress,
    modelOptions,
    modelId,
    setModelId,
    currentModel,
    params,
    onChangeParams,
    selectedBrandProfile,
    setSelectedBrandProfile,
    selectedSkill,
    creditEstimate,
    setSelectedSkillId,
    submit,
    isSubmitting: generateImage.isPending || runAction.isPending || uploading,
    canSubmit:
      !generateImage.isPending &&
      !runAction.isPending &&
      !uploading &&
      (prompt.trim().length > 0 ||
        Boolean(projectTarget ? referenceImage?.mediaVersionId : referenceImage?.fileId))
  }
}
