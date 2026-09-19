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
import { useEffect, useId, useState } from "react"
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
import {
  type AigcInitializationFormValue,
  hasAigcInitializationValue,
  type NewProjectFormValues,
  newProjectSchema,
  parseAigcInitializationResult
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

interface AppliedCreationProposal extends AigcProjectCreationProposalRef {
  targetKey: AigcProjectInitializationFieldKey
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
  targetKey: AigcProjectInitializationFieldKey
  onTargetChange: (targetKey: AigcProjectInitializationFieldKey) => void
  onApply: (
    targetKey: AigcProjectInitializationFieldKey,
    value: AigcInitializationFormValue,
    proposal: AppliedCreationProposal
  ) => void
}

function CreationDraftSuggestionPanel({
  blueprintVersionId,
  fields,
  targetKey,
  onTargetChange,
  onApply
}: CreationDraftSuggestionPanelProps) {
  const [draftId, setDraftId] = useState<string | null>(null)
  const [activeProposalId, setActiveProposalId] = useState<string | null>(null)
  const [selectedItemKey, setSelectedItemKey] = useState("")
  const [instruction, setInstruction] = useState("")
  const [assistantId, setAssistantId] = useState("")
  const [approvalIntent, setApprovalIntent] = useState<ApprovalIntent | null>(null)
  const [error, setError] = useState<string | null>(null)
  const proposalsQuery = useAigcProjectCreationDraftProposals(draftId)
  const propose = useProposeAigcProjectCreationDraftGeneration()
  const revise = useReviseAigcProjectCreationDraftGenerationProposal()
  const approveAndStart = useApproveAndStartAigcProjectCreationDraftGeneration()
  const assistantsQuery = useAssistants()

  useEffect(() => {
    setActiveProposalId(null)
    setSelectedItemKey("")
    setInstruction("")
    setApprovalIntent(null)
    setError(null)
  }, [targetKey])

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
  const selectedItem =
    activeProposal?.items.find((item) => item.itemKey === selectedItemKey) ??
    activeProposal?.items.at(0)
  const taskQuery = useTaskDetails(activeProposal?.linkedTaskId ?? null)
  const task = taskQuery.data
  const targetField = fields.find((field) => field.key === targetKey)
  const isEditingCurrentProposal =
    activeProposal?.status === "AWAITING_CONFIRMATION" &&
    activeProposal.scopeKind === "CREATION_DRAFT" &&
    activeProposal.draftId === draftId &&
    activeProposal.blueprintVersionId === blueprintVersionId &&
    activeProposal.items.length === 1 &&
    activeProposal.items[0]?.targetKey === targetKey
  const instructionChanged =
    isEditingCurrentProposal &&
    activeProposal.items[0]?.generationInstruction.trim() !== instruction.trim()
  const proposalPending = propose.isPending || revise.isPending

  function acceptProposal(proposal: AigcProjectGenerationProposal) {
    if (
      proposal.scopeKind !== "CREATION_DRAFT" ||
      proposal.draftId === null ||
      proposal.blueprintVersionId !== blueprintVersionId ||
      proposal.items.length !== 1 ||
      proposal.items[0]?.targetKey !== targetKey
    ) {
      setError("服务端返回的 Proposal 与当前 Blueprint 初始化字段不一致，请重新提议。")
      return
    }
    setDraftId(proposal.draftId)
    setActiveProposalId(proposal.proposalId)
    setSelectedItemKey(proposal.items[0].itemKey)
    setApprovalIntent(null)
    setError(null)
  }

  function submitProposal() {
    const generationInstruction = instruction.trim()
    if (!generationInstruction) {
      setError("请先填写希望 Assistant 如何生成该字段。")
      return
    }
    const item = {
      targetType: "INITIALIZATION_FIELD" as const,
      targetKey,
      generationInstruction
    }
    setError(null)
    if (isEditingCurrentProposal && activeProposal && draftId) {
      revise.mutate(
        {
          draftId,
          blueprintVersionId,
          proposalId: activeProposal.proposalId,
          proposalVersion: activeProposal.proposalVersion,
          proposalHash: activeProposal.proposalHash,
          items: [item]
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
        items: [item]
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
      !selectedItem ||
      !assistantId ||
      instructionChanged
    ) {
      return
    }
    const operationKey = `${activeProposal.proposalId}:${activeProposal.proposalVersion}:${activeProposal.proposalHash}:${selectedItem.itemKey}:${assistantId}`
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
        approvedTargetKeys: [selectedItem.itemKey],
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
      !selectedItem ||
      selectedItem.targetType !== "INITIALIZATION_FIELD" ||
      !CREATION_DRAFT_FIELD_KEYS.has(
        selectedItem.targetKey as AigcProjectInitializationFieldKey
      ) ||
      !fields.some((field) => field.key === selectedItem.targetKey && field.aiAssist === true) ||
      !targetField ||
      !task?.rootResult
    ) {
      setError("当前结果未满足填入条件，请等待 Task 完成并确认字段范围。")
      return
    }
    try {
      const resultTargetKey = selectedItem.targetKey as AigcProjectInitializationFieldKey
      const resultField = fields.find((field) => field.key === resultTargetKey)
      if (!resultField) throw new Error("当前 Blueprint 未启用该初始化字段。")
      const value = parseAigcInitializationResult(resultField, task.rootResult.result)
      onApply(resultTargetKey, value, {
        targetKey: resultTargetKey,
        draftId: activeProposal.draftId,
        proposalId: activeProposal.proposalId,
        proposalVersion: activeProposal.proposalVersion,
        proposalHash: activeProposal.proposalHash
      })
      setError(null)
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
        {draftId ? <CardAction><Badge variant="outline">{draftId}</Badge></CardAction> : null}
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
            <span>初始化字段</span>
            <Select
              value={targetKey}
              onValueChange={(value) => {
                if (
                  value &&
                  CREATION_DRAFT_FIELD_KEYS.has(value as AigcProjectInitializationFieldKey) &&
                  fields.some((field) => field.key === value && field.aiAssist === true)
                ) {
                  onTargetChange(value as AigcProjectInitializationFieldKey)
                }
              }}
              disabled={proposalPending || approveAndStart.isPending}
            >
              <SelectTrigger className="w-full" aria-label="AI 建议初始化字段">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {fields.map((field) => (
                    <SelectItem key={field.key} value={field.key}>{field.label}</SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>
          <div className="flex flex-col gap-1 font-medium text-sm">
            <span>执行 Assistant</span>
            <Select
              value={assistantId}
              onValueChange={(value) => setAssistantId(value ?? "")}
              disabled={
                assistantsQuery.isLoading ||
                approveAndStart.isPending ||
                approvalIntent !== null
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
              <p className="text-destructive text-xs">当前没有可执行的 Assistant，暂不能启动生成。</p>
            ) : null}
          </div>
        </div>

        <label className="flex flex-col gap-1 font-medium text-sm">
          生成指令
          <Textarea
            value={instruction}
            onChange={(event) => setInstruction(event.target.value)}
            placeholder={`说明希望如何生成“${targetField?.label ?? targetKey}”`}
            className="min-h-24 font-normal"
            disabled={activeProposal?.status === "APPROVED"}
          />
        </label>

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
              <span>本次确认的单项目标</span>
              <Select
                value={selectedItem?.itemKey ?? ""}
                onValueChange={(value) => setSelectedItemKey(value ?? "")}
                disabled={activeProposal.status !== "AWAITING_CONFIRMATION"}
              >
                <SelectTrigger className="w-full" aria-label="确认的单项目标">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {activeProposal.items.map((item) => (
                      <SelectItem key={item.itemKey} value={item.itemKey}>
                        {item.targetKey} · {item.generationInstruction}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
            {instructionChanged ? (
              <p className="text-muted-foreground text-sm">生成指令已修改，请先提交修订；旧确认将失效。</p>
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
          disabled={proposalPending || approveAndStart.isPending || activeProposal?.status === "APPROVED"}
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
            !selectedItem ||
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
  const nameEditedRef = { current: false }
  const nameEdited = nameEditedRef.current

  const projectTypeCode = watch("projectTypeCode")
  const blueprintVersionId = watch("blueprintVersionId")
  const brandProfileId = watch("brandProfileId")
  const channelCodes = watch("channelCodes")
  const coverMode = watch("coverMode")
  const coverUpload = watch("coverUpload")
  const coverPrompt = watch("coverPrompt")
  const [briefDocumentId, setBriefDocumentId] = useState("")
  const [aiSuggestionTargetKey, setAiSuggestionTargetKey] =
    useState<AigcProjectInitializationFieldKey | null>(null)
  const [appliedCreationProposal, setAppliedCreationProposal] =
    useState<AppliedCreationProposal | null>(null)
  const [creationProposalError, setCreationProposalError] = useState<string | null>(null)
  const briefDocumentIdNumber = Number(briefDocumentId)
  const { data: briefDocumentList, isLoading: briefDocumentListLoading } = useDocList()
  const { data: briefDocumentDetail } = useDocument(
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
  const briefDocumentSpec = selectedBlueprint?.projectInitialization?.documents?.find(
    (document) => document.role === "brief"
  )

  useEffect(() => {
    if (!briefDocumentSpec || !briefDocumentDetail?.currentVersionId) {
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
  }, [briefDocumentDetail, briefDocumentSpec, setValue])

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
    setValue("blueprintVersionId", target.id)
    setValue("productionMode", target.productionMode)
    setValue("channelCodes", type?.defaultChannels ?? [])
    if (!nameEdited) setValue("name", `${target.name} · ${projectDate}`)
  }, [
    allTypes,
    nameEdited,
    preselectedBlueprintId,
    preselectedBlueprintPage,
    projectDate,
    projectTypeCode,
    setValue
  ])

  useEffect(() => {
    setAiSuggestionTargetKey(null)
    setAppliedCreationProposal(null)
    setCreationProposalError(null)
  }, [selectedBlueprint?.id])

  function handleTypeChange(code: AigcProjectTypeCode) {
    const type = allTypes.find((item) => item.code === code)
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
    targetKey: AigcProjectInitializationFieldKey,
    value: AigcInitializationFormValue,
    proposal: AppliedCreationProposal
  ) {
    setValue(targetKey, value, { shouldDirty: true, shouldValidate: true })
    setAppliedCreationProposal(proposal)
    setCreationProposalError(null)
  }

  function onSubmit(values: NewProjectFormValues) {
    if (!canCreate || !selectedType || !selectedBlueprint || coverUploading) return
    if (
      appliedCreationProposal &&
      !hasAigcInitializationValue(values[appliedCreationProposal.targetKey])
    ) {
      setCreationProposalError(
        `已确认的初始化字段“${
          initializationFields.find((field) => field.key === appliedCreationProposal.targetKey)
            ?.label ?? appliedCreationProposal.targetKey
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
    const selectedProfile = profiles.find((profile) => profile.id === values.brandProfileId)
    materialize.mutate(
      {
        sourceType: "CATALOG",
        sourceRef: `blueprint:${selectedBlueprint.id}@${selectedBlueprint.blueprintVersion}`,
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
        documents: values.documents,
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
              coverIdempotencyKey: createCoverIdempotencyKey()
            }
          : {})
      },
      { onSuccess: (result) => router.push(paths.studio.project(result.project.id)) }
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
            <div className="flex flex-col gap-2">
              <span className="font-medium text-sm">
                项目模板 <span className="text-destructive">*</span>
              </span>
              {blueprintsLoading ? (
                <p className="text-muted-foreground text-sm">正在加载项目模板…</p>
              ) : blueprints.length > 0 ? (
                <ProjectBlueprintPicker
                  blueprints={blueprints}
                  value={selectedBlueprint?.id}
                  onChange={(value) => setValue("blueprintVersionId", value)}
                />
              ) : (
                <p className="text-destructive text-sm">
                  当前项目类型没有已发布项目模板，暂不能创建。
                </p>
              )}
              {formState.errors.blueprintVersionId ? (
                <p className="text-destructive text-xs">
                  {formState.errors.blueprintVersionId.message}
                </p>
              ) : null}
            </div>
          ) : null}

          {selectedBlueprint ? (
            <div className="flex flex-col gap-5">
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
                  <BlueprintActionSteps steps={blueprintActions} slotLabelByKey={slotLabelByKey} />
                </div>
              </div>
            </div>
          ) : null}

          {initializationFields.map((initializationField) => {
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
                      onClick={() => setAiSuggestionTargetKey(initializationField.key)}
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

          {selectedBlueprint &&
          aiSuggestionTargetKey &&
          aiAssistedInitializationFields.some(
            (field) => field.key === aiSuggestionTargetKey
          ) ? (
            <CreationDraftSuggestionPanel
              key={selectedBlueprint.id}
              blueprintVersionId={selectedBlueprint.id}
              fields={aiAssistedInitializationFields}
              targetKey={aiSuggestionTargetKey}
              onTargetChange={setAiSuggestionTargetKey}
              onApply={applyCreationDraftSuggestion}
            />
          ) : null}

          {appliedCreationProposal ? (
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

          {briefDocumentSpec ? (
            <div className="flex flex-col gap-2">
              <label htmlFor={briefDocumentSelectId} className="font-medium text-sm">
                {briefDocumentSpec.label}
                {briefDocumentSpec.required ? <span className="text-destructive"> *</span> : null}
              </label>
              {briefDocumentSpec.description ? (
                <p className="text-muted-foreground text-xs">{briefDocumentSpec.description}</p>
              ) : null}
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

          {!canCreate ? (
            <p className="text-amber-600 text-sm">当前账号没有创建项目的权限。</p>
          ) : null}
          <div className="flex justify-end">
            <GlowButton
              tone="violet"
              size="lg"
              type="submit"
              disabled={
                !canCreate ||
                !selectedType ||
                !selectedBlueprint ||
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
