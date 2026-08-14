/**
 * 视频生成 controller。
 *
 * 管理视频模型、参数、品牌、技能、多参考图、首尾帧上传与提交，不依赖页面路由。
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
  PendingMediaImageAttachment,
  VideoInputMode
} from "@/features/studio/media-generation/types"
import { useAiSkills, useGenerateVideo } from "@/lib/api/rest/ai"
import { type AigcBrandProfileSelection, mergeAigcSystemPrompts } from "@/lib/api/rest/ai/aigc"
import { useEstimateAigcCredits } from "@/lib/hooks/use-estimate-aigc-credits"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { useGenerationParams } from "@/lib/hooks/use-generation-params"
import { useModelSelector } from "@/lib/hooks/use-model-selector"

const MAX_REFERENCE_IMAGES = 9

const MODEL_SUFFIX_BY_MODE: Record<VideoInputMode, string> = {
  T2V: "t2v",
  REFERENCE: "r2v",
  FIRST_LAST_FRAME: "r2v"
}

function attachmentFromDraft(
  fileId: number | undefined,
  previewSrc: string | undefined,
  name: string
): MediaImageAttachment | null {
  return fileId ? { fileId, url: previewSrc ?? "", previewSrc: previewSrc ?? "", name } : null
}

export function useVideoGenerationController({
  projectId = null,
  initialDraft,
  onTaskSubmitted
}: MediaGenerationControllerOptions = {}) {
  const initialImageMode: VideoInputMode =
    initialDraft?.videoImageMode ??
    (initialDraft?.lastFrameImageFileId
      ? "FIRST_LAST_FRAME"
      : initialDraft?.referenceImageFileId
        ? "REFERENCE"
        : "T2V")
  const initialReferenceImage = attachmentFromDraft(
    initialDraft?.referenceImageFileId,
    initialDraft?.referenceImagePreviewUrl,
    "参考图"
  )

  const [prompt, setPrompt] = useState(initialDraft?.prompt ?? "")
  const [imageMode, setImageModeState] = useState<VideoInputMode>(initialImageMode)
  const [referenceImages, setReferenceImages] = useState<MediaImageAttachment[]>(() =>
    initialImageMode === "REFERENCE" && initialReferenceImage ? [initialReferenceImage] : []
  )
  const [firstFrameImage, setFirstFrameImage] = useState<MediaImageAttachment | null>(() =>
    initialImageMode === "FIRST_LAST_FRAME" ? initialReferenceImage : null
  )
  const [lastFrameImage, setLastFrameImage] = useState<MediaImageAttachment | null>(() =>
    initialImageMode === "FIRST_LAST_FRAME"
      ? attachmentFromDraft(
          initialDraft?.lastFrameImageFileId,
          initialDraft?.lastFrameImagePreviewUrl,
          "尾帧图"
        )
      : null
  )
  const [pendingImage, setPendingImage] = useState<PendingMediaImageAttachment | null>(null)
  const [pendingLastFrameImage, setPendingLastFrameImage] =
    useState<PendingMediaImageAttachment | null>(null)
  const [selectedBrand, setSelectedBrand] = useState("")
  const [selectedBrandProfile, setSelectedBrandProfile] =
    useState<AigcBrandProfileSelection | null>(null)

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

  const setImageMode = useCallback((nextMode: VideoInputMode) => {
    setImageModeState(nextMode)
    setPendingImage(null)
    setPendingLastFrameImage(null)
    if (nextMode === "REFERENCE") {
      setFirstFrameImage(null)
      setLastFrameImage(null)
    } else if (nextMode === "FIRST_LAST_FRAME") {
      setReferenceImages([])
    } else {
      setReferenceImages([])
      setFirstFrameImage(null)
      setLastFrameImage(null)
    }
  }, [])

  const uploadReferenceImages = useCallback(
    async (files: File[]) => {
      const remaining = MAX_REFERENCE_IMAGES - referenceImages.length
      if (remaining <= 0) {
        toast.info(`参考图最多上传 ${MAX_REFERENCE_IMAGES} 张`)
        return
      }
      const selectedFiles = files.slice(0, remaining)
      if (files.length > remaining) {
        toast.info(`参考图最多上传 ${MAX_REFERENCE_IMAGES} 张`)
      }

      for (const file of selectedFiles) {
        const previewSrc = URL.createObjectURL(file)
        setPendingImage({ name: file.name, previewSrc })
        try {
          const result = await upload(file)
          setReferenceImages((current) => [
            ...current,
            { fileId: result.fileId, url: result.url, previewSrc, name: file.name }
          ])
        } catch {
          URL.revokeObjectURL(previewSrc)
          toast.error(`参考图「${file.name}」上传失败`)
        } finally {
          setPendingImage(null)
        }
      }
    },
    [referenceImages.length, upload]
  )

  const uploadFirstFrameImage = useCallback(
    async (file: File) => {
      const previewSrc = URL.createObjectURL(file)
      setPendingImage({ name: file.name, previewSrc })
      setFirstFrameImage(null)
      try {
        const result = await upload(file)
        setFirstFrameImage({ fileId: result.fileId, url: result.url, previewSrc, name: file.name })
      } catch {
        URL.revokeObjectURL(previewSrc)
        toast.error("首帧图上传失败")
      } finally {
        setPendingImage(null)
      }
    },
    [upload]
  )

  const uploadLastFrameImage = useCallback(
    async (file: File) => {
      const previewSrc = URL.createObjectURL(file)
      setPendingLastFrameImage({ name: file.name, previewSrc })
      setLastFrameImage(null)
      try {
        const result = await upload(file)
        setLastFrameImage({ fileId: result.fileId, url: result.url, previewSrc, name: file.name })
      } catch {
        URL.revokeObjectURL(previewSrc)
        toast.error("尾帧图上传失败")
      } finally {
        setPendingLastFrameImage(null)
      }
    },
    [upload]
  )

  const submit = useCallback(async () => {
    const normalizedPrompt = prompt.trim()
    if (imageMode === "T2V" && !normalizedPrompt) {
      toast.error("请输入创作描述")
      return
    }
    if (imageMode === "REFERENCE" && !referenceImages.some((image) => image.fileId !== undefined)) {
      toast.error("请至少上传一张参考图")
      return
    }
    if (imageMode === "FIRST_LAST_FRAME" && !firstFrameImage?.fileId) {
      toast.error("请上传首帧图")
      return
    }
    if (imageMode === "FIRST_LAST_FRAME" && !lastFrameImage?.fileId) {
      toast.error("请上传尾帧图")
      return
    }

    const referenceImageFileIds =
      imageMode === "REFERENCE"
        ? referenceImages
            .map((image) => image.fileId)
            .filter((fileId): fileId is number => fileId !== undefined)
        : imageMode === "FIRST_LAST_FRAME"
          ? [firstFrameImage?.fileId, lastFrameImage?.fileId].filter(
              (fileId): fileId is number => fileId !== undefined
            )
          : undefined

    try {
      const videoConfig = currentModel?.videoConfig
      const taskId = await generateVideo.mutateAsync({
        prompt: normalizedPrompt || "参考图生成视频",
        model: resolvedModelId ?? undefined,
        projectId,
        imageMode: imageMode === "T2V" ? "T2V" : "REFERENCE",
        referenceImageFileIds,
        ...(videoConfig?.resolutions?.length
          ? { resolution: params.resolution ?? videoConfig.resolutions[0] }
          : {}),
        ...(videoConfig?.ratios?.length
          ? { ratio: params.aspectRatio ?? videoConfig.ratios[0] }
          : {}),
        duration: Number(params.videoDuration?.replace("s", "")) || undefined,
        systemPrompt: mergeAigcSystemPrompts(
          selectedSkill?.systemPrompt,
          selectedBrandProfile?.systemPrompt
        )
      })
      toast.success(`视频任务已提交（#${taskId}）`)
      setPrompt("")
      setReferenceImages([])
      setFirstFrameImage(null)
      setLastFrameImage(null)
      setPendingImage(null)
      setPendingLastFrameImage(null)
      onTaskSubmitted?.({ mode: "video", taskId })
    } catch {
      // API 客户端已统一提示请求错误
    }
  }, [
    currentModel,
    firstFrameImage,
    generateVideo,
    imageMode,
    lastFrameImage,
    onTaskSubmitted,
    params,
    projectId,
    prompt,
    referenceImages,
    resolvedModelId,
    selectedBrandProfile,
    selectedSkill
  ])

  const hasRequiredInput =
    imageMode === "T2V"
      ? prompt.trim().length > 0
      : imageMode === "REFERENCE"
        ? referenceImages.some((image) => image.fileId !== undefined)
        : Boolean(firstFrameImage?.fileId && lastFrameImage?.fileId)

  return {
    prompt,
    setPrompt,
    imageMode,
    setImageMode,
    referenceImages,
    firstFrameImage,
    lastFrameImage,
    pendingImage,
    pendingLastFrameImage,
    maxReferenceImages: MAX_REFERENCE_IMAGES,
    uploadReferenceImages,
    uploadFirstFrameImage,
    uploadLastFrameImage,
    removeReferenceImage: (index: number) =>
      setReferenceImages((current) => current.filter((_, itemIndex) => itemIndex !== index)),
    removeFirstFrameImage: () => {
      setFirstFrameImage(null)
      setPendingImage(null)
    },
    removeLastFrameImage: () => {
      setLastFrameImage(null)
      setPendingLastFrameImage(null)
    },
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
    selectedBrandProfile,
    setSelectedBrandProfile,
    selectedSkill,
    creditEstimate,
    setSelectedSkillId,
    submit,
    isSubmitting: generateVideo.isPending || uploading,
    canSubmit: !generateVideo.isPending && !uploading && hasRequiredInput
  }
}
