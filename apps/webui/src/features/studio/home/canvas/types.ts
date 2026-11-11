/**
 * Studio 首页中央画布的类型：协作关系拓扑、运行实况任务流程与各节点数据。
 *
 * 节点数据用 type 而不是 interface 声明，满足 @xyflow/react 对 data 为 Record<string, unknown> 的约束。
 * @author AaronZZH & Kiro
 */

import type { Edge, Node } from "@xyflow/react"
import type { CSSProperties } from "react"
import type { AigcExecutionStatus, AigcExecutionTargetType } from "@/lib/api/rest/ai/aigc/execution"
import type { AiRoleSkillActivationMode, AiRoleVisibility } from "@/lib/api/rest/ai/role"
import type { CanvasFitPadding } from "../cockpit-dimensions"

/** 画布在当前布局下的可视区：适应视图内边距、浮层之外的定位区域、是否接管滚轮 */
export interface CanvasFrame {
  fitPadding: CanvasFitPadding
  /** 浮层之外可视区的绝对定位样式 */
  area: CSSProperties
  /** 是否用滚轮缩放（窄屏堆叠布局下关闭，避免抢走页面滚动） */
  wheelZoom: boolean
}

// ---------- 协作关系 ----------

/** 角色可调用的资源类型 */
export type CollaborationResourceKind = "skill" | "tool" | "knowledge"

/** 协作拓扑中的一个角色 */
export interface CollaborationRole {
  id: string
  /** 角色稳定业务码（AiRoleVO.code），用于匹配任务节点的执行角色；示例数据为空 */
  roleKey?: string
  name: string
  visibility: AiRoleVisibility
  enabled: boolean
  defaultRole: boolean
  skills: { key: string; activationMode: AiRoleSkillActivationMode }[]
  tools: string[]
  /** 知识库 stable_id */
  knowledgeBaseIds: string[]
  /** 角色分类 ID；为空或分类不可见时归入"未分类" */
  categoryId: string | null
}

/** 角色分类，已按排序值升序 */
export interface CollaborationRoleCategory {
  id: string
  name: string
}

/** 助理 → 角色 → 技能 → 工具 / 知识库的静态拓扑，真实数据与示例数据同形状 */
export interface CollaborationTopology {
  assistantName: string
  roles: CollaborationRole[]
  categories: CollaborationRoleCategory[]
  /** 技能 key → 名称 */
  skillNames: Record<string, string>
  /** 平台内置技能的 key；不在其中的技能视为用户自建 */
  builtInSkillKeys: string[]
  /** 技能 key → 当前版本声明需要的工具名 */
  skillTools: Record<string, string[]>
  /** 知识库 stable_id → 名称 */
  knowledgeNames: Record<string, string>
}

export type AssistantNodeData = {
  name: string
  roleCount: number
  /** 当前用户未终结的任务数，大于 0 时显示呼吸角标 */
  runningTasks?: number
}

export type RoleNodeData = {
  name: string
  /** 角色稳定业务码；示例数据为空 */
  roleKey?: string
  visibility: AiRoleVisibility
  enabled: boolean
  defaultRole: boolean
  skillCount: number
  toolCount: number
  knowledgeCount: number
  href: string
  /** 活跃任务中由该角色执行且未终结的节点数，大于 0 时显示角标 */
  runningNodes?: number
}

/** 资源节点；overflow 为真时是"+N"汇总节点 */
export type ResourceNodeData = {
  kind: CollaborationResourceKind | "role"
  label: string
  overflow: boolean
  href: string | null
  /** 右侧小标签，缺省显示资源类型名称 */
  badge?: string
  /** 可展开节点（系统技能、系统工具、"+N"）的展开状态；普通节点为空 */
  expanded?: boolean
}

/**
 * 角色分类节点：展开时是包住该分类角色的虚线框（标题可点击折叠），
 * 折叠时是一张虚线小卡片，该分类角色的连线都汇到它上面。
 */
