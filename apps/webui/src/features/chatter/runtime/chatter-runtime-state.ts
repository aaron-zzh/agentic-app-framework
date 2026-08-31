/**
 * Chatter AG-UI 路径、线程共享状态与本次 run 调用参数的纯构造逻辑。
 *
 * 两者按 AG-UI 协议语义分开：`state` 只放页面感知上下文（pageId / preset），
 * `forwardedProps` 放本次 run 的调用参数（assistantId / taskModelSelection）。
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

interface BuildChatterStateOptions {
  currentPageId?: string | null
  pageConfig?: ChatterPageRuntimeConfig
}

interface BuildChatterForwardedPropsOptions {
  target: ChatterTarget
  taskModelSelection?: TaskModelSelection
}

/** 构建 AG-UI `state`：仅页面感知上下文，可被服务端 STATE_SNAPSHOT 回吐。 */
export function buildChatterState({
  currentPageId,
  pageConfig
}: BuildChatterStateOptions): Record<string, unknown> {
  return {
    pageId: currentPageId,
    preset: pageConfig?.preset
  }
}

/** 构建 AG-UI `forwardedProps`：本次 run 的一次性调用参数。 */
export function buildChatterForwardedProps({
  target,
  taskModelSelection
}: BuildChatterForwardedPropsOptions): Record<string, unknown> {
  const usesAssistantRuntime = target.type === "ai"
  const effectiveTaskModelSelection = usesAssistantRuntime
    ? (taskModelSelection ?? DEFAULT_TASK_MODEL_SELECTION)
    : undefined
  const assistantId =
    target.assistantId ?? (usesAssistantRuntime ? DEFAULT_CHATTER_ASSISTANT_ID : undefined)

  return {
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
