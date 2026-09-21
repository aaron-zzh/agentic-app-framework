/**
 * Content Studio 项目生成 Proposal 表单与确认入口。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  CircleAlert,
  FileText,
  LoaderCircle,
  Pencil,
  Plus,
  RefreshCw,
  Sparkles,
  Trash2
} from "lucide-react"
import type { FormEvent } from "react"
import { useId, useRef, useState } from "react"
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
import { Checkbox } from "@/components/ui/checkbox"
import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle
} from "@/components/ui/sheet"
import { Textarea } from "@/components/ui/textarea"
import { ApiError } from "@/lib/api/errors"
import type {
  AigcActionOption,
  AigcGenerationProposalConfirmationPolicy,
  AigcGenerationProposalStatus,
  AigcGenerationProposalTargetType,
  AigcProject,
  AigcProjectCurrentTask,
  AigcProjectDocumentRef,
  AigcProjectGenerationProposal,
  AigcProjectGenerationProposalItemInput,
  AigcProjectObject
} from "@/lib/api/rest/ai/aigc"
import {
  useAigcProjectGenerationProposals,
  useApproveAndStartAigcProjectGeneration,
  useProposeAigcProjectGeneration,
  useReviseAigcProjectGenerationProposal
} from "@/lib/api/rest/ai/aigc"
import { notify } from "@/lib/notification"
import { useChatterStore } from "@/lib/store/chatter-store"
import { PROJECT_INITIALIZATION_FIELD_OPTIONS } from "./project-initialization-fields"

const NO_ACTION = "__no_action__"

const TARGET_TYPES: Array<{ value: AigcGenerationProposalTargetType; label: string }> = [
  { value: "PROJECT_OBJECT", label: "项目对象" },
  { value: "PROJECT_DOCUMENT", label: "项目文档" },
  { value: "INITIALIZATION_FIELD", label: "项目初始化字段" },
  { value: "PROJECT_COVER", label: "项目封面" }
]

interface ProposalDraftItem extends AigcProjectGenerationProposalItemInput {
  clientKey: string
}

interface ProposalEditorState {
  source: AigcProjectGenerationProposal | null
  confirmationPolicy: AigcGenerationProposalConfirmationPolicy
  expiresAt: string
  items: ProposalDraftItem[]
}

interface ApprovalIntent {
  operationKey: string
  idempotencyKey: string
}

interface TargetOption {
  value: string
  label: string
}

export interface GenerationProposalPanelProps {
  project: AigcProject
  objects: AigcProjectObject[]
  documentRefs: AigcProjectDocumentRef[]
  actions: AigcActionOption[]
  canMutate: boolean
  /** 项目当前未终结的 canonical Task；非空时禁止启动第二个竞争任务。 */
  currentTask?: AigcProjectCurrentTask | null
  /** 受控开关：唯一入口是 ContentActionBar 的批量生成与查看提议。 */
  open: boolean
  onOpenChange: (open: boolean) => void
}

function emptyDraftItem(clientKey: string): ProposalDraftItem {
  return {
    clientKey,
    targetType: "PROJECT_OBJECT",
    targetKey: "",
    generationInstruction: "",
    dependencyKeys: [],
    requiredContextKeys: []
  }
}

function createEditor(): ProposalEditorState {
  return {
    source: null,
    confirmationPolicy: "REQUIRED",
    expiresAt: "",
    items: [emptyDraftItem("new-0")]
  }
}

function reviseEditor(proposal: AigcProjectGenerationProposal): ProposalEditorState {
  return {
    source: proposal,
    confirmationPolicy: proposal.confirmationPolicy,
    expiresAt: proposal.expiresAt?.slice(0, 16) ?? "",
    items: proposal.items.map((item) => ({
      clientKey: `${item.itemKey}:${item.itemVersion}`,
      targetType: item.targetType,
      targetKey: item.targetKey,
      actionKey: item.actionKey ?? undefined,
      generationInstruction: item.generationInstruction,
      dependencyKeys: item.dependencyKeys,
      requiredContextKeys: item.requiredContextKeys,
      modelPolicy: item.modelPolicy ?? undefined,
      confirmationPolicy: item.confirmationPolicy
    }))
  }
}

