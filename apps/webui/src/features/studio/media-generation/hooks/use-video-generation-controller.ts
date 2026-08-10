/**
 * 视频生成 controller。
 *
 * 管理视频模型、参数、品牌、技能、首尾帧上传与提交，不依赖页面路由。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { toast } from "sonner"
import { useAigcStore } from "@/features/aigc/store"
import type {
  MediaGenerationControllerOptions,
  MediaImageAttachment,
  PendingMediaImageAttachment
} from "@/features/studio/media-generation/types"
import {
  useAiSkills,
  useGenerateVideo,
  type VideoImageMode
} from "@/lib/api/rest/ai"
import { useEstimateAigcCredits } from "@/lib/hooks/use-estimate-aigc-credits"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"

const MODEL_SUFFIX_BY_MODE: Record<VideoImageMode, string> = {
  T2V: "t2v",
  FIRST_FRAME: "i2v",
  REFERENCE: "r2v"
}

function attachmentFromUrl(url: string | undefined, name: string): MediaImageAttachment | null {
  return url ? { url, previewSrc: url, name } : null
}

export function useVideoGenerationController({
  projectId = null,
  initialDraft,
  onTaskSubmitted
}: MediaGenerationControllerOptions = {}) {
  const [prompt, setPrompt] = useState(initialDraft?.prompt ?? "")
  const [imageMode, setImageModeState] = useState<VideoImageMode>(
    initialDraft?.videoImageMode ?? "T2V"
  )
  const [referenceImage, setReferenceImage] = useState<MediaImageAttachment | null>(() =>
    attachmentFromUrl(initialDraft?.referenceImageUrl, "参考图")
  )
  const [lastFrameImage, setLastFrameImage] = useState<MediaImageAttachment | null>(() =>
    attachmentFromUrl(initialDraft?.lastFrameImageUrl, "尾帧")
  )
  const [pendingImage, setPendingImage] = useState<PendingMediaImageAttachment | null>(null)
  const [selectedBrand, setSelectedBrand] = useState("")

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
  } = useModelSelector("VIDEO_GEN", { defaultValue: initialDraft?.model })
  const { params, onChangeParams } = useGenerationParams(currentModel)
  const generateVideo = useGenerateVideo()

  const brands = useMemo(
    () =>
      Array.from(
        modelOptions.reduce((map, option) => {
          if (!map.has(option.meta.provider)) {
            map.set(option.meta.provider, option.meta.displayName.split(/[-\s]/)[0])
          }
          return map
        }, new Map<string, string>())
      ).map(([provider, label]) => ({ provider, label })),
    [modelOptions]
  )

  useEffect(() => {
    if (brands.length === 0 || selectedBrand) return
    const currentProvider = modelOptions.find((option) => option.value === modelId)?.meta.provider
    if (currentProvider) {
      setSelectedBrand(currentProvider)
      return
    }

    const firstProvider = brands[0].provider
    setSelectedBrand(firstProvider)
    const matched =
      modelOptions.find(
        (option) =>
          option.meta.provider === firstProvider &&
          option.value.includes(MODEL_SUFFIX_BY_MODE[imageMode])
      ) ?? modelOptions.find((option) => option.meta.provider === firstProvider)
    if (matched) setModelId(matched.value)
  }, [brands, imageMode, modelId, modelOptions, selectedBrand, setModelId])

  const resolvedModelId = useMemo(() => {
    const selectedProvider = currentModel?.provider ?? selectedBrand
    return (
      modelOptions.find(
        (option) =>
          option.meta.provider === selectedProvider &&
          option.value.includes(MODEL_SUFFIX_BY_MODE[imageMode])
      )?.value ?? modelId
    )
  }, [currentModel?.provider, imageMode, modelId, modelOptions, selectedBrand])
  const estimateParams = useMemo(
    () => ({
      duration: Number(params.videoDuration?.replace("s", "")) || undefined,
      resolution: params.resolution ?? currentModel?.videoConfig?.resolutions?.[0],
      ratio: params.aspectRatio ?? currentModel?.videoConfig?.ratios?.[0]
    }),
    [
      currentModel?.videoConfig?.ratios,
      currentModel?.videoConfig?.resolutions,
      params.aspectRatio,
      params.resolution,
      params.videoDuration
    ]
  )
  const creditEstimate = useEstimateAigcCredits({
    type: "VIDEO",
    model: resolvedModelId,
    prompt,
    params: estimateParams,
    enabled: Boolean(resolvedModelId)
  })

  const selectBrand = useCallback(
    (provider: string) => {
      setSelectedBrand(provider)
      const matched =
        modelOptions.find(
          (option) =>
            option.meta.provider === provider &&
            option.value.includes(MODEL_SUFFIX_BY_MODE[imageMode])
        ) ?? modelOptions.find((option) => option.meta.provider === provider)
      if (matched) setModelId(matched.value)
    },
    [imageMode, modelOptions, setModelId]
  )

  const setImageMode = useCallback((nextMode: VideoImageMode) => {
    setImageModeState(nextMode)
    if (nextMode === "T2V") {
      setReferenceImage(null)
      setLastFrameImage(null)
    } else if (nextMode !== "REFERENCE") {
      setLastFrameImage(null)
    }
  }, [])

  const uploadReferenceImage = useCallback(
    async (file: File) => {
      const previewSrc = URL.createObjectURL(file)
      setPendingImage({ name: file.name, previewSrc })
      setReferenceImage(null)
      try {
        const result = await upload(file)
        setReferenceImage({ url: result.url, previewSrc, name: file.name })
        setPendingImage(null)
        if (imageMode === "T2V") setImageModeState("FIRST_FRAME")
      } catch {
        URL.revokeObjectURL(previewSrc)
        setPendingImage(null)
        toast.error("参考图上传失败")
      }
    },
    [imageMode, upload]
  )

  const uploadLastFrameImage = useCallback(
    async (file: File) => {
      const previewSrc = URL.createObjectURL(file)
      try {
        const result = await upload(file)
        setLastFrameImage({ url: result.url, previewSrc, name: file.name })
      } catch {
        URL.revokeObjectURL(previewSrc)
        toast.error("尾帧上传失败")
      }
    },
    [upload]
  )

  const submit = useCallback(async () => {
    const normalizedPrompt = prompt.trim()
    if (!normalizedPrompt && !referenceImage) {
      toast.error("请输入创作描述")
      return
    }
    if (imageMode !== "T2V" && !referenceImage) {
      toast.error(imageMode === "REFERENCE" ? "请上传首帧" : "请上传起始图")
      return
    }
    if (imageMode === "REFERENCE" && !lastFrameImage) {
      toast.error("请上传尾帧")
      return
    }

    try {
      const videoConfig = currentModel?.videoConfig
      const taskId = await generateVideo.mutateAsync({
        prompt: normalizedPrompt || "参考图生成视频",
        model: resolvedModelId ?? undefined,
        projectId,
        imageMode,
        imageUrl: imageMode === "FIRST_FRAME" ? referenceImage?.url : undefined,
        referenceImageUrls:
          imageMode === "REFERENCE"
            ? [referenceImage?.url, lastFrameImage?.url].filter(
                (url): url is string => Boolean(url)
              )
            : undefined,
        ...(videoConfig?.resolutions?.length
          ? { resolution: params.resolution ?? videoConfig.resolutions[0] }
          : {}),
        ...(videoConfig?.ratios?.length
          ? { ratio: params.aspectRatio ?? videoConfig.ratios[0] }
          : {}),
        duration: Number(params.videoDuration?.replace("s", "")) || undefined,
        systemPrompt: selectedSkill?.systemPrompt ?? undefined
      })
      toast.success(`视频任务已提交（#${taskId}）`)
      setPrompt("")
      setReferenceImage(null)
      setLastFrameImage(null)
      setPendingImage(null)
      onTaskSubmitted?.({ mode: "video", taskId })
    } catch {
      // API 客户端已统一提示请求错误
    }
  }, [
    currentModel,
    generateVideo,
    imageMode,
    lastFrameImage,
    modelId,
    modelOptions,
    onTaskSubmitted,
    params,
    projectId,
    prompt,
    referenceImage,
    resolvedModelId,
    selectedSkill
  ])

  return {
    prompt,
    setPrompt,
    imageMode,
    setImageMode,
    referenceImage,
    lastFrameImage,
    pendingImage,
    uploadReferenceImage,
    uploadLastFrameImage,
    removeReferenceImage: () => {
      setReferenceImage(null)
      setPendingImage(null)
    },
    removeLastFrameImage: () => setLastFrameImage(null),
    uploadProgress: progress,
    modelOptions,
    modelId,
    setModelId,
    currentModel,
    params,
    onChangeParams,
    brands,
    selectedBrand,
    selectBrand,
    selectedSkill,
    creditEstimate,
    setSelectedSkillId,
    submit,
    isSubmitting: generateVideo.isPending || uploading,
    canSubmit:
      !generateVideo.isPending &&
      !uploading &&
      (prompt.trim().length > 0 || Boolean(referenceImage))
  }
}
