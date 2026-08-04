/**
 * AIGC 模块 UI 状态管理
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"

/** 尚未进入 Media 聚合的用户上传参考文件，仅作为当前生成表单草稿。 */
export interface UploadedReferenceDraft {
  key: string
  name: string
  url: string
}

interface AigcStore {
  /** 生成面板是否展开 */
  generationPanelOpen: boolean
  /** 文案面板是否展开 */
  copywritingPanelOpen: boolean
  /** 文案内容 */
  copywritingContent: string
  /** 文案生成类型：直接传 ai_skill_definition.code（如 voiceover=口播 redbook=小红书），viral=爆款复制固定值 */
  copywritingType: string
  /** 文案生成模板 */
  copywritingTemplate: string
  /** 文案生成语言/翻译目标 */
  copywritingTranslateTo: string
  /** 文案生成长度（字数） */
  copywritingLength: "short" | "medium" | "long"
  /** 文案模型 */
  copywritingModel: string
  /** 文案生成参考图（用于视觉理解辅助生成）：key 透传给后端，url 用于前端缩略图预览 */
  copywritingReferenceImages: Array<{ key: string; url: string; name: string }>
  /** 当前预览的媒体 ID；媒体对象由 TanStack Query 持有。 */
  previewMediaId: number | null
  /** 预览媒体 ID 列表（用于导航）。 */
  previewMediaIds: number[]
  /** 拖入生成面板的持久媒体 ID。 */
  referenceMediaIds: number[]
  /** 当前生成表单中新上传、尚未建 Media 的参考文件草稿。 */
  uploadedReferenceDrafts: UploadedReferenceDraft[]
  /** 元素区的持久媒体 ID。 */
  storyboardMediaIds: number[]
  /** 元素看板是否展开 */
  storyboardPanelOpen: boolean
  /** 素材区只展示未分配素材 */
  fileFilterUnassigned: boolean
  /** 素材区是否展开 */
  fileAreaOpen: boolean
  /** 素材区素材类型筛选 */
  fileTypeFilter: "ALL" | "IMAGE" | "VIDEO" | "AUDIO"
  /** 素材区缩放比例（50-150） */
  fileZoom: number
  /** 当前选中的技能 ID；技能对象由 TanStack Query 持有。 */
  selectedSkillId: number | null
  /** 生成类型：image=AI生图 video=AI视频 */
  generationType: "IMAGE_GEN" | "VIDEO_GEN" | "VOICE" | "MUSIC"
  /** 视频时长（秒） */
  videoDuration: string
  /** 模型选择 */
  model: string
  /** 分辨率 */
  resolution: string
  /** 比例 */
  aspectRatio: string
  /** 生成时使用的助理角色 roleId */
  agentRole: string
  /** 生成 Prompt */
  prompt: string
  /** 项目提示词是否被用户临时移除（true = 不注入输入框且不参与生成，可一键恢复） */
  projectPromptDismissed: boolean
  /** 随机种子（0 表示不指定） */
  seed: number
  /** 是否开启提示词智能改写 */
  promptExtend: boolean
  /** 反向提示词 */
  negativePrompt: string
  /** 生成张数 */
  imageCount: number
  /** 画质：low / medium / high / auto */
  quality: string
  /** 图片格式：png / jpeg / webp */
  format: string
  /** 尺寸档位：1K / 2K / 4K（万相等档位模型用） */
  sizePreset: string
  /** 背景模式：auto / transparent / opaque */
  background: string
  /** 内容审核：auto / low */
  contentModeration: string

