/**
 * Assistant Blueprint Proposal 独立规划与确认面板。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Ban,
  Check,
  ChevronDown,
  CircleAlert,
  LoaderCircle,
  RefreshCw,
  Sparkles
} from "lucide-react"
import { useEffect, useId, useState } from "react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle
} from "@/components/ui/card"
import { Collapsible, CollapsibleContent } from "@/components/ui/collapsible"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type {
  AssistantBlueprintCreationContext,
  AssistantBlueprintProposalStatus,
  AssistantBlueprintProposalView,
  AssistantBlueprintSnapshotObject
} from "@/lib/api/rest/ai/aigc"
import {
  assistantBlueprintCreationContextFingerprint,
  useAssistantBlueprintProposals,
  useCancelAssistantBlueprintProposal,
  useConfirmAssistantBlueprintProposal,
  useReviseAssistantBlueprintProposal,
  useStartAssistantBlueprintProposal
} from "@/lib/api/rest/ai/aigc"
import { useAssistants } from "@/lib/api/rest/ai/assistant"
import { type TaskDetails, useTaskDetails } from "@/lib/api/rest/ai/task"

export interface AssistantBlueprintProposalPanelProps {
  creationContext: AssistantBlueprintCreationContext
  planningDisabledReason?: string | null
  onProposalChange: (proposal: AssistantBlueprintProposalView | null) => void
  onUseFrozenContext: (proposal: AssistantBlueprintProposalView) => void
}

const RECOVERABLE_PROPOSAL_STATUSES = new Set<AssistantBlueprintProposalStatus>([
  "PLANNING",
  "AWAITING_CONFIRMATION",
  "CONFIRMED"
])

function proposalKey(proposal: AssistantBlueprintProposalView): string {
  return `${proposal.proposalId}:${proposal.proposalVersion}`
}

function errorMessage(error: unknown): string {
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

function proposalStatusLabel(status: AssistantBlueprintProposalStatus): string {
  const labels: Record<AssistantBlueprintProposalStatus, string> = {
    PLANNING: "规划中",
    AWAITING_CONFIRMATION: "待确认",
    CONFIRMED: "已确认",
    EXPIRED: "已过期",
    SUPERSEDED: "已被新版本替代",
    MATERIALIZED: "已创建项目",
    CANCELED: "已取消",
    FAILED: "规划失败"
  }
  return labels[status]
}

function statusVariant(
  status: AssistantBlueprintProposalStatus
): "default" | "secondary" | "destructive" | "outline" {
  if (status === "CONFIRMED" || status === "MATERIALIZED") return "secondary"
  if (status === "FAILED" || status === "EXPIRED") return "destructive"
  if (status === "SUPERSEDED" || status === "CANCELED") return "outline"
  return "default"
}

function validationReason(proposal: AssistantBlueprintProposalView): string | null {
  const reason = proposal.validationResult.reason
  if (typeof reason === "string" && reason.trim()) return reason
  if (proposal.status === "EXPIRED") return `该 Proposal 已于 ${proposal.expiresAt} 过期。`
  if (proposal.status === "SUPERSEDED") return "该版本已被后续修订版本替代，请选择最新版本。"
  if (proposal.status === "CANCELED") return "用户已取消本次规划，canonical Task 已同步取消。"
  if (proposal.status === "FAILED") return "规划 Task 未生成有效结构，请重新规划。"
  return null
}

function creationContextSummary(context: AssistantBlueprintCreationContext): string {
  const specification = context.projectSpecification
  const channels = specification.channelCodes.join("、") || "无"
  const brands = context.brandProfileVersionIds.join("、") || "无"
  const documents =
    context.documents
      .map(
        (document) =>
          `${document.fieldKey}:${document.documentVersionId ?? document.documentId ?? "未绑定"}`
      )
      .join("、") || "无"
  return `${context.name}；${specification.projectTypeCode}/${specification.productionMode}；渠道 ${channels}；品牌版本 ${brands}；文档 ${documents}；封面 ${context.coverMode}`
}

function ObjectTree({ objects }: { objects: AssistantBlueprintSnapshotObject[] }) {
  const ordered = objects.toSorted((left, right) => left.orderNo - right.orderNo)
  const children = new Map<string, AssistantBlueprintSnapshotObject[]>()
  for (const object of ordered) {
    if (!object.parentKey) continue
    children.set(object.parentKey, [...(children.get(object.parentKey) ?? []), object])
  }

  function renderObject(object: AssistantBlueprintSnapshotObject) {
    const descendants = children.get(object.stableKey) ?? []
    return (
      <li key={object.stableKey} className="flex flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2 rounded-md border px-3 py-2">
          <span className="font-medium text-sm">{object.displayName}</span>
          <Badge variant="outline">{object.objectType}</Badge>
          <span className="text-muted-foreground text-xs">
            {object.contractRole} · {object.outputContract}
          </span>
        </div>
        {descendants.length > 0 ? (
          <ul className="ml-4 flex flex-col gap-1 border-l pl-3">
            {descendants.map(renderObject)}
          </ul>
        ) : null}
      </li>
    )
  }

  const roots = ordered.filter(
    (object) =>
      !object.parentKey || !objects.some((candidate) => candidate.stableKey === object.parentKey)
  )
  return roots.length > 0 ? (
    <ul className="flex flex-col gap-2">{roots.map(renderObject)}</ul>
  ) : (
    <p className="text-muted-foreground text-sm">Proposal 未包含对象结构。</p>
  )
}

function ProposalPreview({ proposal }: { proposal: AssistantBlueprintProposalView }) {
  const snapshot = proposal.normalizedSnapshot
  if (!snapshot) return null
  const confirmationActions = snapshot.actions.filter(
    (action) =>
      action.confirmationPolicy === "REQUIRED" ||
      snapshot.processPolicy.confirmationGateKeys.includes(action.actionKey)
  )

  return (
    <div className="flex flex-col gap-5">
      <section className="flex flex-col gap-2">
        <h4 className="font-medium text-sm">对象树</h4>
        <ObjectTree objects={snapshot.objectSpecs} />
      </section>

      <section className="flex flex-col gap-2">
        <h4 className="font-medium text-sm">初始化文档</h4>
        {snapshot.projectInitialization.documents.length > 0 ? (
          <ul className="flex flex-wrap gap-2">
            {snapshot.projectInitialization.documents.map((document) => (
              <li key={document.key}>
                <Badge variant="outline">
                  {document.key} · {document.referenceType}
                </Badge>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-muted-foreground text-sm">没有初始化文档键。</p>
        )}
      </section>

      <section className="flex flex-col gap-2">
        <h4 className="font-medium text-sm">关系</h4>
        {snapshot.relationSpecs.length > 0 ? (
          <ul className="flex flex-col gap-1 text-sm">
            {snapshot.relationSpecs.map((relation) => (
              <li
                key={`${relation.sourceKey}:${relation.relationType}:${relation.targetKey}`}
                className="rounded-md border px-3 py-2"
              >
                {relation.sourceKey} → {relation.relationType} → {relation.targetKey}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-muted-foreground text-sm">没有额外关系。</p>
        )}
      </section>

      <section className="flex flex-col gap-2">
        <h4 className="font-medium text-sm">交付范围</h4>
        {snapshot.deliverableSets.length > 0 ? (
          <ul className="flex flex-col gap-2">
            {snapshot.deliverableSets.map((deliverable) => (
              <li key={deliverable.setTemplateKey} className="rounded-md border px-3 py-2 text-sm">
                <p className="font-medium">{deliverable.setTemplateKey}</p>
                <p className="text-muted-foreground text-xs">
                  槽位：{deliverable.allowedSlotTemplateKeys.join("、") || "无"} · 完成：
                  {deliverable.completionMode} · 审阅：{deliverable.reviewMode}
                </p>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-muted-foreground text-sm">没有独立交付集合。</p>
        )}
      </section>

      <section className="flex flex-col gap-2">
        <h4 className="font-medium text-sm">确认点</h4>
        {confirmationActions.length > 0 ? (
          <ul className="flex flex-col gap-1 text-sm">
            {confirmationActions.map((action) => (
              <li key={action.actionKey} className="rounded-md border px-3 py-2">
                {action.actionKey} → {action.targetTemplateKey}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-muted-foreground text-sm">没有额外人工确认点。</p>
        )}
        <p className="text-muted-foreground text-xs">
          项目审阅：{snapshot.processPolicy.reviewRequired ? "需要" : "不需要"} · 发布策略：
          {snapshot.processPolicy.publicationPolicy}
        </p>
      </section>
    </div>
  )
}

function TaskAuthoritySummary({ task }: { task: TaskDetails }) {
  return (
    <Card size="sm">
      <CardHeader>
        <CardTitle>canonical Task 权威摘要</CardTitle>
        <CardDescription>
          {task.goal ?? "未提供目标"} · {taskStatusLabel(task.status)}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 text-sm">
        <section className="flex flex-col gap-2" aria-label="Task Plan">
          <h4 className="font-medium">Plan</h4>
          {task.plan ? (
            <>
              <p className="text-muted-foreground">
                revision {task.plan.revision} · {task.plan.status} · {task.plan.nodes.length} 个节点
              </p>
              <ul className="flex flex-col gap-1">
                {task.plan.nodes.map((node) => (
                  <li
                    key={node.nodeId}
                    className="flex flex-wrap items-center gap-2 rounded-md border px-3 py-2"
                  >
                    <Badge variant="outline">{node.kind}</Badge>
                    <span className="min-w-0 flex-1">{node.description}</span>
                    <Badge variant="secondary">{taskStatusLabel(node.status)}</Badge>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="text-muted-foreground">Task 尚未生成 Plan。</p>
          )}
        </section>

        <section className="flex flex-col gap-2" aria-label="Task Executions">
          <h4 className="font-medium">Executions</h4>
          {task.executions.length > 0 ? (
            <ul className="flex flex-col gap-1">
              {task.executions.map((execution) => (
                <li
                  key={execution.executionId}
                  className="flex flex-wrap items-center gap-2 rounded-md border px-3 py-2"
                >
                  <span className="min-w-0 flex-1 font-mono text-xs">
                    {execution.nodeId ?? execution.scope} · attempt {execution.attemptNo}
                  </span>
                  <Badge variant="outline">{taskStatusLabel(execution.status)}</Badge>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-muted-foreground">Task 尚无执行记录。</p>
          )}
        </section>

        <section className="flex flex-col gap-2" aria-label="Task Root Result">
          <h4 className="font-medium">Root result</h4>
          {task.rootResult ? (
            <p className="whitespace-pre-wrap break-words rounded-md border px-3 py-2">
              {task.rootResult.result}
            </p>
          ) : (
            <p className="text-muted-foreground">Task 尚无 root result。</p>
          )}
        </section>
      </CardContent>
    </Card>
  )
}

/** 通过服务端 Proposal 列表恢复规划状态，不在浏览器持久化任何 Proposal/Task/Snapshot。 */
export function AssistantBlueprintProposalPanel({
  creationContext,
  planningDisabledReason = null,
  onProposalChange,
  onUseFrozenContext
}: AssistantBlueprintProposalPanelProps) {
  const taskDetailsId = useId()
  const instructionId = useId()
  const [selectedKey, setSelectedKey] = useState("")
  const [assistantId, setAssistantId] = useState("")
  const [instruction, setInstruction] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [cancelError, setCancelError] = useState<string | null>(null)
  const [taskDetailsKey, setTaskDetailsKey] = useState<string | null>(null)
  const [cancelConfirmationKey, setCancelConfirmationKey] = useState<string | null>(null)
  const proposalsQuery = useAssistantBlueprintProposals()
  const assistantsQuery = useAssistants()
  const start = useStartAssistantBlueprintProposal()
  const revise = useReviseAssistantBlueprintProposal()
  const confirm = useConfirmAssistantBlueprintProposal()
  const cancel = useCancelAssistantBlueprintProposal()

  const proposals = proposalsQuery.data ?? []
  const currentContextFingerprint = assistantBlueprintCreationContextFingerprint(creationContext)
  const explicitlySelectedProposal = selectedKey
    ? (proposals.find((proposal) => proposalKey(proposal) === selectedKey) ?? null)
    : null
  const matchingRecoverableProposal = selectedKey
    ? null
    : (proposals.find(
        (proposal) =>
          RECOVERABLE_PROPOSAL_STATUSES.has(proposal.status) &&
          assistantBlueprintCreationContextFingerprint(proposal.creationContext) ===
            currentContextFingerprint
      ) ?? null)
  const activeProposal =
    explicitlySelectedProposal ?? matchingRecoverableProposal ?? proposals.at(0) ?? null
  const activeProposalKey = activeProposal ? proposalKey(activeProposal) : null
  const creationContextMatches =
    activeProposal !== null &&
    assistantBlueprintCreationContextFingerprint(activeProposal.creationContext) ===
      currentContextFingerprint
  const taskQuery = useTaskDetails(activeProposal?.sourceTaskId ?? null)
  const taskDetailsOpen = activeProposalKey !== null && taskDetailsKey === activeProposalKey
  const cancelDialogOpen = activeProposalKey !== null && cancelConfirmationKey === activeProposalKey
  const pending = start.isPending || revise.isPending || confirm.isPending || cancel.isPending
  const revisable =
    activeProposal !== null &&
    activeProposal.proposalHash !== null &&
    (activeProposal.status === "AWAITING_CONFIRMATION" || activeProposal.status === "CONFIRMED")
  const cancelable =
    activeProposal !== null && RECOVERABLE_PROPOSAL_STATUSES.has(activeProposal.status)
  const proposalMissingBrief =
    activeProposal?.creationContext.documents.some((document) => document.fieldKey === "brief") &&
    activeProposal.normalizedSnapshot
      ? !activeProposal.normalizedSnapshot.projectInitialization.documents.some(
          (document) => document.key === "brief"
        )
      : false
  const confirmable =
    activeProposal?.status === "AWAITING_CONFIRMATION" &&
    activeProposal.proposalHash !== null &&
    !proposalMissingBrief
  const startDisabledReason = planningDisabledReason
    ? planningDisabledReason
    : assistantsQuery.isLoading
      ? "正在加载可用 Assistant。"
      : (assistantsQuery.data ?? []).length === 0
        ? "当前没有可执行的 canonical Assistant。"
        : !assistantId
          ? "请先选择执行 Assistant。"
          : !instruction.trim()
            ? "请先填写项目结构规划要求。"
            : pending
              ? "正在提交当前操作。"
              : null
  const reviseDisabledReason = !activeProposal
    ? "尚无可修订的 Proposal。"
    : pending
      ? "正在提交当前操作。"
      : activeProposal.status !== "AWAITING_CONFIRMATION" && activeProposal.status !== "CONFIRMED"
        ? `当前状态“${proposalStatusLabel(activeProposal.status)}”不允许修订。`
        : activeProposal.proposalHash === null
          ? "服务端尚未签发 hash，暂不能修订。"
          : !instruction.trim()
            ? "请填写新的完整规划要求。"
            : null
  const confirmDisabledReason = !activeProposal
    ? "尚无可确认的 Proposal。"
    : pending
      ? "正在提交当前操作。"
      : activeProposal.status !== "AWAITING_CONFIRMATION"
        ? `当前状态“${proposalStatusLabel(activeProposal.status)}”不允许确认。`
        : activeProposal.proposalHash === null
          ? "服务端尚未签发 hash，暂不能确认。"
          : proposalMissingBrief
            ? "Proposal 缺少当前创建所需的 brief 文档键。"
            : null
  const cancelDisabledReason = pending ? "正在提交当前操作。" : null

  useEffect(() => {
    if (!assistantId && assistantsQuery.data?.[0]) {
      setAssistantId(assistantsQuery.data[0].assistantId)
    }
  }, [assistantId, assistantsQuery.data])

  useEffect(() => {
    onProposalChange(activeProposal)
  }, [activeProposal, onProposalChange])

  function selectProposal(proposal: AssistantBlueprintProposalView) {
    setSelectedKey(proposalKey(proposal))
    setError(null)
    setCancelError(null)
  }

  function selectProposalKey(value: string | null) {
    setSelectedKey(value ?? "")
    setError(null)
    setCancelError(null)
  }

  function startPlanning() {
    if (startDisabledReason) return
    start.mutate(
      { assistantId, instruction: instruction.trim(), creationContext },
      {
        onSuccess: selectProposal,
        onError: (failure) => setError(errorMessage(failure))
      }
    )
  }

  function revisePlanning() {
    if (!revisable || !activeProposal?.proposalHash || !instruction.trim()) return
    revise.mutate(
      {
        proposalId: activeProposal.proposalId,
        proposalVersion: activeProposal.proposalVersion,
        proposalHash: activeProposal.proposalHash,
        instruction: instruction.trim()
      },
      {
        onSuccess: (proposal) => {
          selectProposal(proposal)
          onProposalChange(proposal)
        },
        onError: (failure) => setError(errorMessage(failure))
      }
    )
  }

  function confirmPlanning() {
    if (!confirmable || !activeProposal?.proposalHash) return
    confirm.mutate(
      {
        proposalId: activeProposal.proposalId,
        proposalVersion: activeProposal.proposalVersion,
        proposalHash: activeProposal.proposalHash
      },
      {
        onSuccess: (proposal) => {
          selectProposal(proposal)
          onProposalChange(proposal)
        },
        onError: (failure) => setError(errorMessage(failure))
      }
    )
  }

  function cancelPlanning() {
    if (!cancelable || !activeProposal) return
    setCancelError(null)
    cancel.mutate(
      {
        proposalId: activeProposal.proposalId,
        proposalVersion: activeProposal.proposalVersion,
        proposalHash: activeProposal.proposalHash
      },
      {
        onSuccess: (proposal) => {
          selectProposal(proposal)
          onProposalChange(proposal)
        },
        onError: (failure) => setCancelError(errorMessage(failure))
      }
    )
  }

  const terminalReason = activeProposal ? validationReason(activeProposal) : null

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles /> Assistant 项目结构规划
        </CardTitle>
        <CardDescription>
          Assistant 只提交逻辑结构，服务端校验并签发 Proposal。已选择的 Brief 必须在 Proposal
          的项目初始化文档中保留同一个 <code>brief</code> 键。
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {error || proposalsQuery.error ? (
          <Alert variant="destructive" role="alert">
            <CircleAlert />
            <AlertTitle>结构规划操作失败</AlertTitle>
            <AlertDescription>{error ?? errorMessage(proposalsQuery.error)}</AlertDescription>
          </Alert>
        ) : null}
        {cancelError ? (
          <Alert variant="destructive" role="alert">
            <CircleAlert />
            <AlertTitle>取消规划失败</AlertTitle>
            <AlertDescription>{cancelError}</AlertDescription>
          </Alert>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="flex flex-col gap-1">
            <span className="font-medium text-sm">执行 Assistant</span>
            <Select
              value={assistantId}
              onValueChange={(value) => setAssistantId(value ?? "")}
              disabled={assistantsQuery.isLoading || pending}
            >
              <SelectTrigger className="w-full" aria-label="结构规划 Assistant">
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
          </div>

          <div className="flex flex-col gap-1">
            <span className="font-medium text-sm">服务端 Proposal 版本</span>
            <Select
              value={activeProposal ? proposalKey(activeProposal) : ""}
              onValueChange={selectProposalKey}
              disabled={proposalsQuery.isLoading || proposals.length === 0 || pending}
            >
              <SelectTrigger className="w-full" aria-label="Blueprint Proposal 版本">
                <SelectValue placeholder={proposalsQuery.isLoading ? "正在恢复…" : "尚未规划"} />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  {proposals.map((proposal) => (
                    <SelectItem key={proposalKey(proposal)} value={proposalKey(proposal)}>
                      {proposal.projectName} · v{proposal.proposalVersion} ·{" "}
                      {proposalStatusLabel(proposal.status)}
                    </SelectItem>
                  ))}
                </SelectGroup>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <Label htmlFor={instructionId} className="font-medium text-sm">
            项目结构规划要求
          </Label>
          <Textarea
            id={instructionId}
            value={instruction}
            onChange={(event) => setInstruction(event.target.value)}
            placeholder="描述对象层级、关系、交付范围、流程与确认点；修订时填写新的完整要求。"
            className="min-h-28 font-normal"
            maxLength={4000}
            disabled={pending}
          />
        </div>

        {startDisabledReason ? (
          <p className="text-muted-foreground text-xs">{startDisabledReason}</p>
        ) : null}

        {activeProposal ? (
          <div
            className="flex flex-col gap-4 rounded-lg border p-4"
            role="status"
            aria-live="polite"
          >
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={statusVariant(activeProposal.status)}>
                Proposal v{activeProposal.proposalVersion} ·{" "}
                {proposalStatusLabel(activeProposal.status)}
              </Badge>
              <span className="font-mono text-muted-foreground text-xs">
                {activeProposal.proposalHash
                  ? `${activeProposal.proposalHash.slice(0, 12)}…`
                  : "等待服务端签发 hash"}
              </span>
              {activeProposal.estimatedCredits !== null ? (
                <span className="text-muted-foreground text-xs">
                  预计 {activeProposal.estimatedCredits} 积分
                </span>
              ) : null}
            </div>

            {!creationContextMatches ? (
              <Alert>
                <CircleAlert />
                <AlertTitle>此 Proposal 使用不同的冻结创建上下文</AlertTitle>
                <AlertDescription className="flex flex-col gap-3">
                  <div className="flex flex-col gap-1 text-xs">
                    <p>
                      <strong>当前表单：</strong>
                      {creationContextSummary(creationContext)}
                    </p>
                    <p>
                      <strong>Proposal 冻结事实：</strong>
                      {creationContextSummary(activeProposal.creationContext)}
                    </p>
                  </div>
                  <div>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => onUseFrozenContext(activeProposal)}
                    >
                      使用此 Proposal 冻结上下文
                    </Button>
                  </div>
                </AlertDescription>
              </Alert>
            ) : null}

            <Alert>
              <AlertTitle>canonical Task · {activeProposal.sourceTaskId}</AlertTitle>
              <AlertDescription className="flex flex-col items-start gap-2">
                <span>
                  {taskQuery.isLoading
                    ? "正在读取 Task 状态…"
                    : taskQuery.data
                      ? `状态：${taskStatusLabel(taskQuery.data.status)}`
                      : "Task 状态暂不可用。"}
                </span>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  aria-expanded={taskDetailsOpen}
                  aria-controls={taskDetailsId}
                  disabled={taskQuery.isLoading || !taskQuery.data}
                  onClick={() => setTaskDetailsKey(taskDetailsOpen ? null : activeProposalKey)}
                >
                  <ChevronDown className={taskDetailsOpen ? "rotate-180" : undefined} />
                  {taskDetailsOpen ? "收起 Task" : "查看 Task"}
                </Button>
                {taskQuery.isLoading ? (
                  <span className="text-muted-foreground text-xs">Task 正在加载，暂不能展开。</span>
                ) : !taskQuery.data ? (
                  <span className="text-muted-foreground text-xs">
                    Task 详情不可用，暂不能展开。
                  </span>
                ) : null}
              </AlertDescription>
            </Alert>

            {taskQuery.error ? (
              <Alert variant="destructive" role="alert">
                <CircleAlert />
                <AlertTitle>Task 详情读取失败</AlertTitle>
                <AlertDescription>{errorMessage(taskQuery.error)}</AlertDescription>
              </Alert>
            ) : null}

            <Collapsible open={taskDetailsOpen}>
              <CollapsibleContent id={taskDetailsId}>
                {taskQuery.data ? <TaskAuthoritySummary task={taskQuery.data} /> : null}
              </CollapsibleContent>
            </Collapsible>

            {activeProposal.status === "PLANNING" ? (
              <p className="text-muted-foreground text-sm">
                Assistant 正在规划并由服务端校验结构；完成后将在原位展示可确认预览。
              </p>
            ) : null}

            {terminalReason ? (
              <Alert variant={activeProposal.status === "SUPERSEDED" ? "default" : "destructive"}>
                <CircleAlert />
                <AlertTitle>{proposalStatusLabel(activeProposal.status)}</AlertTitle>
                <AlertDescription>{terminalReason}</AlertDescription>
              </Alert>
            ) : null}

            {activeProposal.status === "AWAITING_CONFIRMATION" ||
            activeProposal.status === "CONFIRMED" ? (
              <>
                <Alert>
                  <AlertTitle>
                    服务端校验：{activeProposal.validationResult.valid === true ? "通过" : "待复核"}
                  </AlertTitle>
                  <AlertDescription>
                    <ul className="flex flex-col gap-1">
                      {Object.entries(activeProposal.validationResult).map(([key, value]) => (
                        <li key={key}>
                          {key}：
                          {value !== null && typeof value === "object"
                            ? JSON.stringify(value)
                            : String(value)}
                        </li>
                      ))}
                    </ul>
                  </AlertDescription>
                </Alert>
                <ProposalPreview proposal={activeProposal} />
                {proposalMissingBrief ? (
                  <Alert variant="destructive" role="alert">
                    <CircleAlert />
                    <AlertTitle>Proposal 缺少 brief 文档键</AlertTitle>
                    <AlertDescription>
                      当前创建上下文已选择 Brief 精确版本，请修订 Proposal，并在项目初始化文档中保留
                      <code>brief</code> 键后再确认。
                    </AlertDescription>
                  </Alert>
                ) : null}
              </>
            ) : null}

            {activeProposal.status === "CONFIRMED" ? (
              <Alert>
                <Check />
                <AlertTitle>结构已确认</AlertTitle>
                <AlertDescription>
                  当前 Proposal 三元凭据可用于项目物化；修改规划要求会生成新版本并使旧确认失效。
                </AlertDescription>
              </Alert>
            ) : null}
          </div>
        ) : (
          <p className="text-muted-foreground text-sm" role="status" aria-live="polite">
            尚无服务端 Proposal。填写要求并启动规划后，可刷新页面继续查看。
          </p>
        )}

        <div className="flex flex-col gap-1" aria-live="polite">
          {reviseDisabledReason ? (
            <p className="text-muted-foreground text-xs">修订不可用：{reviseDisabledReason}</p>
          ) : null}
          {confirmDisabledReason ? (
            <p className="text-muted-foreground text-xs">确认不可用：{confirmDisabledReason}</p>
          ) : null}
          {cancelable && cancelDisabledReason ? (
            <p className="text-muted-foreground text-xs">取消不可用：{cancelDisabledReason}</p>
          ) : null}
        </div>
      </CardContent>
      <CardFooter className="flex-wrap justify-end gap-2">
        <Button
          type="button"
          variant="outline"
          onClick={startPlanning}
          disabled={startDisabledReason !== null}
        >
          {start.isPending ? <LoaderCircle className="animate-spin" /> : <RefreshCw />}
          {activeProposal ? "重新规划" : "开始规划"}
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={revisePlanning}
          disabled={reviseDisabledReason !== null}
        >
          {revise.isPending ? <LoaderCircle className="animate-spin" /> : <Sparkles />}
          提交修订
        </Button>
        {cancelable ? (
          <Button
            type="button"
            variant="outline"
            onClick={() => setCancelConfirmationKey(activeProposalKey)}
            disabled={cancelDisabledReason !== null}
          >
            {cancel.isPending ? <LoaderCircle className="animate-spin" /> : <Ban />}
            取消规划
          </Button>
        ) : null}
        <Button type="button" onClick={confirmPlanning} disabled={confirmDisabledReason !== null}>
          {confirm.isPending ? <LoaderCircle className="animate-spin" /> : <Check />}
          确认结构
        </Button>
      </CardFooter>
      <ConfirmDialog
        open={cancelDialogOpen}
        onOpenChange={(open) =>
          setCancelConfirmationKey(open && activeProposalKey ? activeProposalKey : null)
        }
        title="取消当前结构规划"
        description="取消后当前 Proposal 将进入已取消终态，canonical Task 同步取消且不能再用于项目创建；如仍需规划，可重新发起新的 Proposal。"
        confirmText="确认取消规划"
        variant="destructive"
        onConfirm={cancelPlanning}
      />
    </Card>
  )
}
