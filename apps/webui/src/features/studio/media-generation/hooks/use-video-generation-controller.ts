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
  return fileId
    ? {
        fileId,
        url: previewSrc ?? "",
        previewSrc: previewSrc ?? "",
        name,
        source: "UPLOAD"
      }
    : null
}

export function useVideoGenerationController({
  projectTarget,
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
  const runAction = useRunAigcAction()

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

  const resolveUploadedAttachment = useCallback(
    async (file: File, previewSrc: string): Promise<MediaImageAttachment> => {
      const result = await upload(file)
      if (!projectTarget) {
        return {
          fileId: result.fileId,
          url: result.url,
          previewSrc,
          name: file.name,
          source: "UPLOAD"
        }
      }
      const media = await mediaApi.materializeUploadedImage({
        fileId: result.fileId,
        name: file.name,
        originalProjectId: projectTarget.projectId
      })
      URL.revokeObjectURL(previewSrc)
      return {
        fileId: media.currentVersion.fileId,
        url: media.currentVersion.url,
        previewSrc: media.currentVersion.thumbnailUrl ?? media.currentVersion.url,
        name: media.name,
        source: "UPLOAD",
        mediaVersionId: media.currentVersion.id
      }
    },
    [projectTarget, upload]
  )

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
          const attachment = await resolveUploadedAttachment(file, previewSrc)
          setReferenceImages((current) => [...current, attachment])
        } catch {
          URL.revokeObjectURL(previewSrc)
          toast.error(`参考图「${file.name}」上传失败`)
        } finally {
          setPendingImage(null)
        }
      }
    },
    [referenceImages.length, resolveUploadedAttachment]
  )

  const uploadFirstFrameImage = useCallback(
    async (file: File) => {
      const previewSrc = URL.createObjectURL(file)
      setPendingImage({ name: file.name, previewSrc })
      setFirstFrameImage(null)
      try {
        const attachment = await resolveUploadedAttachment(file, previewSrc)
        setFirstFrameImage(attachment)
      } catch {
        URL.revokeObjectURL(previewSrc)
        toast.error("首帧图上传失败")
      } finally {
        setPendingImage(null)
      }
    },
    [resolveUploadedAttachment]
  )

  const uploadLastFrameImage = useCallback(
    async (file: File) => {
      const previewSrc = URL.createObjectURL(file)
      setPendingLastFrameImage({ name: file.name, previewSrc })
      setLastFrameImage(null)
      try {
        const attachment = await resolveUploadedAttachment(file, previewSrc)
        setLastFrameImage(attachment)
      } catch {
        URL.revokeObjectURL(previewSrc)
        toast.error("尾帧图上传失败")
      } finally {
        setPendingLastFrameImage(null)
      }
    },
    [resolveUploadedAttachment]
  )

  const addProjectReferenceImage = useCallback((attachment: MediaImageAttachment) => {
    setReferenceImages((current) => {
      if (
        attachment.mediaVersionId !== undefined &&
        current.some((image) => image.mediaVersionId === attachment.mediaVersionId)
      ) {
        toast.info("该项目素材已添加")
        return current
      }
      if (current.length >= MAX_REFERENCE_IMAGES) {
        toast.info(`参考图最多上传 ${MAX_REFERENCE_IMAGES} 张`)
        return current
      }
      return [...current, attachment]
    })
  }, [])

  const selectProjectFirstFrameImage = useCallback((attachment: MediaImageAttachment) => {
    setPendingImage(null)
    setFirstFrameImage(attachment)
  }, [])

  const selectProjectLastFrameImage = useCallback((attachment: MediaImageAttachment) => {
    setPendingLastFrameImage(null)
    setLastFrameImage(attachment)
  }, [])

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

    const selectedAttachments =
      imageMode === "REFERENCE"
        ? referenceImages
        : imageMode === "FIRST_LAST_FRAME"
          ? [firstFrameImage, lastFrameImage].filter(
              (attachment): attachment is MediaImageAttachment => attachment !== null
            )
          : []
    if (projectTarget && selectedAttachments.some((image) => !image.mediaVersionId)) {
      toast.error("项目生成仅接受已物化的媒体版本")
      return
    }

    try {
      const videoConfig = currentModel?.videoConfig
      const resolution = videoConfig?.resolutions?.length
        ? (params.resolution ?? videoConfig.resolutions[0])
        : undefined
      const ratio = videoConfig?.ratios?.length
        ? (params.aspectRatio ?? videoConfig.ratios[0])
        : undefined
      const duration = Number(params.videoDuration?.replace("s", "")) || undefined

      if (projectTarget) {
        const run = await runAction.mutateAsync({
          projectId: projectTarget.projectId,
          data: {
            objectId: projectTarget.objectId,
            actionKey: projectTarget.actionKey,
            prompt: normalizedPrompt || "参考图生成视频",
            requestedModelId: resolvedModelId ?? undefined,
            actionArguments: {
              resolution,
              ratio,
              duration,
              imageMode: imageMode === "T2V" ? "T2V" : "REFERENCE"
            },
            attachmentMediaVersionIds: selectedAttachments.map(
              (attachment) => attachment.mediaVersionId as number
            ),
            confirmed: true,
            idempotencyKey: crypto.randomUUID()
          }
        })
        toast.success(`项目视频执行已提交（#${run.id}）`)
        setPrompt("")
        setReferenceImages([])
        setFirstFrameImage(null)
        setLastFrameImage(null)
        setPendingImage(null)
        setPendingLastFrameImage(null)
        onTaskSubmitted?.({ mode: "video", executionRunId: run.id })
        return
      }

      const taskId = await generateVideo.mutateAsync({
        prompt: normalizedPrompt || "参考图生成视频",
        model: resolvedModelId ?? undefined,
        imageMode: imageMode === "T2V" ? "T2V" : "REFERENCE",
        referenceImageFileIds,
        resolution,
        ratio,
        duration,
        systemPrompt: mergeAigcSystemPrompts(
          selectedSkill?.currentVersion?.content,
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
    } catch (error) {
      if (projectTarget) {
        toast.error(error instanceof Error ? error.message : "提交失败，请重试")
      }
    }
  }, [
    currentModel,
    firstFrameImage,
    generateVideo,
    imageMode,
    lastFrameImage,
    onTaskSubmitted,
    params,
    projectTarget,
    prompt,
    referenceImages,
    resolvedModelId,
    runAction,
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
    addProjectReferenceImage,
    selectProjectFirstFrameImage,
    selectProjectLastFrameImage,
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
    isSubmitting: generateVideo.isPending || runAction.isPending || uploading,
    canSubmit: !generateVideo.isPending && !runAction.isPending && !uploading && hasRequiredInput
  }
}
