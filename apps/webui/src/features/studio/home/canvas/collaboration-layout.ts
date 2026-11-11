/**
 * 协作关系拓扑 → React Flow 节点与连线（纯函数）。
 *
 * 从左到右：助理 → 角色 → 技能 → 工具 / 知识库。
 * - 角色列：按角色分类分组，每个分类用虚线框包住，点框标题折叠为一张虚线卡片（连线汇到卡片上），再点展开。
 * - 技能列：系统级技能（isSystemSkill）收拢为"系统技能"节点；其余内置技能在上、自建技能在下，右侧小标签区分"内置"与"我的"。
 * - 工具列：平台控制类工具（SYSTEM_TOOL_NAMES）收拢为"系统工具"节点；其余业务工具逐个显示；知识库排在最下方。
 * - 技能声明了所需工具（toolRequirements）时画"技能 → 工具"连线；角色工具清单里没有被其技能覆盖的工具，才从角色直连。
 * - "系统技能""系统工具"与"+N"节点都可点击展开：展开后在原位置下方列出全部条目，再点收起。
 *   收起时被收拢的条目连线统一汇到该节点上。
 * - 同一个资源被多个角色或技能使用时只画一个节点，多条连线汇入；单列超过 12 个时按比例折叠为"+N"。
 * - 助理到角色用粗线，其余连线按资源类型着色；按需激活（ON_DEMAND）的技能用虚线。
 *
 * @example
 * const { nodes, edges } = buildCollaborationGraph(topology, {
 *   expanded: new Set(),
 *   collapsedCategories: new Set()
 * })
 * @author AaronZZH & Kiro
 */

import type { Edge } from "@xyflow/react"
import { ASSISTANT_STROKE, RESOURCE_KIND_LABELS, RESOURCE_STROKE } from "./canvas-palette"
import type {
  CollaborationFlowNode,
  CollaborationGraph,
  CollaborationResourceKind,
  CollaborationRole,
  CollaborationTopology,
  ResourceNodeData
} from "./types"

/** 单列最多显示的节点数，超出折叠为"+N" */
const COLUMN_LIMIT = 12

/** 各列的横坐标 */
const ASSISTANT_X = 0
const ROLE_X = 320
const SKILL_X = 700
const ASSET_X = 1010

/** 节点尺寸与间距（与节点组件的实际尺寸保持接近） */
const ASSISTANT_SIZE = 128
const ROLE_HEIGHT = 92
const ROLE_GAP = 20
const RESOURCE_HEIGHT = 40
const RESOURCE_GAP = 12
const GROUP_GAP = 32

const ROLE_HREF = "/settings/assistant-roles"
const SKILL_HREF = "/studio/assets/skills"
const KNOWLEDGE_HREF = "/studio/knowledge"

const SYSTEM_SKILLS_NODE_ID = "system-skills"
const SYSTEM_TOOLS_NODE_ID = "system-tools"

/**
 * 系统级技能：平台运行机制类（builtin-* 前缀，如任务分诊、步骤规划、委派执行）
 * 以及服务端受控流程专用技能。与后端 aaf/skills 下的技能文件保持一致。
 */
const SYSTEM_SKILL_KEYS: ReadonlySet<string> = new Set([
  "system.skill.content-project-collaboration",
  "system.skill.assistant-blueprint-planning"
])

function isSystemSkill(key: string): boolean {
  return key.startsWith("builtin-") || SYSTEM_SKILL_KEYS.has(key)
}

/**
 * 平台控制类工具名：任务控制、执行上报、澄清、上下文加载、知识检索、UI 渲染与脚本执行。
 * 与后端 BuiltinControlToolProfile 的控制工具及框架内置通用工具保持一致，后端增删时同步修改。
 */
const SYSTEM_TOOL_NAMES: ReadonlySet<string> = new Set([
  "inspect_tasks",
  "promote_to_task",
  "amend_task",
  "pause_task",
  "resume_task",
  "take_over_task",
  "hand_back_task",
  "cancel_task",
  "context.load",
  "plan_steps",
  "report_step",
  "request_clarification",
  "delegate_agents",
  "knowledge_search",
  "render_ui_block",
  "script.execute.javascript"
])

