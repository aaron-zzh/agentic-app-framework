/**
 * Content Studio 最近 AIGC 项目网格。
 * @author AaronZZH & Kiro
 */

"use client"

import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { ChevronRight, Plus } from "lucide-react"
import Link from "next/link"
import { LottieIcon } from "@/components/animate"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Empty, EmptyContent, EmptyHeader } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import type { AigcProject } from "@/lib/api/rest/ai/aigc"
import { useAigcProjects } from "@/lib/api/rest/ai/aigc"
import { getProjectTypeConfig, PROJECT_STATUS_CONFIG } from "./project-type-config"

function NewProjectCard() {
  return (
    <Link href="/studio/projects/new" className="group block focus-visible:outline-none">
      <GlassCard interactive className="h-full">
        <div className="flex aspect-video items-center justify-center bg-muted/40 text-muted-foreground">
          <Plus className="size-10 transition-transform duration-300 group-hover:scale-110" />
        </div>
        <div className="flex flex-col gap-2 px-4 py-3">
          <p className="font-medium text-sm">新建项目</p>
          <p className="text-muted-foreground text-xs">从项目类型开始创作</p>
        </div>
      </GlassCard>
    </Link>
  )
}

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
  const { data, isLoading } = useAigcProjects({ pageSize: 4 })
  const projects = data?.list ?? []

  return (
    <section className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-base">最近项目</h2>
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
              <LottieIcon name="cat" width={100} height={100} loop />
            </EmptyHeader>
            <EmptyContent>
              <GlowButton
                nativeButton={false}
                render={<Link href="/studio/projects/new" />}
                tone="ghost"
                size="sm"
              >
                <Plus />
                创建第一个项目
              </GlowButton>
            </EmptyContent>
          </Empty>
        </GlassCard>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
          <NewProjectCard />
          {projects.map((project) => (
            <RecentProjectCard key={project.id} project={project} />
          ))}
        </div>
      )}
    </section>
  )
}
