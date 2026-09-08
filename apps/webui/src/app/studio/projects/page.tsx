/**
 * Content Studio AIGC 项目列表。
 * @author AaronZZH & Kiro
 */

"use client"

import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Archive, FolderKanban, Layers, MoreHorizontal, Pencil, Plus } from "lucide-react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { useId, useState } from "react"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import {
  getProjectTypeConfig,
  isProjectContentWritable,
  PROJECT_STATUS_CONFIG
} from "@/features/studio/content"
import type { AigcProject, AigcProjectStatus } from "@/lib/api/rest/ai/aigc"
import {
  useAigcProjectLifecycle,
  useAigcProjects,
  useAigcProjectTypes,
  useUpdateAigcProject
} from "@/lib/api/rest/ai/aigc"
import { useEntityAccess } from "@/lib/api/rest/user/permission"
import { usePermissionGuard } from "@/lib/hooks/use-permission-guard"
import { cn } from "@/lib/utils/index"

const STATUS_TABS: { value: "all" | AigcProjectStatus; label: string }[] = [
  { value: "all", label: "全部" },
  { value: "CONFIGURING", label: "配置中" },
  { value: "CREATING", label: "创作中" },
  { value: "EXECUTING", label: "生成中" },
  { value: "REVIEWING", label: "审核中" },
  { value: "DELIVERING", label: "交付中" },
  { value: "COMPLETED", label: "已完成" },
  { value: "ARCHIVED", label: "已归档" }
]

type PendingProjectAction = { projectId: number }

function buildFilterHref(status: string, type?: string): string {
  const params = new URLSearchParams()
  if (status !== "all") params.set("status", status)
  if (type) params.set("type", type)
  const query = params.toString()
  return query ? `/studio/projects?${query}` : "/studio/projects"
}

interface ProjectCardProps {
  project: AigcProject
  canUpdate: boolean
  onEdit: (project: AigcProject) => void
  onArchive: (project: AigcProject) => void
}

function ProjectCard({ project, canUpdate, onEdit, onArchive }: ProjectCardProps) {
  const type = getProjectTypeConfig({ code: project.projectTypeCode, name: "" })
  const TypeIcon = type.icon
  const status = PROJECT_STATUS_CONFIG[project.status]
  const editable = canUpdate && isProjectContentWritable(project.status)

  return (
    <GlassCard interactive className="group relative h-full">
      <Link
        href={`/studio/projects/${project.id}`}
        className="block h-full focus-visible:outline-none"
      >
        <div className="relative flex aspect-video items-center justify-center overflow-hidden bg-muted text-muted-foreground">
          <TypeIcon className="size-10 transition-transform duration-300 group-hover:scale-110" />
          <div className="absolute top-2 left-2 flex gap-1.5">
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
      </Link>

      {canUpdate ? (
        <div className="absolute top-2 right-2 z-10">
          <DropdownMenu>
            <DropdownMenuTrigger
              type="button"
              aria-label={`打开项目「${project.name}」操作菜单`}
              className="flex size-7 items-center justify-center rounded-full bg-black/45 text-white opacity-100 shadow-sm backdrop-blur-sm transition-opacity hover:bg-black/65 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/70 data-popup-open:opacity-100 sm:opacity-0 sm:group-hover:opacity-100 sm:group-focus-within:opacity-100 [&_svg]:size-4"
            >
              <MoreHorizontal />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuGroup>
                <DropdownMenuItem disabled={!editable} onClick={() => onEdit(project)}>
                  <Pencil />
                  编辑
                </DropdownMenuItem>
                <DropdownMenuItem
                  disabled={project.status === "ARCHIVED"}
                  onClick={() => onArchive(project)}
                >
                  <Archive />
                  {project.status === "COMPLETED" ? "完成后归档" : "放弃并归档"}
                </DropdownMenuItem>
              </DropdownMenuGroup>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      ) : null}
    </GlassCard>
  )
}

