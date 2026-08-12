/**
 * Studio 首屏生产概况。
 *
 * 展示项目、执行、异常、素材与作品五项行动指标；不展示积分、文档等弱行动库存。
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import {
  AlertTriangle,
  ArrowRight,
  CirclePlay,
  FolderKanban,
  Images,
  PackageOpen
} from "lucide-react"
import Link from "next/link"
import { DataCapsule } from "@/components/studio"
import { useAigcProjects, useAigcWorks } from "@/lib/api/rest/ai/aigc"
import { type PageResult, request } from "@/lib/api/rest/entity"
import { useMediaList } from "@/lib/api/rest/media"

interface TaskCountRecord {
  id: number
}

function useTaskStatusCounts() {
  return useQuery({
    queryKey: ["aigc.task", "home-status-counts"] as const,
    queryFn: async () => {
      const [pending, running, failed] = await Promise.all([
        request<PageResult<TaskCountRecord>>("/aigc/tasks?pageNo=1&pageSize=1&status=PENDING"),
        request<PageResult<TaskCountRecord>>("/aigc/tasks?pageNo=1&pageSize=1&status=RUNNING"),
        request<PageResult<TaskCountRecord>>("/aigc/tasks?pageNo=1&pageSize=1&status=FAIL")
      ])
      return {
        active: pending.total + running.total,
        failed: failed.total
      }
    }
  })
}

function CapsuleArrow() {
  return (
    <ArrowRight className="size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
  )
}

export function HomeDataCapsules() {
  const { data: projectPage, isLoading: projectLoading } = useAigcProjects({
    pageNo: 1,
    pageSize: 1,
    status: "in_progress"
  })
  const { data: taskCounts, isLoading: taskLoading } = useTaskStatusCounts()
  const { data: mediaPage, isLoading: mediaLoading } = useMediaList({ pageNo: 1, pageSize: 1 })
  const { data: workPage, isLoading: workLoading } = useAigcWorks({ pageNo: 1, pageSize: 1 })

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
      <Link href="/studio/projects?status=in_progress" className="group">
        <DataCapsule
          label="进行中项目"
          value={projectPage?.total ?? 0}
          unit="个"
          loading={projectLoading}
          icon={<FolderKanban className="size-4" />}
          tone="violet"
          action={<CapsuleArrow />}
        />
      </Link>
      <Link href="/studio/me/generations" className="group">
        <DataCapsule
          label="执行中任务"
          value={taskCounts?.active ?? 0}
          unit="个"
          loading={taskLoading}
          icon={<CirclePlay className="size-4" />}
          tone="cyan"
          action={<CapsuleArrow />}
        />
      </Link>
      <Link href="/studio/me/generations" className="group">
        <DataCapsule
          label="失败任务"
          value={taskCounts?.failed ?? 0}
          unit="个"
          loading={taskLoading}
          icon={<AlertTriangle className="size-4" />}
          tone="amber"
          action={<CapsuleArrow />}
        />
      </Link>
      <Link href="/studio/assets/materials" className="group">
        <DataCapsule
          label="素材"
          value={mediaPage?.total ?? 0}
          unit="个"
          loading={mediaLoading}
          icon={<PackageOpen className="size-4" />}
          tone="cyan"
          action={<CapsuleArrow />}
        />
      </Link>
      <Link href="/studio/assets/works" className="group">
        <DataCapsule
          label="作品"
          value={workPage?.total ?? 0}
          unit="个"
          loading={workLoading}
          icon={<Images className="size-4" />}
          tone="emerald"
          action={<CapsuleArrow />}
        />
      </Link>
    </div>
  )
}
