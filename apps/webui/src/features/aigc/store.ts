/**
 * AIGC 模块 UI 状态管理
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import type { MediaAssetVO } from "./types"

interface AigcStore {
  /** 生成面板是否展开 */
  generationPanelOpen: boolean
  /** 文案面板是否展开 */
  copywritingPanelOpen: boolean
  /** 文案内容 */
  copywritingContent: string
  /** 文案生成类型：oral=口播 xiaohongshu=小红书 viral=爆款复制 */
  copywritingType: "oral" | "xiaohongshu" | "viral"
  /** 文案生成模板 */
  copywritingTemplate: string
  /** 文案生成语言/翻译目标 */
  copywritingTranslateTo: string
  /** 文案生成长度（字数） */
  copywritingLength: "short" | "medium" | "long"
  /** 文案模型 */
  copywritingModel: string
  /** 当前预览的素材 */
  previewAsset: MediaAssetVO | null
  /** 预览素材列表（用于导航） */
  previewList: MediaAssetVO[]
  /** 拖入生成面板的参考素材 */
  referenceAssets: MediaAssetVO[]
  /** 元素区（从素材库中选取的关键元素） */
  storyboardAssets: MediaAssetVO[]
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
  /** 正在生成中的任务（显示 loading 占位） */
  pendingTasks: Array<{
    id: number
    prompt: string
    type: string
    modelId?: string
    ossUrl?: string
    error?: string
    asset?: MediaAssetVO
  }>
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
  /** 当前项目提示词标签（null = 未启用），由项目数据同步，删除标签不清空此源 */
  projectPromptTag: { label: string; content: string } | null
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
  setGenerationType: (type: "IMAGE_GEN" | "VIDEO_GEN" | "VOICE" | "MUSIC") => void
  setAgentRole: (roleId: string) => void
  setCopywritingPanelOpen: (open: boolean) => void
  setCopywritingContent: (content: string) => void
  setCopywritingType: (type: "oral" | "xiaohongshu" | "viral") => void
  setCopywritingTemplate: (template: string) => void
  setCopywritingTranslateTo: (lang: string) => void
  setCopywritingLength: (length: "short" | "medium" | "long") => void
  setCopywritingModel: (model: string) => void
  setPreviewAsset: (asset: MediaAssetVO | null) => void
  setPreviewList: (list: MediaAssetVO[]) => void
  navigatePreview: (direction: 1 | -1) => void
  addReferenceAsset: (asset: MediaAssetVO) => void
  removeReferenceAsset: (id: number) => void
  clearReferenceAssets: () => void
  addStoryboardAsset: (asset: MediaAssetVO) => void
  removeStoryboardAsset: (id: number) => void
  toggleFileFilter: () => void
  setFileAreaOpen: (open: boolean) => void
  setFileTypeFilter: (type: "ALL" | "IMAGE" | "VIDEO" | "AUDIO") => void
  setFileZoom: (zoom: number) => void
  setPrompt: (prompt: string) => void
  setProjectPromptTag: (tag: { label: string; content: string } | null) => void
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
  addPendingTask: (task: { id: number; prompt: string; type: string; modelId?: string }) => void
  completePendingTask: (id: number, ossUrl: string, asset?: MediaAssetVO) => void
  failPendingTask: (id: number, error: string) => void
  removePendingTask: (id: number) => void
}

export const useAigcStore = create<AigcStore>((set, _get) => ({
  generationPanelOpen: false,
  generationType: "IMAGE_GEN",
  videoDuration: "5s",
  copywritingPanelOpen: false,
  copywritingContent: "",
  copywritingType: "oral",
  copywritingTemplate: "",
  copywritingTranslateTo: "",
  copywritingLength: "medium",
  copywritingModel: "",
  previewAsset: null,
  previewList: [],
  referenceAssets: [],
  storyboardAssets: [],
  storyboardPanelOpen: true,
  fileFilterUnassigned: false,
  fileAreaOpen: true,
  fileTypeFilter: "ALL",
  fileZoom: 100,
  pendingTasks: [],
  prompt: "",
  projectPromptTag: null,
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
  setGenerationType: (type) => set({ generationType: type }),
  setAgentRole: (roleId) => set({ agentRole: roleId }),
  setCopywritingPanelOpen: (open) => set({ copywritingPanelOpen: open }),
  setCopywritingContent: (content) => set({ copywritingContent: content }),
  setCopywritingType: (type) => set({ copywritingType: type }),
  setCopywritingTemplate: (template) => set({ copywritingTemplate: template }),
  setCopywritingTranslateTo: (lang) => set({ copywritingTranslateTo: lang }),
  setCopywritingLength: (length) => set({ copywritingLength: length }),
  setCopywritingModel: (model) => set({ copywritingModel: model }),
  setPreviewAsset: (asset) => set({ previewAsset: asset }),
  setPreviewList: (list) => set({ previewList: list }),
  navigatePreview: (direction) =>
    set((state) => {
      if (!state.previewAsset || state.previewList.length === 0) return state
      const idx = state.previewList.findIndex((a) => a.id === state.previewAsset?.id)
      if (idx === -1) return state
      const next = state.previewList[idx + direction]
      return next ? { previewAsset: next } : state
    }),
  addReferenceAsset: (asset) =>
    set((state) => {
      if (state.referenceAssets.length >= 16) return state
      if (state.referenceAssets.some((a) => a.id === asset.id)) return state
      return { referenceAssets: [...state.referenceAssets, asset] }
    }),
  removeReferenceAsset: (id) =>
    set((state) => ({ referenceAssets: state.referenceAssets.filter((a) => a.id !== id) })),
  clearReferenceAssets: () => set({ referenceAssets: [] }),
  addStoryboardAsset: (asset) =>
    set((state) => {
      if (state.storyboardAssets.some((a) => a.id === asset.id)) return state
      return { storyboardAssets: [...state.storyboardAssets, asset] }
    }),
  removeStoryboardAsset: (id) =>
    set((state) => ({ storyboardAssets: state.storyboardAssets.filter((a) => a.id !== id) })),
  toggleFileFilter: () => set((state) => ({ fileFilterUnassigned: !state.fileFilterUnassigned })),
  setFileAreaOpen: (open) => set({ fileAreaOpen: open }),
  setFileTypeFilter: (type) => set({ fileTypeFilter: type }),
  setFileZoom: (zoom) => set({ fileZoom: zoom }),
  setPrompt: (prompt) => set({ prompt }),
  // 项目提示词源变化时（项目加载/切换/更新）自动重置移除状态，确保新内容默认展示
  setProjectPromptTag: (projectPromptTag) =>
    set({ projectPromptTag, projectPromptDismissed: false }),
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
  setVideoDuration: (duration) => set({ videoDuration: duration }),
  addPendingTask: (task) =>
    set((state) => {
      if (state.pendingTasks.some((t) => t.id === task.id)) return state
      return { pendingTasks: [...state.pendingTasks, task] }
    }),
  completePendingTask: (id, ossUrl, asset) =>
    set((state) => ({
      pendingTasks: state.pendingTasks.map((t) => (t.id === id ? { ...t, ossUrl, asset } : t))
    })),
  failPendingTask: (id, error) =>
    set((state) => ({
      pendingTasks: state.pendingTasks.map((t) => (t.id === id ? { ...t, error } : t))
    })),
  removePendingTask: (id) =>
    set((state) => ({ pendingTasks: state.pendingTasks.filter((t) => t.id !== id) }))
}))
