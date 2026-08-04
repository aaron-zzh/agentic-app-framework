/**
 * Content Studio AIGC 项目列表。
 * @author AaronZZH & Kiro
 */

"use client"

import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { FolderKanban, Layers, Plus } from "lucide-react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { GlassCard, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { getProjectTypeConfig, PROJECT_STATUS_CONFIG } from "@/features/studio/content"
import type { AigcProject, AigcProjectStatus } from "@/lib/api/rest/ai/aigc"
import { useAigcProjects, useAigcProjectTypes } from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils/index"

const STATUS_TABS: { value: "all" | AigcProjectStatus; label: string }[] = [
  { value: "all", label: "全部" },
  { value: "in_progress", label: "进行中" },
  { value: "reviewing", label: "审核中" },
  { value: "delivering", label: "交付中" },
  { value: "completed", label: "已完成" },
  { value: "archived", label: "已归档" }
]

function buildFilterHref(status: string, type?: string): string {
  const params = new URLSearchParams()
  if (status !== "all") params.set("status", status)
  if (type) params.set("type", type)
  const query = params.toString()
  return query ? `/studio/projects?${query}` : "/studio/projects"
}

function ProjectCard({ project }: { project: AigcProject }) {
  const type = getProjectTypeConfig({ code: project.projectTypeCode, name: "" })
  const TypeIcon = type.icon
  const status = PROJECT_STATUS_CONFIG[project.status]

  return (
    <Link
      href={`/studio/projects/${project.id}`}
      className="group block focus-visible:outline-none"
    >
      <GlassCard interactive className="h-full">
        <div className="relative flex aspect-video items-center justify-center overflow-hidden bg-muted text-muted-foreground">
          <TypeIcon className="size-10 transition-transform duration-300 group-hover:scale-110" />
          <div className="absolute top-2 right-2 flex gap-1.5">
            <NeonChip tone={type.tone} size="sm">
              {type.label}
            </NeonChip>
            <NeonChip tone={status.tone} size="sm">
              {status.label}
            </NeonChip>
          </div>
        </div>
        <div className="flex flex-col gap-2 px-4 py-3">
          <p className="truncate font-medium text-sm">{project.name}</p>
          <div className="flex items-center justify-between gap-2 text-muted-foreground text-xs">
            <span className="truncate">
              {project.primaryBrandProfileId
                ? `品牌 #${project.primaryBrandProfileId}`
                : "未绑定品牌"}
            </span>
            <span className="shrink-0">
              {formatDistanceToNow(new Date(project.lastActiveTime ?? project.updateTime), {
                locale: zhCN,
                addSuffix: true
              })}
            </span>
          </div>
        </div>
      </GlassCard>
    </Link>
  )
}

export default function StudioProjectsPage() {
  const searchParams = useSearchParams()
  const rawStatus = searchParams.get("status") ?? "all"
  const status = STATUS_TABS.some((tab) => tab.value === rawStatus) ? rawStatus : "all"
  const type = searchParams.get("type")
  const { data: typePage } = useAigcProjectTypes()
  const { data, isLoading } = useAigcProjects({
    pageSize: 50,
    ...(status !== "all" ? { status: status as AigcProjectStatus } : {}),
    ...(type ? { projectTypeCode: type } : {})
  })
  const projects = data?.list ?? []
  const projectTypes = typePage?.list ?? []

  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="cyan" />
      <div className="relative mx-auto flex max-w-7xl flex-col gap-6 p-6">
        <header className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <FolderKanban className="size-5 text-primary" />
            <h1 className="font-semibold text-xl">我的项目</h1>
          </div>
          <GlowButton
            nativeButton={false}
            render={<Link href="/studio/projects/new" />}
            tone="violet"
          >
            <Plus /> 新建项目
          </GlowButton>
        </header>

        <Tabs value={status}>
          <TabsList className="bg-foreground/[0.04]">
            {STATUS_TABS.map((tab) => (
              <TabsTrigger
                key={tab.value}
                value={tab.value}
                nativeButton={false}
                render={<Link href={buildFilterHref(tab.value, type ?? undefined)} />}
              >
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        <div className="flex flex-wrap gap-2">
          <Link
            href={buildFilterHref(status)}
            className={cn(
              "rounded-full border px-3 py-1 text-xs transition-colors",
              type
                ? "text-muted-foreground hover:text-foreground"
                : "border-primary/40 bg-primary/10 text-primary"
            )}
          >
            全部类型
          </Link>
          {projectTypes.map((projectType) => (
            <Link
              key={projectType.code}
              href={buildFilterHref(status, projectType.code)}
              className={cn(
                "rounded-full border px-3 py-1 text-xs transition-colors",
                type === projectType.code
                  ? "border-primary/40 bg-primary/10 text-primary"
                  : "text-muted-foreground hover:text-foreground"
              )}
            >
              {projectType.name}
            </Link>
          ))}
        </div>

        {isLoading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {Array.from({ length: 10 }, (_, index) => (
              <Skeleton key={`project-${index}`} className="aspect-[4/3] rounded-2xl" />
            ))}
          </div>
        ) : projects.length === 0 ? (
          <GlassCard glow="none">
            <Empty className="min-h-72">
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <Layers />
                </EmptyMedia>
                <EmptyTitle>没有匹配的项目</EmptyTitle>
                <EmptyDescription>调整状态或类型筛选，或创建一个新的内容项目。</EmptyDescription>
              </EmptyHeader>
              <EmptyContent>
                <GlowButton
                  nativeButton={false}
                  render={<Link href="/studio/projects/new" />}
                  tone="violet"
                >
                  <Plus />
                  创建项目
                </GlowButton>
              </EmptyContent>
            </Empty>
          </GlassCard>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {projects.map((project) => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
