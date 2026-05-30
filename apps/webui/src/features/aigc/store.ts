/**
 * AIGC 模块 UI 状态管理
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import type { MediaAsset } from "./types"

interface AigcStore {
  /** 生成面板是否展开 */
  generationPanelOpen: boolean
  /** 当前预览的素材 */
  previewAsset: MediaAsset | null
  /** 拖入生成面板的参考素材 */
  referenceAssets: MediaAsset[]
  /** prompt 文本 */
  prompt: string
  /** 模型选择 */
  model: string
  /** 分辨率 */
  resolution: string
  /** 比例 */
  aspectRatio: string

  setGenerationPanelOpen: (open: boolean) => void
  setPreviewAsset: (asset: MediaAsset | null) => void
  addReferenceAsset: (asset: MediaAsset) => void
  removeReferenceAsset: (id: string) => void
  setPrompt: (prompt: string) => void
  setModel: (model: string) => void
  setResolution: (resolution: string) => void
  setAspectRatio: (ratio: string) => void
}

export const useAigcStore = create<AigcStore>((set) => ({
  generationPanelOpen: false,
  previewAsset: null,
  referenceAssets: [],
  prompt: "",
  // TODO: 提取为常量（DEFAULT_MODEL / DEFAULT_RESOLUTION / DEFAULT_ASPECT_RATIO）
  model: "GPT Image 2",
  resolution: "2K",
  aspectRatio: "9:16",

  setGenerationPanelOpen: (open) => set({ generationPanelOpen: open }),
  setPreviewAsset: (asset) => set({ previewAsset: asset }),
  addReferenceAsset: (asset) =>
    set((state) => {
      if (state.referenceAssets.length >= 16) return state
      if (state.referenceAssets.some((a) => a.id === asset.id)) return state
      return { referenceAssets: [...state.referenceAssets, asset] }
    }),
  removeReferenceAsset: (id) =>
    set((state) => ({
      referenceAssets: state.referenceAssets.filter((a) => a.id !== id)
    })),
  setPrompt: (prompt) => set({ prompt }),
  setModel: (model) => set({ model }),
  setResolution: (resolution) => set({ resolution }),
  setAspectRatio: (ratio) => set({ aspectRatio: ratio })
}))
