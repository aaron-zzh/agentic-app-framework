/**
 * Studio 首页中央画布配色：资源类型（技能、工具、知识库）与运行步骤状态的连线色、节点样式。
 *
 * 连线是 SVG，颜色用具体色值；节点用 Tailwind 类。两者保持同一色相。
 * @author AaronZZH & Kiro
 */

import type { CollaborationResourceKind, RunStepStatus } from "./types"

/** 资源类型的连线颜色 */
export const RESOURCE_STROKE: Record<CollaborationResourceKind | "role", string> = {
  skill: "#a78bfa",
  tool: "#22d3ee",
  knowledge: "#34d399",
  role: "#818cf8"
}

/** 资源类型的节点图标色与描边 */
export const RESOURCE_TONE_CLASSES: Record<CollaborationResourceKind | "role", string> = {
  skill: "border-violet-400/35 text-violet-300",
  tool: "border-cyan-400/35 text-cyan-300",
  knowledge: "border-emerald-400/35 text-emerald-300",
  role: "border-indigo-400/35 text-indigo-300"
}

/** 资源类型名称 */
export const RESOURCE_KIND_LABELS: Record<CollaborationResourceKind | "role", string> = {
  skill: "技能",
  tool: "工具",
  knowledge: "知识库",
  role: "角色"
}

/** 助理到角色的主连线颜色 */
export const ASSISTANT_STROKE = "#818cf8"

/** 运行步骤状态的连线颜色 */
export const RUN_STATUS_STROKE: Record<RunStepStatus, string> = {
  completed: "#34d399",
  running: "#818cf8",
  awaiting: "#fbbf24",
  waiting: "#64748b",
  failed: "#fb7185"
}

/** 运行步骤状态名称 */
export const RUN_STATUS_LABELS: Record<RunStepStatus, string> = {
  completed: "已完成",
  running: "运行中",
  awaiting: "待审批",
  waiting: "等待中",
  failed: "失败"
}

/** 运行步骤节点的描边与底色 */
export const RUN_STATUS_NODE_CLASSES: Record<RunStepStatus, string> = {
  completed: "border-emerald-400/40",
  running: "border-indigo-400/70 shadow-[0_0_18px_-4px_rgba(129,140,248,0.7)]",
  awaiting:
    "border-amber-400 shadow-[0_0_22px_-4px_rgba(251,191,36,0.75)] ring-1 ring-amber-400/40",
  waiting: "border-foreground/10 opacity-60",
  failed: "border-rose-400/70 shadow-[0_0_18px_-6px_rgba(251,113,133,0.7)]"
}

/** 运行步骤状态标签色 */
export const RUN_STATUS_TEXT_CLASSES: Record<RunStepStatus, string> = {
  completed: "text-emerald-300",
  running: "text-indigo-300",
  awaiting: "text-amber-300",
  waiting: "text-muted-foreground",
  failed: "text-rose-300"
}
