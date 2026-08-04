/**
 * Content Studio 项目列表。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { FolderKanban, Layers, Plus, Trash2 } from "lucide-react"
import Image from "next/image"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
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
import {
  type ContentProjectStatus,
  type ContentProjectTypeCode,
  type ContentProjectVO,
  useContentProjects,
  useContentProjectTypes,
  useDeleteContentProject
} from "@/lib/api/rest/content"
import { cn } from "@/lib/utils/index"

const STATUS_TABS: { value: "all" | ContentProjectStatus; label: string }[] = [
  { value: "all", label: "全部" },
  { value: "in_progress", label: "进行中" },
  { value: "reviewing", label: "审核中" },
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

function ProjectCard({ project }: { project: ContentProjectVO }) {
  const confirm = useBoolean()
  const deleteProject = useDeleteContentProject()
  const type = getProjectTypeConfig({ code: project.projectTypeCode, name: "" })
  const TypeIcon = type.icon
  const status = PROJECT_STATUS_CONFIG[project.status]

  return (
    <>
      <GlassCard interactive className="group/card relative h-full">
        <Link href={`/studio/projects/${project.id}`} className="block focus-visible:outline-none">
          <div className="relative aspect-video overflow-hidden bg-muted">
            {project.coverUrl ? (
              <Image
                src={project.coverUrl}
                alt={project.name}
                fill
                className="object-cover transition-transform duration-300 group-hover/card:scale-105"
              />
            ) : (
              <div className="flex size-full items-center justify-center text-muted-foreground">
                <TypeIcon className="size-10" />
              </div>
            )}
            <div className="absolute top-2 right-2 flex gap-1.5">
              <NeonChip tone={type.tone} size="sm">
                {type.label}
              </NeonChip>
              <NeonChip tone={status.tone} size="sm">
                {status.label}
              </NeonChip>
            </div>
          </div>
          <div className="flex flex-col gap-2 px-4 py-3 pr-11">
            <p className="truncate font-medium text-sm">{project.name}</p>
            <div className="flex items-center justify-between gap-2 text-muted-foreground text-xs">
              <span className="truncate">{project.primaryBrandProfileName ?? "未绑定品牌"}</span>
              <span className="shrink-0">
                {formatDistanceToNow(new Date(project.lastActiveTime ?? project.updateTime), {
                  locale: zhCN,
                  addSuffix: true
                })}
              </span>
            </div>
          </div>
        </Link>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={`删除${project.name}`}
          className="absolute right-3 bottom-3 opacity-0 focus-visible:opacity-100 group-hover/card:opacity-100"
          onClick={confirm.onTrue}
        >
          <Trash2 />
        </Button>
      </GlassCard>
      <ConfirmDialog
        open={confirm.value}
        onOpenChange={confirm.setValue}
        title="删除项目"
        description={`确定要删除「${project.name}」吗？此操作不可撤销。`}
        confirmText="删除"
        variant="destructive"
        onConfirm={() =>
          deleteProject.mutate(project.id, {
            onSuccess: () => toast.success(`项目「${project.name}」已删除`)
          })
        }
      />
    </>
  )
}

export default function StudioProjectsPage() {
  const searchParams = useSearchParams()
  const rawStatus = searchParams.get("status") ?? "all"
  const status = STATUS_TABS.some((tab) => tab.value === rawStatus) ? rawStatus : "all"
  const rawType = searchParams.get("type")
  const type = rawType as ContentProjectTypeCode | null
  const { data: typePage } = useContentProjectTypes()
  const { data, isLoading } = useContentProjects({
    pageSize: 50,
    ...(status !== "all" ? { status: status as ContentProjectStatus } : {}),
    ...(type ? { projectTypeCode: type } : {})
  })
  const projects = data?.list ?? []
  const projectTypes = typePage?.list ?? []

  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="cyan" />
      <div className="relative mx-auto flex max-w-7xl flex-col gap-6 p-6">
        <header className="flex items-center justify-between gap-4">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2">
              <FolderKanban className="size-5 text-primary" />
              <h1 className="font-semibold text-xl">我的项目</h1>
            </div>
          </div>
          <GlowButton
            nativeButton={false}
            render={<Link href="/studio/projects/new" />}
            tone="violet"
          >
            <Plus />
            新建项目
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
