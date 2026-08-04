/**
 * Content Studio 项目工作台头部。
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowLeft, GitBranch, LayoutList, MessageSquare } from "lucide-react"
import Link from "next/link"
import type { ReactNode } from "react"
import { NeonChip } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { AigcProject } from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils/index"
import { getProjectTypeConfig, PROJECT_STATUS_CONFIG } from "./project-type-config"

export type ProjectWorkbenchView = "structure" | "graph"

export interface ProjectWorkbenchHeaderProps {
  project: AigcProject
  view: ProjectWorkbenchView
  lifecycleActions?: ReactNode
  onViewChange: (view: ProjectWorkbenchView) => void
  onToggleChat: () => void
  chatOpen: boolean
}

export function ProjectWorkbenchHeader({
  project,
  view,
  lifecycleActions,
  onViewChange,
  onToggleChat,
  chatOpen
}: ProjectWorkbenchHeaderProps) {
  const type = getProjectTypeConfig({ code: project.projectTypeCode, name: "" })
  const status = PROJECT_STATUS_CONFIG[project.status]

  return (
    <header className="flex flex-wrap items-center justify-between gap-3 border-b bg-background/70 px-4 py-3 backdrop-blur sm:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <Button
          nativeButton={false}
          render={<Link href="/studio/projects" />}
          variant="ghost"
          size="icon-sm"
          aria-label="返回项目列表"
        >
          <ArrowLeft />
        </Button>
        <div className="min-w-0">
          <p className="truncate text-muted-foreground text-xs">
            {project.primaryBrandProfileId
              ? `品牌 #${project.primaryBrandProfileId}`
              : "未绑定品牌"}{" "}
            · {type.label}
          </p>
          <h1 className="truncate font-semibold text-base">{project.name}</h1>
        </div>
        <NeonChip tone={status.tone} size="sm">
          {status.label}
        </NeonChip>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {lifecycleActions}
        <ToggleGroup
          value={[view]}
          onValueChange={(values: string[]) => {
            const next = values.at(-1)
            if (next === "structure" || next === "graph") onViewChange(next)
          }}
          variant="outline"
          size="sm"
          spacing={0}
        >
          <ToggleGroupItem value="structure" aria-label="结构视图">
            <LayoutList />
            结构
          </ToggleGroupItem>
          <ToggleGroupItem value="graph" aria-label="图谱视图">
            <GitBranch />
            图谱
          </ToggleGroupItem>
        </ToggleGroup>
        <Button
          variant="outline"
          size="sm"
          aria-label="打开项目对话"
          className={cn(chatOpen && "border-primary/40 bg-primary/10 text-primary")}
          onClick={onToggleChat}
        >
          <MessageSquare />
          对话
        </Button>
      </div>
    </header>
  )
}