export interface CollaborationLayoutOptions {
  /** 已展开的可展开节点 id（系统技能、系统工具、各"+N"节点） */
  expanded: ReadonlySet<string>
  /** 已折叠的角色分类节点 id（role-group:{分类 ID}），默认全部展开 */
  collapsedCategories: ReadonlySet<string>
}

/** 角色到某个资源的一条关联 */
interface RoleLink {
  roleIndex: number
  dashed: boolean
}

/** 去重后的资源条目 */
interface ResourceEntry {
  key: string
  label: string
  /** 直接引用它的角色 */
  links: RoleLink[]
}

/** 列内的一段：可收拢为单个节点（aggregate），或普通列表（超出配额折叠为"+N"） */
interface Section {
  kind: CollaborationResourceKind
  entries: ResourceEntry[]
  aggregate?: { id: string; label: string }
  overflowId: string
  badge?: string
  href: string | null
}

/** 列中实际绘制的一个节点；keys 为它代表的条目 */
interface ColumnItem {
  id: string
  data: ResourceNodeData
  keys: string[]
}

/** 待合并的资源连线：同一对节点只画一条 */
interface PendingEdge {
  source: string
  target: string
  kind: CollaborationResourceKind
  dashed: boolean
  enabled: boolean
}

function roleNodeId(index: number): string {
  return `role:${index}`
}

/** 默认角色排在最前，其余保持原顺序 */
function orderRoles(roles: CollaborationRole[]): CollaborationRole[] {
  return [...roles.filter((role) => role.defaultRole), ...roles.filter((role) => !role.defaultRole)]
}

/** 按 key 去重收集资源，记录每个资源被哪些角色直接引用 */
function collect<T>(
  roles: CollaborationRole[],
  itemsOf: (role: CollaborationRole) => T[],
  describe: (
    item: T,
    role: CollaborationRole
  ) => { key: string; label: string; dashed: boolean } | null
): ResourceEntry[] {
  const entries = new Map<string, ResourceEntry>()
  roles.forEach((role, roleIndex) => {
    for (const item of itemsOf(role)) {
      const described = describe(item, role)
      if (!described) continue
      const { key, label, dashed } = described
      const existing = entries.get(key)
      if (!existing) {
        entries.set(key, { key, label, links: [{ roleIndex, dashed }] })
      } else if (!existing.links.some((link) => link.roleIndex === roleIndex)) {
        existing.links.push({ roleIndex, dashed })
      }
    }
  })
  return [...entries.values()]
}

/** 一组在列中可直接显示的条数：总数不超过配额时全部显示，否则按比例分配，超出部分折叠 */
function visibleCountOf(count: number, total: number, limit: number): number {
  if (total <= limit) return count
  const budget = Math.max(2, Math.floor((limit * count) / total))
  return count > budget ? budget - 1 : count
}

/** 把一列的各段展开成要绘制的节点，每段一组 */
function columnItems(sections: Section[], expanded: ReadonlySet<string>): ColumnItem[][] {
  const active = sections.filter((section) => section.entries.length > 0)
  const aggregateCount = active.filter((section) => section.aggregate).length
  const listTotal = active
    .filter((section) => !section.aggregate)
    .reduce((sum, section) => sum + section.entries.length, 0)
  const budget = Math.max(2, COLUMN_LIMIT - aggregateCount)

  return active.map((section) => {
    const entryItem = (entry: ResourceEntry): ColumnItem => ({
      id: `${section.kind}:${entry.key}`,
      data: {
        kind: section.kind,
        label: entry.label,
        overflow: false,
        href: section.href,
        badge: section.badge
      },
      keys: [entry.key]
    })
    const { aggregate } = section
    if (aggregate) {
      const open = expanded.has(aggregate.id)
      const header: ColumnItem = {
        id: aggregate.id,
        data: {
          kind: section.kind,
          label: aggregate.label,
          overflow: false,
          href: null,
          badge: `${section.entries.length} 个`,
          expanded: open
        },
        keys: open ? [] : section.entries.map((entry) => entry.key)
      }
      return open ? [header, ...section.entries.map(entryItem)] : [header]
    }

    const shown = visibleCountOf(section.entries.length, listTotal, budget)
    const hidden = section.entries.slice(shown)
    if (hidden.length === 0) return section.entries.map(entryItem)
    const open = expanded.has(section.overflowId)
    const overflow: ColumnItem = {
      id: section.overflowId,
      data: {
        kind: section.kind,
        label: open ? "收起" : `+${hidden.length} 个${RESOURCE_KIND_LABELS[section.kind]}`,
        overflow: true,
        href: null,
        badge: section.badge,
        expanded: open
      },
      keys: open ? [] : hidden.map((entry) => entry.key)
    }
    const visible = open ? section.entries : section.entries.slice(0, shown)
    return [...visible.map(entryItem), overflow]
  })
}

