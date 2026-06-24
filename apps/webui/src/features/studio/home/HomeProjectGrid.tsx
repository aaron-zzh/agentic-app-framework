/**
 * Studio 首屏-项目网格
 *
 * 响应式网格 + 新建按钮 + 项目卡片（封面 + 名称 + 类型 + 更新时间）
 * 复用现有 useAigcProjects hook，零新增 endpoint。
 */

"use client"

import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Layers, Plus } from "lucide-react"
import NextImage from "next/image"
import Link from "next/link"
import { LottieIcon } from "@/components/animate/LottieIcon"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import { type AigcProjectVO, useAigcProjects } from "@/lib/queries/use-aigc-projects"
import { cn } from "@/lib/utils/index"

const TYPE_LABELS: Record<string, string> = {
  IMAGE_POST: "图像",
  SHORT_VIDEO: "短视频",
  VIDEO_DRAMA: "视频",
  MIXED: "综合",
  MUSIC: "音乐",
  VOICE: "配音",
  MODEL_3D: "3D",
  LIFE: "生活",
  STUDY: "学习",
  WORK: "工作",
  CONTENT_OPS: "运营"
}

function ProjectCard({ project }: { project: AigcProjectVO }) {
  return (
    <Link
      href={`/studio/projects/${project.id}`}
      className="group/proj-card block focus-visible:outline-none"
    >
      <GlassCard interactive className="h-full">
        {/* 封面 */}
        <div className="relative aspect-video overflow-hidden bg-foreground/[0.04]">
          {project.coverUrl ? (
            <NextImage
              src={project.coverUrl}
              alt={project.name}
              fill
              className="object-cover transition-transform duration-300 group-hover/proj-card:scale-105"
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center text-foreground/20">
              <Layers className="size-10" />
            </div>
          )}
          {/* 类型角标 */}
          <div className="absolute top-2 right-2">
            <NeonChip tone="violet" size="sm">
              {TYPE_LABELS[project.type] ?? project.type}
            </NeonChip>
          </div>
        </div>

        {/* 信息 */}
        <div className="px-4 py-3">
          <p className="truncate font-medium text-sm">{project.name}</p>
          <p className="mt-1 text-muted-foreground text-xs">
            {formatDistanceToNow(new Date(project.updateTime), { locale: zhCN, addSuffix: true })}
          </p>
        </div>
      </GlassCard>
    </Link>
  )
}

function ProjectSkeleton() {
  return (
    <GlassCard glow="none">
      <Skeleton className="aspect-video w-full" />
      <div className="px-4 py-3">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="mt-2 h-3 w-1/2" />
      </div>
    </GlassCard>
  )
}

interface HomeProjectGridProps {
  className?: string
}

export function HomeProjectGrid({ className }: HomeProjectGridProps) {
  const { data, isLoading } = useAigcProjects({ pageSize: 8 })
  const projects = data?.list ?? []

  return (
    <section className={cn("space-y-3", className)}>
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-semibold text-base">我的项目</h2>
          <p className="pt-1 text-muted-foreground text-xs">最近更新的创作项目</p>
        </div>
        <Link href="/studio/projects/new">
          <GlowButton tone="violet" size="sm">
            <Plus className="size-4" />
            新建项目
          </GlowButton>
        </Link>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {Array.from({ length: 5 }).map((_, i) => (
            <ProjectSkeleton key={i} />
          ))}
        </div>
      ) : projects.length === 0 ? (
        <GlassCard className="flex min-h-[180px] flex-col items-center justify-center gap-3 text-muted-foreground">
          <LottieIcon name="cat" width={120} height={120} loop />
          <Link href="/studio/projects/new">
            <GlowButton tone="violet" size="sm" className="mb-10">
              <Plus className="size-4" />
              创建第一个项目
            </GlowButton>
          </Link>
        </GlassCard>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {projects.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </div>
      )}
    </section>
  )
}