function isTargetType(value: string | null): value is AigcGenerationProposalTargetType {
  return TARGET_TYPES.some((option) => option.value === value)
}

function targetOptions(
  targetType: AigcGenerationProposalTargetType,
  objects: AigcProjectObject[],
  documentRefs: AigcProjectDocumentRef[]
): TargetOption[] {
  if (targetType === "PROJECT_OBJECT") {
    return objects.map((object) => ({
      value: object.stableKey,
      label: object.title || object.stableKey
    }))
  }
  if (targetType === "PROJECT_DOCUMENT") {
    const unique = new Map<string, TargetOption>()
    for (const reference of documentRefs) {
      if (reference.referenceType !== "PROJECT_DOCUMENT" || !reference.fieldKey) continue
      unique.set(reference.fieldKey, {
        value: reference.fieldKey,
        label: `${reference.title} · ${reference.fieldKey}`
      })
    }
    return [...unique.values()]
  }
  if (targetType === "INITIALIZATION_FIELD") {
    return PROJECT_INITIALIZATION_FIELD_OPTIONS
  }
  return [{ value: "cover", label: "项目封面" }]
}

function applicableActions(
  item: AigcProjectGenerationProposalItemInput,
  objects: AigcProjectObject[],
  actions: AigcActionOption[]
): AigcActionOption[] {
  if (item.targetType !== "PROJECT_OBJECT") return []
  const object = objects.find((candidate) => candidate.stableKey === item.targetKey)
  if (!object) return []
  return actions.filter(
    (action) =>
      action.applicableObjectTypes.length === 0 ||
      action.applicableObjectTypes.includes(object.objectType)
  )
}

function statusLabel(status: AigcGenerationProposalStatus): string {
  switch (status) {
    case "AWAITING_CONFIRMATION":
      return "待确认"
    case "APPROVED":
      return "已确认"
    case "SUPERSEDED":
      return "已被修订"
    case "EXPIRED":
      return "已过期"
  }
}

function statusVariant(
  status: AigcGenerationProposalStatus
): "default" | "secondary" | "destructive" | "outline" {
  if (status === "AWAITING_CONFIRMATION") return "default"
  if (status === "APPROVED") return "secondary"
  if (status === "EXPIRED") return "destructive"
  return "outline"
}

function terminalReason(status: AigcGenerationProposalStatus): string | null {
  if (status === "SUPERSEDED") return "此版本已有后续修订，旧 hash 与确认已失效。"
  if (status === "EXPIRED") return "此提议已超过有效期，请创建新的生成提议。"
  return null
}

function creditLabel(estimatedCredits: number): string {
  return estimatedCredits > 0 ? `预计 ${estimatedCredits} 积分` : "成本待评估"
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code === 409) {
    return `${error.message}。已重新拉取权威状态，请复核后重试。`
  }
  return error instanceof Error ? error.message : "操作失败，请稍后重试"
}

