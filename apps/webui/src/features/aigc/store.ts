/**
 * AIGC 模块 UI 状态管理
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import type { MediaAssetVO } from "./types"

interface AigcStore {
  /** 生成面板是否展开 */
  generationPanelOpen: boolean
  /** 当前预览的素材 */
  previewAsset: MediaAssetVO | null
  /** 预览素材列表（用于导航） */
  previewList: MediaAssetVO[]
  /** 拖入生成面板的参考素材 */
  referenceAssets: MediaAssetVO[]
  /** 故事板元素（从素材库中选取的关键元素） */
  storyboardAssets: MediaAssetVO[]
  /** 文件区只展示未分配素材 */
  fileFilterUnassigned: boolean
  /** prompt 文本 */
  prompt: string
  /** 模型选择 */
  model: string
  /** 分辨率 */
  resolution: string
  /** 比例 */
  aspectRatio: string

  setGenerationPanelOpen: (open: boolean) => void
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
}

export const useAigcStore = create<AigcStore>((set, _get) => ({
  generationPanelOpen: false,
  previewAsset: null,
  previewList: [],
  referenceAssets: [],
  storyboardAssets: [],
  fileFilterUnassigned: false,
  prompt: "",
  model: "GPT Image 2",
  resolution: "2K",
  aspectRatio: "9:16",

  setGenerationPanelOpen: (open) => set({ generationPanelOpen: open }),
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
  setAspectRatio: (ratio) => set({ aspectRatio: ratio })
}))
