/**
 * Studio 首屏生产概况。
 *
 * 展示项目、执行、异常、素材与作品五项行动指标；不展示积分、文档等弱行动库存。
 */

"use client"

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
import { useAigcStudioHomeSummary } from "@/lib/api/rest/ai/aigc/home"

function CapsuleArrow() {
  return (
    <ArrowRight className="size-4 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
  )
}

export function HomeDataCapsules() {
  const { data: summary, isLoading } = useAigcStudioHomeSummary()

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-5">
      <Link href="/studio/projects?status=in_progress" className="group">
        <DataCapsule
          label="进行中项目"
          value={summary?.activeProjectCount ?? 0}
          unit="个"
          loading={isLoading}
          icon={<FolderKanban className="size-4" />}
          tone="violet"
          action={<CapsuleArrow />}
        />
      </Link>
      <Link href="/studio/me/generations" className="group">
        <DataCapsule
          label="执行中任务"
          value={summary?.activeTaskCount ?? 0}
          unit="个"
          loading={isLoading}
          icon={<CirclePlay className="size-4" />}
          tone="cyan"
          action={<CapsuleArrow />}
        />
      </Link>
      <Link href="/studio/me/generations" className="group">
        <DataCapsule
          label="失败任务"
          value={summary?.failedTaskCount ?? 0}
          unit="个"
          loading={isLoading}
          icon={<AlertTriangle className="size-4" />}
          tone="amber"
          action={<CapsuleArrow />}
        />
      </Link>
      <Link href="/studio/assets/materials" className="group">
        <DataCapsule
          label="素材"
          value={summary?.mediaCount ?? 0}
          unit="个"
          loading={isLoading}
          icon={<PackageOpen className="size-4" />}
          tone="cyan"
          action={<CapsuleArrow />}
        />
      </Link>
      <Link href="/studio/assets/works" className="group">
        <DataCapsule
          label="作品"
          value={summary?.workCount ?? 0}
          unit="个"
          loading={isLoading}
          icon={<Images className="size-4" />}
          tone="emerald"
          action={<CapsuleArrow />}
        />
      </Link>
    </div>
  )
}
