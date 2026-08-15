/**
 * 创作-文案 智能体技能矩阵。
 *
 * 左：我的/公共技能目录；右：点击技能后展开编辑区（inline，无弹窗）。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean, useTabs } from "@aaf/hooks"
import {
  BarChart3,
  Briefcase,
  Check,
  FileText,
  Hash,
  Heart,
  Mic,
  Pencil,
  Plus,
  Save,
  Target,
  Video,
  Wand2
} from "lucide-react"
import { useSearchParams } from "next/navigation"
import { useEffect, useMemo, useState } from "react"

import { LottieIcon } from "@/components/animate"
import { AnimateBorder } from "@/components/animate/animate-border"
import { ModelSelector } from "@/components/common/ModelSelector"
import { GlassCard, NeonChip } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { CopywritingReferenceImages } from "@/features/aigc/copywriting/CopywritingReferenceImages"
import { TRANSLATE_OPTIONS } from "@/features/aigc/copywriting/constants"
import { useCopywriting } from "@/features/aigc/copywriting/use-copywriting"
import { SkillEditorDialog } from "@/features/aigc/skills/SkillEditorDialog"
import { useAigcStore } from "@/features/aigc/store"
import { StreamingEditor } from "@/features/rich-text-editor"
import { type AiSkillVO, useAiSkillMeta, useMyAiSkills, usePublicAiSkills } from "@/lib/api/rest/ai"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { cn } from "@/lib/utils/index"

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

type SkillDirectoryView = "MINE" | "PUBLIC"

function sourceLabel(skill: AiSkillVO): string {
  return skill.ownerId === null ? "内置" : "工作区"
}

function SkillItem({
  skill,
  active,
  showSource,
  onSelect,
  onEdit
}: {
  skill: AiSkillVO
  active: boolean
  showSource: boolean
  onSelect: (code: string) => void
  onEdit?: () => void
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
        <div
          className={`flex size-9 shrink-0 items-center justify-center rounded-xl bg-foreground/[0.04] text-${tone}-300`}
        >
          <Icon />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <p className="truncate font-medium text-sm">{skill.name}</p>
            {HOT_CODES.has(code) ? (
              <NeonChip tone="rose" size="sm">
                热
              </NeonChip>
            ) : null}
            {showSource ? <Badge variant="secondary">{sourceLabel(skill)}</Badge> : null}
          </div>
          <p className="text-muted-foreground text-xs leading-4">{skill.description ?? ""}</p>
        </div>
      </div>
    </GlassCard>
  )

  return (
    <div className="relative">
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
      {onEdit ? (
        <Button
          variant="ghost"
          size="icon-sm"
          className="absolute top-4 right-4"
          onClick={onEdit}
          aria-label={`编辑${skill.name}`}
        >
          <Pencil />
        </Button>
      ) : null}
    </div>
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

/** 精简参数栏：只保留模型 + 长度 + 翻译。 */
function SkillParamsBar() {
  const length = useAigcStore((state) => state.copywritingLength)
  const setLength = useAigcStore((state) => state.setCopywritingLength)
  const translateTo = useAigcStore((state) => state.copywritingTranslateTo)
  const setTranslateTo = useAigcStore((state) => state.setCopywritingTranslateTo)
  const model = useAigcStore((state) => state.copywritingModel)
  const setModel = useAigcStore((state) => state.setCopywritingModel)
  const { options, modelId, setModelId } = useModelSelector("CHAT", {
    value: model,
    onChange: (id) => setModel(id)
  })
  return (
    <div className="flex flex-wrap items-center gap-2">
      <ModelSelector variant="select" options={options} value={modelId} onChange={setModelId} />
      <Select
        value={length}
        onValueChange={(value) => setLength(value as "short" | "medium" | "long")}
      >
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
      <Select value={translateTo} onValueChange={(value) => setTranslateTo(value ?? "")}>
        <SelectTrigger className="h-8 w-[110px] text-xs">
          <span className="shrink-0 text-muted-foreground">翻译</span>
          <span>
            {TRANSLATE_OPTIONS.find((option) => option.value === translateTo)?.label ?? "不翻译"}
          </span>
        </SelectTrigger>
        <SelectContent>
          {TRANSLATE_OPTIONS.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

/** 内联编辑区——复用 useCopywriting 逻辑，无弹窗容器。 */
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
      <div className="flex items-center justify-between">
        <h2 className="font-medium text-sm">{skillName}</h2>
        <Button
          variant="outline"
          size="xs"
          className="gap-1"
          disabled={saved || createDoc.isPending || !content.trim()}
          onClick={handleSaveDoc}
        >
          {saved ? <Check /> : <Save />}
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
            className="h-8 gap-1 bg-linear-to-r from-emerald-500 to-teal-500 text-white text-xs hover:from-emerald-600 hover:to-teal-600"
          >
            <FileText />
            {generating ? "生成中..." : "生成"}
          </Button>
        </div>
      </div>
    </div>
  )
}

export default function StudioCreateCopyPage() {
  const directory = useTabs("MINE")
  const editDialog = useBoolean()
  const [editTarget, setEditTarget] = useState<AiSkillVO | null>(null)
  const queryParams = {
    category: "COPYWRITING",
    activeOnly: true,
    pageNo: 1,
    pageSize: 100
  }
  const mineQuery = useMyAiSkills(queryParams, directory.value === "MINE")
  const publicQuery = usePublicAiSkills(queryParams, directory.value === "PUBLIC")
  const metaQuery = useAiSkillMeta()
  const activeQuery = directory.value === "PUBLIC" ? publicQuery : mineQuery
  const operations = useMemo(
    () => new Set(metaQuery.data?.operations ?? []),
    [metaQuery.data?.operations]
  )
  const canCreate = directory.value === "MINE" && operations.has("create")
  const canUpdate = operations.has("update")
  const sorted = useMemo(
    () => [...(activeQuery.data?.list ?? [])].sort((left, right) => right.priority - left.priority),
    [activeQuery.data?.list]
  )

  const type = useAigcStore((state) => state.copywritingType)
  const setType = useAigcStore((state) => state.setCopywritingType)
  const setContent = useAigcStore((state) => state.setCopywritingContent)
  const searchParams = useSearchParams()

  useEffect(() => {
    const skillCode = searchParams.get("skillCode")
    if (skillCode && sorted.find((skill) => skill.code === skillCode)) {
      setType(skillCode)
    }
    const stored = sessionStorage.getItem("aaf:launcher:prompt")
    const topic = stored ?? searchParams.get("topic")
    if (stored) sessionStorage.removeItem("aaf:launcher:prompt")
    const notes = searchParams.get("notes")
    if (!topic) return
    const prefill = notes ? `${topic}\n\n创作建议：${notes}` : topic
    setContent(prefill)
    if (!type && !skillCode && sorted.length > 0) {
      setType(sorted[0].code ?? "")
    }
  }, [searchParams, setContent, setType, sorted, type])

  const selectedSkill = sorted.find((skill) => skill.code === type)

  function handleSelect(code: string) {
    if (type === code) return
    setType(code)
    setContent("")
  }

  function openCreate() {
    if (!canCreate) return
    setEditTarget(null)
    editDialog.onTrue()
  }

  function openEdit(skill: AiSkillVO) {
    if (!canUpdate || !skill.ownedByCurrentUser) return
    setEditTarget(skill)
    editDialog.onTrue()
  }

  function handleSaved(skill: AiSkillVO) {
    if (skill.code) setType(skill.code)
  }

  const directoryView = directory.value as SkillDirectoryView

  return (
    <div className="flex h-full gap-0">
      <aside className="flex w-72 shrink-0 flex-col gap-2 overflow-y-auto border-r p-4">
        <div className="mb-1 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Wand2 className="text-violet-400" />
            <h1 className="font-semibold text-sm">文案生成</h1>
          </div>
          {canCreate ? (
            <Button variant="ghost" size="icon-sm" onClick={openCreate} aria-label="新建技能">
              <Plus />
            </Button>
          ) : null}
        </div>

        <Tabs value={directory.value} onValueChange={directory.onChange}>
          <TabsList className="w-full">
            <TabsTrigger value="MINE">我的</TabsTrigger>
            <TabsTrigger value="PUBLIC">公共</TabsTrigger>
          </TabsList>
        </Tabs>

        {activeQuery.isLoading ? (
          ["one", "two", "three", "four", "five", "six"].map((key) => <SkillSkeleton key={key} />)
        ) : sorted.length === 0 ? (
          <Empty className="min-h-64">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <Wand2 />
              </EmptyMedia>
              <EmptyTitle>
                {directoryView === "MINE" ? "还没有文案技能" : "暂无公共文案技能"}
              </EmptyTitle>
              <EmptyDescription>
                {directoryView === "MINE"
                  ? "创建技能后即可在此选择并开始创作。"
                  : "公共目录包含平台内置和工作区公开技能。"}
              </EmptyDescription>
            </EmptyHeader>
            {canCreate ? (
              <EmptyContent>
                <Button size="sm" onClick={openCreate}>
                  <Plus data-icon="inline-start" />
                  新建技能
                </Button>
              </EmptyContent>
            ) : null}
          </Empty>
        ) : (
          sorted.map((skill) => (
            <SkillItem
              key={skill.id}
              skill={skill}
              active={type === (skill.code ?? "")}
              showSource={directoryView === "PUBLIC"}
              onSelect={handleSelect}
              onEdit={
                directoryView === "MINE" && canUpdate && skill.ownedByCurrentUser
                  ? () => openEdit(skill)
                  : undefined
              }
            />
          ))
        )}
      </aside>

      <main className="flex min-w-0 flex-1 flex-col p-6">
        {selectedSkill ? (
          <CopywritingWorkspace skillName={selectedSkill.name} />
        ) : (
          <div className="mb-20 flex h-full flex-col items-center justify-center gap-3 text-muted-foreground">
            <LottieIcon name="cat" width={180} height={180} loop />
            <p className="text-sm">选择左侧智能体开始创作</p>
          </div>
        )}
      </main>

      <SkillEditorDialog
        open={editDialog.value}
        onOpenChange={editDialog.setValue}
        initial={editTarget}
        defaultCategory="COPYWRITING"
        onSaved={handleSaved}
      />
    </div>
  )
}
