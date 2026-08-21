/**
 * 创作-文案 智能体技能矩阵。
 *
 * 左：我的/公共技能目录；右：点击技能后展开编辑区（inline，无弹窗）。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  BarChart3,
  Briefcase,
  Check,
  ChevronDown,
  Database,
  FileText,
  Hash,
  Heart,
  Loader2,
  Mic,
  Pencil,
  RefreshCw,
  ShieldCheck,
  Target,
  Video,
  Wand2,
  Wrench,
  X
} from "lucide-react"
import { useSearchParams } from "next/navigation"
import { useEffect, useRef, useState } from "react"

import { LottieIcon } from "@/components/animate"
import { AnimateBorder } from "@/components/animate/animate-border"
import { ModelSelector } from "@/components/common/ModelSelector"
import { GlassCard, NeonChip } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { Separator } from "@/components/ui/separator"
import { Skeleton } from "@/components/ui/skeleton"
import { Textarea } from "@/components/ui/textarea"
import { CopywritingReferenceImages } from "@/features/aigc/copywriting/CopywritingReferenceImages"
import { COPYWRITING_ROLE_KEY, useCopywriting } from "@/features/aigc/copywriting/use-copywriting"
import { useAigcStore } from "@/features/aigc/store"
import { StreamingEditor } from "@/features/rich-text-editor"
import type { AssistantExecutionPhase } from "@/lib/api/assistant-agui"
import { type AiSkillVO, COPYWRITING_SKILL_CATEGORY, useAiSkills } from "@/lib/api/rest/ai"
import { DictType } from "@/lib/constants/dict-type"
import { useDict } from "@/lib/hooks/use-dict"
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
const EMPTY_SKILLS: AiSkillVO[] = []

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
          <p className="text-muted-foreground text-xs leading-4">{skill.summary}</p>
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
  const noSelectionValue = "__none__"
  const length = useAigcStore((state) => state.copywritingLength)
  const setLength = useAigcStore((state) => state.setCopywritingLength)
  const translateTo = useAigcStore((state) => state.copywritingTranslateTo)
  const setTranslateTo = useAigcStore((state) => state.setCopywritingTranslateTo)
  const model = useAigcStore((state) => state.copywritingModel)
  const setModel = useAigcStore((state) => state.setCopywritingModel)
  const { options: localeOptions, getLabel: getLocaleLabel } = useDict(
    DictType.Ai.COPYWRITING_OUTPUT_LOCALE
  )
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
      <Select
        value={translateTo ?? noSelectionValue}
        onValueChange={(value) => setTranslateTo(value === noSelectionValue ? null : value)}
      >
        <SelectTrigger className="h-8 w-[110px] text-xs">
          <span className="shrink-0 text-muted-foreground">翻译</span>
          <span>{translateTo === null ? "不翻译" : getLocaleLabel(translateTo)}</span>
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={noSelectionValue}>不翻译</SelectItem>
          {localeOptions.map((option) => (
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
function ExecutionPhaseBadge({ phase }: { phase: AssistantExecutionPhase }) {
  const label = {
    idle: "待开始",
    running: "执行中",
    awaiting_authorization: "等待授权",
    paused: "已暂停",
    success: "已完成",
    error: "失败"
  }[phase]
  const variant =
    phase === "error"
      ? "destructive"
      : phase === "success"
        ? "default"
        : phase === "running" || phase === "awaiting_authorization"
          ? "secondary"
          : "outline"
  return <Badge variant={variant}>{label}</Badge>
}

function CopywritingWorkspace({ skillName }: { skillName: string }) {
  const {
    prompt,
    setPrompt,
    content,
    setContent,
    generating,
    documentId,
    phase,
    pendingApproval,
    approvalReady,
    approvalLoading,
    handleApprovalDecision,
    processEntries,
    contextSources,
    toolCalls,
    selectedSkillKey,
    selectedModelId,
    selectedMaterials,
    streamingEditorRef,
    handleGenerate,
    handleRewrite
  } = useCopywriting()
  const [processOpen, setProcessOpen] = useState(false)
  const latestProcessEntry = processEntries[processEntries.length - 1]
  const currentProcessSummary =
    latestProcessEntry?.summary ??
    {
      idle: "尚未开始执行",
      running: "正在准备 Assistant 执行",
      awaiting_authorization: "执行已暂停，等待确认受控工具操作",
      paused: "执行保持暂停，可调整方案后重新发起",
      success: "文案生成已完成",
      error: "执行未完成，请展开查看详情"
    }[phase]

  return (
    <div className="grid min-h-full grid-cols-1 gap-4 xl:h-full xl:min-h-0 xl:grid-cols-2">
      <div className="flex min-w-0 flex-col gap-4">
        <Card className="min-w-0">
          <CardHeader>
            <CardTitle>创作主题与要求</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <CopywritingReferenceImages />
            <div className="flex flex-col gap-2">
              <Textarea
                id="copywriting-prompt"
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                placeholder="例如：为新品咖啡机撰写一篇面向年轻上班族的小红书种草文案……"
                className="min-h-36 resize-y"
              />
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <SkillParamsBar />
              <Button
                size="sm"
                disabled={generating || !prompt.trim()}
                onClick={handleGenerate}
                className="ml-auto shrink-0 bg-linear-to-r from-emerald-500 to-teal-500 text-white hover:from-emerald-600 hover:to-teal-600"
              >
                {generating ? (
                  <Loader2 data-icon="inline-start" className="animate-spin" />
                ) : (
                  <FileText data-icon="inline-start" />
                )}
                {generating ? "生成中..." : "生成"}
              </Button>
            </div>
          </CardContent>
        </Card>

        {pendingApproval ? (
          <Card className="border-amber-500/40 bg-amber-500/5">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <ShieldCheck className="size-4 text-amber-600" />
                需要确认受控工具操作
              </CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <p className="text-muted-foreground text-sm">
                {approvalReady
                  ? "Assistant 已安全暂停。批准后将从持久恢复点继续执行，不会提升其他工具权限。"
                  : "Assistant 正在保存暂停点，请稍候再确认工具操作。"}
              </p>
              <div className="flex flex-wrap gap-2">
                <Badge variant="secondary">工具：{pendingApproval.toolName}</Badge>
                <Badge variant="outline">
                  {pendingApproval.reversible ? "操作可撤销" : "操作不可自动撤销"}
                </Badge>
              </div>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  disabled={!approvalReady || approvalLoading}
                  onClick={() => void handleApprovalDecision("APPROVED")}
                >
                  {approvalLoading ? (
                    <Loader2 data-icon="inline-start" className="animate-spin" />
                  ) : (
                    <Check data-icon="inline-start" />
                  )}
                  批准并继续
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  disabled={!approvalReady || approvalLoading}
                  onClick={() => void handleApprovalDecision("REJECTED")}
                >
                  <X data-icon="inline-start" />
                  拒绝
                </Button>
              </div>
            </CardContent>
          </Card>
        ) : null}

        <Collapsible open={processOpen} onOpenChange={setProcessOpen}>
          <Card className="min-w-0">
            <CardHeader className="p-0">
              <CollapsibleTrigger
                render={
                  <button
                    type="button"
                    className="flex w-full items-center gap-3 px-6 py-4 text-left"
                  />
                }
              >
                <span className="flex min-w-0 flex-1 items-center gap-3">
                  <span className="shrink-0 font-semibold">执行过程</span>
                  <ExecutionPhaseBadge phase={phase} />
                  <span
                    className="truncate text-muted-foreground text-sm"
                    title={currentProcessSummary}
                  >
                    {currentProcessSummary}
                  </span>
                </span>
                <ChevronDown
                  aria-hidden="true"
                  className={cn(
                    "size-4 shrink-0 transition-transform",
                    processOpen && "rotate-180"
                  )}
                />
              </CollapsibleTrigger>
            </CardHeader>
            <CollapsibleContent>
              <CardContent className="flex flex-col gap-4 border-t pt-4">
                <div className="flex flex-wrap gap-2">
                  <Badge variant="outline">技能：{skillName || selectedSkillKey}</Badge>
                  <Badge variant="outline">模型：{selectedModelId ?? "自动选择"}</Badge>
                  <Badge variant="outline">材料：{selectedMaterials.length}</Badge>
                  <Badge variant="outline">上下文：{contextSources.length}</Badge>
                </div>

                <ScrollArea className="h-52 rounded-md border">
                  {processEntries.length === 0 ? (
                    <div className="flex h-full flex-col items-center justify-center gap-2 p-6 text-center text-muted-foreground">
                      <ShieldCheck />
                      <p className="text-sm">提交生成后，这里会显示可审计的安全执行阶段。</p>
                    </div>
                  ) : (
                    <ol className="flex flex-col gap-3 p-4">
                      {processEntries.map((entry) => (
                        <li key={entry.id} className="flex items-start gap-3 text-sm">
                          <Badge variant="secondary">{entry.status}</Badge>
                          <span className="min-w-0 flex-1 leading-6">{entry.summary}</span>
                        </li>
                      ))}
                    </ol>
                  )}
                </ScrollArea>

                <Separator />
                <div className="grid gap-3 sm:grid-cols-2">
                  <section
                    className="flex min-w-0 flex-col gap-2"
                    aria-labelledby="copy-context-title"
                  >
                    <h3
                      id="copy-context-title"
                      className="flex items-center gap-2 font-medium text-sm"
                    >
                      <Database />
                      知识与材料来源
                    </h3>
                    {contextSources.length === 0 && selectedMaterials.length === 0 ? (
                      <p className="text-muted-foreground text-xs">本次尚未装载外部来源</p>
                    ) : (
                      <ul className="flex flex-col gap-1 text-muted-foreground text-xs">
                        {contextSources.map((source) => (
                          <li key={source.key}>{source.label}</li>
                        ))}
                        {selectedMaterials.map((material) => (
                          <li key={material.id}>参考图：{material.label}</li>
                        ))}
                      </ul>
                    )}
                  </section>
                  <section
                    className="flex min-w-0 flex-col gap-2"
                    aria-labelledby="copy-tools-title"
                  >
                    <h3
                      id="copy-tools-title"
                      className="flex items-center gap-2 font-medium text-sm"
                    >
                      <Wrench />
                      工具调用
                    </h3>
                    {toolCalls.length === 0 ? (
                      <p className="text-muted-foreground text-xs">本次尚未调用受控工具</p>
                    ) : (
                      <ul className="flex flex-col gap-1 text-muted-foreground text-xs">
                        {toolCalls.map((tool) => (
                          <li key={tool.id}>
                            {tool.name} · {tool.status}
                          </li>
                        ))}
                      </ul>
                    )}
                  </section>
                </div>
              </CardContent>
            </CollapsibleContent>
          </Card>
        </Collapsible>
      </div>

      <Card className="min-h-[28rem] min-w-0 xl:min-h-0">
        <CardHeader>
          <CardTitle>输出</CardTitle>
          <CardAction className="flex flex-wrap items-center justify-end gap-2">
            <ExecutionPhaseBadge phase={phase} />
            <Button
              variant="outline"
              size="xs"
              disabled={generating || !content.trim()}
              onClick={handleRewrite}
            >
              <RefreshCw data-icon="inline-start" />
              改写
            </Button>
            {documentId !== null ? <Badge variant="outline">数据库草稿 #{documentId}</Badge> : null}
          </CardAction>
        </CardHeader>
        <CardContent className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-80 flex-1 overflow-hidden rounded-md border xl:min-h-0">
            <StreamingEditor
              ref={streamingEditorRef}
              value={content}
              onChange={setContent}
              placeholder="生成结果会显示在这里。生成时的版本由 Assistant 保存为草稿；后续手动编辑仅保留在当前页面。"
              preset="minimal"
              className="relative h-full min-h-80 xl:min-h-0"
            />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

export default function StudioCreateCopyPage() {
  const launcherPrefillRef = useRef<string | null>(null)
  const queryParams = {
    categoryCode: COPYWRITING_SKILL_CATEGORY,
    publishedOnly: true,
    roleKey: COPYWRITING_ROLE_KEY
  }
  const skillsQuery = useAiSkills(queryParams, true)
  const skills = skillsQuery.data ?? EMPTY_SKILLS

  const type = useAigcStore((state) => state.copywritingType)
  const setType = useAigcStore((state) => state.setCopywritingType)
  const setPrompt = useAigcStore((state) => state.setCopywritingPrompt)
  const searchParams = useSearchParams()

  useEffect(() => {
    const requestedSkillCode = searchParams.get("skillCode")
    if (requestedSkillCode && skills.some((skill) => skill.code === requestedSkillCode)) {
      setType(requestedSkillCode)
    } else if (!skills.some((skill) => skill.code === type) && skills[0]) {
      setType(skills[0].code)
    }
    const stored = sessionStorage.getItem("aaf:launcher:prompt")
    const topic = stored ?? searchParams.get("topic")
    if (stored) sessionStorage.removeItem("aaf:launcher:prompt")
    const notes = searchParams.get("notes")
    if (!topic) return
    const prefill = notes ? `${topic}\n\n创作建议：${notes}` : topic
    if (launcherPrefillRef.current !== prefill) {
      launcherPrefillRef.current = prefill
      setPrompt(prefill)
    }
  }, [searchParams, setPrompt, setType, skills, type])

  const selectedSkill = skills.find((skill) => skill.code === type)

  function handleSelect(code: string) {
    if (type === code) return
    setType(code)
  }

  return (
    <div className="flex h-full min-h-0 flex-col gap-0 lg:flex-row">
      <aside className="flex max-h-72 w-full shrink-0 flex-col gap-2 overflow-y-auto border-b p-4 lg:max-h-none lg:w-72 lg:border-r lg:border-b-0">
        <div className="mb-1 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Wand2 className="text-violet-400" />
            <h1 className="font-semibold text-sm">文案生成</h1>
          </div>
        </div>

        {skillsQuery.isLoading ? (
          ["one", "two", "three", "four", "five", "six"].map((key) => <SkillSkeleton key={key} />)
        ) : skills.length === 0 ? (
          <Empty className="min-h-64">
            <EmptyHeader>
              <EmptyMedia variant="icon">
                <Wand2 />
              </EmptyMedia>
              <EmptyTitle>暂无可用文案技能</EmptyTitle>
              <EmptyDescription>
                管理员需发布 Skill、归入文案生成分类、声明草稿工具并挂入内容创作者 Role
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          skills.map((skill) => (
            <SkillItem
              key={skill.id}
              skill={skill}
              active={type === (skill.code ?? "")}
              showSource
              onSelect={handleSelect}
            />
          ))
        )}
      </aside>

      <main className="min-h-0 min-w-0 flex-1 overflow-y-auto p-4 lg:p-6">
        {selectedSkill ? (
          <CopywritingWorkspace skillName={selectedSkill.name} />
        ) : (
          <div className="mb-20 flex h-full flex-col items-center justify-center gap-3 text-muted-foreground">
            <LottieIcon name="cat" width={180} height={180} loop />
            <p className="text-sm">选择左侧智能体开始创作</p>
          </div>
        )}
      </main>
    </div>
  )
}