/** 按组从上到下放置一列节点，整列垂直居中于 y=0；返回条目 key → 节点 id */
function placeColumn(
  x: number,
  groups: ColumnItem[][],
  nodes: CollaborationFlowNode[]
): Map<string, string> {
  const slotCount = groups.reduce((sum, group) => sum + group.length, 0)
  const height =
    slotCount * RESOURCE_HEIGHT +
    Math.max(0, slotCount - groups.length) * RESOURCE_GAP +
    Math.max(0, groups.length - 1) * GROUP_GAP
  const nodeOf = new Map<string, string>()
  let y = -height / 2
  groups.forEach((group, groupIndex) => {
    if (groupIndex > 0) y += GROUP_GAP - RESOURCE_GAP
    for (const item of group) {
      nodes.push({ id: item.id, type: "resource", position: { x, y }, data: item.data })
      for (const key of item.keys) nodeOf.set(key, item.id)
      y += RESOURCE_HEIGHT + RESOURCE_GAP
    }
  })
  return nodeOf
}

/** 角色分类框与折叠卡片的尺寸（与 RoleGroupNode 保持一致） */
const ROLE_WIDTH = 240
const FRAME_HEADER = 32
const FRAME_PAD = 12
const COLLAPSED_GROUP_HEIGHT = 56
const UNCATEGORIZED = "uncategorized"

function roleGroupId(categoryKey: string): string {
  return `role-group:${categoryKey}`
}

/**
 * 放置角色列：按分类分组（分类按排序值，未分类排最后），组间留空。
 * - 存在分类时，每个展开的分类画一个虚线框包住其角色；折叠的分类显示为一张虚线卡片。
 * - 只有未分类角色时不画框。
 * - 单个分类超过 12 个角色时折叠为"+N 个角色"，可展开。
 * 返回角色序号 → 画布上承接其连线的节点 id。
 */
