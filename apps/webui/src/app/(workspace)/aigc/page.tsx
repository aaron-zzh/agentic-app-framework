/**
 * AIGC 项目列表——卡片看板入口
 * @author AaronZZH & Kiro
 */

"use client"

import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Image as ImageIcon, Layers, Plus, Trash2, Video } from "lucide-react"
import NextImage from "next/image"
import Link from "next/link"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { Skeleton } from "@/components/ui/skeleton"
import {
  type AigcProjectVO,
  useAigcProjects,
  useDeleteAigcProject
} from "@/lib/queries/use-aigc-projects"

const TYPE_LABELS: Record<string, string> = {
  IMAGE_POST: "图像",
  SHORT_VIDEO: "短视频",
  VIDEO_DRAMA: "视频",
  MIXED: "综合"
}

const TYPE_ICONS: Record<string, React.ReactNode> = {
  IMAGE_POST: <ImageIcon className="size-4" />,
  SHORT_VIDEO: <Video className="size-4" />,
  VIDEO_DRAMA: <Video className="size-4" />,
  MIXED: <Layers className="size-4" />
}

/** 项目类型 → 工作台子路由 */
function getProjectRoute(project: AigcProjectVO): string {
  const typeRouteMap: Record<string, string> = {
    IMAGE_POST: "image",
    SHORT_VIDEO: "video",
    VIDEO_DRAMA: "video",
    MIXED: "image"
  }
  const sub = typeRouteMap[project.type] ?? "image"
  return `/aigc/${project.id}/${sub}`
}

function ProjectCard({ project }: { project: AigcProjectVO }) {
  const { mutate: deleteProject } = useDeleteAigcProject()
  const [confirmOpen, setConfirmOpen] = useState(false)

  return (
    <Card className="group gap-0 overflow-hidden py-0 shadow-md ring-0 transition-shadow hover:shadow-lg">
      {/* 封面图 */}
      <Link href={getProjectRoute(project)} className="relative block aspect-video bg-muted">
        {project.coverUrl ? (
          <NextImage src={project.coverUrl} alt={project.name} fill className="object-cover" />
        ) : (
          <div className="flex size-full items-center justify-center text-muted-foreground/30">
            {TYPE_ICONS[project.type] ?? <Layers className="size-8" />}
          </div>
        )}
      </Link>

      <CardContent className="p-4">
        <Link href={getProjectRoute(project)} className="hover:underline">
          <p className="truncate font-medium text-sm">{project.name}</p>
        </Link>

        <div className="mt-1.5 flex items-center justify-between">
          <span className="text-muted-foreground text-xs">
            {TYPE_LABELS[project.type] ?? project.type}
          </span>
          <div className="flex items-center gap-2">
            <span className="text-muted-foreground text-xs">
              {formatDistanceToNow(new Date(project.updateTime), { locale: zhCN, addSuffix: true })}
            </span>
            <Button
              variant="ghost"
              size="icon"
              className="size-6 opacity-0 transition-opacity group-hover:opacity-100"
              onClick={(e) => {
                e.preventDefault()
                setConfirmOpen(true)
              }}
            >
              <Trash2 className="size-3.5 text-destructive" />
            </Button>
          </div>
        </div>
      </CardContent>

      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title="删除项目"
        description={`确定要删除「${project.name}」吗？此操作不可撤销。`}
        confirmText="删除"
        onConfirm={() => deleteProject(project.id)}
      />
    </Card>
  )
}

function ProjectSkeleton() {
  return (
    <div className="overflow-hidden rounded-xl shadow-md">
      <Skeleton className="aspect-video w-full" />
      <div className="p-3">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="mt-1.5 h-3 w-1/2" />
      </div>
    </div>
  )
}

export default function AigcProjectListPage() {
  const { data, isLoading } = useAigcProjects({ pageSize: 50 })
  const projects = data?.list ?? []

  return (
    <div className="flex h-full flex-col p-6">
      {/* 头部 */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="font-bold text-2xl">创作项目</h1>
          <p className="text-muted-foreground text-sm">管理你的 AIGC 创作项目</p>
        </div>
        <Button asChild>
          <Link href="/aigc/new">
            <Plus className="mr-2 size-4" />
            新建项目
          </Link>
        </Button>
      </div>

      {/* 项目列表 */}
      {isLoading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {Array.from({ length: 8 }).map((_, i) => (
            <ProjectSkeleton key={i} />
          ))}
        </div>
      ) : projects.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-muted-foreground">
          <Layers className="size-12 opacity-20" />
          <p>还没有创作项目</p>
          <Button asChild variant="outline">
            <Link href="/aigc/new">创建第一个项目</Link>
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
          {projects.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </div>
      )}
    </div>
  )
}
