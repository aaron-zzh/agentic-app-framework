/**
 * 富文本编辑器 Preset 配置
 * @author AaronZZH & Kiro
 */

import type { ToolbarFeature } from "../types"

export type PresetName = "minimal" | "chatter" | "richField" | "document"

export interface PresetConfig {
  toolbarFeatures: ToolbarFeature[]
  image: boolean
  mention: boolean
  showToolbar: boolean
  draggable: boolean
  slashMenu: boolean
  floatingToolbar: boolean
}

export const presets: Record<PresetName, PresetConfig> = {
  minimal: {
    toolbarFeatures: [],
    image: false,
    mention: false,
    showToolbar: false,
    draggable: false,
    slashMenu: false,
    floatingToolbar: false
  },
  chatter: {
    toolbarFeatures: ["format", "link"],
    image: false,
    mention: true,
    showToolbar: true,
    draggable: false,
    slashMenu: false,
    floatingToolbar: false
  },
  richField: {
    toolbarFeatures: ["history", "heading", "format", "list", "quote", "code", "link", "ai"],
    image: false,
    mention: false,
    showToolbar: true,
    draggable: true,
    slashMenu: true,
    floatingToolbar: true
  },
  document: {
    toolbarFeatures: [
      "history",
      "heading",
      "format",
      "list",
      "quote",
      "code",
      "link",
      "image",
      "ai"
    ],
    image: true,
    mention: false,
    showToolbar: true,
    draggable: true,
    slashMenu: true,
    floatingToolbar: true
  }
}