function placeRoles(
  roles: CollaborationRole[],
  topology: CollaborationTopology,
  expanded: ReadonlySet<string>,
  collapsedCategories: ReadonlySet<string>,
  nodes: CollaborationFlowNode[],
  edges: Edge[]
): Map<number, string> {
  const known = new Set(topology.categories.map((category) => category.id))
  const members = new Map<string, number[]>()
  roles.forEach((role, index) => {
    const key = role.categoryId && known.has(role.categoryId) ? role.categoryId : UNCATEGORIZED
    members.set(key, [...(members.get(key) ?? []), index])
  })
  const groups = [
    ...topology.categories.map((category) => ({ key: category.id, name: category.name })),
    { key: UNCATEGORIZED, name: "未分类" }
  ].flatMap((group) => {
    const indices = members.get(group.key)
    return indices ? [{ ...group, indices }] : []
  })
  const framed = groups.some((group) => group.key !== UNCATEGORIZED)

  /** 每组的布局：折叠卡片，或若干行（角色 + 可选的"+N"） */
  const blocks = groups.map((group) => {
    const id = roleGroupId(group.key)
    const collapsed = framed && collapsedCategories.has(id)
    const overflowId = `overflow:role:${group.key}`
    const overflow = !collapsed && group.indices.length > COLUMN_LIMIT
    const open = overflow && expanded.has(overflowId)
    const shown = overflow && !open ? group.indices.slice(0, COLUMN_LIMIT - 1) : group.indices
    const rows = shown.length + (overflow ? 1 : 0)
    const rowsHeight = rows * ROLE_HEIGHT + Math.max(0, rows - 1) * ROLE_GAP
    const height = collapsed
      ? COLLAPSED_GROUP_HEIGHT
      : rowsHeight + (framed ? FRAME_HEADER + FRAME_PAD : 0)
    return { ...group, id, collapsed, overflowId, overflow, open, shown, height }
  })
  const total =
    blocks.reduce((sum, block) => sum + block.height, 0) +
    Math.max(0, blocks.length - 1) * GROUP_GAP

  const roleTarget = new Map<number, string>()
  function linkAssistant(target: string, opacity: number) {
    edges.push({
      id: `assistant->${target}`,
      source: "assistant",
      target,
      style: { stroke: ASSISTANT_STROKE, strokeWidth: 2.5, opacity }
    })
  }

  let y = -total / 2
  for (const block of blocks) {
    if (block.collapsed) {
      nodes.push({
        id: block.id,
        type: "roleGroup",
        position: { x: ROLE_X, y },
        data: { name: block.name, roleCount: block.indices.length, collapsed: true }
      })
      for (const index of block.indices) roleTarget.set(index, block.id)
      const anyEnabled = block.indices.some((index) => roles[index]?.enabled)
      linkAssistant(block.id, anyEnabled ? 0.9 : 0.35)
      y += block.height + GROUP_GAP
      continue
    }

    let rowY = y
    if (framed) {
      // 虚线框先入列，渲染在角色节点下方；框体不拦截鼠标，只有标题可点
      nodes.push({
        id: block.id,
        type: "roleGroup",
        position: { x: ROLE_X - FRAME_PAD, y },
        selectable: false,
        style: {
          width: ROLE_WIDTH + FRAME_PAD * 2,
          height: block.height,
          pointerEvents: "none"
        },
        data: { name: block.name, roleCount: block.indices.length, collapsed: false }
      })
      rowY += FRAME_HEADER
    }

    for (const index of block.shown) {
      const role = roles[index]
      if (!role) continue
      const id = roleNodeId(index)
      roleTarget.set(index, id)
      nodes.push({
        id,
        type: "role",
        position: { x: ROLE_X, y: rowY },
        data: {
          name: role.name,
          roleKey: role.roleKey,
          visibility: role.visibility,
          enabled: role.enabled,
          defaultRole: role.defaultRole,
          skillCount: role.skills.length,
          toolCount: role.tools.length,
          knowledgeCount: role.knowledgeBaseIds.length,
          href: ROLE_HREF
        }
      })
      linkAssistant(id, role.enabled ? 0.9 : 0.35)
      rowY += ROLE_HEIGHT + ROLE_GAP
    }

    if (block.overflow) {
      const hidden = block.indices.length - block.shown.length
      if (!block.open) {
        for (const index of block.indices.slice(block.shown.length)) {
          roleTarget.set(index, block.overflowId)
        }
        linkAssistant(block.overflowId, 0.6)
      }
      nodes.push({
        id: block.overflowId,
        type: "resource",
        position: { x: ROLE_X, y: rowY + (ROLE_HEIGHT - RESOURCE_HEIGHT) / 2 },
        data: {
          kind: "role",
          label: block.open ? "收起" : `+${hidden} 个角色`,
          overflow: true,
          href: null,
          expanded: block.open
        }
      })
    }
    y += block.height + GROUP_GAP
  }
  return roleTarget
}