  setGenerationPanelOpen: (open: boolean) => void
  setStoryboardPanelOpen: (open: boolean) => void
  setSelectedSkillId: (skillId: number | null) => void
  setGenerationType: (type: "IMAGE_GEN" | "VIDEO_GEN" | "VOICE" | "MUSIC") => void
  setAgentRole: (roleId: string) => void
  setCopywritingPanelOpen: (open: boolean) => void
  setCopywritingContent: (content: string) => void
  setCopywritingType: (type: string) => void
  setCopywritingTemplate: (template: string) => void
  setCopywritingTranslateTo: (lang: string) => void
  setCopywritingLength: (length: "short" | "medium" | "long") => void
  setCopywritingModel: (model: string) => void
  addCopywritingReferenceImage: (image: { key: string; url: string; name: string }) => void
  removeCopywritingReferenceImage: (key: string) => void
  clearCopywritingReferenceImages: () => void
  setPreviewMediaId: (mediaId: number | null) => void
  setPreviewMediaIds: (mediaIds: number[]) => void
  navigatePreview: (direction: 1 | -1) => void
  addReferenceMediaId: (mediaId: number) => void
  addUploadedReferenceDraft: (draft: UploadedReferenceDraft) => void
  removeReferenceMediaId: (mediaId: number) => void
  removeUploadedReferenceDraft: (key: string) => void
  clearReferenceAssets: () => void
  addStoryboardMediaId: (mediaId: number) => void
  removeStoryboardMediaId: (mediaId: number) => void
  toggleFileFilter: () => void
  setFileAreaOpen: (open: boolean) => void
  setFileTypeFilter: (type: "ALL" | "IMAGE" | "VIDEO" | "AUDIO") => void
  setFileZoom: (zoom: number) => void
  setPrompt: (prompt: string) => void
  setProjectPromptDismissed: (dismissed: boolean) => void
  setSeed: (seed: number) => void
  setPromptExtend: (v: boolean) => void
  setNegativePrompt: (v: string) => void
  setImageCount: (n: number) => void
  setQuality: (q: string) => void
  setFormat: (f: string) => void
  setSizePreset: (s: string) => void
  setBackground: (b: string) => void
  setContentModeration: (v: string) => void
  setModel: (model: string) => void
  setResolution: (resolution: string) => void
  setAspectRatio: (ratio: string) => void
  setVideoDuration: (duration: string) => void
}

