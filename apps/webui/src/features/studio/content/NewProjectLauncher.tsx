/**
 * Content Studio 项目类型优先创建器。
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import {
  Check,
  ChevronRight,
  CircleAlert,
  ImageIcon,
  LoaderCircle,
  Plus,
  Sparkles,
  Star,
  Upload,
  X
} from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useCallback, useEffect, useId, useRef, useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { GlassCard, GlassCardBody, GlowButton } from "@/components/studio"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle
} from "@/components/ui/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Progress } from "@/components/ui/progress"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import {
  type AigcBlueprintSlotTemplate,
  type AigcBrandProfile,
  type AigcChannelCode,
  type AigcProjectBlueprint,
  type AigcProjectCreationProposalRef,
  type AigcProjectGenerationProposal,
  type AigcProjectInitializationField,
  type AigcProjectInitializationFieldKey,
  type AigcProjectType,
  type AigcProjectTypeCode,
  type AssistantBlueprintCreationContext,
  type AssistantBlueprintCreationDocument,
  type AssistantBlueprintProposalView,
  assistantBlueprintCreationContextFingerprint,
  useAigcBrandProfiles,
  useAigcChannelSpecs,
  useAigcProjectBlueprints,
  useAigcProjectCreationDraftProposals,
  useAigcProjectTypes,
  useApproveAndStartAigcProjectCreationDraftGeneration,
  useMaterializeAigcProject,
  useProposeAigcProjectCreationDraftGeneration,
  useReviseAigcProjectCreationDraftGenerationProposal
} from "@/lib/api/rest/ai/aigc"
import { useAssistants } from "@/lib/api/rest/ai/assistant"
import { useTaskDetails } from "@/lib/api/rest/ai/task"
import { useDocList, useDocument } from "@/lib/api/rest/system/document"
import { useEntityAccess } from "@/lib/api/rest/user/permission"
import { paths } from "@/lib/constants/paths"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { usePermissionGuard } from "@/lib/hooks/use-permission-guard"
import { AssistantBlueprintProposalPanel } from "./AssistantBlueprintProposalPanel"
import {
  type AigcInitializationResultEntry,
  hasAigcInitializationValue,
  type NewProjectFormValues,
  newProjectSchema,
  parseAigcInitializationResults
} from "./new-project-schema"
import { getChannelLabel, getObjectTypeConfig, getProjectTypeConfig } from "./project-type-config"

const CREATION_DRAFT_FIELD_KEYS = new Set<AigcProjectInitializationFieldKey>([
  "description",
  "background",
  "targetAudience",
  "requirements",
  "constraints",
  "keyFacts",
  "keyMessages",
  "styleGuidance",
  "successCriteria",
  "deliveryRequirements",
  "locale",
  "additionalContext"
])

const ASSISTANT_INITIALIZATION_FIELDS: AigcProjectInitializationField[] = [
  {
    key: "description",
    label: "项目目标",
    description: "说明项目希望解决的问题与最终目标。",
    valueType: "TEXTAREA",
    required: true,
    affectsStructure: true
  },
  {
    key: "targetAudience",
    label: "目标受众",
    description: "描述内容面向的人群、场景与核心诉求。",
    valueType: "TEXTAREA",
    affectsStructure: true
  },
  {
    key: "requirements",
    label: "内容要求",
    description: "每行一项，列出必须产出的内容或格式。",
    valueType: "STRING_LIST",
    required: true,
    affectsStructure: true
  },
  {
    key: "constraints",
    label: "约束",
    description: "每行一项，列出时间、合规、风格或资源限制。",
    valueType: "STRING_LIST",
    affectsStructure: true
  },
  {
    key: "successCriteria",
    label: "成功标准",
    description: "每行一项，说明如何判断项目交付完成。",
    valueType: "STRING_LIST",
    affectsStructure: true
  },
  {
    key: "additionalContext",
    label: "参考信息",
    description: "补充背景、素材说明或希望 Assistant 特别关注的信息。",
    valueType: "TEXTAREA",
    affectsStructure: true
  }
]

interface AppliedCreationProposal extends AigcProjectCreationProposalRef {
  targetKeys: AigcProjectInitializationFieldKey[]
}

interface ApprovalIntent {
  operationKey: string
  idempotencyKey: string
}

function proposalErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "操作失败，请稍后重试。"
}

function taskStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    DRAFT: "草稿",
    PLANNING: "规划中",
    READY: "待执行",
    RUNNING: "处理中",
    VERIFYING: "汇总中",
    AWAITING_AUTHORIZATION: "等待授权",
    AWAITING_CLARIFICATION: "等待补充信息",
    PAUSING: "暂停中",
    PAUSED: "已暂停",
    CANCELING: "取消中",
    COMPLETED: "已完成",
    CANCELED: "已取消",
    FAILED: "失败"
  }
  return labels[status] ?? status
}

interface BrandProfileSelectProps {
  profiles: AigcBrandProfile[]
  value?: number
  onChange: (value?: number) => void
  disabled?: boolean
}

export function BrandProfileSelect({
  profiles,
  value,
  onChange,
  disabled = false
}: BrandProfileSelectProps) {
  return (
    <Select
      value={value === undefined ? "none" : String(value)}
      onValueChange={(next) => onChange(next && next !== "none" ? Number(next) : undefined)}
      disabled={disabled}
    >
      <SelectTrigger className="w-full sm:w-72">
        <SelectValue>
          {profiles.find((profile) => profile.id === value)?.name ?? "选择已发布品牌 / IP 资料"}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          <SelectItem value="none">暂不选择</SelectItem>
          {profiles.map((profile) => (
            <SelectItem key={profile.id} value={String(profile.id)}>
              {profile.name}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}

interface ProjectTypePickerProps {
  types: AigcProjectType[]
  value?: AigcProjectTypeCode
  onChange: (value: AigcProjectTypeCode) => void
}

export function ProjectTypePicker({ types, value, onChange }: ProjectTypePickerProps) {
  return (
    <div className="flex flex-col gap-3">
      <div className="-mx-1 overflow-x-auto px-1 pb-2">
        <ToggleGroup
          value={value ? [value] : []}
          onValueChange={(values: string[]) => {
            const next = values.at(-1)
            if (next) onChange(next)
          }}
          variant="outline"
          className="flex w-max min-w-full justify-start gap-2"
        >
          {types.map((type) => {
            const config = getProjectTypeConfig(type)
            const Icon = config.icon
            return (
              <ToggleGroupItem
                key={type.code}
                value={type.code}
                aria-label={config.label}
                className="h-auto min-h-16 w-40 shrink-0 flex-col items-start gap-1.5 px-3 py-2.5 text-left"
              >
                <span className="flex w-full items-center justify-between">
                  <Icon />
                  {value === type.code ? <Check /> : null}
                </span>
                <span className="font-medium text-sm">{config.label}</span>
              </ToggleGroupItem>
            )
          })}
        </ToggleGroup>
      </div>
    </div>
  )
}

interface ChannelPickerProps {
  channels: AigcChannelCode[]
  value: AigcChannelCode[]
  onChange: (value: AigcChannelCode[]) => void
}

export function ChannelPicker({ channels, value, onChange }: ChannelPickerProps) {
  return (
    <ToggleGroup
      value={value}
      onValueChange={onChange}
      variant="outline"
      size="sm"
      className="flex w-full flex-wrap justify-start"
    >
      {channels.map((channel) => (
        <ToggleGroupItem key={channel} value={channel} aria-label={getChannelLabel(channel)}>
          {getChannelLabel(channel)}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

interface ProjectBlueprintPickerProps {
  blueprints: AigcProjectBlueprint[]
  value?: number
  onChange: (value: number) => void
}

function ProjectBlueprintPicker({ blueprints, value, onChange }: ProjectBlueprintPickerProps) {
  return (
    <div className="-mx-1 overflow-x-auto px-1 pb-2">
      <ToggleGroup
        value={value === undefined ? [] : [String(value)]}
        onValueChange={(values: string[]) => {
          const next = Number(values.at(-1))
          if (Number.isFinite(next) && next > 0) onChange(next)
        }}
        variant="outline"
        className="flex w-max min-w-full justify-start gap-2"
      >
        {blueprints.map((blueprint) => (
          <ToggleGroupItem
            key={blueprint.id}
            value={String(blueprint.id)}
            aria-label={`选择项目模板 ${blueprint.name}`}
            className="h-auto min-h-28 w-64 shrink-0 flex-col items-start gap-2 px-4 py-3 text-left"
          >
            <span className="flex w-full items-center justify-between gap-2">
              <span className="truncate font-medium text-sm">{blueprint.name}</span>
              {value === blueprint.id ? <Check /> : null}
            </span>
            <span className="line-clamp-2 text-muted-foreground text-xs leading-5">
              {blueprint.description || "已发布项目模板"}
            </span>
            <span className="text-muted-foreground text-xs">v{blueprint.blueprintVersion}</span>
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  )
}

function createCoverIdempotencyKey(): string {
  return `project-cover-${globalThis.crypto.randomUUID()}`
}

/** 去除 displayNamePattern 中的 %02d 等 printf 序号占位符，预览阶段不展示具体序号。 */
function stripSlotNamePattern(pattern: string): string {
  return pattern.replace(/%0?\d*d/g, "").trim()
}

