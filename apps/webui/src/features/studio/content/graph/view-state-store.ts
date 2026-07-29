/**
 * ProjectGraph 视图状态。
 *
 * 只保存焦点、viewport、折叠、图层和语义缩放；禁止保存服务端 graph。
 * @author AaronZZH & Kiro
 */

import type { Viewport } from "@xyflow/react"
import { create } from "zustand"
import type { ContentRelationLayer } from "@/lib/api/rest/content"
import type { ProjectGraphStage, ProjectGraphZoomTier } from "./graph-projection"

interface ProjectGraphViewState {
  focusObjectId: number | null
  viewport: Viewport
  collapsedGroups: ProjectGraphStage[]
  activeLayers: ContentRelationLayer[]
  zoomTier: ProjectGraphZoomTier
  setFocusObjectId: (id: number | null) => void
  setViewport: (viewport: Viewport) => void
  toggleCollapsedGroup: (group: ProjectGraphStage) => void
  toggleLayer: (layer: ContentRelationLayer) => void
  setZoomTier: (tier: ProjectGraphZoomTier) => void
}

export const useProjectGraphViewState = create<ProjectGraphViewState>((set) => ({
  focusObjectId: null,
  viewport: { x: 0, y: 0, zoom: 0.8 },
  collapsedGroups: [],
  activeLayers: ["domain"],
  zoomTier: "overview",
  setFocusObjectId: (focusObjectId) => set({ focusObjectId }),
  setViewport: (viewport) => set({ viewport }),
  toggleCollapsedGroup: (group) =>
    set((state) => ({
      collapsedGroups: state.collapsedGroups.includes(group)
        ? state.collapsedGroups.filter((item) => item !== group)
        : [...state.collapsedGroups, group]
    })),
  toggleLayer: (layer) =>
    set((state) => ({
      activeLayers: state.activeLayers.includes(layer)
        ? state.activeLayers.filter((item) => item !== layer)
        : [...state.activeLayers, layer]
    })),
  setZoomTier: (zoomTier) => set({ zoomTier })
}))
