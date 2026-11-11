/**
 * Studio 首页左栏"我的资源"：角色、技能、知识库、项目、执行中任务、失败任务。
 *
 * 不套带标题的面板，每行是一张 30% 透明的小卡片，直接浮在画布上。
 * - 角色、技能与中央协作画布同口径：角色为默认助理已绑定的角色数，技能为这些角色用到的技能数（去重）。
 * - 项目为我的全部 AIGC 项目数，悬停提示附进行中数量。
 * 每行独立取数，接口失败时该行数字显示"—"。任务两行来自受 AIGC License 限制的 home-summary。
 * 大屏模式为竖向列表，窄屏为 2～3 列网格。
 * @author AaronZZH & Kiro
 */

"use client"

import { BookOpen, CircleAlert, CirclePlay, FolderKanban, Sparkles, UserRound } from "lucide-react"
import { useMemo } from "react"
import { useAigcStudioHomeSummary } from "@/lib/api/rest/ai/aigc/home"
import { useAigcProjects } from "@/lib/api/rest/ai/aigc/project"
import { useAiRoleLibrary } from "@/lib/api/rest/ai/role"
import { useKnowledgeBases } from "@/lib/api/rest/knowledge/knowledge"
import { cn } from "@/lib/utils/cn"
import { HomeResourceRow } from "./HomeResourceRow"

/** 只取总数时的分页参数 */
const COUNT_ONLY_PAGE = { pageSize: 1 }

interface HomeResourceListProps {
  /** list：竖向列表（大屏左栏）；grid：网格（窄屏堆叠） */
  layout: "list" | "grid"
}

export function HomeResourceList({ layout }: HomeResourceListProps) {
  const library = useAiRoleLibrary()
  const knowledgeBases = useKnowledgeBases(COUNT_ONLY_PAGE)
  const projects = useAigcProjects(COUNT_ONLY_PAGE)
  const summary = useAigcStudioHomeSummary()

  // 已绑定角色用到的技能，按技能 key 去重
  const boundSkillCount = useMemo(() => {
    if (!library.data) return undefined
    const keys = new Set<string>()
    for (const item of library.data.items) {
      if (!item.bound) continue
      for (const binding of item.role.skillBindings) keys.add(binding.skillKey)
    }
    return keys.size
  }, [library.data])

  const activeProjects = summary.data?.activeProjectCount
  const projectHint =
    activeProjects === undefined ? "我的全部项目" : `我的全部项目，其中进行中 ${activeProjects} 个`

  return (
    <section
      aria-label="我的资源"
      className={cn(
        layout === "list" ? "flex flex-col gap-2" : "grid grid-cols-2 gap-2 md:grid-cols-3"
      )}
    >
      <HomeResourceRow
        href="/settings/assistant-roles"
        label="角色"
        hint="默认助理已绑定的角色"
        value={library.data?.boundCount}
        loading={library.isLoading}
        icon={<UserRound />}
        tone="violet"
      />
      <HomeResourceRow
        href="/studio/assets/skills"
        label="技能"
        hint="已绑定角色用到的技能（去重）"
        value={boundSkillCount}
        loading={library.isLoading}
        icon={<Sparkles />}
        tone="cyan"
      />
      <HomeResourceRow
        href="/studio/knowledge"
        label="知识库"
        hint="可访问的知识库"
        value={knowledgeBases.data?.total}
        loading={knowledgeBases.isLoading}
        icon={<BookOpen />}
        tone="emerald"
      />
      <HomeResourceRow
        href="/studio/projects"
        label="项目"
        hint={projectHint}
        value={projects.data?.total}
        loading={projects.isLoading}
        icon={<FolderKanban />}
        tone="amber"
      />
      <HomeResourceRow
        href="/studio/me/generations"
        label="任务"
        hint="执行中的任务"
        value={summary.data?.activeTaskCount}
        loading={summary.isLoading}
        icon={<CirclePlay />}
        tone="violet"
      />
      <HomeResourceRow
        href="/studio/me/generations"
        label="失败"
        hint="失败的任务"
        value={summary.data?.failedTaskCount}
        loading={summary.isLoading}
        icon={<CircleAlert />}
        tone="rose"
        alert
      />
    </section>
  )
}
