/**
 * 工作流 - 详情运行器
 *
 * v0.2.1 P1：展示步骤清单 + 输入起始参数 + 一键运行
 * 运行机制：前端串行调度（v0.2.1 是 stub，输入收集后调用 incrementRunCount + toast 提示
 * 完整 worker 留 v0.3）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ArrowLeft,
  CheckCircle2,
  FileText,
  Image as ImageIcon,
  Loader2,
  PlayCircle,
  ScanText,
  Video,
  Workflow
} from "lucide-react"
import Link from "next/link"
import { useParams } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip, SectionHaze } from "@/components/studio"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import {
  useIncrementRunCount,
  useWorkflowTemplate,
  type WorkflowStep
} from "@/lib/queries/use-workflow-templates"
import { cn } from "@/lib/utils"

const STEP_ICONS: Record<WorkflowStep["kind"], typeof FileText> = {
  COPY: FileText,
  IMAGE: ImageIcon,
  VIDEO: Video,
  OCR: ScanText
}

const STEP_TONE: Record<WorkflowStep["kind"], "violet" | "cyan" | "amber" | "emerald"> = {
  COPY: "violet",
  IMAGE: "cyan",
  VIDEO: "amber",
  OCR: "emerald"
}

interface StepStatus {
  stepIdx: number
  status: "pending" | "running" | "done" | "failed"
  output?: string
  error?: string
}

function StepCard({
  step,
  index,
  status
}: {
  step: WorkflowStep
  index: number
  status: StepStatus["status"]
}) {
  const Icon = STEP_ICONS[step.kind]
  const tone = STEP_TONE[step.kind]

  return (
    <div
      className={cn(
        "flex items-center gap-3 rounded-xl border px-4 py-3 transition-colors",
        "border-foreground/[0.08] bg-foreground/[0.02]"
      )}
    >
      {/* Step 序号 */}
      <div
        className={cn(
          "flex size-8 shrink-0 items-center justify-center rounded-full font-medium text-xs",
          status === "done"
            ? "bg-emerald-400/20 text-emerald-300"
            : status === "running"
              ? "bg-violet-400/20 text-violet-300"
              : status === "failed"
                ? "bg-rose-400/20 text-rose-300"
                : "bg-foreground/[0.06] text-muted-foreground"
        )}
      >
        {status === "done" ? (
          <CheckCircle2 className="size-4" />
        ) : status === "running" ? (
          <Loader2 className="size-4 animate-spin" />
        ) : (
          index + 1
        )}
      </div>

      {/* 内容 */}
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <Icon className="size-3.5 opacity-70" />
          <span className="font-medium text-sm">{step.label}</span>
          <NeonChip tone={tone} size="sm">
            {step.kind}
          </NeonChip>
        </div>
        <p className="mt-0.5 text-muted-foreground text-xs">
          {step.skill && `技能: ${step.skill}`}
          {step.model && `模型: ${step.model}`}
          {step.duration && ` · ${step.duration}s`}
          {step.aspect && ` · ${step.aspect}`}
          {step.count && step.count > 1 && ` · ×${step.count}`}
          {step.promptFrom && ` · 接收 ${step.promptFrom} 输出`}
        </p>
      </div>
    </div>
  )
}

export default function StudioPipelineDetailPage() {
  const params = useParams<{ id: string }>()
  const id = Number(params.id)

  const { data: template, isLoading } = useWorkflowTemplate(id)
  const incrementRun = useIncrementRunCount()

  const [input, setInput] = useState("")
  const [running, setRunning] = useState(false)
  const [statuses, setStatuses] = useState<StepStatus[]>([])

  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  if (!template) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-6">
        <Workflow className="size-10 opacity-30" />
        <p className="text-muted-foreground text-sm">模板不存在或已删除</p>
        <Link href="/studio/create/pipeline">
          <GlowButton tone="ghost" size="sm">
            返回模板列表
          </GlowButton>
        </Link>
      </div>
    )
  }

  const steps = template.templateConfig?.steps ?? []
  const firstInputKey = steps.find((s) => s.inputKey)?.inputKey ?? "topic"

  const handleRun = async () => {
    if (!input.trim()) {
      toast.error("请输入起始内容")
      return
    }
    setRunning(true)
    setStatuses(steps.map((_, i) => ({ stepIdx: i, status: "pending" })))

    try {
      // 增加 run 计数
      await incrementRun.mutateAsync(id)

      // 模拟串行执行（每步停 1.2s 演示进度）
      // 实际生产：每步调用对应 AIGC mutation，等待 SSE 完成后传递 output 到下一步
      for (let i = 0; i < steps.length; i++) {
        setStatuses((prev) => prev.map((s) => (s.stepIdx === i ? { ...s, status: "running" } : s)))
        await new Promise((r) => setTimeout(r, 1200))
        setStatuses((prev) => prev.map((s) => (s.stepIdx === i ? { ...s, status: "done" } : s)))
      }

      toast.success(`流水线完成：「${template.name}」 ${steps.length} 步全部执行`)
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "执行失败")
    } finally {
      setRunning(false)
    }
  }

  return (
    <div className="relative">
      <SectionHaze variant="violet" />
      <div className="mx-auto max-w-3xl space-y-5 p-6">
        {/* 头 */}
        <header className="space-y-2">
          <Link
            href="/studio/create/pipeline"
            className="inline-flex items-center gap-1.5 text-muted-foreground text-xs transition-colors hover:text-foreground"
          >
            <ArrowLeft className="size-3.5" />
            返回模板列表
          </Link>
          <div className="flex items-start justify-between">
            <div className="space-y-1">
              <h1 className="font-semibold text-xl">{template.name}</h1>
              {template.description && (
                <p className="text-muted-foreground text-sm">{template.description}</p>
              )}
            </div>
            <NeonChip tone="violet" size="sm">
              {steps.length} 步
            </NeonChip>
          </div>
        </header>

        {/* 起始输入 */}
        <GlassCard glow="violet">
          <div className="space-y-3 p-5">
            <Label htmlFor="pipeline-input" className="text-xs">
              起始输入（{firstInputKey}）
            </Label>
            <Textarea
              id="pipeline-input"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="输入主题、产品名、关键词..."
              className="min-h-[80px] resize-none border-foreground/[0.08] bg-foreground/[0.02]"
              disabled={running}
            />
            <GlowButton
              tone="violet"
              className="w-full"
              onClick={handleRun}
              disabled={running || !input.trim()}
            >
              {running ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <PlayCircle className="size-4" />
              )}
              {running ? "正在运行..." : "一键运行流水线"}
            </GlowButton>
          </div>
        </GlassCard>

        {/* 步骤进度 */}
        <div className="space-y-2">
          <p className="text-muted-foreground text-xs">流程</p>
          {steps.map((step, idx) => (
            <StepCard
              key={`step-${idx}`}
              step={step}
              index={idx}
              status={statuses[idx]?.status ?? "pending"}
            />
          ))}
        </div>

        {/* 说明 */}
        <div className="rounded-lg border border-amber-400/20 bg-amber-400/[0.04] p-4 text-amber-200/90 text-xs leading-relaxed">
          <strong className="text-amber-100">v0.2.1 说明：</strong>
          流水线运行器当前为前端协调模式（演示串行进度）。完整后端 worker（带 SSE 进度推送 +
          中间产物自动保存到资产库）将在 v0.3 实施。如需立即生产使用，可直接进入「创作 /
          项目工作台」逐步操作。
        </div>
      </div>
    </div>
  )
}
