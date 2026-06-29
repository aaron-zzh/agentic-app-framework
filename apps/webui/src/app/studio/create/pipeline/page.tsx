/**
 * 工作流 - 模板列表
 *
 * v0.2.1 P1：5 官方流水线模板（口播/宣传/小红书/IP/学习笔记）
 * 列表卡片网格，点击进入详情运行器
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowRight, Loader2, Workflow } from "lucide-react"
import Link from "next/link"
import { GlassCard, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import { Skeleton } from "@/components/ui/skeleton"
import { useWorkflowTemplates, type WorkflowTemplate } from "@/lib/queries/use-workflow-templates"

const CATEGORY_LABELS: Record<string, string> = {
  CONTENT: "内容创作",
  MARKETING: "营销宣传",
  STUDY: "学习笔记",
  LIFE: "生活记录"
}

const CATEGORY_TONE: Record<string, "violet" | "cyan" | "emerald" | "amber" | "rose"> = {
  CONTENT: "violet",
  MARKETING: "amber",
  STUDY: "cyan",
  LIFE: "emerald"
}

function TemplateCard({ template }: { template: WorkflowTemplate }) {
  const tone = CATEGORY_TONE[template.category] ?? "violet"
  // GlassCard.glow 只支持 violet/cyan/accent/none，其他色映射到 accent
  const cardGlow: "violet" | "cyan" | "accent" =
    tone === "violet" ? "violet" : tone === "cyan" ? "cyan" : "accent"
  const stepCount = template.templateConfig?.steps?.length ?? 0

  return (
    <Link
      href={`/studio/create/pipeline/${template.id}`}
      className="block transition-transform hover:-translate-y-0.5"
    >
      <GlassCard glow={cardGlow}>
        <div className="space-y-3 p-5">
          <div className="flex items-start justify-between gap-2">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-foreground/[0.04]">
              <Workflow className="size-5 opacity-80" />
            </div>
            <NeonChip tone={tone} size="sm">
              {CATEGORY_LABELS[template.category] ?? template.category}
            </NeonChip>
          </div>
          <div className="space-y-1">
            <h3 className="font-semibold text-base">{template.name}</h3>
            {template.description && (
              <p className="line-clamp-2 text-muted-foreground text-xs leading-relaxed">
                {template.description}
              </p>
            )}
          </div>
          <div className="flex items-center justify-between border-foreground/6 border-t pt-3">
            <span className="text-muted-foreground text-xs">
              {stepCount} 步骤 · {template.usageCount} 人用过
            </span>
            <ArrowRight className="size-3.5 opacity-50 transition-transform group-hover:translate-x-0.5" />
          </div>
        </div>
      </GlassCard>
    </Link>
  )
}

export default function StudioPipelineListPage() {
  const { data, isLoading } = useWorkflowTemplates({ pageSize: 20 })
  const templates = data?.list ?? []

  return (
    <div className="relative">
      <SectionHaze variant="violet" />
      <div className="mx-auto max-w-6xl space-y-6 p-6">
        <header className="space-y-2">
          <div className="flex items-center gap-2">
            <Workflow className="size-5 text-violet-400" />
            <h1 className="font-semibold text-xl">工作流</h1>
          </div>
          <p className="text-muted-foreground text-sm">
            选择一个模板，一键串联文案 / 图像 / 视频，生成成品。
          </p>
        </header>

        {isLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={`pl-${i}`} className="h-44 w-full" />
            ))}
          </div>
        ) : templates.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-foreground/8 border-dashed py-12 text-center">
            <Loader2 className="size-6 animate-spin opacity-40" />
            <p className="text-muted-foreground text-sm">暂无模板</p>
            <Link href="/studio/create">
              <GlowButton tone="ghost" size="sm">
                返回创作首页
              </GlowButton>
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {templates.map((t) => (
              <TemplateCard key={t.id} template={t} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
