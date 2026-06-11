/**
 * 项目入口——获取项目类型后 redirect 到对应工作台子路由
 * @author AaronZZH & Kiro
 */

"use client"

import { useRouter } from "next/navigation"
import { use, useEffect } from "react"
import { Skeleton } from "@/components/ui/skeleton"
import { useAigcProject } from "@/lib/queries/use-aigc-projects"

/** 项目类型 → 工作台子路由 */
const TYPE_ROUTE: Record<string, string> = {
  IMAGE_POST: "image",
  SHORT_VIDEO: "video",
  VIDEO_DRAMA: "video",
  MIXED: "image"
}

interface Props {
  params: Promise<{ projectId: string }>
}

export default function AigcProjectRedirectPage({ params }: Props) {
  const router = useRouter()
  const { projectId } = use(params)
  const id = Number(projectId)
  const { data: project, isError } = useAigcProject(Number.isNaN(id) ? null : id)

  useEffect(() => {
    if (isError) {
      router.replace("/aigc")
      return
    }
    if (project) {
      const sub = TYPE_ROUTE[project.type] ?? "image"
      router.replace(`/aigc/${projectId}/${sub}`)
    }
  }, [project, isError, projectId, router])

  return (
    <div className="flex h-full items-center justify-center">
      <Skeleton className="h-8 w-32" />
    </div>
  )
}