export const useAigcStore = create<AigcStore>((set, _get) => ({
  generationPanelOpen: false,
  selectedSkillId: null,
  generationType: "IMAGE_GEN",
  videoDuration: "5s",
  copywritingPanelOpen: false,
  copywritingContent: "",
  copywritingType: "voiceover",
  copywritingTemplate: "",
  copywritingTranslateTo: "",
  copywritingLength: "medium",
  copywritingModel: "",
  copywritingReferenceImages: [],
  previewMediaId: null,
  previewMediaIds: [],
  referenceMediaIds: [],
  uploadedReferenceDrafts: [],
  storyboardMediaIds: [],
  storyboardPanelOpen: true,
  fileFilterUnassigned: false,
  fileAreaOpen: true,
  fileTypeFilter: "ALL",
  fileZoom: 100,
  prompt: "",
  projectPromptDismissed: false,
  seed: 0,
  promptExtend: true,
  negativePrompt: "",
  imageCount: 1,
  quality: "auto",
  format: "png",
  sizePreset: "2K",
  background: "auto",
  contentModeration: "auto",
  model: "",
  resolution: "2K",
  aspectRatio: "9:16",
  agentRole: "",

  setGenerationPanelOpen: (open) => set({ generationPanelOpen: open }),
  setStoryboardPanelOpen: (open) => set({ storyboardPanelOpen: open }),
  setSelectedSkillId: (selectedSkillId) => set({ selectedSkillId }),
  setGenerationType: (type) => set({ generationType: type }),
  setAgentRole: (roleId) => set({ agentRole: roleId }),
  setCopywritingPanelOpen: (open) => set({ copywritingPanelOpen: open }),
  setCopywritingContent: (content) => set({ copywritingContent: content }),
  setCopywritingType: (type) => set({ copywritingType: type }),
  setCopywritingTemplate: (template) => set({ copywritingTemplate: template }),
  setCopywritingTranslateTo: (lang) => set({ copywritingTranslateTo: lang }),
  setCopywritingLength: (length) => set({ copywritingLength: length }),
  setCopywritingModel: (model) => set({ copywritingModel: model }),
  addCopywritingReferenceImage: (image) =>
    set((state) => {
      // 上限 4 张：避免视觉 token 占用过大
      if (state.copywritingReferenceImages.length >= 4) return state
      if (state.copywritingReferenceImages.some((img) => img.key === image.key)) return state
      return { copywritingReferenceImages: [...state.copywritingReferenceImages, image] }
    }),
  removeCopywritingReferenceImage: (key) =>
    set((state) => ({
      copywritingReferenceImages: state.copywritingReferenceImages.filter((img) => img.key !== key)
    })),
  clearCopywritingReferenceImages: () => set({ copywritingReferenceImages: [] }),
  setPreviewMediaId: (previewMediaId) => set({ previewMediaId }),
  setPreviewMediaIds: (previewMediaIds) => set({ previewMediaIds }),
  navigatePreview: (direction) =>
    set((state) => {
      if (state.previewMediaId === null || state.previewMediaIds.length === 0) return state
      const index = state.previewMediaIds.indexOf(state.previewMediaId)
      if (index === -1) return state
      const nextMediaId = state.previewMediaIds[index + direction]
      return nextMediaId !== undefined ? { previewMediaId: nextMediaId } : state
    }),
  addReferenceMediaId: (mediaId) =>
    set((state) => {
      if (state.referenceMediaIds.length + state.uploadedReferenceDrafts.length >= 16) return state
      if (state.referenceMediaIds.includes(mediaId)) return state
      return { referenceMediaIds: [...state.referenceMediaIds, mediaId] }
    }),
  addUploadedReferenceDraft: (draft) =>
    set((state) => {
      if (state.referenceMediaIds.length + state.uploadedReferenceDrafts.length >= 16) return state
      if (state.uploadedReferenceDrafts.some((item) => item.key === draft.key)) return state
      return { uploadedReferenceDrafts: [...state.uploadedReferenceDrafts, draft] }
    }),
  removeReferenceMediaId: (mediaId) =>
    set((state) => ({
      referenceMediaIds: state.referenceMediaIds.filter((id) => id !== mediaId)
    })),
  removeUploadedReferenceDraft: (key) =>
    set((state) => ({
      uploadedReferenceDrafts: state.uploadedReferenceDrafts.filter((item) => item.key !== key)
    })),
  clearReferenceAssets: () => set({ referenceMediaIds: [], uploadedReferenceDrafts: [] }),
  addStoryboardMediaId: (mediaId) =>
    set((state) => {
      if (state.storyboardMediaIds.includes(mediaId)) return state
      return { storyboardMediaIds: [...state.storyboardMediaIds, mediaId] }
    }),
  removeStoryboardMediaId: (mediaId) =>
    set((state) => ({
      storyboardMediaIds: state.storyboardMediaIds.filter((id) => id !== mediaId)
    })),
  toggleFileFilter: () => set((state) => ({ fileFilterUnassigned: !state.fileFilterUnassigned })),
  setFileAreaOpen: (open) => set({ fileAreaOpen: open }),
  setFileTypeFilter: (type) => set({ fileTypeFilter: type }),
  setFileZoom: (zoom) => set({ fileZoom: zoom }),
  setPrompt: (prompt) => set({ prompt }),
  setProjectPromptDismissed: (projectPromptDismissed) => set({ projectPromptDismissed }),
  setSeed: (seed) => set({ seed }),
  setPromptExtend: (promptExtend) => set({ promptExtend }),
  setNegativePrompt: (negativePrompt) => set({ negativePrompt }),
  setImageCount: (imageCount) => set({ imageCount }),
  setQuality: (quality) => set({ quality }),
  setFormat: (format) => set({ format }),
  setSizePreset: (sizePreset) => set({ sizePreset }),
  setBackground: (background) => set({ background }),
  setContentModeration: (contentModeration) => set({ contentModeration }),
  setModel: (model) => set({ model }),
  setResolution: (resolution) => set({ resolution }),
  setAspectRatio: (ratio) => set({ aspectRatio: ratio }),
  setVideoDuration: (duration) => set({ videoDuration: duration })
}))