export function buildCollaborationGraph(
  topology: CollaborationTopology,
  { expanded, collapsedCategories }: CollaborationLayoutOptions
): CollaborationGraph {
  const roles = orderRoles(topology.roles)
  const nodes: CollaborationFlowNode[] = []
  const edges: Edge[] = []

  // 助理
  nodes.push({
    id: "assistant",
    type: "assistant",
    position: { x: ASSISTANT_X, y: -ASSISTANT_SIZE / 2 },
    data: { name: topology.assistantName, roleCount: roles.length }
  })

  /** 角色序号 → 画布上的节点 id（被折叠的角色指向分类卡片或"+N"节点） */
  const roleTarget = placeRoles(roles, topology, expanded, collapsedCategories, nodes, edges)

  // 技能
  const builtInSkills = new Set(topology.builtInSkillKeys)
  const skills = collect(
    roles,
    (role) => role.skills,
    (skill) => ({
      key: skill.key,
      label: topology.skillNames[skill.key] ?? skill.key,
      dashed: skill.activationMode === "ON_DEMAND"
    })
  )
  const skillGroups = columnItems(
    [
      {
        kind: "skill",
        entries: skills.filter((entry) => isSystemSkill(entry.key)),
        aggregate: { id: SYSTEM_SKILLS_NODE_ID, label: "系统技能" },
        overflowId: "overflow:skill:system",
        badge: "系统",
        href: SKILL_HREF
      },
      {
        kind: "skill",
        entries: skills.filter(
          (entry) => !isSystemSkill(entry.key) && builtInSkills.has(entry.key)
        ),
        overflowId: "overflow:skill:builtIn",
        badge: "内置",
        href: SKILL_HREF
      },
      {
        kind: "skill",
        entries: skills.filter(
          (entry) => !isSystemSkill(entry.key) && !builtInSkills.has(entry.key)
        ),
        overflowId: "overflow:skill:custom",
        badge: "我的",
        href: SKILL_HREF
      }
    ],
    expanded
  )
  const skillNodeOf = placeColumn(SKILL_X, skillGroups, nodes)

  // 工具：角色工具清单里已由其技能声明的工具，改由技能连出，其余从角色直连
  const toolSkills = new Map<string, Set<string>>()
  for (const skill of skills) {
    for (const tool of topology.skillTools[skill.key] ?? []) {
      const set = toolSkills.get(tool) ?? new Set<string>()
      set.add(skill.key)
      toolSkills.set(tool, set)
    }
  }
  const directTools = collect(
    roles,
    (role) => role.tools,
    (tool, role) => {
      const coveredBySkill = role.skills.some((skill) =>
        (topology.skillTools[skill.key] ?? []).includes(tool)
      )
      return coveredBySkill ? null : { key: tool, label: tool, dashed: false }
    }
  )
  const tools: ResourceEntry[] = [...directTools]
  for (const tool of toolSkills.keys()) {
    if (!tools.some((entry) => entry.key === tool))
      tools.push({ key: tool, label: tool, links: [] })
  }
  const knowledge = collect(
    roles,
    (role) => role.knowledgeBaseIds,
    (kbId) => ({
      key: kbId,
      label: topology.knowledgeNames[kbId] ?? kbId.slice(0, 8),
      dashed: false
    })
  )
  const assetGroups = columnItems(
    [
      {
        kind: "tool",
        entries: tools.filter((entry) => SYSTEM_TOOL_NAMES.has(entry.key)),
        aggregate: { id: SYSTEM_TOOLS_NODE_ID, label: "系统工具" },
        overflowId: "overflow:tool:system",
        badge: "系统",
        href: null
      },
      {
        kind: "tool",
        entries: tools.filter((entry) => !SYSTEM_TOOL_NAMES.has(entry.key)),
        overflowId: "overflow:tool",
        href: null
      },
      {
        kind: "knowledge",
        entries: knowledge,
        overflowId: "overflow:knowledge",
        href: KNOWLEDGE_HREF
      }
    ],
    expanded
  )
  const assetNodeOf = placeColumn(ASSET_X, assetGroups, nodes)

  // 资源连线：同一对节点合并为一条；全部按需激活才画虚线，任一来源启用即按启用显示
  const pending = new Map<string, PendingEdge>()
  function link(edge: PendingEdge) {
    const id = `${edge.source}->${edge.target}`
    const existing = pending.get(id)
    if (!existing) {
      pending.set(id, edge)
      return
    }
    existing.dashed = existing.dashed && edge.dashed
    existing.enabled = existing.enabled || edge.enabled
  }
  function linkRoles(
    entries: ResourceEntry[],
    kind: CollaborationResourceKind,
    nodeOf: Map<string, string>
  ) {
    for (const entry of entries) {
      const target = nodeOf.get(entry.key)
      if (!target) continue
      for (const roleLink of entry.links) {
        const source = roleTarget.get(roleLink.roleIndex)
        if (!source) continue
        link({
          source,
          target,
          kind,
          dashed: roleLink.dashed,
          enabled: roles[roleLink.roleIndex]?.enabled ?? false
        })
      }
    }
  }
  linkRoles(skills, "skill", skillNodeOf)
  linkRoles(tools, "tool", assetNodeOf)
  linkRoles(knowledge, "knowledge", assetNodeOf)

  const skillEnabled = new Map(
    skills.map((entry) => [
      entry.key,
      entry.links.some((roleLink) => roles[roleLink.roleIndex]?.enabled)
    ])
  )
  for (const [tool, skillKeys] of toolSkills) {
    const target = assetNodeOf.get(tool)
    if (!target) continue
    for (const skillKey of skillKeys) {
      const source = skillNodeOf.get(skillKey)
      if (!source) continue
      link({
        source,
        target,
        kind: "tool",
        dashed: false,
        enabled: skillEnabled.get(skillKey) ?? false
      })
    }
  }

  for (const [id, edge] of pending) {
    edges.push({
      id,
      source: edge.source,
      target: edge.target,
      style: {
        stroke: RESOURCE_STROKE[edge.kind],
        strokeWidth: 1.25,
        strokeDasharray: edge.dashed ? "5 4" : undefined,
        opacity: edge.enabled ? 0.75 : 0.3
      }
    })
  }

  return { nodes, edges }
}