interface BlueprintActionStep {
  actionKey: string
  targetTemplateKey: string
  dependencyTemplateKeys: string[]
}

/** 从蓝图 actionSpec（后端未强类型的 JSON）中防御性解析生成流程步骤。 */
function parseBlueprintActions(
  actionSpec: Record<string, unknown> | undefined
): BlueprintActionStep[] {
  const actions = actionSpec?.actions
  if (!Array.isArray(actions)) return []
  return actions.flatMap((item) => {
    if (typeof item !== "object" || item === null) return []
    const record = item as Record<string, unknown>
    const actionKey = record.actionKey
    const targetTemplateKey = record.targetTemplateKey
    if (typeof actionKey !== "string" || typeof targetTemplateKey !== "string") return []
    const dependencyTemplateKeys = Array.isArray(record.dependencyTemplateKeys)
      ? record.dependencyTemplateKeys.filter((key): key is string => typeof key === "string")
      : []
    return [{ actionKey, targetTemplateKey, dependencyTemplateKeys }]
  })
}

/** 按 parentTemplateKey 构建产物层级并渲染可折叠树形结构。 */
function SlotProductTree({ templates }: { templates: AigcBlueprintSlotTemplate[] }) {
  const sorted = [...templates].toSorted(
    (left, right) => (left.orderNo ?? 0) - (right.orderNo ?? 0)
  )
  const roots = sorted.filter((template) => !template.parentTemplateKey)
  const childrenByParent = new Map<string, AigcBlueprintSlotTemplate[]>()
  for (const template of sorted) {
    if (!template.parentTemplateKey) continue
    const list = childrenByParent.get(template.parentTemplateKey) ?? []
    list.push(template)
    childrenByParent.set(template.parentTemplateKey, list)
  }

  function renderNode(template: AigcBlueprintSlotTemplate) {
    const children = childrenByParent.get(template.templateKey) ?? []
    const config = getObjectTypeConfig(
      template.objectType as Parameters<typeof getObjectTypeConfig>[0]
    )
    const Icon = config?.icon ?? ImageIcon
    const isRequired = (template.defaultContractRole ?? "REQUIRED") === "REQUIRED"

    const row = (
      <div className="flex items-center gap-2 py-1.5">
        <Icon className="size-4 shrink-0 text-muted-foreground" />
        <span className="flex min-w-0 shrink items-center gap-1 truncate text-sm">
          {(template.displayNamePattern && stripSlotNamePattern(template.displayNamePattern)) ||
            config?.label ||
            template.templateKey}
          {isRequired ? (
            <Star className="size-3.5 shrink-0 fill-amber-400 text-amber-400" aria-label="必需" />
          ) : null}
        </span>
        <span className="ml-auto shrink-0 text-muted-foreground text-xs">
          ×{template.defaultCount}
        </span>
      </div>
    )

    if (children.length === 0) {
      return <li key={template.templateKey}>{row}</li>
    }

    return (
      <li key={template.templateKey}>
        <Collapsible defaultOpen>
          <CollapsibleTrigger className="flex w-full items-center gap-1 text-left [&[data-panel-open]_svg[data-tree-caret]]:rotate-90">
            <ChevronRight
              data-tree-caret
              className="size-3.5 shrink-0 text-muted-foreground transition-transform"
            />
            <div className="min-w-0 flex-1">{row}</div>
          </CollapsibleTrigger>
          <CollapsibleContent>
            <ul className="ml-5 flex flex-col border-muted-foreground/20 border-l pl-3">
              {children.map((child) => renderNode(child))}
            </ul>
          </CollapsibleContent>
        </Collapsible>
      </li>
    )
  }

  if (roots.length === 0) {
    return <p className="text-muted-foreground text-sm">该项目模板没有预设产物。</p>
  }

  return <ul className="flex flex-col">{roots.map((template) => renderNode(template))}</ul>
}

/** 按顺序渲染生成流程步骤条。 */
function BlueprintActionSteps({
  steps,
  slotLabelByKey
}: {
  steps: BlueprintActionStep[]
  slotLabelByKey: Map<string, string>
}) {
  if (steps.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-3 text-muted-foreground text-sm">
        该项目模板没有预设生成流程。
      </p>
    )
  }

  return (
    <ol className="flex flex-col">
      {steps.map((step, index) => (
        <li key={step.actionKey} className="flex gap-3">
          <div className="flex flex-col items-center">
            <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary/15 font-medium text-primary text-xs">
              {index + 1}
            </span>
            {index < steps.length - 1 ? (
              <span className="w-px flex-1 bg-muted-foreground/20" />
            ) : null}
          </div>
          <div className="min-w-0 flex-1 pb-4">
            <p className="text-sm">
              {slotLabelByKey.get(step.targetTemplateKey) ?? step.targetTemplateKey}
            </p>
            <p className="text-muted-foreground text-xs">
              {step.dependencyTemplateKeys.length > 0
                ? `依赖：${step.dependencyTemplateKeys
                    .map((key) => slotLabelByKey.get(key) ?? key)
                    .join("、")}`
                : "无前置依赖"}
            </p>
          </div>
        </li>
      ))}
    </ol>
  )
}

interface CreationDraftSuggestionPanelProps {
  blueprintVersionId: number
  fields: AigcProjectInitializationField[]
  /** 本批要生成的初始化字段；由调用方按单字段按钮或"补全空白字段"入口决定。 */
  targetKeys: AigcProjectInitializationFieldKey[]
  onApply: (values: AigcInitializationResultEntry[], proposal: AppliedCreationProposal) => void
  /** 退出本批 AI 建议；不影响已启动的 canonical Task。 */
  onCancel: () => void
}

