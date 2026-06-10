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
  /** 文案生成类型：oral=口播 xiaohongshu=小红书 */
  copywritingType: "oral" | "xiaohongshu"
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
  /** 文件区只展示未分配素材 */
  fileFilterUnassigned: boolean
  /** 正在生成中的任务（显示 loading 占位） */
  pendingTasks: Array<{ id: number; prompt: string; type: string }>
  /** 生成类型：image=AI生图 video=AI视频 */
  generationType: "image" | "video"
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

  setGenerationPanelOpen: (open: boolean) => void
  setStoryboardPanelOpen: (open: boolean) => void
  setGenerationType: (type: "image" | "video") => void
  setAgentRole: (roleId: string) => void
  setCopywritingPanelOpen: (open: boolean) => void
  setCopywritingContent: (content: string) => void
  setCopywritingType: (type: "oral" | "xiaohongshu") => void
  setCopywritingTemplate: (template: string) => void
  setCopywritingTranslateTo: (lang: string) => void
  setCopywritingLength: (length: "short" | "medium" | "long") => void
  setCopywritingModel: (model: string) => void
  setPreviewAsset: (asset: MediaAssetVO | null) => void
  setPreviewList: (list: MediaAssetVO[]) => void
  navigatePreview: (direction: 1 | -1) => void
  addReferenceAsset: (asset: MediaAssetVO) => void
  removeReferenceAsset: (id: number) => void
  addStoryboardAsset: (asset: MediaAssetVO) => void
  removeStoryboardAsset: (id: number) => void
  toggleFileFilter: () => void
  setPrompt: (prompt: string) => void
  setModel: (model: string) => void
  setResolution: (resolution: string) => void
  setAspectRatio: (ratio: string) => void
  setVideoDuration: (duration: string) => void
  addPendingTask: (task: { id: number; prompt: string; type: string }) => void
  removePendingTask: (id: number) => void
}

export const useAigcStore = create<AigcStore>((set, _get) => ({
  generationPanelOpen: false,
  generationType: "image",
  videoDuration: "5s",
  copywritingPanelOpen: false,
  copywritingContent: "",
  copywritingType: "oral",
  copywritingTemplate: "",
  copywritingTranslateTo: "",
  copywritingLength: "medium",
  copywritingModel: "GPT-4o",
  previewAsset: null,
  previewList: [],
  referenceAssets: [],
  storyboardAssets: [],
  storyboardPanelOpen: true,
  fileFilterUnassigned: false,
  pendingTasks: [],
  prompt: "",
  model: "GPT Image 2",
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
  addStoryboardAsset: (asset) =>
    set((state) => {
      if (state.storyboardAssets.some((a) => a.id === asset.id)) return state
      return { storyboardAssets: [...state.storyboardAssets, asset] }
    }),
  removeStoryboardAsset: (id) =>
    set((state) => ({ storyboardAssets: state.storyboardAssets.filter((a) => a.id !== id) })),
  toggleFileFilter: () => set((state) => ({ fileFilterUnassigned: !state.fileFilterUnassigned })),
  setPrompt: (prompt) => set({ prompt }),
  setModel: (model) => set({ model }),
  setResolution: (resolution) => set({ resolution }),
  setAspectRatio: (ratio) => set({ aspectRatio: ratio }),
  setVideoDuration: (duration) => set({ videoDuration: duration }),
  addPendingTask: (task) => set((state) => ({ pendingTasks: [...state.pendingTasks, task] })),
  removePendingTask: (id) =>
    set((state) => ({ pendingTasks: state.pendingTasks.filter((t) => t.id !== id) }))
}))
