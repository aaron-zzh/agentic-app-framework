/**
 * ProjectGraph 项目隔离视图状态。
 *
 * 每个项目独立持久化焦点、主视图、viewport、折叠、图层和语义缩放；禁止保存服务端 graph。
 * @author AaronZZH & Kiro
 */

import type { Viewport } from "@xyflow/react"
import { create, type StoreApi, type UseBoundStore } from "zustand"
import { persist } from "zustand/middleware"
import type { AigcRelationLayer } from "@/lib/api/rest/ai/aigc"
import type { ProjectWorkbenchView } from "../ProjectWorkbenchHeader"
import type { ProjectGraphStage, ProjectGraphZoomTier } from "./graph-projection"

export interface ProjectGraphViewState {
  view: ProjectWorkbenchView
  focusObjectId: number | null
  viewport: Viewport
  collapsedGroups: ProjectGraphStage[]
  activeLayers: AigcRelationLayer[]
  zoomTier: ProjectGraphZoomTier
  setView: (view: ProjectWorkbenchView) => void
  setFocusObjectId: (id: number | null) => void
  setViewport: (viewport: Viewport) => void
  toggleCollapsedGroup: (group: ProjectGraphStage) => void
  toggleLayer: (layer: AigcRelationLayer) => void
  setZoomTier: (tier: ProjectGraphZoomTier) => void
}

type ProjectViewStore = UseBoundStore<StoreApi<ProjectGraphViewState>>
export const PROJECT_VIEW_STORE_LIMIT = 8
const stores = new Map<number, ProjectViewStore>()

function createProjectStore(projectId: number): ProjectViewStore {
  return create<ProjectGraphViewState>()(
    persist(
      (set) => ({
        view: "structure",
        focusObjectId: null,
        viewport: { x: 0, y: 0, zoom: 0.8 },
        collapsedGroups: [],
        activeLayers: ["domain"],
        zoomTier: "overview",
        setView: (view) => set({ view }),
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
      }),
      {
        name: `aaf.studio.project-view.${projectId}`,
        version: 1,
        partialize: ({ view, focusObjectId, viewport, collapsedGroups, activeLayers, zoomTier }) => ({
          view,
          focusObjectId,
          viewport,
          collapsedGroups,
          activeLayers,
          zoomTier
        })
      }
    )
  )
}

function getProjectStore(projectId: number): ProjectViewStore {
  const existing = stores.get(projectId)
  if (existing) {
    stores.delete(projectId)
    stores.set(projectId, existing)
    return existing
  }

  while (stores.size >= PROJECT_VIEW_STORE_LIMIT) {
    const oldestProjectId = stores.keys().next().value
    if (oldestProjectId === undefined) break
    stores.delete(oldestProjectId)
  }
  const store = createProjectStore(projectId)
  stores.set(projectId, store)
  return store
}

/** 页面卸载时释放项目级 store 引用；持久化的纯 UI 偏好保留。 */
export function releaseProjectGraphViewState(projectId: number): void {
  stores.delete(projectId)
}

/** 暴露缓存规模供边界测试与诊断。 */
export function getProjectGraphViewStoreCount(): number {
  return stores.size
}

export function useProjectGraphViewState<T>(
  projectId: number,
  selector: (state: ProjectGraphViewState) => T
): T {
  return getProjectStore(projectId)(selector)
}