export function GenerationProposalPanel({
  project,
  objects,
  documentRefs,
  actions,
  canMutate,
  currentTask,
  open,
  onOpenChange
}: GenerationProposalPanelProps) {
  const expiresAtId = useId()
  const startBlockedId = useId()
  const approvalTargetId = useId()
  const setChatterOpen = useChatterStore((state) => state.setOpen)
  const startBlockedReason =
    currentTask == null
      ? undefined
      : `项目已有未完成任务（${currentTask.taskId} · ${currentTask.status}），完成或取消后才能启动新的生成任务`
  const nextDraftId = useRef(1)
  const [editor, setEditor] = useState<ProposalEditorState | null>(() =>
    canMutate ? createEditor() : null
  )
  const [formError, setFormError] = useState<string | null>(null)
  const [selectedApproval, setSelectedApproval] = useState<{
    proposalRef: string
    itemKeys: string[]
  } | null>(null)
  const [approvalIntent, setApprovalIntent] = useState<ApprovalIntent | null>(null)
  const proposalsQuery = useAigcProjectGenerationProposals(open ? project.id : null)
  const propose = useProposeAigcProjectGeneration()
  const revise = useReviseAigcProjectGenerationProposal()
  const approveAndStart = useApproveAndStartAigcProjectGeneration()
  const proposals = (proposalsQuery.data ?? []).filter(
    (proposal) => proposal.scopeKind === "PROJECT" && proposal.projectId === project.id
  )
  const editorPending = propose.isPending || revise.isPending

  function proposalRef(proposal: AigcProjectGenerationProposal): string {
    return `${proposal.proposalId}:${proposal.proposalVersion}`
  }

  function updateDraftItem(clientKey: string, patch: Partial<ProposalDraftItem>) {
    setEditor((current) =>
      current
        ? {
            ...current,
            items: current.items.map((item) =>
              item.clientKey === clientKey ? { ...item, ...patch } : item
            )
          }
        : current
    )
  }

  function addDraftItem() {
    const clientKey = `new-${nextDraftId.current}`
    nextDraftId.current += 1
    setEditor((current) =>
      current ? { ...current, items: [...current.items, emptyDraftItem(clientKey)] } : current
    )
  }

  function removeDraftItem(clientKey: string) {
    setEditor((current) =>
      current && current.items.length > 1
        ? { ...current, items: current.items.filter((item) => item.clientKey !== clientKey) }
        : current
    )
  }

  function normalizedItems(current: ProposalEditorState): AigcProjectGenerationProposalItemInput[] {
    return current.items.map(({ clientKey: _clientKey, ...item }) => {
      const validAction = applicableActions(item, objects, actions).some(
        (action) => action.actionKey === item.actionKey
      )
      return {
        ...item,
        targetKey: item.targetKey.trim(),
        actionKey: validAction ? item.actionKey : undefined,
        generationInstruction: item.generationInstruction.trim(),
        confirmationPolicy: current.confirmationPolicy
      }
    })
  }

  function validateEditor(current: ProposalEditorState): string | null {
    if (current.items.length === 0) return "至少需要一个生成目标。"
    if (current.items.some((item) => !item.targetKey.trim())) return "请为每一项选择生成目标。"
    if (current.items.some((item) => !item.generationInstruction.trim())) {
      return "请为每一项填写生成指令。"
    }
    const keys = current.items.map((item) => `${item.targetType}:${item.targetKey.trim()}`)
    if (new Set(keys).size !== keys.length) return "同一目标不能在一个提议中重复。"
    return null
  }

  function submitEditor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editor || !canMutate || editorPending) return
    const validationError = validateEditor(editor)
    if (validationError) {
      setFormError(validationError)
      return
    }
    setFormError(null)
    const common = {
      projectId: project.id,
      expectedProjectVersion: project.version,
      confirmationPolicy: editor.confirmationPolicy,
      expiresAt: editor.expiresAt || undefined,
      items: normalizedItems(editor)
    }
    const callbacks = {
      onSuccess: () => {
        setEditor(null)
        notify.success(editor.source ? "生成提议已修订" : "生成提议已创建")
      },
      onError: (error: unknown) => setFormError(errorMessage(error))
    }
    if (editor.source) {
      revise.mutate(
        {
          ...common,
          proposalId: editor.source.proposalId,
          proposalVersion: editor.source.proposalVersion,
          proposalHash: editor.source.proposalHash
        },
        callbacks
      )
      return
    }
    propose.mutate(common, callbacks)
  }

  /** 勾选本次要在同一 canonical Task 内推进的已批准目标；范围最终由服务端 approve 再次校验。 */
  function toggleApprovalTarget(reference: string, itemKey: string, checked: boolean) {
    setSelectedApproval((current) => {
      const itemKeys = current?.proposalRef === reference ? current.itemKeys : []
      const next = checked
        ? itemKeys.includes(itemKey)
          ? itemKeys
          : [...itemKeys, itemKey]
        : itemKeys.filter((key) => key !== itemKey)
      return next.length === 0 ? null : { proposalRef: reference, itemKeys: next }
    })
    setApprovalIntent(null)
  }

  function startApproval(proposal: AigcProjectGenerationProposal) {
    const reference = proposalRef(proposal)
    if (
      !canMutate ||
      startBlockedReason !== undefined ||
      proposal.status !== "AWAITING_CONFIRMATION" ||
      selectedApproval?.proposalRef !== reference ||
      selectedApproval.itemKeys.length === 0 ||
      approveAndStart.isPending
    ) {
      return
    }
    const operationKey = `${reference}:${proposal.proposalHash}:${selectedApproval.itemKeys.join(",")}`
    const idempotencyKey =
      approvalIntent?.operationKey === operationKey
        ? approvalIntent.idempotencyKey
        : crypto.randomUUID()
    if (approvalIntent?.operationKey !== operationKey) {
      setApprovalIntent({ operationKey, idempotencyKey })
    }
    setFormError(null)
    approveAndStart.mutate(
      {
        projectId: project.id,
        expectedProjectVersion: project.version,
        proposalId: proposal.proposalId,
        proposalVersion: proposal.proposalVersion,
        proposalHash: proposal.proposalHash,
        approvedTargetKeys: selectedApproval.itemKeys,
        approvalIdempotencyKey: idempotencyKey
      },
      {
        onSuccess: (approved) => {
          setApprovalIntent(null)
          setSelectedApproval(null)
          onOpenChange(false)
          setChatterOpen(true)
          notify.success("已交给 Assistant，可在任务面板查看进度", {
            description: approved.linkedTaskId ?? "Task 关联状态将从服务端刷新"
          })
        },
        onError: (error) => setFormError(errorMessage(error))
      }
    )
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-2xl">
        <SheetHeader className="border-b">
          <SheetTitle>AI 生成提议</SheetTitle>
          <SheetDescription>
            先审查目标与指令，再通过同一结构化 Approval 原子启动唯一 canonical Task。
          </SheetDescription>
        </SheetHeader>

        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-4 pb-6">
          <div className="flex flex-wrap items-center gap-2">
            <Button
              type="button"
              size="sm"
              onClick={() => {
                setEditor(createEditor())
                setFormError(null)
              }}
              disabled={!canMutate || editorPending}
            >
              <Plus />
              新建提议
            </Button>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => void proposalsQuery.refetch()}
              disabled={proposalsQuery.isFetching}
            >
              <RefreshCw className={proposalsQuery.isFetching ? "animate-spin" : undefined} />
              刷新
            </Button>
            {!canMutate ? (
              <p className="text-muted-foreground text-sm">当前项目只读，不能提议、修订或确认。</p>
            ) : null}
          </div>

          {formError ? (
            <Alert variant="destructive">
              <CircleAlert />
              <AlertTitle>操作未完成</AlertTitle>
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          ) : null}

          {editor ? (
            <Card>
              <CardHeader>
                <CardTitle>
                  {editor.source ? `修订 v${editor.source.proposalVersion}` : "新建生成提议"}
                </CardTitle>
                <CardDescription>
                  修订会产生新版本，并使旧版本的 hash 与已有确认失效。
                </CardDescription>
              </CardHeader>
              <CardContent>
                <form className="flex flex-col gap-4" onSubmit={submitEditor}>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div className="flex flex-col gap-1 font-medium text-sm">
                      <span>确认策略</span>
                      <Select
                        value={editor.confirmationPolicy}
                        onValueChange={(value) => {
                          if (value === "REQUIRED" || value === "OPTIONAL") {
                            setEditor((current) =>
                              current ? { ...current, confirmationPolicy: value } : current
                            )
                          }
                        }}
                      >
                        <SelectTrigger className="w-full" aria-label="确认策略">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            <SelectItem value="REQUIRED">必须确认</SelectItem>
                            <SelectItem value="OPTIONAL">可选确认</SelectItem>
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    </div>
                    <label
                      htmlFor={expiresAtId}
                      className="flex flex-col gap-1 font-medium text-sm"
                    >
                      过期时间（可选）
                      <Input
                        id={expiresAtId}
                        type="datetime-local"
                        value={editor.expiresAt}
                        onChange={(event) =>
                          setEditor((current) =>
                            current ? { ...current, expiresAt: event.target.value } : current
                          )
                        }
                      />
                    </label>
                  </div>

                  {editor.items.map((item, index) => {
                    const options = targetOptions(item.targetType, objects, documentRefs)
                    const itemActions = applicableActions(item, objects, actions)
                    return (
                      <Card key={item.clientKey} size="sm">
                        <CardHeader>
                          <CardTitle>目标 {index + 1}</CardTitle>
                          <CardAction>
                            <Button
                              type="button"
                              variant="ghost"
                              size="icon-xs"
                              aria-label={`移除目标 ${index + 1}`}
                              disabled={editor.items.length === 1}
                              onClick={() => removeDraftItem(item.clientKey)}
                            >
                              <Trash2 />
                            </Button>
                          </CardAction>
                        </CardHeader>
                        <CardContent className="flex flex-col gap-3">
                          <div className="grid gap-3 sm:grid-cols-2">
                            <div className="flex flex-col gap-1 font-medium text-sm">
                              <span>目标类型</span>
                              <Select
                                value={item.targetType}
                                onValueChange={(value) => {
                                  if (isTargetType(value)) {
                                    updateDraftItem(item.clientKey, {
                                      targetType: value,
                                      targetKey: value === "PROJECT_COVER" ? "cover" : "",
                                      actionKey: undefined
                                    })
                                  }
                                }}
                              >
                                <SelectTrigger
                                  className="w-full"
                                  aria-label={`目标 ${index + 1} 类型`}
                                >
                                  <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectGroup>
                                    {TARGET_TYPES.map((option) => (
                                      <SelectItem key={option.value} value={option.value}>
                                        {option.label}
                                      </SelectItem>
                                    ))}
                                  </SelectGroup>
                                </SelectContent>
                              </Select>
                            </div>
                            <div className="flex flex-col gap-1 font-medium text-sm">
                              <span>目标</span>
                              <Select
                                value={item.targetKey}
                                onValueChange={(value) =>
                                  updateDraftItem(item.clientKey, {
                                    targetKey: value ?? "",
                                    actionKey: undefined
                                  })
                                }
                                disabled={options.length === 0}
                              >
                                <SelectTrigger className="w-full" aria-label={`目标 ${index + 1}`}>
                                  <SelectValue placeholder="选择目标" />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectGroup>
                                    {options.map((option) => (
                                      <SelectItem key={option.value} value={option.value}>
                                        {option.label}
                                      </SelectItem>
                                    ))}
                                  </SelectGroup>
                                </SelectContent>
                              </Select>
                            </div>
                          </div>

                          {item.targetType === "PROJECT_OBJECT" ? (
                            <div className="flex flex-col gap-1 font-medium text-sm">
                              <span>Action（可选，已按对象类型过滤）</span>
                              <Select
                                value={
                                  itemActions.some((action) => action.actionKey === item.actionKey)
                                    ? item.actionKey
                                    : NO_ACTION
                                }
                                onValueChange={(value) =>
                                  updateDraftItem(item.clientKey, {
                                    actionKey: value && value !== NO_ACTION ? value : undefined
                                  })
                                }
                              >
                                <SelectTrigger
                                  className="w-full"
                                  aria-label={`目标 ${index + 1} Action`}
                                >
                                  <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectGroup>
                                    <SelectItem value={NO_ACTION}>
                                      不指定，由 Assistant 决定
                                    </SelectItem>
                                    {itemActions.map((action) => (
                                      <SelectItem key={action.actionKey} value={action.actionKey}>
                                        {action.label}
                                      </SelectItem>
                                    ))}
                                  </SelectGroup>
                                </SelectContent>
                              </Select>
                            </div>
                          ) : null}

                          <label
                            htmlFor={`${item.clientKey}-instruction`}
                            className="flex flex-col gap-1 font-medium text-sm"
                          >
                            生成指令
                            <Textarea
                              id={`${item.clientKey}-instruction`}
                              value={item.generationInstruction}
                              onChange={(event) =>
                                updateDraftItem(item.clientKey, {
                                  generationInstruction: event.target.value
                                })
                              }
                              placeholder="说明该目标要生成什么、重点和约束…"
                              className="min-h-24 font-normal"
                            />
                          </label>
                        </CardContent>
                      </Card>
                    )
                  })}

                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <Button type="button" variant="outline" size="sm" onClick={addDraftItem}>
                      <Plus />
                      添加目标
                    </Button>
                    <div className="flex items-center gap-2">
                      <Button
                        type="button"
                        variant="ghost"
                        onClick={() => {
                          setEditor(null)
                          setFormError(null)
                        }}
                      >
                        取消
                      </Button>
                      <Button type="submit" disabled={!canMutate || editorPending}>
                        {editorPending ? <LoaderCircle className="animate-spin" /> : <Sparkles />}
                        {editor.source ? "提交修订" : "创建提议"}
                      </Button>
                    </div>
                  </div>
                </form>
              </CardContent>
            </Card>
          ) : null}

          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="font-medium">提议版本</h2>
              <p className="text-muted-foreground text-sm">
                服务端返回当前项目的全部 Proposal 版本。
              </p>
            </div>
            <Badge variant="outline">{proposals.length}</Badge>
          </div>
          <p role="status" aria-live="polite" className="sr-only">
            {proposalsQuery.isFetching
              ? "正在刷新生成提议"
              : `已加载 ${proposals.length} 个生成提议版本`}
          </p>

          {proposalsQuery.isLoading ? (
            <p role="status" className="text-muted-foreground text-sm">
              正在加载生成提议…
            </p>
          ) : proposals.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <FileText />
                </EmptyMedia>
                <EmptyTitle>暂无生成提议</EmptyTitle>
                <EmptyDescription>创建提议后，可在这里审查、修订并单项确认启动。</EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            proposals.map((proposal) => {
              const reference = proposalRef(proposal)
              const reason = terminalReason(proposal.status)
              const selectedItemKeys =
                selectedApproval?.proposalRef === reference ? selectedApproval.itemKeys : []
              return (
                <Card key={reference}>
                  <CardHeader>
                    <CardTitle className="flex flex-wrap items-center gap-2">
                      Proposal v{proposal.proposalVersion}
                      <Badge variant={statusVariant(proposal.status)}>
                        {statusLabel(proposal.status)}
                      </Badge>
                    </CardTitle>
                    <CardDescription className="font-mono text-xs">
                      {proposal.proposalId} · hash {proposal.proposalHash.slice(0, 12)}…
                    </CardDescription>
                    <CardAction>
                      <Badge variant="outline">{creditLabel(proposal.estimatedCredits)}</Badge>
                    </CardAction>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-3">
                    {reason ? <p className="text-muted-foreground text-sm">{reason}</p> : null}
                    {proposal.status === "APPROVED" ? (
                      <Alert>
                        <AlertTitle>canonical Task</AlertTitle>
                        <AlertDescription>
                          {proposal.linkedTaskId ?? "Task 关联尚未返回，请刷新权威状态。"}
                        </AlertDescription>
                      </Alert>
                    ) : null}
                    {proposal.expiresAt ? (
                      <p className="text-muted-foreground text-xs">有效期至 {proposal.expiresAt}</p>
                    ) : null}
                    {proposal.items.map((item) => (
                      <Card key={item.itemKey} size="sm">
                        <CardHeader>
                          <CardTitle>{item.targetKey}</CardTitle>
                          <CardDescription>
                            {TARGET_TYPES.find((option) => option.value === item.targetType)?.label}
                            {item.actionKey ? ` · ${item.actionKey}` : ""}
                          </CardDescription>
                          <CardAction>
                            <Badge variant="outline">{creditLabel(item.estimatedCredits)}</Badge>
                          </CardAction>
                        </CardHeader>
                        <CardContent className="flex flex-col gap-2">
                          <p className="whitespace-pre-wrap text-sm">
                            {item.generationInstruction}
                          </p>
                          <p className="font-mono text-muted-foreground text-xs">
                            instruction {item.instructionHash.slice(0, 12)}… · item v
                            {item.itemVersion}
                          </p>
                          {item.dependencyKeys.length > 0 ? (
                            <p className="text-muted-foreground text-xs">
                              依赖：{item.dependencyKeys.join("、")}
                            </p>
                          ) : null}
                          {item.requiredContextKeys.length > 0 ? (
                            <p className="text-muted-foreground text-xs">
                              上下文：{item.requiredContextKeys.join("、")}
                            </p>
                          ) : null}
                        </CardContent>
                      </Card>
                    ))}

                    {proposal.status === "AWAITING_CONFIRMATION" ? (
                      <fieldset className="flex flex-col gap-2">
                        <legend className="font-medium text-sm">本次确认并启动的目标</legend>
                        <p className="text-muted-foreground text-xs">
                          勾选的目标在同一个 canonical Task 内由服务端逐个提交动作；Assistant
                          只决定推进顺序，不能超出这里的批准范围。
                        </p>
                        {proposal.items.map((item) => (
                          <label
                            key={item.itemKey}
                            className="flex items-start gap-2 text-sm"
                            htmlFor={`${approvalTargetId}-${reference}-${item.itemKey}`}
                          >
                            <Checkbox
                              id={`${approvalTargetId}-${reference}-${item.itemKey}`}
                              checked={selectedItemKeys.includes(item.itemKey)}
                              disabled={!canMutate}
                              onCheckedChange={(checked) =>
                                toggleApprovalTarget(reference, item.itemKey, checked === true)
                              }
                            />
                            <span>
                              {item.targetKey} · {item.generationInstruction.slice(0, 48)}
                            </span>
                          </label>
                        ))}
                      </fieldset>
                    ) : null}
                  </CardContent>
                  {proposal.status === "AWAITING_CONFIRMATION" ? (
                    <CardFooter className="justify-end gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => {
                          setEditor(reviseEditor(proposal))
                          setFormError(null)
                        }}
                        disabled={!canMutate || editorPending || approveAndStart.isPending}
                      >
                        <Pencil />
                        修订
                      </Button>
                      <div className="flex flex-col items-end gap-1">
                        <Button
                          type="button"
                          onClick={() => startApproval(proposal)}
                          aria-describedby={startBlockedReason ? startBlockedId : undefined}
                          disabled={
                            !canMutate ||
                            !selectedItemKeys.length ||
                            editorPending ||
                            approveAndStart.isPending ||
                            startBlockedReason !== undefined
                          }
                        >
                          {approveAndStart.isPending ? (
                            <LoaderCircle className="animate-spin" />
                          ) : (
                            <Sparkles />
                          )}
                          确认并启动
                        </Button>
                        {startBlockedReason ? (
                          <p id={startBlockedId} className="max-w-72 text-muted-foreground text-xs">
                            {startBlockedReason}
                          </p>
                        ) : null}
                      </div>
                    </CardFooter>
                  ) : null}
                </Card>
              )
            })
          )}
        </div>
      </SheetContent>
    </Sheet>
  )
}