function ProjectEditDialog({
  project,
  mutable,
  onOpenChange
}: {
  project: AigcProject
  mutable: boolean
  onOpenChange: (open: boolean) => void
}) {
  const nameId = useId()
  const descriptionId = useId()
  const [name, setName] = useState(project.name)
  const [description, setDescription] = useState(project.description ?? "")
  const updateProject = useUpdateAigcProject()

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedName = name.trim()
    if (!mutable || !trimmedName) return
    updateProject.mutate(
      {
        id: project.id,
        data: {
          name: trimmedName,
          description: description.trim(),
          expectedVersion: project.version
        }
      },
      {
        onSuccess: () => {
          toast.success("项目已更新")
          onOpenChange(false)
        },
        onError: (error) =>
          toast.error(`更新失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>编辑项目</DialogTitle>
            <DialogDescription>修改项目名称和描述。</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <label htmlFor={nameId} className="font-medium text-sm">
              项目名称
            </label>
            <Input
              id={nameId}
              autoFocus
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="flex flex-col gap-2">
            <label htmlFor={descriptionId} className="font-medium text-sm">
              项目描述
            </label>
            <Textarea
              id={descriptionId}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="补充项目背景、目标或交付说明"
              className="min-h-28 resize-y"
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              取消
            </Button>
            <Button type="submit" disabled={!mutable || !name.trim() || updateProject.isPending}>
              {updateProject.isPending ? "保存中…" : "保存"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default function StudioProjectsPage() {
  const searchParams = useSearchParams()
  const { data: projectAccess } = useEntityAccess("project")
  const { canCreate, canUpdate } = usePermissionGuard(projectAccess)
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
  const [editingProjectId, setEditingProjectId] = useState<number | null>(null)
  const [pendingAction, setPendingAction] = useState<PendingProjectAction | null>(null)
  const [archiveReason, setArchiveReason] = useState("")
  const lifecycle = useAigcProjectLifecycle()
  const editingProject = projects.find((project) => project.id === editingProjectId)
  const pendingProject = projects.find((project) => project.id === pendingAction?.projectId)

  function executePendingAction() {
    if (
      !canUpdate ||
      !pendingProject ||
      pendingProject.status === "ARCHIVED" ||
      !archiveReason.trim()
    )
      return
    lifecycle.mutate(
      {
        projectId: pendingProject.id,
        action: "archive",
        expectedProjectVersion: pendingProject.version,
        reason: archiveReason.trim(),
        idempotencyKey: crypto.randomUUID()
      },
      {
        onSuccess: () => {
          toast.success("项目已归档并保留全部历史")
          setPendingAction(null)
          setArchiveReason("")
        },
        onError: (error) =>
          toast.error(`归档失败：${error instanceof Error ? error.message : "未知错误"}`)
      }
    )
  }

  return (
    <div className="relative h-full overflow-y-auto">
      <SectionHaze variant="cyan" />
      <div className="relative flex flex-col gap-6 p-6">
        <header className="flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <FolderKanban className="size-5 text-primary" />
            <h1 className="font-semibold text-xl">我的项目</h1>
          </div>
          {canCreate ? (
            <GlowButton
              nativeButton={false}
              render={<Link href="/studio/projects/new" />}
              tone="violet"
            >
              <Plus /> 新建项目
            </GlowButton>
          ) : null}
        </header>

        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex min-w-0 flex-wrap gap-2 lg:flex-1">
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

          <Tabs value={status} className="overflow-x-auto lg:shrink-0">
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
              {canCreate ? (
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
              ) : null}
            </Empty>
          </GlassCard>
        ) : (
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
            {projects.map((project) => (
              <ProjectCard
                key={project.id}
                project={project}
                canUpdate={canUpdate}
                onEdit={(target) => {
                  if (canUpdate && isProjectContentWritable(target.status))
                    setEditingProjectId(target.id)
                }}
                onArchive={(target) => {
                  if (canUpdate && target.status !== "ARCHIVED")
                    setPendingAction({ projectId: target.id })
                }}
              />
            ))}
          </div>
        )}

        {editingProject && canUpdate && isProjectContentWritable(editingProject.status) ? (
          <ProjectEditDialog
            key={editingProject.id}
            project={editingProject}
            mutable={canUpdate && isProjectContentWritable(editingProject.status)}
            onOpenChange={(open) => {
              if (!open) setEditingProjectId(null)
            }}
          />
        ) : null}

        <Dialog
          open={pendingProject !== undefined && pendingProject.status !== "ARCHIVED" && canUpdate}
          onOpenChange={(open) => {
            if (!open) {
              setPendingAction(null)
              setArchiveReason("")
            }
          }}
        >
          <DialogContent>
            <DialogHeader>
              <DialogTitle>
                {pendingProject?.status === "COMPLETED" ? "完成后归档" : "放弃并归档项目"}
              </DialogTitle>
              <DialogDescription>
                归档后项目全局只读，但图谱、版本、Run、Review、Work 和 Publication 历史全部保留。
              </DialogDescription>
            </DialogHeader>
            <Textarea
              value={archiveReason}
              onChange={(event) => setArchiveReason(event.target.value)}
              placeholder="填写归档或放弃原因（必填）"
            />
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setPendingAction(null)}>
                取消
              </Button>
              <Button
                type="button"
                variant="destructive"
                disabled={!archiveReason.trim() || lifecycle.isPending}
                onClick={executePendingAction}
              >
                确认归档
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </div>
  )
}
