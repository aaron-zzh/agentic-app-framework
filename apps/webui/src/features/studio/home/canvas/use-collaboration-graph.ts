/**
 * 协作关系画布数据：由角色库构建"助理 → 已绑定角色 → 技能 → 工具 / 知识库"拓扑。
 *
 * - 助理：角色库的 assistantId，名称取 useAssistants() 中对应项。
 * - 角色：角色库中 bound 为真的角色；工具取 toolWhitelist（JSON 字符串数组，解析失败按空处理）。
 * - 技能、知识库名称尽量取 useAiSkills() / useKnowledgeBases()，取不到时显示 key 或 ID 前 8 位。
 * - 技能按 builtIn 标为"内置"或"我的"；技能列表里查不到的技能按"我的"处理。
 * - 技能所需工具取 useAiSkills() 中当前版本的 toolRequirements（toolId 即工具名）。
 * - 角色分类取 useAiRoleCategories()（按排序值升序）；角色的 categoryId 不在其中时归入"未分类"。
 * - 没有默认助理、没有绑定角色或角色库加载失败时，使用示例拓扑并标记 mock。
 *
 * @example
 * const { graph, mock, loading } = useCollaborationGraph({
 *   expanded: new Set(),
 *   collapsedCategories: new Set()
 * })
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import { useAssistants } from "@/lib/api/rest/ai/assistant"
import { useAiRoleCategories, useAiRoleLibrary } from "@/lib/api/rest/ai/role"
import { useAiSkills } from "@/lib/api/rest/ai/skill"
import { useKnowledgeBases } from "@/lib/api/rest/knowledge/knowledge"
import { MOCK_COLLABORATION_TOPOLOGY } from "./canvas-mock-data"
import { buildCollaborationGraph, type CollaborationLayoutOptions } from "./collaboration-layout"
import type { CollaborationGraph, CollaborationRole, CollaborationTopology } from "./types"

/** 取知识库名称时一次拉取的数量 */
const KNOWLEDGE_NAME_PAGE = { pageNo: 1, pageSize: 100 }

const FALLBACK_ASSISTANT_NAME = "我的助理"

interface CollaborationGraphResult {
  graph: CollaborationGraph
  /** 是否为示例数据 */
  mock: boolean
  /** 角色库仍在加载 */
  loading: boolean
}

/** 解析工具授权池；不是字符串数组时按空处理 */
function parseToolWhitelist(raw: string | null): string[] {
  if (!raw) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed)
      ? parsed.filter((item): item is string => typeof item === "string" && item.length > 0)
      : []
  } catch {
    return []
  }
}

/** @param options 可展开节点的展开状态与已折叠的角色分类 */
export function useCollaborationGraph(
  options: CollaborationLayoutOptions
): CollaborationGraphResult {
  const library = useAiRoleLibrary()
  const assistants = useAssistants()
  const skills = useAiSkills()
  const knowledgeBases = useKnowledgeBases(KNOWLEDGE_NAME_PAGE)
  const categories = useAiRoleCategories()

  const topology = useMemo<CollaborationTopology | null>(() => {
    const data = library.data
    if (!data || data.assistantId === null) return null
    const boundItems = data.items.filter((item) => item.bound)
    if (boundItems.length === 0) return null

    const assistantName =
      assistants.data?.find((item) => item.assistantId === String(data.assistantId))?.name ??
      FALLBACK_ASSISTANT_NAME
    const roles: CollaborationRole[] = boundItems.map((item) => ({
      id: String(item.role.id),
      roleKey: item.role.code,
      name: item.role.name,
      visibility: item.role.visibility,
      enabled: item.enabled,
      defaultRole: item.defaultRole,
      skills: item.role.skillBindings.map((binding) => ({
        key: binding.skillKey,
        activationMode: binding.activationMode
      })),
      tools: parseToolWhitelist(item.role.toolWhitelist),
      knowledgeBaseIds: item.role.knowledgeBaseIds,
      categoryId: item.role.categoryId === null ? null : String(item.role.categoryId)
    }))
    const skillNames: Record<string, string> = {}
    const builtInSkillKeys: string[] = []
    const skillTools: Record<string, string[]> = {}
    for (const skill of skills.data ?? []) {
      skillNames[skill.code] = skill.name
      if (skill.builtIn) builtInSkillKeys.push(skill.code)
      const requirements = skill.currentVersion?.toolRequirements ?? []
      if (requirements.length > 0) skillTools[skill.code] = requirements.map((item) => item.toolId)
    }
    const knowledgeNames: Record<string, string> = {}
    for (const kb of knowledgeBases.data?.list ?? []) knowledgeNames[kb.stableId] = kb.name
    const roleCategories = (categories.data ?? []).map((category) => ({
      id: String(category.id),
      name: category.name
    }))

    return {
      assistantName,
      roles,
      categories: roleCategories,
      skillNames,
      builtInSkillKeys,
      skillTools,
      knowledgeNames
    }
  }, [library.data, assistants.data, skills.data, knowledgeBases.data, categories.data])

  const graph = useMemo(
    () => buildCollaborationGraph(topology ?? MOCK_COLLABORATION_TOPOLOGY, options),
    [topology, options]
  )

  return { graph, mock: topology === null, loading: library.isLoading }
}
