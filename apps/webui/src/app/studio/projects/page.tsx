/**
 * /studio/projects——项目列表
 *
 * 迁移自 app/(workspace)/aigc/page.tsx，重做风格：
 * - 状态分类 tab（全部 / 进行中 / 草稿 / 完成 / 归档）
 * - 卡片用 GlassCard，封面图、类型 chip、相对时间
 * - 跳转 /studio/projects/[id]
 *
 * 用 nuqs 同步 status 到 URL，符合"客户端 UI 状态进 URL 而非 Zustand"硬规则
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { FolderKanban, Image as ImageIcon, Layers, Mic, Music, Plus, Video } from "lucide-react"
import NextImage from "next/image"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  type AigcProjectVO,
  useAigcProjects,
  useDeleteAigcProject
} from "@/lib/queries/use-aigc-projects"
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

const TYPE_ICONS: Record<string, React.ReactNode> = {
  IMAGE_POST: <ImageIcon className="size-4" />,
  SHORT_VIDEO: <Video className="size-4" />,
  VIDEO_DRAMA: <Video className="size-4" />,
  MIXED: <Layers className="size-4" />,
  MUSIC: <Music className="size-4" />,
  VOICE: <Mic className="size-4" />,
  MODEL_3D: <Layers className="size-4" />
}

const STATUS_TABS = [
  { value: "all", label: "全部" },
  { value: "IN_PROGRESS", label: "进行中" },
  { value: "DRAFT", label: "草稿" },
  { value: "COMPLETED", label: "已完成" },
  { value: "ARCHIVED", label: "已归档" }
] as const

function ProjectCard({ project }: { project: AigcProjectVO }) {
  const { mutate: deleteProject } = useDeleteAigcProject()
  const [confirmOpen, setConfirmOpen] = useState(false)

  return (
    <>
      <Link
        href={`/studio/projects/${project.id}`}
        className="group/card block focus-visible:outline-none"
      >
        <GlassCard interactive className="h-full">
          {/* 封面 */}
          <div className="relative aspect-video overflow-hidden bg-foreground/[0.04]">
            {project.coverUrl ? (
              <NextImage
                src={project.coverUrl}
                alt={project.name}
                fill
                className="object-cover transition-transform duration-300 group-hover/card:scale-105"
              />
            ) : (
              <div className="flex h-full w-full items-center justify-center text-foreground/20">
                {TYPE_ICONS[project.type] ?? <Layers className="size-10" />}
              </div>
            )}
            <div className="absolute top-2 right-2">
              <NeonChip tone="violet" size="sm">
                {TYPE_LABELS[project.type] ?? project.type}
              </NeonChip>
            </div>
          </div>

          <div className="flex items-start justify-between gap-2 px-4 py-3">
            <div className="min-w-0 flex-1">
              <p className="truncate font-medium text-sm">{project.name}</p>
              <p className="mt-1 text-muted-foreground text-xs">
                {formatDistanceToNow(new Date(project.updateTime), {
                  locale: zhCN,
                  addSuffix: true
                })}
              </p>
            </div>
          </div>
        </GlassCard>
      </Link>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="删除项目"
        description={`确定要删除「${project.name}」吗？此操作不可撤销。`}
        confirmText="删除"
        onConfirm={() =>
          deleteProject(project.id, {
            onSuccess: () => toast.success(`项目「${project.name}」已删除`),
            onError: (err) =>
              toast.error(`删除失败：${err instanceof Error ? err.message : "未知错误"}`)
          })
        }
      />
    </>
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

export default function StudioProjectsPage() {
  const searchParams = useSearchParams()
  const status = searchParams.get("status") ?? "all"

  const { data, isLoading } = useAigcProjects({
    pageSize: 50,
    ...(status !== "all" ? { status } : {})
  })
  const projects = data?.list ?? []

  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="cyan" />

      <div className="relative mx-auto max-w-7xl space-y-6 p-6">
        {/* 头部 */}
        <header className="flex items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <FolderKanban className="size-5 text-primary" />
              <h1 className="font-semibold text-xl">我的项目</h1>
            </div>
            <p className="text-muted-foreground text-sm">
              所有 AI 创作项目集中管理，沉淀你的素材与作品
            </p>
          </div>
          <Link href="/studio/projects/new">
            <GlowButton tone="violet" size="default">
              <Plus className="size-4" />
              新建项目
            </GlowButton>
          </Link>
        </header>

        {/* 状态分类 tab */}
        <Tabs value={status}>
          <TabsList className="bg-foreground/[0.04]">
            {STATUS_TABS.map((tab) => (
              <TabsTrigger
                key={tab.value}
                value={tab.value}
                render={
                  <Link
                    href={
                      tab.value === "all"
                        ? "/studio/projects"
                        : `/studio/projects?status=${tab.value}`
                    }
                  />
                }
                className="data-[selected]:bg-primary/15 data-[selected]:text-primary"
              >
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        {/* 列表 */}
        {isLoading ? (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {Array.from({ length: 10 }).map((_, i) => (
              <ProjectSkeleton key={i} />
            ))}
          </div>
        ) : projects.length === 0 ? (
          <GlassCard className="flex min-h-[280px] flex-col items-center justify-center gap-3 text-muted-foreground">
            <Layers className="size-12 opacity-30" />
            <p className="text-sm">{status === "all" ? "还没有创作项目" : "该状态下还没有项目"}</p>
            <Link href="/studio/projects/new">
              <GlowButton tone="violet" size="default">
                <Plus className="size-4" />
                创建第一个项目
              </GlowButton>
            </Link>
          </GlassCard>
        ) : (
          <div
            className={cn("grid gap-4", "grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5")}
          >
            {projects.map((project) => (
              <ProjectCard key={project.id} project={project} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
