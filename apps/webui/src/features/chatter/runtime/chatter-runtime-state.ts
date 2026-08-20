/**
 * Chatter AG-UI 路径与 HttpAgent.initialState 纯构造逻辑。
 * @author AaronZZH & Kiro
 */

import {
  type ChatterTarget,
  DEFAULT_TASK_MODEL_SELECTION,
  type TaskModelSelection
} from "@/features/chatter/types"

export const DEFAULT_CHATTER_ASSISTANT_ID = "system.assistant.default-user"

interface ChatterPageRuntimeConfig {
  preset?: string
  agentRole?: string
}

interface BuildChatterInitialStateOptions {
  target: ChatterTarget
  currentPageId?: string | null
  pageConfig?: ChatterPageRuntimeConfig
  taskModelSelection?: TaskModelSelection
}

/** 构建传给 HttpAgent.initialState 的 Chatter 状态。 */
export function buildChatterInitialState({
  target,
  currentPageId,
  pageConfig,
  taskModelSelection
}: BuildChatterInitialStateOptions): Record<string, unknown> {
  const usesAssistantRuntime = target.type === "ai"
  const effectiveTaskModelSelection = usesAssistantRuntime
    ? (taskModelSelection ?? DEFAULT_TASK_MODEL_SELECTION)
    : undefined
  const assistantId =
    target.assistantId ?? (usesAssistantRuntime ? DEFAULT_CHATTER_ASSISTANT_ID : undefined)

  return {
    pageId: currentPageId,
    preset: pageConfig?.preset,
    ...(assistantId ? { assistantId } : {}),
    ...(effectiveTaskModelSelection ? { taskModelSelection: effectiveTaskModelSelection } : {})
  }
}

/** 解析 Chatter 使用的 AG-UI API 路径。 */
export function resolveChatterAguiPath(target: ChatterTarget): string {
  if (target.type === "kiro") {
    return "/autodev/kiro/run"
  }
  return "/agui/run"
}
