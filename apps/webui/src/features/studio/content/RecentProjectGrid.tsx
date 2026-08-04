/**
 * Content Studio 最近 AIGC 项目网格。
 * @author AaronZZH & Kiro
 */

"use client"

import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { ChevronRight, FolderKanban, Plus } from "lucide-react"
import Link from "next/link"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import type { AigcProject } from "@/lib/api/rest/ai/aigc"
import { useAigcProjects } from "@/lib/api/rest/ai/aigc"
import { getProjectTypeConfig, PROJECT_STATUS_CONFIG } from "./project-type-config"

function RecentProjectCard({ project }: { project: AigcProject }) {
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
          <div className="absolute top-2 right-2">
            <NeonChip tone={status.tone} size="sm">
              {status.label}
            </NeonChip>
          </div>
        </div>
        <div className="flex flex-col gap-2 px-4 py-3">
          <div className="flex items-center justify-between gap-2">
            <p className="truncate font-medium text-sm">{project.name}</p>
            <NeonChip tone={type.tone} size="sm">
              {type.label}
            </NeonChip>
          </div>
          <p className="text-muted-foreground text-xs">
            {formatDistanceToNow(new Date(project.lastActiveTime ?? project.updateTime), {
              locale: zhCN,
              addSuffix: true
            })}
          </p>
        </div>
      </GlassCard>
    </Link>
  )
}

export function RecentProjectGrid() {
  const { data, isLoading } = useAigcProjects({ pageSize: 5 })
  const projects = data?.list ?? []

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-semibold text-base">最近项目</h2>
          <p className="pt-1 text-muted-foreground text-xs">继续推进最近活跃的内容项目</p>
        </div>
        <Link
          href="/studio/projects"
          className="flex items-center gap-1 text-muted-foreground text-sm hover:text-foreground"
        >
          查看全部 <ChevronRight />
        </Link>
      </div>
      {isLoading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={`recent-project-${index}`} className="aspect-[4/3] rounded-2xl" />
          ))}
        </div>
      ) : projects.length === 0 ? (
        <GlassCard glow="none">
          <Empty className="min-h-44">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <FolderKanban />
              </EmptyMedia>
              <EmptyTitle>还没有内容项目</EmptyTitle>
              <EmptyDescription>选择上方项目类型，立即建立第一个项目骨架。</EmptyDescription>
            </EmptyHeader>
            <EmptyContent>
              <GlowButton
                nativeButton={false}
                render={<Link href="/studio/projects/new" />}
                tone="ghost"
                size="sm"
              >
                <Plus />
                完整创建
              </GlowButton>
            </EmptyContent>
          </Empty>
        </GlassCard>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          {projects.map((project) => (
            <RecentProjectCard key={project.id} project={project} />
          ))}
        </div>
      )}
    </section>
  )
}
