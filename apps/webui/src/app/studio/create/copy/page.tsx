/**
 * 创作-文案 智能体技能矩阵
 *
 * 左：技能卡列表；右：点击技能后展开编辑区（inline，无弹窗）
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  BarChart3,
  Briefcase,
  Check,
  FileText,
  Hash,
  Heart,
  Mic,
  Save,
  Target,
  Video,
  Wand2
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { ModelSelector } from "@/components/common/ModelSelector"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { TRANSLATE_OPTIONS } from "@/features/aigc/copywriting/constants"
import { CopywritingReferenceImages } from "@/features/aigc/copywriting/CopywritingReferenceImages"
import { useCopywriting } from "@/features/aigc/copywriting/use-copywriting"
import { StreamingEditor } from "@/features/rich-text-editor"
import { useAigcStore } from "@/features/aigc/store"
import { AnimateBorder } from "@/components/animate/animate-border"
import { GlassCard, NeonChip } from "@/components/studio"
import { cn } from "@/lib/utils/index"
import { type AiSkillVO, useAiSkills } from "@/lib/queries/use-ai-skills"

const ICON_MAP: Record<string, React.FC<{ className?: string }>> = {
  voiceover: Mic,
  redbook: Heart,
  "product-copy": Briefcase,
  "ip-position": Target,
  "short-script": Video,
  "title-topic": Hash,
  "biz-analysis": BarChart3
}

const TONE_MAP: Record<string, "violet" | "cyan" | "emerald" | "amber" | "rose"> = {
  voiceover: "violet",
  redbook: "rose",
  "product-copy": "amber",
  "ip-position": "violet",
  "short-script": "cyan",
  "title-topic": "emerald",
  "biz-analysis": "cyan"
}

const HOT_CODES = new Set(["voiceover", "redbook"])

function SkillItem({
  skill,
  active,
  onSelect
}: {
  skill: AiSkillVO
  active: boolean
  onSelect: (code: string) => void
}) {
  const code = skill.code ?? ""
  const Icon = ICON_MAP[code] ?? Wand2
  const tone = TONE_MAP[code] ?? "violet"

  const card = (
    <GlassCard
      glow={active ? "violet" : "none"}
      interactive
      className={cn(
        "h-full transition-all",
        active ? "bg-primary/10" : "hover:ring-1 hover:ring-foreground/20"
      )}
    >
      <div className="flex items-center gap-3 p-4">
        <div className={`flex size-9 shrink-0 items-center justify-center rounded-xl bg-foreground/[0.04] text-${tone}-300`}>
          <Icon className="size-4" />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            <p className="truncate font-medium text-sm">{skill.name}</p>
            {HOT_CODES.has(code) && <NeonChip tone="rose" size="sm">热</NeonChip>}
          </div>
          <p className="text-muted-foreground text-xs leading-4">{skill.description ?? ""}</p>
        </div>
      </div>
    </GlassCard>
  )

  return (
    <button
      type="button"
      onClick={() => onSelect(code)}
      className="w-full text-left focus-visible:outline-none"
    >
      {active ? (
        <AnimateBorder rounded="xl" borderWidth={1} className="w-full">
          {card}
        </AnimateBorder>
      ) : (
        card
      )}
    </button>
  )
}

function SkillSkeleton() {
  return (
    <div className="rounded-2xl bg-card p-4">
      <div className="flex items-center gap-3">
        <Skeleton className="size-9 shrink-0 rounded-xl" />
        <div className="flex-1">
          <Skeleton className="mb-1.5 h-3.5 w-20" />
          <Skeleton className="h-3 w-full" />
        </div>
      </div>
    </div>
  )
}

/** 精简参数栏：只保留模型 + 长度 + 翻译 */
function SkillParamsBar() {
  const length = useAigcStore((s) => s.copywritingLength)
  const setLength = useAigcStore((s) => s.setCopywritingLength)
  const translateTo = useAigcStore((s) => s.copywritingTranslateTo)
  const setTranslateTo = useAigcStore((s) => s.setCopywritingTranslateTo)
  const model = useAigcStore((s) => s.copywritingModel)
  const setModel = useAigcStore((s) => s.setCopywritingModel)
  const { options, modelId, setModelId } = useModelSelector("CHAT", {
    value: model,
    onChange: (id) => setModel(id)
  })
  return (
    <div className="flex flex-wrap items-center gap-2">
      <ModelSelector variant="select" options={options} value={modelId} onChange={setModelId} />
      <Select value={length} onValueChange={(v) => setLength(v as "short" | "medium" | "long")}>
        <SelectTrigger className="h-8 w-[110px] text-xs">
          <span className="shrink-0 text-muted-foreground">长度</span>
          <span>{{ short: "短篇", medium: "中篇", long: "长篇" }[length]}</span>
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="short">短篇（≤200字）</SelectItem>
          <SelectItem value="medium">中篇（200-500字）</SelectItem>
          <SelectItem value="long">长篇（≤3000字）</SelectItem>
        </SelectContent>
      </Select>
      <Select value={translateTo} onValueChange={(v) => setTranslateTo(v ?? "")}>
        <SelectTrigger className="h-8 w-[110px] text-xs">
          <span className="shrink-0 text-muted-foreground">翻译</span>
          <span>{TRANSLATE_OPTIONS.find((o) => o.value === translateTo)?.label ?? "不翻译"}</span>
        </SelectTrigger>
        <SelectContent>
          {TRANSLATE_OPTIONS.map((o) => (
            <SelectItem key={o.value} value={o.value}>{o.label}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

/** 内联编辑区——复用 useCopywriting 逻辑，无弹窗容器 */
function CopywritingWorkspace({ skillName }: { skillName: string }) {
  const {
    content,
    setContent,
    generating,
    saved,
    streamingEditorRef,
    createDoc,
    handleSaveDoc,
    handleGenerate
  } = useCopywriting()

  return (
    <div className="flex h-full flex-col gap-3">
      {/* 顶部：技能名 + 保存按钮 */}
      <div className="flex items-center justify-between">
        <h2 className="font-medium text-sm">{skillName}</h2>
        <Button
          variant="outline"
          size="xs"
          className="gap-1"
          disabled={saved || createDoc.isPending || !content.trim()}
          onClick={handleSaveDoc}
        >
          {saved ? <Check className="size-3" /> : <Save className="size-3" />}
          {saved ? "已保存" : "保存文档"}
        </Button>
      </div>

      <CopywritingReferenceImages />

      <div className="relative min-h-[160px] flex-1 overflow-hidden rounded-md border">
        <StreamingEditor
          ref={streamingEditorRef}
          value={content}
          onChange={setContent}
          placeholder="输入主题或关键词，或粘贴需要改写的文案..."
          preset="minimal"
          className="relative h-full"
        />
      </div>

      <div className="shrink-0 border-t pt-3">
        <div className="flex flex-wrap items-center justify-center gap-2">
          <SkillParamsBar />
          <Separator orientation="vertical" className="h-5" />
          <Button
            size="sm"
            disabled={generating}
            onClick={handleGenerate}
            className="h-8 gap-1 bg-gradient-to-r from-emerald-500 to-teal-500 text-white text-xs hover:from-emerald-600 hover:to-teal-600"
          >
            <FileText className="size-3" />
            {generating ? "生成中..." : "生成"}
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function StudioCreateCopyPage() {
  const { data: skills, isLoading } = useAiSkills({ category: "COPYWRITING", activeOnly: true })
  const sorted = skills ? [...skills].sort((a, b) => b.priority - a.priority) : []

  const type = useAigcStore((s) => s.copywritingType)
  const setType = useAigcStore((s) => s.setCopywritingType)
  const setContent = useAigcStore((s) => s.setCopywritingContent)

  const selectedSkill = sorted.find((s) => s.code === type)

  const handleSelect = (code: string) => {
    if (type === code) return
    setType(code)
    setContent("")
  }

  return (
    <div className="flex h-full gap-0">
      {/* 左：技能列表 */}
      <aside className="flex w-72 shrink-0 flex-col gap-2 overflow-y-auto border-r p-4">
        <div className="mb-2 flex items-center gap-2">
          <Wand2 className="size-4 text-violet-400" />
          <h1 className="font-semibold text-sm">智能体文案</h1>
        </div>
        {isLoading
          ? Array.from({ length: 6 }).map((_, i) => <SkillSkeleton key={i} />)
          : sorted.map((skill) => (
              <SkillItem
                key={skill.id}
                skill={skill}
                active={type === (skill.code ?? "")}
                onSelect={handleSelect}
              />
            ))}
      </aside>

      {/* 右：编辑区 */}
      <main className="flex min-w-0 flex-1 flex-col p-6">
        {selectedSkill ? (
          <CopywritingWorkspace skillName={selectedSkill.name} />
        ) : (
          <div className="flex h-full flex-col items-center justify-center gap-3 text-muted-foreground">
            <Wand2 className="size-10 opacity-20" />
            <p className="text-sm">选择左侧智能体开始创作</p>
          </div>
        )}
      </main>
    </div>
  )
}
