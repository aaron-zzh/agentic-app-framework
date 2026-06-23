/**
 * 项目摘要面板——payload.projectId: 项目 ID
 * @author AaronZZH & Kiro
 */

"use client"

import { ExternalLink } from "lucide-react"
import Link from "next/link"
import type { SlotPanelProps } from "../registry"

export function ProjectSummaryPanel({ payload }: SlotPanelProps) {
  const projectId = payload?.projectId as number | undefined

  if (!projectId) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 text-center">
        <p className="text-muted-foreground text-xs">未指定项目</p>
        <Link
          href="/studio/projects"
          className="text-primary text-xs underline-offset-2 hover:underline"
        >
          查看所有项目
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-2">
      <p className="text-muted-foreground text-xs">项目 ID: #{projectId}</p>
      <Link
        href={`/studio/projects/${projectId}`}
        className="flex items-center justify-center gap-1 rounded border border-foreground/[0.08] bg-foreground/[0.02] px-2 py-1.5 text-xs transition-colors hover:bg-foreground/[0.06]"
      >
        <ExternalLink className="size-3" />
        进入工作台
      </Link>
    </div>
  )
}