function CreationDraftSuggestionPanel({
  blueprintVersionId,
  fields,
  targetKeys,
  onApply,
  onCancel
}: CreationDraftSuggestionPanelProps) {
  const instructionId = useId()
  const [draftId, setDraftId] = useState<string | null>(null)
  const [activeProposalId, setActiveProposalId] = useState<string | null>(null)
  const [instruction, setInstruction] = useState("")
  const [assistantId, setAssistantId] = useState("")
  const [approvalIntent, setApprovalIntent] = useState<ApprovalIntent | null>(null)
  const [error, setError] = useState<string | null>(null)
  const proposalsQuery = useAigcProjectCreationDraftProposals(draftId)
  const propose = useProposeAigcProjectCreationDraftGeneration()
  const revise = useReviseAigcProjectCreationDraftGenerationProposal()
  const approveAndStart = useApproveAndStartAigcProjectCreationDraftGeneration()
  const assistantsQuery = useAssistants()

  const targetKeySignature = [...targetKeys].sort().join(",")

  // biome-ignore lint/correctness/useExhaustiveDependencies: 本批字段集合变化即重置草稿状态，未提交的指令不跨批次保留
  useEffect(() => {
    setActiveProposalId(null)
    setInstruction("")
    setApprovalIntent(null)
    setError(null)
  }, [targetKeySignature])

  const proposalCandidates = [
    approveAndStart.data,
    revise.data,
    propose.data,
    ...(proposalsQuery.data ?? [])
  ].filter((proposal): proposal is AigcProjectGenerationProposal => proposal !== undefined)
  const activeProposal = proposalCandidates
    .filter((proposal) => proposal.proposalId === activeProposalId)
    .toSorted((left, right) => right.proposalVersion - left.proposalVersion)
    .at(0)
  const taskQuery = useTaskDetails(activeProposal?.linkedTaskId ?? null)
  const task = taskQuery.data
  const targetFields = targetKeys
    .map((key) => fields.find((field) => field.key === key))
    .filter((field): field is AigcProjectInitializationField => field !== undefined)
  /** 提案目标集合与本批字段集合是否逐一对应；顺序无关。 */
  const matchesTargetKeys = (proposal: AigcProjectGenerationProposal) =>
    proposal.items.length === targetKeys.length &&
    [...proposal.items.map((item) => item.targetKey)].sort().join(",") === targetKeySignature
  const isEditingCurrentProposal =
    activeProposal?.status === "AWAITING_CONFIRMATION" &&
    activeProposal.scopeKind === "CREATION_DRAFT" &&
    activeProposal.draftId === draftId &&
    activeProposal.blueprintVersionId === blueprintVersionId &&
    matchesTargetKeys(activeProposal)
  const instructionChanged =
    isEditingCurrentProposal &&
    activeProposal.items.some((item) => item.generationInstruction.trim() !== instruction.trim())
  const proposalPending = propose.isPending || revise.isPending

  function acceptProposal(proposal: AigcProjectGenerationProposal) {
    if (
      proposal.scopeKind !== "CREATION_DRAFT" ||
      proposal.draftId === null ||
      proposal.blueprintVersionId !== blueprintVersionId ||
      !matchesTargetKeys(proposal)
    ) {
      setError("服务端返回的 Proposal 与当前 Blueprint 初始化字段不一致，请重新提议。")
      return
    }
    setDraftId(proposal.draftId)
    setActiveProposalId(proposal.proposalId)
    setApprovalIntent(null)
    setError(null)
  }

  function submitProposal() {
    const generationInstruction = instruction.trim()
    if (!generationInstruction) {
      setError("请先填写希望 Assistant 如何生成这些字段。")
      return
    }
    // 一批字段共用同一条指令：字段之间需要互相呼应，分别下指令等于回到多次独立请求。
    const items = targetKeys.map((key) => ({
      targetType: "INITIALIZATION_FIELD" as const,
      targetKey: key,
      generationInstruction
    }))
    setError(null)
    if (isEditingCurrentProposal && activeProposal && draftId) {
      revise.mutate(
        {
          draftId,
          blueprintVersionId,
          proposalId: activeProposal.proposalId,
          proposalVersion: activeProposal.proposalVersion,
          proposalHash: activeProposal.proposalHash,
          items
        },
        {
          onSuccess: acceptProposal,
          onError: (failure) => setError(proposalErrorMessage(failure))
        }
      )
      return
    }
    propose.mutate(
      {
        ...(draftId ? { draftId } : {}),
        blueprintVersionId,
        items
      },
      {
        onSuccess: acceptProposal,
        onError: (failure) => setError(proposalErrorMessage(failure))
      }
    )
  }

  function approveProposal() {
    if (
      !activeProposal ||
      activeProposal.status !== "AWAITING_CONFIRMATION" ||
      activeProposal.draftId === null ||
      activeProposal.items.length === 0 ||
      !assistantId ||
      instructionChanged
    ) {
      return
    }
    const approvedItemKeys = activeProposal.items.map((item) => item.itemKey)
    const operationKey = `${activeProposal.proposalId}:${activeProposal.proposalVersion}:${activeProposal.proposalHash}:${approvedItemKeys.join(",")}:${assistantId}`
    const idempotencyKey =
      approvalIntent?.operationKey === operationKey
        ? approvalIntent.idempotencyKey
        : crypto.randomUUID()
    if (approvalIntent?.operationKey !== operationKey) {
      setApprovalIntent({ operationKey, idempotencyKey })
    }
    setError(null)
    approveAndStart.mutate(
      {
        draftId: activeProposal.draftId,
        blueprintVersionId,
        proposalId: activeProposal.proposalId,
        proposalVersion: activeProposal.proposalVersion,
        proposalHash: activeProposal.proposalHash,
        approvedTargetKeys: approvedItemKeys,
        approvalIdempotencyKey: idempotencyKey,
        assistantId
      },
      {
        onSuccess: acceptProposal,
        onError: (failure) => setError(proposalErrorMessage(failure))
      }
    )
  }

  function applyResult() {
    if (
      !activeProposal ||
      activeProposal.scopeKind !== "CREATION_DRAFT" ||
      activeProposal.draftId === null ||
      activeProposal.status !== "APPROVED" ||
      !task?.rootResult
    ) {
      setError("当前结果未满足填入条件，请等待 Task 完成并确认字段范围。")
      return
    }
    // 只接受本次已确认、且当前 Blueprint 真实启用 AI 辅助的初始化字段；其余键一律忽略。
    const confirmedFields = activeProposal.items
      .filter((item) => item.targetType === "INITIALIZATION_FIELD")
      .map((item) =>
        fields.find(
          (field) =>
            field.key === item.targetKey &&
            field.aiAssist === true &&
            CREATION_DRAFT_FIELD_KEYS.has(field.key)
        )
      )
      .filter((field): field is AigcProjectInitializationField => field !== undefined)
    if (confirmedFields.length === 0) {
      setError("本次确认的字段在当前 Blueprint 中未启用 AI 辅助，无法填入。")
      return
    }
    try {
      const { applied, rejected } = parseAigcInitializationResults(
        confirmedFields,
        task.rootResult.result
      )
      onApply(applied, {
        targetKeys: applied.map((entry) => entry.key),
        draftId: activeProposal.draftId,
        proposalId: activeProposal.proposalId,
        proposalVersion: activeProposal.proposalVersion,
        proposalHash: activeProposal.proposalHash
      })
      setError(
        rejected.length > 0 ? `${rejected.join("、")} 的值类型不符合字段要求，未填入。` : null
      )
    } catch (failure) {
      setError(proposalErrorMessage(failure))
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles /> AI 初始化建议
        </CardTitle>
        <CardDescription>
          先确认生成目标和指令，再启动 canonical Task；结果只在你点击填入后进入创建表单。
        </CardDescription>
        <CardAction className="flex items-center gap-2">
          {draftId ? <Badge variant="outline">{draftId}</Badge> : null}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={proposalPending || approveAndStart.isPending}
            onClick={onCancel}
          >
            取消
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {error ? (
          <Alert variant="destructive" role="alert">
            <CircleAlert />
            <AlertTitle>AI 建议操作未完成</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1 font-medium text-sm">
            <span>本批初始化字段（{targetFields.length}）</span>
            <div className="flex flex-wrap gap-1">
              {targetFields.map((field) => (
                <Badge key={field.key} variant="outline">
                  {field.label}
                </Badge>
              ))}
            </div>
          </div>
          <div className="flex flex-col gap-1 font-medium text-sm">
            <span>执行 Assistant</span>
            <Select
              value={assistantId}
              onValueChange={(value) => setAssistantId(value ?? "")}
              disabled={
                assistantsQuery.isLoading || approveAndStart.isPending || approvalIntent !== null
              }
            >
              <SelectTrigger className="w-full" aria-label="执行 Assistant">
                <SelectValue placeholder="选择 Assistant" />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {(assistantsQuery.data ?? []).map((assistant) => (
                    <SelectItem key={assistant.assistantId} value={assistant.assistantId}>
                      {assistant.name} · {assistant.defaultRoleKey}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
            {!assistantsQuery.isLoading && (assistantsQuery.data ?? []).length === 0 ? (
              <p className="text-destructive text-xs">
                当前没有可执行的 Assistant，暂不能启动生成。
              </p>
            ) : null}
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor={instructionId} className="font-medium text-sm">
            生成指令
          </Label>
          <Textarea
            id={instructionId}
            value={instruction}
            onChange={(event) => setInstruction(event.target.value)}
            placeholder={
              targetFields.length === 1
                ? `说明希望如何生成“${targetFields[0]?.label}”`
                : `说明希望如何生成这 ${targetFields.length} 个字段，它们会一起生成并保持呼应`
            }
            className="min-h-24 font-normal"
            disabled={activeProposal?.status === "APPROVED"}
          />
        </div>

        {activeProposal ? (
          <div className="flex flex-col gap-3 rounded-lg border p-3">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={activeProposal.status === "APPROVED" ? "secondary" : "default"}>
                Proposal v{activeProposal.proposalVersion} · {activeProposal.status}
              </Badge>
              <span className="font-mono text-muted-foreground text-xs">
                {activeProposal.proposalHash.slice(0, 12)}…
              </span>
            </div>
            <div className="flex flex-col gap-1 font-medium text-sm">
              <span>本次确认并启动的目标（{activeProposal.items.length}）</span>
              <ul className="flex flex-col gap-1 font-normal">
                {activeProposal.items.map((item) => (
                  <li key={item.itemKey} className="text-muted-foreground text-xs">
                    {fields.find((field) => field.key === item.targetKey)?.label ?? item.targetKey}
                  </li>
                ))}
              </ul>
            </div>
            {instructionChanged ? (
              <p className="text-muted-foreground text-sm">
                生成指令已修改，请先提交修订；旧确认将失效。
              </p>
            ) : null}
          </div>
        ) : null}

        {activeProposal?.linkedTaskId ? (
          <Alert role="status" aria-live="polite">
            <AlertTitle>canonical Task · {activeProposal.linkedTaskId}</AlertTitle>
            <AlertDescription>
              {taskQuery.isLoading
                ? "正在读取 Task 状态…"
                : task
                  ? `状态：${taskStatusLabel(task.status)}`
                  : "Task 状态暂不可用。"}
            </AlertDescription>
          </Alert>
        ) : null}

        {task?.rootResult ? (
          <Card size="sm">
            <CardHeader>
              <CardTitle>Assistant 结果</CardTitle>
              <CardDescription>请审阅后再填入表单；填入后仍可继续人工修改。</CardDescription>
            </CardHeader>
            <CardContent>
              <p className="whitespace-pre-wrap text-sm">{task.rootResult.result}</p>
            </CardContent>
            <CardFooter className="justify-end">
              <Button type="button" onClick={applyResult}>
                <Check /> 填入表单并用于创建
              </Button>
            </CardFooter>
          </Card>
        ) : null}
      </CardContent>
      <CardFooter className="justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          onClick={submitProposal}
          disabled={
            proposalPending || approveAndStart.isPending || activeProposal?.status === "APPROVED"
          }
        >
          {proposalPending ? <LoaderCircle className="animate-spin" /> : <Sparkles />}
          {isEditingCurrentProposal ? "提交修订" : "生成建议提议"}
        </Button>
        <Button
          type="button"
          onClick={approveProposal}
          disabled={
            !activeProposal ||
            activeProposal.status !== "AWAITING_CONFIRMATION" ||
            activeProposal.items.length === 0 ||
            !assistantId ||
            instructionChanged ||
            proposalPending ||
            approveAndStart.isPending
          }
        >
          {approveAndStart.isPending ? <LoaderCircle className="animate-spin" /> : <Check />}
          确认并启动
        </Button>
      </CardFooter>
    </Card>
  )
}

export interface NewProjectLauncherProps {
  mode?: "compact" | "full"
  className?: string
  /** 从蓝图卡片预选进入时的蓝图 ID，跳过类型/蓝图选择步骤 */
  preselectedBlueprintId?: number
}

export function NewProjectLauncher({
  mode = "compact",
  className,
  preselectedBlueprintId
}: NewProjectLauncherProps) {
  const router = useRouter()
  const { data: projectAccess } = useEntityAccess("project")
  const { canCreate } = usePermissionGuard(projectAccess)
  const nameId = useId()
  const descriptionId = useId()
  const coverFileId = useId()
  const coverPromptId = useId()
  const briefDocumentSelectId = useId()
  const projectDate = new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit" }).format(
    new Date()
  )
  const { data: typePage, isLoading: typesLoading } = useAigcProjectTypes()
  const { data: profilePage, isLoading: profilesLoading } = useAigcBrandProfiles()
  const { data: channelPage, isLoading: channelsLoading } = useAigcChannelSpecs({
    status: "published"
  })
  const materialize = useMaterializeAigcProject()
  const {
    upload: uploadCover,
    uploading: coverUploading,
    progress: coverUploadProgress
  } = useFileUpload({ maxWidth: 1920, maxHeight: 1080, quality: 0.9 })

  const methods = useForm<NewProjectFormValues>({
    resolver: zodResolver(newProjectSchema),
    defaultValues: {
      name: `新项目 · ${projectDate}`,
      projectTypeCode: "",
      structureSource: "CATALOG",
      blueprintVersionId: 0,
      brandProfileId: undefined,
      productionMode: "standard",
      channelCodes: [],
      documents: [],
      coverMode: "AI_GENERATE",
      coverPrompt: "",
      description: "",
      background: "",
      targetAudience: "",
      requirements: [],
      constraints: [],
      keyFacts: [],
      keyMessages: [],
      styleGuidance: "",
      successCriteria: [],
      deliveryRequirements: {},
      locale: "zh-CN",
      additionalContext: ""
    }
  })
  const { control, watch, setValue, handleSubmit, formState } = methods
  const nameEditedRef = useRef(false)
  const sourceResolvedTypeRef = useRef<string | null>(null)
  const [coverIdempotencyKey] = useState(createCoverIdempotencyKey)

  const formValues = watch()
  const projectTypeCode = formValues.projectTypeCode
  const structureSource = formValues.structureSource
  const blueprintVersionId = formValues.blueprintVersionId
  const brandProfileId = formValues.brandProfileId
  const productionMode = formValues.productionMode
  const channelCodes = formValues.channelCodes
  const currentDocuments = formValues.documents
  const coverMode = formValues.coverMode
  const coverUpload = formValues.coverUpload
  const coverPrompt = formValues.coverPrompt
  const [briefDocumentId, setBriefDocumentId] = useState("")
  const [aiSuggestionTargetKeys, setAiSuggestionTargetKeys] = useState<
    AigcProjectInitializationFieldKey[]
  >([])
  const [appliedCreationProposal, setAppliedCreationProposal] =
    useState<AppliedCreationProposal | null>(null)
  const [selectedAssistantProposal, setSelectedAssistantProposal] =
    useState<AssistantBlueprintProposalView | null>(null)
  const [acceptedFrozenProposalKey, setAcceptedFrozenProposalKey] = useState<string | null>(null)
  const [creationProposalError, setCreationProposalError] = useState<string | null>(null)
  const handleAssistantProposalChange = useCallback(
    (proposal: AssistantBlueprintProposalView | null) => {
      const nextKey = proposal ? `${proposal.proposalId}:${proposal.proposalVersion}` : null
      setAcceptedFrozenProposalKey((acceptedKey) => (acceptedKey === nextKey ? acceptedKey : null))
      setSelectedAssistantProposal(proposal)
    },
    []
  )
  const handleUseFrozenContext = useCallback((proposal: AssistantBlueprintProposalView) => {
    setAcceptedFrozenProposalKey(`${proposal.proposalId}:${proposal.proposalVersion}`)
    setSelectedAssistantProposal(proposal)
    setCreationProposalError(null)
  }, [])
  const briefDocumentIdNumber = Number(briefDocumentId)
  const { data: briefDocumentList, isLoading: briefDocumentListLoading } = useDocList()
  const { data: briefDocumentDetail, isFetching: briefDocumentVersionLoading } = useDocument(
    Number.isInteger(briefDocumentIdNumber) && briefDocumentIdNumber > 0
      ? briefDocumentIdNumber
      : null
  )

  const allTypes = (typePage?.list ?? []).filter((type) => type.status === "published")
  const types = mode === "compact" ? allTypes.filter((type) => type.quickEntry) : allTypes
  const profiles = (profilePage?.list ?? []).filter(
    (profile) => profile.currentVersionId !== undefined
  )
  const channels = (channelPage?.list ?? []).filter((channel) => channel.status === "published")
  const selectedType = allTypes.find((type) => type.code === projectTypeCode)
  const { data: blueprintPage, isLoading: blueprintsLoading } = useAigcProjectBlueprints(
    {
      projectTypeCode: projectTypeCode || undefined,
      status: "published"
    },
    selectedType !== undefined
  )
  const blueprints = (blueprintPage?.list ?? []).filter(
    (blueprint) => blueprint.projectTypeCode === projectTypeCode
  )
  const selectedBlueprint =
    blueprints.find((blueprint) => blueprint.id === blueprintVersionId) ?? blueprints.at(0)
  const initializationFields = (selectedBlueprint?.projectInitialization?.fields ?? []).toSorted(
    (left, right) => (left.orderNo ?? 0) - (right.orderNo ?? 0)
  )
  const aiAssistedInitializationFields = initializationFields.filter(
    (field) => field.aiAssist === true && CREATION_DRAFT_FIELD_KEYS.has(field.key)
  )
  /** 蓝图声明支持 AI 辅助、且当前还没有值的字段；已填写的不纳入，避免覆盖用户内容。 */
  const emptyAiAssistedFieldKeys = aiAssistedInitializationFields
    .filter((field) => !hasAigcInitializationValue(formValues[field.key]))
    .map((field) => field.key)
  const displayedInitializationFields =
    structureSource === "ASSISTANT_GENERATED"
      ? ASSISTANT_INITIALIZATION_FIELDS
      : initializationFields
  const briefDocumentSpec = selectedBlueprint?.projectInitialization?.documents?.find(
    (document) => document.role === "brief"
  )

  useEffect(() => {
    if (!briefDocumentDetail?.currentVersionId) {
      setValue("documents", [])
      return
    }
    if (structureSource === "ASSISTANT_GENERATED") {
      setValue("documents", [
        {
          fieldKey: "brief",
          documentVersionId: briefDocumentDetail.currentVersionId
        }
      ])
      return
    }
    if (!briefDocumentSpec) {
      setValue("documents", [])
      return
    }
    setValue("documents", [
      {
        fieldKey: briefDocumentSpec.key,
        ...(briefDocumentSpec.referenceType === "PROJECT_DOCUMENT"
          ? { documentId: briefDocumentDetail.id }
          : { documentVersionId: briefDocumentDetail.currentVersionId })
      }
    ])
  }, [briefDocumentDetail, briefDocumentSpec, setValue, structureSource])

  const slotTemplates = selectedBlueprint?.slotTemplateSpec?.slotTemplates ?? []
  const slotLabelByKey = new Map(
    slotTemplates.map((template) => [
      template.templateKey,
      (template.displayNamePattern && stripSlotNamePattern(template.displayNamePattern)) ||
        template.templateKey
    ])
  )
  const blueprintActions = parseBlueprintActions(selectedBlueprint?.actionSpec)

  // 从蓝图卡片预选进入时，全量查询蓝图反查其所属项目类型，自动选中类型和蓝图两级。
  const { data: preselectedBlueprintPage } = useAigcProjectBlueprints(
    { status: "published" },
    preselectedBlueprintId !== undefined && !projectTypeCode
  )
  useEffect(() => {
    if (!preselectedBlueprintId || projectTypeCode) return
    const target = preselectedBlueprintPage?.list.find(
      (blueprint) => blueprint.id === preselectedBlueprintId
    )
    if (!target) return
    let type: AigcProjectType | undefined
    for (const item of allTypes) {
      if (item.code === target.projectTypeCode) {
        type = item
        break
      }
    }
    setValue("projectTypeCode", target.projectTypeCode)
    setValue("structureSource", "CATALOG")
    setValue("blueprintVersionId", target.id)
    setValue("productionMode", target.productionMode)
    setValue("channelCodes", type?.defaultChannels ?? [])
    if (!nameEditedRef.current) setValue("name", `${target.name} · ${projectDate}`)
  }, [
    allTypes,
    preselectedBlueprintId,
    preselectedBlueprintPage,
    projectDate,
    projectTypeCode,
    setValue
  ])

  useEffect(() => {
    if (!selectedType || blueprintsLoading || sourceResolvedTypeRef.current === selectedType.code) {
      return
    }
    sourceResolvedTypeRef.current = selectedType.code
    if (blueprints.length === 0) {
      setValue("structureSource", "ASSISTANT_GENERATED")
      setValue("blueprintVersionId", 0)
      return
    }
    setValue("structureSource", "CATALOG")
    if (!blueprints.some((blueprint) => blueprint.id === blueprintVersionId)) {
      setValue("blueprintVersionId", blueprints[0]?.id ?? 0)
    }
  }, [blueprintVersionId, blueprints, blueprintsLoading, selectedType, setValue])

  // biome-ignore lint/correctness/useExhaustiveDependencies: blueprint id 是重置触发器，删掉则切换模板后残留旧建议
  useEffect(() => {
    setAiSuggestionTargetKeys([])
    setAppliedCreationProposal(null)
    setCreationProposalError(null)
  }, [selectedBlueprint?.id])

  function handleTypeChange(code: AigcProjectTypeCode) {
    const type = allTypes.find((item) => item.code === code)
    sourceResolvedTypeRef.current = null
    handleAssistantProposalChange(null)
    setValue("projectTypeCode", code)
    setValue("productionMode", type?.defaultProductionMode ?? "standard")
    setValue("channelCodes", type?.defaultChannels ?? [])
    setValue("blueprintVersionId", 0)
    if (!nameEditedRef.current && type) setValue("name", `${type.name} · ${projectDate}`)
  }

  function handleCoverModeChange(value: "UPLOAD" | "AI_GENERATE") {
    setValue("coverMode", value)
    if (value === "AI_GENERATE") {
      setValue("coverUpload", undefined)
    }
  }

  async function handleCoverFile(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith("image/")) return
    const result = await uploadCover(file)
    setValue("coverUpload", { fileId: result.fileId, url: result.url, name: result.name })
  }

  function applyCreationDraftSuggestion(
    values: AigcInitializationResultEntry[],
    proposal: AppliedCreationProposal
  ) {
    for (const entry of values) {
      setValue(entry.key, entry.value, { shouldDirty: true, shouldValidate: true })
    }
    setAppliedCreationProposal(proposal)
    setCreationProposalError(null)
  }

  const selectedProfile = profiles.find((profile) => profile.id === brandProfileId)
  const assistantDocuments = currentDocuments.flatMap<AssistantBlueprintCreationDocument>(
    (document) => {
      const contextNote = document.contextNote?.trim() || null
      if (document.documentVersionId && !document.documentId) {
        return [
          {
            fieldKey: document.fieldKey.trim(),
            documentVersionId: document.documentVersionId,
            contextNote
          }
        ]
      }
      if (document.documentId && !document.documentVersionId) {
        return [
          {
            fieldKey: document.fieldKey.trim(),
            documentId: document.documentId,
            contextNote
          }
        ]
      }
      return []
    }
  )
  const assistantCreationContext: AssistantBlueprintCreationContext = {
    name: formValues.name.trim(),
    projectSpecification: {
      projectTypeCode,
      productionMode,
      channelCodes: channelCodes.map((code) => code.trim()),
      description: formValues.description?.trim() || null,
      background: formValues.background?.trim() || null,
      targetAudience: formValues.targetAudience?.trim() || null,
      requirements: formValues.requirements.map((item) => item.trim()),
      constraints: formValues.constraints.map((item) => item.trim()),
      keyFacts: formValues.keyFacts.map((item) => item.trim()),
      keyMessages: formValues.keyMessages.map((item) => item.trim()),
      styleGuidance: formValues.styleGuidance?.trim() || null,
      successCriteria: formValues.successCriteria.map((item) => item.trim()),
      deliveryRequirements: formValues.deliveryRequirements,
      locale: formValues.locale?.trim() || null,
      additionalContext: formValues.additionalContext?.trim() || null
    },
    brandProfileVersionIds: selectedProfile?.currentVersionId
      ? [selectedProfile.currentVersionId]
      : [],
    documents: assistantDocuments,
    coverMode,
    coverFileId: coverMode === "UPLOAD" ? (coverUpload?.fileId ?? null) : null,
    coverPrompt: coverMode === "AI_GENERATE" ? coverPrompt?.trim() || null : null,
    coverIdempotencyKey: coverMode === "AI_GENERATE" ? coverIdempotencyKey : null
  }
  const selectedAssistantProposalKey = selectedAssistantProposal
    ? `${selectedAssistantProposal.proposalId}:${selectedAssistantProposal.proposalVersion}`
    : null
  const assistantContextMatchesProposal =
    selectedAssistantProposal !== null &&
    (assistantBlueprintCreationContextFingerprint(selectedAssistantProposal.creationContext) ===
      assistantBlueprintCreationContextFingerprint(assistantCreationContext) ||
      (acceptedFrozenProposalKey !== null &&
        acceptedFrozenProposalKey === selectedAssistantProposalKey))
  const assistantPlanningBlockReason =
    structureSource !== "ASSISTANT_GENERATED"
      ? null
      : !formValues.description?.trim()
        ? "请先填写项目目标。"
        : formValues.requirements.length === 0
          ? "请至少填写一项内容要求。"
          : briefDocumentId !== "" &&
              (briefDocumentVersionLoading || !briefDocumentDetail?.currentVersionId)
            ? "正在读取 Brief 当前精确版本，请稍候。"
            : coverMode === "UPLOAD" && !coverUpload
              ? "请先上传项目封面。"
              : null
  const assistantMaterializeBlockReason =
    structureSource !== "ASSISTANT_GENERATED"
      ? null
      : assistantPlanningBlockReason
        ? assistantPlanningBlockReason
        : !selectedAssistantProposal
          ? "请先由 Assistant 完成项目结构规划。"
          : selectedAssistantProposal.status !== "CONFIRMED"
            ? `当前 Proposal 状态为“${selectedAssistantProposal.status}”，请先确认结构。`
            : !selectedAssistantProposal.proposalHash
              ? "当前 Proposal 尚未签发有效 hash，不能创建项目。"
              : !assistantContextMatchesProposal
                ? "创建上下文已变化，请重新规划或明确采用当前 Proposal 的冻结上下文。"
                : null

  function onSubmit(values: NewProjectFormValues) {
    if (!canCreate || !selectedType || coverUploading) return

    if (values.structureSource === "ASSISTANT_GENERATED") {
      if (
        assistantMaterializeBlockReason ||
        !selectedAssistantProposal ||
        selectedAssistantProposal.status !== "CONFIRMED" ||
        !selectedAssistantProposal.proposalHash
      ) {
        setCreationProposalError(
          assistantMaterializeBlockReason ?? "当前 Assistant Blueprint Proposal 不能用于创建项目。"
        )
        return
      }
      setCreationProposalError(null)
      materialize.mutate(
        {
          sourceType: "ASSISTANT_GENERATED",
          assistantProposal: {
            proposalId: selectedAssistantProposal.proposalId,
            proposalVersion: selectedAssistantProposal.proposalVersion,
            proposalHash: selectedAssistantProposal.proposalHash
          }
        },
        {
          onSuccess: (result) => router.push(paths.studio.project(result.project.id)),
          onError: (failure) => setCreationProposalError(proposalErrorMessage(failure))
        }
      )
      return
    }

    if (!selectedBlueprint) {
      setCreationProposalError("请选择已发布的项目模板。")
      return
    }
    const emptyAppliedKey = appliedCreationProposal?.targetKeys.find(
      (key) => !hasAigcInitializationValue(values[key])
    )
    if (emptyAppliedKey) {
      setCreationProposalError(
        `已确认的初始化字段“${
          initializationFields.find((field) => field.key === emptyAppliedKey)?.label ??
          emptyAppliedKey
        }”当前没有值。请填入或取消本次 AI 建议后再创建。`
      )
      return
    }
    const approvedCreationProposal = appliedCreationProposal
      ? {
          draftId: appliedCreationProposal.draftId,
          proposalId: appliedCreationProposal.proposalId,
          proposalVersion: appliedCreationProposal.proposalVersion,
          proposalHash: appliedCreationProposal.proposalHash
        }
      : undefined
    setCreationProposalError(null)
    materialize.mutate(
      {
        sourceType: "CATALOG",
        catalog: {
          projectTypeCode: selectedType.code,
          name: values.name.trim(),
          description: values.description?.trim() || undefined,
          background: values.background?.trim() || undefined,
          targetAudience: values.targetAudience?.trim() || undefined,
          requirements: values.requirements,
          constraints: values.constraints,
          keyFacts: values.keyFacts,
          keyMessages: values.keyMessages,
          styleGuidance: values.styleGuidance?.trim() || undefined,
          successCriteria: values.successCriteria,
          deliveryRequirements: values.deliveryRequirements,
          locale: values.locale?.trim() || undefined,
          additionalContext: values.additionalContext?.trim() || undefined,
          blueprintVersionId: selectedBlueprint.id,
          brandProfileVersionIds: selectedProfile?.currentVersionId
            ? [selectedProfile.currentVersionId]
            : [],
          channelSpecVersionIds: channels
            .filter((channel) => values.channelCodes.includes(channel.code))
            .map((channel) => channel.id),
          documents: assistantDocuments,
          productionMode: values.productionMode,
          slotOverrides: [],
          approvedCreationProposal,
          coverMode: values.coverMode,
          ...(values.coverMode === "UPLOAD" && values.coverUpload
            ? { coverFileId: values.coverUpload.fileId }
            : {}),
          ...(values.coverMode === "AI_GENERATE"
            ? {
                coverPrompt: values.coverPrompt?.trim() || undefined,
                coverIdempotencyKey
              }
            : {})
        }
      },
      {
        onSuccess: (result) => router.push(paths.studio.project(result.project.id)),
        onError: (failure) => setCreationProposalError(proposalErrorMessage(failure))
      }
    )
  }

  return (
    <GlassCard glow="violet" className={className}>
      <GlassCardBody className="flex flex-col gap-6 p-5 sm:p-7">
        <h2 className="font-semibold text-xl">新建项目</h2>

        <form
          className="flex flex-col gap-6"
          onSubmit={handleSubmit(onSubmit)}
          noValidate
          autoComplete="off"
        >
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="flex flex-col gap-2 sm:w-[30%]">
              <span className="font-medium text-sm">
                项目封面 <span className="text-destructive">*</span>
              </span>
              <div className="relative flex aspect-video w-full items-center justify-center overflow-hidden rounded-xl border border-dashed bg-muted/30">
                {coverMode === "UPLOAD" && coverUpload ? (
                  <>
                    {/* biome-ignore lint/performance/noImgElement: 上传结果 URL 为即时预览地址 */}
                    <img
                      src={coverUpload.url}
                      alt="项目封面预览"
                      className="size-full object-cover"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label="移除已上传封面"
                      className="absolute top-1 right-1 bg-background/80"
                      onClick={() => setValue("coverUpload", undefined)}
                    >
                      <X />
                    </Button>
                  </>
                ) : coverMode === "UPLOAD" ? (
                  <Button
                    type="button"
                    variant="ghost"
                    nativeButton={false}
                    // biome-ignore lint/a11y/noLabelWithoutControl: Button 通过 render 渲染为 label，触发文件选择
                    render={<label htmlFor={coverFileId} />}
                    className="flex size-full cursor-pointer flex-col items-center justify-center gap-1 rounded-none text-muted-foreground"
                    disabled={coverUploading}
                  >
                    <ImageIcon />
                    <span className="text-xs">点击上传</span>
                  </Button>
                ) : (
                  <div className="flex flex-col items-center gap-1 text-muted-foreground">
                    <Sparkles />
                    <span className="text-xs">AI 生成</span>
                  </div>
                )}
                <Input
                  id={coverFileId}
                  type="file"
                  accept="image/*"
                  className="sr-only"
                  disabled={coverUploading}
                  onChange={(event) => {
                    void handleCoverFile(event.target.files?.[0])
                    event.target.value = ""
                  }}
                />
              </div>
              <ToggleGroup
                value={[coverMode]}
                onValueChange={(values: string[]) => {
                  const next = values.at(-1)
                  if (next === "UPLOAD" || next === "AI_GENERATE") handleCoverModeChange(next)
                }}
                variant="outline"
                size="sm"
                className="flex w-full"
              >
                <ToggleGroupItem value="UPLOAD" aria-label="上传封面" className="flex-1">
                  <Upload />
                </ToggleGroupItem>
                <ToggleGroupItem value="AI_GENERATE" aria-label="AI 生成封面" className="flex-1">
                  <Sparkles />
                </ToggleGroupItem>
              </ToggleGroup>
              {coverUploading ? (
                <div className="flex w-full items-center gap-2 text-muted-foreground text-xs">
                  <Progress value={coverUploadProgress} className="flex-1" />
                  {coverUploadProgress}%
                </div>
              ) : null}
            </div>

            <div className="flex flex-1 flex-col gap-2">
              <label htmlFor={nameId} className="font-medium text-sm">
                项目名称 <span className="text-destructive">*</span>
              </label>
              <Controller
                name="name"
                control={control}
                render={({ field, fieldState: { error } }) => (
                  <>
                    <Input
                      id={nameId}
                      maxLength={200}
                      aria-invalid={!!error}
                      placeholder="输入项目名称"
                      {...field}
                      onChange={(event) => {
                        nameEditedRef.current = true
                        field.onChange(event)
                      }}
                    />
                    {error ? <p className="text-destructive text-xs">{error.message}</p> : null}
                  </>
                )}
              />
              {coverMode === "AI_GENERATE" ? (
                <div className="flex flex-1 flex-col gap-1">
                  <Label
                    htmlFor={coverPromptId}
                    className="font-normal text-muted-foreground text-xs"
                  >
                    封面描述（可选）
                  </Label>
                  <Textarea
                    id={coverPromptId}
                    value={coverPrompt}
                    onChange={(event) => setValue("coverPrompt", event.target.value)}
                    placeholder="例如：电影感、暖色调、突出主角与城市夜景"
                    className="min-h-16 resize-y"
                  />
                </div>
              ) : null}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <span className="font-medium text-sm">当前品牌 / IP</span>
            <div className="flex flex-wrap items-center gap-3">
              <BrandProfileSelect
                profiles={profiles}
                value={brandProfileId}
                onChange={(value) => setValue("brandProfileId", value)}
                disabled={profilesLoading}
              />
              {profiles.length === 0 && !profilesLoading ? (
                <Link href="/studio/brands" className="text-primary text-sm hover:underline">
                  <Plus /> 新建并发布品牌资料
                </Link>
              ) : null}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <span className="font-medium text-sm">
              项目类型 <span className="text-destructive">*</span>
            </span>
            <ProjectTypePicker
              types={types}
              value={projectTypeCode || undefined}
              onChange={handleTypeChange}
            />
            {typesLoading ? (
              <p className="text-muted-foreground text-sm">正在加载项目类型…</p>
            ) : null}
            {formState.errors.projectTypeCode ? (
              <p className="text-destructive text-xs">{formState.errors.projectTypeCode.message}</p>
            ) : null}
          </div>

          {selectedType ? (
            <div className="flex flex-col gap-3">
              <span className="font-medium text-sm">
                项目结构来源 <span className="text-destructive">*</span>
              </span>
              <ToggleGroup
                value={[structureSource]}
                onValueChange={(values: string[]) => {
                  const next = values.at(-1)
                  if (next !== "CATALOG" && next !== "ASSISTANT_GENERATED") return
                  setValue("structureSource", next, { shouldValidate: true })
                  setCreationProposalError(null)
                  if (next === "CATALOG" && selectedBlueprint) {
                    setValue("blueprintVersionId", selectedBlueprint.id, { shouldValidate: true })
                  }
                }}
                variant="outline"
                className="flex w-full sm:w-fit"
              >
                <ToggleGroupItem
                  value="CATALOG"
                  aria-label="使用项目模板"
                  disabled={!blueprintsLoading && blueprints.length === 0}
                  className="flex-1 sm:flex-none"
                >
                  使用项目模板
                </ToggleGroupItem>
                <ToggleGroupItem
                  value="ASSISTANT_GENERATED"
                  aria-label="助理辅助"
                  className="flex-1 sm:flex-none"
                >
                  助理辅助
                </ToggleGroupItem>
              </ToggleGroup>

              {structureSource === "CATALOG" ? (
                <div className="flex flex-col gap-2">
                  <span className="font-medium text-sm">项目模板</span>
                  {blueprintsLoading ? (
                    <p className="text-muted-foreground text-sm">正在加载项目模板…</p>
                  ) : blueprints.length > 0 ? (
                    <ProjectBlueprintPicker
                      blueprints={blueprints}
                      value={selectedBlueprint?.id}
                      onChange={(value) => setValue("blueprintVersionId", value)}
                    />
                  ) : (
                    <p className="text-muted-foreground text-sm">
                      当前类型没有已发布项目模板，请改由 Assistant 规划。
                    </p>
                  )}
                  {formState.errors.blueprintVersionId ? (
                    <p className="text-destructive text-xs">
                      {formState.errors.blueprintVersionId.message}
                    </p>
                  ) : null}
                </div>
              ) : (
                <Alert>
                  <Sparkles />
                  <AlertTitle>由 Assistant 规划项目结构</AlertTitle>
                  <AlertDescription>
                    {blueprints.length === 0 && !blueprintsLoading
                      ? "当前类型没有已发布 Blueprint，但不会阻断创建；填写目标后完成 Proposal 规划与确认即可。"
                      : "Assistant 将根据当前表单事实生成可审阅 Proposal；确认前不会创建项目。"}
                  </AlertDescription>
                </Alert>
              )}
            </div>
          ) : null}

          {selectedType ? (
            <div className="flex flex-col gap-5">
              <div className="flex flex-col gap-2">
                <span className="font-medium text-sm">生产模式</span>
                <ToggleGroup
                  value={[productionMode]}
                  onValueChange={(values: string[]) => {
                    const next = values.at(-1)
                    if (next === "standard" || next === "short_drama" || next === "motion_comic") {
                      setValue("productionMode", next)
                    }
                  }}
                  variant="outline"
                  className="flex w-full flex-wrap justify-start"
                >
                  <ToggleGroupItem value="standard" aria-label="标准制作">
                    标准制作
                  </ToggleGroupItem>
                  <ToggleGroupItem value="short_drama" aria-label="短剧">
                    短剧
                  </ToggleGroupItem>
                  <ToggleGroupItem value="motion_comic" aria-label="动态漫">
                    动态漫
                  </ToggleGroupItem>
                </ToggleGroup>
              </div>

              <div className="flex flex-col gap-2">
                <span className="font-medium text-sm">投放渠道</span>
                {channelsLoading ? (
                  <p className="text-muted-foreground text-sm">正在加载渠道…</p>
                ) : (
                  <ChannelPicker
                    channels={channels.map((channel) => channel.code)}
                    value={channelCodes}
                    onChange={(value) => setValue("channelCodes", value)}
                  />
                )}
              </div>

              {structureSource === "CATALOG" && selectedBlueprint ? (
                <div className="grid gap-5 rounded-xl border bg-muted/20 p-4 lg:grid-cols-2">
                  <div className="flex flex-col gap-3">
                    <div>
                      <p className="font-medium text-sm">产物</p>
                      <p className="text-muted-foreground text-xs">
                        按项目模板默认数量创建，创建后可在项目中按需添加。
                      </p>
                    </div>
                    <SlotProductTree templates={slotTemplates} />
                  </div>

                  <div className="flex flex-col gap-3">
                    <div>
                      <p className="font-medium text-sm">生成流程</p>
                      <p className="text-muted-foreground text-xs">
                        创建后按此流程逐步生成，可在项目详情执行每一步。
                      </p>
                    </div>
                    <BlueprintActionSteps
                      steps={blueprintActions}
                      slotLabelByKey={slotLabelByKey}
                    />
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}

          {structureSource === "CATALOG" && emptyAiAssistedFieldKeys.length > 1 ? (
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3">
              <p className="min-w-0 flex-1 text-muted-foreground text-xs">
                还有 {emptyAiAssistedFieldKeys.length} 个支持 AI 辅助的字段没填，可以一次生成，
                字段之间会互相呼应。
              </p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setAiSuggestionTargetKeys(emptyAiAssistedFieldKeys)}
              >
                <Sparkles />让 AI 补全这 {emptyAiAssistedFieldKeys.length} 项
              </Button>
            </div>
          ) : null}

          {displayedInitializationFields.map((initializationField) => {
            const fieldId = `${descriptionId}-${initializationField.key}`
            return (
              <div key={initializationField.key} className="flex flex-col gap-2">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <label htmlFor={fieldId} className="font-medium text-sm">
                    {initializationField.label}
                    {initializationField.required ? (
                      <span className="text-destructive"> *</span>
                    ) : null}
                  </label>
                  {initializationField.aiAssist === true &&
                  CREATION_DRAFT_FIELD_KEYS.has(initializationField.key) ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => setAiSuggestionTargetKeys([initializationField.key])}
                    >
                      <Sparkles /> AI 建议
                    </Button>
                  ) : null}
                </div>
                {initializationField.description ? (
                  <p className="text-muted-foreground text-xs">{initializationField.description}</p>
                ) : null}
                <Controller
                  name={initializationField.key}
                  control={control}
                  render={({ field }) => {
                    const rawValue: unknown = field.value
                    const displayValue = Array.isArray(rawValue)
                      ? rawValue.join("\n")
                      : initializationField.key === "deliveryRequirements"
                        ? JSON.stringify(rawValue ?? {}, null, 2)
                        : typeof rawValue === "string"
                          ? rawValue
                          : ""
                    return (
                      <Textarea
                        id={fieldId}
                        required={initializationField.required}
                        placeholder={
                          initializationField.key === "description" && selectedType
                            ? getProjectTypeConfig(selectedType).placeholder
                            : initializationField.description
                        }
                        className="min-h-24 resize-y"
                        name={field.name}
                        onBlur={field.onBlur}
                        ref={field.ref}
                        value={displayValue}
                        onChange={(event) => {
                          const nextValue = event.target.value
                          if (initializationField.valueType === "STRING_LIST") {
                            field.onChange(
                              nextValue
                                .split("\n")
                                .map((item) => item.trim())
                                .filter(Boolean)
                            )
                            return
                          }
                          if (initializationField.key === "deliveryRequirements") {
                            try {
                              const parsed: unknown = JSON.parse(nextValue)
                              if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
                                field.onChange(parsed)
                              }
                            } catch {
                              // JSON 输入未完成时保留上一次合法值
                            }
                            return
                          }
                          field.onChange(nextValue)
                        }}
                      />
                    )
                  }}
                />
              </div>
            )
          })}

          {structureSource === "CATALOG" &&
          selectedBlueprint &&
          aiSuggestionTargetKeys.length > 0 &&
          aiSuggestionTargetKeys.every((key) =>
            aiAssistedInitializationFields.some((field) => field.key === key)
          ) ? (
            <CreationDraftSuggestionPanel
              key={selectedBlueprint.id}
              blueprintVersionId={selectedBlueprint.id}
              fields={aiAssistedInitializationFields}
              targetKeys={aiSuggestionTargetKeys}
              onApply={applyCreationDraftSuggestion}
              onCancel={() => setAiSuggestionTargetKeys([])}
            />
          ) : null}

          {structureSource === "CATALOG" && appliedCreationProposal ? (
            <Alert>
              <Check />
              <AlertTitle>已采用 AI 初始化建议</AlertTitle>
              <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
                <span>
                  创建时将提交 Proposal v{appliedCreationProposal.proposalVersion} 的确认凭据；
                  目标字段必须保持有值。
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setAppliedCreationProposal(null)
                    setCreationProposalError(null)
                  }}
                >
                  不提交凭据
                </Button>
              </AlertDescription>
            </Alert>
          ) : null}

          {creationProposalError ? (
            <Alert variant="destructive" role="alert">
              <CircleAlert />
              <AlertTitle>暂不能创建项目</AlertTitle>
              <AlertDescription>{creationProposalError}</AlertDescription>
            </Alert>
          ) : null}

          {briefDocumentSpec || structureSource === "ASSISTANT_GENERATED" ? (
            <div className="flex flex-col gap-2">
              <label htmlFor={briefDocumentSelectId} className="font-medium text-sm">
                {briefDocumentSpec?.label ?? "Brief 参考文档"}
                {structureSource === "CATALOG" && briefDocumentSpec?.required ? (
                  <span className="text-destructive"> *</span>
                ) : null}
              </label>
              <p className="text-muted-foreground text-xs">
                {structureSource === "ASSISTANT_GENERATED"
                  ? "可选。将冻结当前文档版本，并以 fieldKey: brief 提交；Proposal 必须保留该文档键。"
                  : briefDocumentSpec?.description}
              </p>
              <Select
                value={briefDocumentId === "" ? "none" : briefDocumentId}
                onValueChange={(next) => setBriefDocumentId(next && next !== "none" ? next : "")}
              >
                <SelectTrigger id={briefDocumentSelectId} className="w-full">
                  <SelectValue>
                    {(briefDocumentList ?? []).find(
                      (document) => String(document.id) === briefDocumentId
                    )?.title ?? (briefDocumentListLoading ? "正在加载文档…" : "选择文档")}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="none">暂不选择</SelectItem>
                    {(briefDocumentList ?? []).map((document) => (
                      <SelectItem key={document.id} value={String(document.id)}>
                        {document.title}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
          ) : null}

          {structureSource === "ASSISTANT_GENERATED" && selectedType ? (
            <AssistantBlueprintProposalPanel
              creationContext={assistantCreationContext}
              planningDisabledReason={assistantPlanningBlockReason}
              onProposalChange={handleAssistantProposalChange}
              onUseFrozenContext={handleUseFrozenContext}
            />
          ) : null}

          {structureSource === "ASSISTANT_GENERATED" &&
          selectedAssistantProposal &&
          !assistantContextMatchesProposal ? (
            <Alert variant="destructive" role="alert">
              <CircleAlert />
              <AlertTitle>创建上下文与 Proposal 冻结事实不一致</AlertTitle>
              <AlertDescription>
                项目名称、目标、品牌版本、渠道、生产模式、Brief 或封面设置与当前 Proposal
                冻结事实不一致。请重新规划，或在 Proposal 面板中明确采用该服务端冻结上下文。
              </AlertDescription>
            </Alert>
          ) : null}

          {!canCreate ? (
            <p className="text-amber-600 text-sm">当前账号没有创建项目的权限。</p>
          ) : null}
          {assistantMaterializeBlockReason ? (
            <Alert role="status" aria-live="polite">
              <CircleAlert />
              <AlertTitle>暂不能创建 Assistant 规划项目</AlertTitle>
              <AlertDescription>{assistantMaterializeBlockReason}</AlertDescription>
            </Alert>
          ) : null}
          <div className="flex justify-end">
            <GlowButton
              tone="violet"
              size="lg"
              type="submit"
              disabled={
                !canCreate ||
                !selectedType ||
                (structureSource === "CATALOG"
                  ? !selectedBlueprint
                  : assistantMaterializeBlockReason !== null) ||
                coverUploading ||
                materialize.isPending
              }
            >
              <Sparkles />
              {materialize.isPending ? "创建中…" : "创建项目"}
            </GlowButton>
          </div>
        </form>
      </GlassCardBody>
    </GlassCard>
  )
}