export type RoleGroupNodeData = {
  name: string
  roleCount: number
  collapsed: boolean
}

export type AssistantFlowNode = Node<AssistantNodeData, "assistant">
export type RoleFlowNode = Node<RoleNodeData, "role">
export type ResourceFlowNode = Node<ResourceNodeData, "resource">
export type RoleGroupFlowNode = Node<RoleGroupNodeData, "roleGroup">
export type CollaborationFlowNode =
  | AssistantFlowNode
  | RoleFlowNode
  | ResourceFlowNode
  | RoleGroupFlowNode

export interface CollaborationGraph {
  nodes: CollaborationFlowNode[]
  edges: Edge[]
}

// ---------- 运行实况 ----------

/** 步骤状态：已完成 / 运行中 / 待审批 / 等待中 / 失败 */
export type RunStepStatus = "completed" | "running" | "awaiting" | "waiting" | "failed"

/** 任务整体状态 */
export type RunTaskStatus = "running" | "awaiting" | "failed" | "completed"

export type RunStepIcon =
  | "trigger"
  | "coordinator"
  | "plan"
  | "memory"
  | "retrieve"
  | "search"
  | "tool"
  | "write"
  | "image"
  | "video"
  | "review"
  | "approval"
  | "deliver"

/** 示例任务中的一个步骤；同一 stage 的步骤并行执行 */
export interface RunStepDef {
  id: string
  title: string
  /** 所用角色或工具 */
  actor: string
  icon: RunStepIcon
  /** 执行阶段，从 0 开始 */
  stage: number
  /** 示例耗时（秒） */
  durationSec: number
  /** 是否为审批步骤；审批步骤单独占一个 stage */
  approval?: boolean
}

/** 步骤之间的连线；items 为完成后显示的处理条数 */
export interface RunEdgeDef {
  from: string
  to: string
  items?: number
}

export interface RunTaskDef {
  id: string
  name: string
  steps: RunStepDef[]
  edges: RunEdgeDef[]
  /** 打开首页时所处的阶段，让三个任务一开始处于不同状态 */
  initialStage: number
}

/** 单个任务的推进进度 */
export interface RunTaskProgress {
  /** 当前阶段；大于最大阶段表示全部完成 */
  stage: number
  /** 当前阶段已持续的节拍数 */
  stageTicks: number
  /** 审批被驳回 */
  rejected: boolean
  /** 完成或驳回后停留的节拍数，到时重新开始演示 */
  idleTicks: number
}

export type RunStepNodeData = {
  title: string
  actor: string
  icon: RunStepIcon
  status: RunStepStatus
  /** 耗时或进度文字 */
  timeLabel: string
  /** 待审批时可用的批准、驳回动作 */
  onApprove: (() => void) | null
  onReject: (() => void) | null
}

export type RunStepFlowNode = Node<RunStepNodeData, "runStep">

export interface RunTaskSummary {
  id: string
  name: string
  status: RunTaskStatus
}

// ---------- 项目任务（AIGC） ----------

/** 画布上的一条 AIGC 执行记录，真实 ExecutionRun 与示例数据都归一成这个形状 */
export interface ProjectRunItem {
  id: number
  projectId: number
  /** 父执行记录；根记录为空 */
  parentId: number | null
  /** 动作名称（actionKey） */
  title: string
  targetType: AigcExecutionTargetType
  /** 执行所用角色 */
  roleProfileCode: string | null
  status: AigcExecutionStatus
  startTime: string | null
  endTime: string | null
}

export interface ProjectRunProject {
  id: number
  name: string
}

export type ProjectNodeData = {
  name: string
  runCount: number
  runningCount: number
  href: string | null
}

export type ProjectFlowNode = Node<ProjectNodeData, "project">

/** 项目任务总览图上的节点：项目节点 + 执行记录节点（复用运行步骤节点） */
export type ProjectRunsFlowNode = ProjectFlowNode | RunStepFlowNode
