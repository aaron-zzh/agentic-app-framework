/**
 * 画板模板选择对话框——预设模板：头脑风暴/项目规划/用户旅程地图
 * @author AaronZZH & Kiro
 */

"use client"

import { Brain, FileText, FolderKanban, Route } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import type { CanvasTemplate } from "@/lib/types/entity"

interface CanvasTemplateDialogProps {
  onSelect: (template: CanvasTemplate) => void
}

/** 模板定义 */
const TEMPLATES: { key: CanvasTemplate; label: string; description: string; icon: typeof Brain }[] =
  [
    {
      key: "brainstorm",
      label: "头脑风暴",
      description: "中心主题 + 发散便签，适合创意收集",
      icon: Brain
    },
    {
      key: "project-plan",
      label: "项目规划",
      description: "时间线 + 里程碑 + 任务卡片",
      icon: FolderKanban
    },
    {
      key: "user-journey",
      label: "用户旅程地图",
      description: "阶段→触点→情绪曲线→痛点/机会",
      icon: Route
    },
    {
      key: "blank",
      label: "空白画布",
      description: "从零开始自由创作",
      icon: FileText
    }
  ]

/** 画板模板选择对话框 */
export function CanvasTemplateDialog({ onSelect }: CanvasTemplateDialogProps) {
  return (
    <Dialog>
      <DialogTrigger render={<Button variant="outline" size="sm">选择模板</Button>} />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>选择画板模板</DialogTitle>
        </DialogHeader>
        <div className="grid grid-cols-2 gap-3 pt-2">
          {TEMPLATES.map((tpl) => {
            const Icon = tpl.icon
            return (
              <button
                key={tpl.key}
                type="button"
                className="flex flex-col items-center gap-2 rounded-lg border p-4 text-center transition-colors hover:bg-accent"
                onClick={() => onSelect(tpl.key)}
              >
                <Icon className="h-8 w-8 text-muted-foreground" />
                <span className="font-medium text-sm">{tpl.label}</span>
                <span className="text-muted-foreground text-xs">{tpl.description}</span>
              </button>
            )
          })}
        </div>
      </DialogContent>
    </Dialog>
  )
}