/** 悬停时淡化的不透明度 */
const DIMMED_NODE_OPACITY = 0.25
const DIMMED_EDGE_OPACITY = 0.06

/**
 * 悬停某个节点时突出它的关联链路：沿连线向上游（直到助理）和向下游（直到工具、知识库）各走一遍，
 * 经过的节点与连线保持高亮并流动，其余淡化。
 * - 悬停角色：高亮助理、它的技能、技能用到的工具、直连工具与知识库。
 * - 悬停技能：高亮使用它的角色、助理与它需要的工具。
 * - 悬停工具：高亮声明它的技能、直接或经技能使用它的角色与助理。
 * - 悬停助理、没有连线的节点（如已展开的汇总标题、"收起"）或没有悬停时原样返回。
 */
export function highlightCollaborationGraph(
  graph: CollaborationGraph,
  hoveredId: string | null
): CollaborationGraph {
  if (!hoveredId || hoveredId === "assistant") return graph
  if (!graph.edges.some((edge) => edge.source === hoveredId || edge.target === hoveredId)) {
    return graph
  }

  const related = new Set<string>([hoveredId])
  const relatedEdges = new Set<string>()

  function walk(direction: "up" | "down") {
    const queue = [hoveredId as string]
    const seen = new Set<string>(queue)
    while (queue.length > 0) {
      const current = queue.shift() as string
      for (const edge of graph.edges) {
        const from = direction === "down" ? edge.source : edge.target
        const to = direction === "down" ? edge.target : edge.source
        if (from !== current) continue
        relatedEdges.add(edge.id)
        related.add(to)
        if (!seen.has(to)) {
          seen.add(to)
          queue.push(to)
        }
      }
    }
  }
  walk("up")
  walk("down")

  return {
    nodes: graph.nodes.map((node) =>
      related.has(node.id) || (node.type === "roleGroup" && !node.data.collapsed)
        ? node
        : {
            ...node,
            style: { ...node.style, opacity: DIMMED_NODE_OPACITY, transition: "opacity 150ms" }
          }
    ),
    edges: graph.edges.map((edge) =>
      relatedEdges.has(edge.id)
        ? { ...edge, animated: true, style: { ...edge.style, opacity: 1 } }
        : { ...edge, style: { ...edge.style, opacity: DIMMED_EDGE_OPACITY } }
    )
  }
}
