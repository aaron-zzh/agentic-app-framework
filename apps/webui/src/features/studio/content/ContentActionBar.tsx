/**
 * Content Studio 工作台统一创作输入区。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import {
  ChevronDown,
  ChevronUp,
  Layers,
  Library,
  LoaderCircle,
  MessageSquare,
  Paperclip,
  Sparkles
} from "lucide-react"
import { useId, useState } from "react"
import { GlassCard, NeonChip } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { ConfirmDialog } from "@/components/ui/confirm-dialog"
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { ApiError } from "@/lib/api/errors"
import type {
  AigcGenerationMode,
  AigcObjectReadiness,
  AigcProject,
  AigcProjectCurrentTask,
  AigcProjectObject,
  AigcProjectProgress
} from "@/lib/api/rest/ai/aigc"
import {
  useAigcProjectActions,
  useAigcSnippets,
  useRunAigcAction,
  useSwitchAigcProjectGenerationMode
} from "@/lib/api/rest/ai/aigc"
import { useMediaList } from "@/lib/api/rest/media"
import { notify } from "@/lib/notification"
import { useChatterStore } from "@/lib/store/chatter-store"

const CONTENT_CONFIRMATION_REQUIRED = 8_000_022

/**
 * 项目级 Assistant 聚合状态。
 *
 * <p>只映射服务端 ProjectProgressSnapshot 的权威字段，不在前端推导任务状态机；`STALE` 表示尚未取到权威投影。
 * ui-design 的 `RECOVERING` 目前没有对应服务端字段，因此不在此枚举内伪造。
 */
type ProjectTaskUiState =
  | "IDLE"
  | "STALE"
  | "PROCESSING"
  | "AWAITING_EXTERNAL"
  | "AWAITING_CONFIRMATION"
  | "PAUSED"
  | "CANCELING"

/** 禁用期间的主按钮文案；与 ui-design 的聚合状态表一一对应。 */
const BATCH_BUTTON_LABEL: Record<ProjectTaskUiState, string> = {
  IDLE: "批量生成",
  STALE: "刷新项目状态",
  PROCESSING: "Assistant 处理中…",
  AWAITING_EXTERNAL: "等待生成结果…",
  AWAITING_CONFIRMATION: "等待确认",
  PAUSED: "Task 已暂停",
  CANCELING: "正在取消任务…"
}

/**
 * canonical Task.Status 名称 → 项目级聚合状态。
 *
 * <p>`decisionBlockers` 是服务端"仍在等待外部结果且需人工决策"的权威判定，前端不另行推导等待语义；
 * 未识别的非终态一律按处理中保守禁用项目级写入口。
 */
function resolveTaskUiState(currentTask: AigcProjectCurrentTask): ProjectTaskUiState {
  switch (currentTask.status) {
    case "AWAITING_AUTHORIZATION":
    case "AWAITING_CLARIFICATION":
      return "AWAITING_CONFIRMATION"
    case "PAUSING":
    case "PAUSED":
      return "PAUSED"
    case "CANCELING":
      return "CANCELING"
    default:
      return currentTask.decisionBlockers.length > 0 ? "AWAITING_EXTERNAL" : "PROCESSING"
  }
}

/**
 * 批量生成按钮的可见说明。
 *
 * <p>只解释按钮为什么不可用以及下一步去哪，不复制任务进度：步骤与等待明细由对话流的任务面板承载，
 * 对象粒度状态由结构/图谱视图的对象行承载。同一状态下文案稳定，避免轮询重复播报。
 */
const BATCH_HINT: Record<ProjectTaskUiState, string> = {
  IDLE: "Assistant 空闲",
  STALE: "正在读取项目状态",
  PROCESSING: "Assistant 正在处理当前任务，完成后可再次批量生成",
  AWAITING_EXTERNAL: "正在等待生成结果，可安全关闭页面",
  AWAITING_CONFIRMATION: "任务等待人工确认",
  PAUSED: "任务已暂停，不会继续消耗积分",
  CANCELING: "任务正在取消"
}

export interface ContentActionBarProps {
  project: AigcProject
  focusedObject?: AigcProjectObject
  readiness?: AigcObjectReadiness
  canAction: boolean
  disabledReason?: string
  /** 项目级权威进度投影；`undefined` 表示尚未取到，此时禁用项目级写入口。 */
  progress?: AigcProjectProgress
  /** 是否允许提议并批准项目级批量生成（项目更新权限 + 生命周期可写）。 */
  canPropose: boolean
  proposalDisabledReason?: string
  /** 打开项目生成 Proposal Sheet；批量生成与查看提议共用同一入口。 */
  onOpenProposals: () => void
}

export function ContentActionBar({
  project,
  focusedObject,
  readiness,
  canAction,
  disabledReason,
  progress,
  canPropose,
  proposalDisabledReason,
  onOpenProposals
}: ContentActionBarProps) {
  const promptId = useId()
  const disabledReasonId = useId()
  const batchHintId = useId()
  const setChatterOpen = useChatterStore((state) => state.setOpen)
  const context = useBoolean(true)
  const confirmation = useBoolean(false)
  const [prompt, setPrompt] = useState("")
  const [selectedActionKey, setSelectedActionKey] = useState("")
  const [selectedSnippetIds, setSelectedSnippetIds] = useState<number[]>([])
  const [attachmentMediaVersionIds, setAttachmentMediaVersionIds] = useState<number[]>([])
  const { data: actions = [], isLoading: actionsLoading } = useAigcProjectActions(
    canAction ? project.id : null
  )
  const { data: mediaPage, isLoading: mediaLoading } = useMediaList({
    pageNo: 1,
    pageSize: 20
  })
  const { data: snippetPage, isLoading: snippetsLoading } = useAigcSnippets({
    projectTypeCode: project.projectTypeCode
  })
  const runAction = useRunAigcAction()
  const switchMode = useSwitchAigcProjectGenerationMode()
  const modeHintId = useId()
  const modeDisabledReason = !canAction ? (disabledReason ?? "无项目动作权限") : undefined

  /** 模式切换是独立的项目命令：只带 mode 与 CAS，不携带执行范围。 */
  function switchGenerationMode(mode: AigcGenerationMode) {
    if (modeDisabledReason !== undefined || mode === project.generationMode) return
    switchMode.mutate(
      {
        projectId: project.id,
        mode,
        expectedProjectVersion: project.version,
        idempotencyKey: crypto.randomUUID()
      },
      {
        onSuccess: () => notify.success(mode === "auto" ? "已切换为自动模式" : "已切换为手动模式"),
        onError: (error) =>
          notify.error(error instanceof Error ? error.message : "协作模式切换失败")
      }
    )
  }
  const snippets = snippetPage?.list ?? []
  const media = mediaPage?.list ?? []
  const writable = canAction && focusedObject !== undefined
  const currentTask = progress?.currentTask ?? null
  const taskUiState: ProjectTaskUiState =
    progress === undefined
      ? "STALE"
      : currentTask === null
        ? "IDLE"
        : resolveTaskUiState(currentTask)
  const batchHint = !canPropose
    ? (proposalDisabledReason ?? "无项目更新权限")
    : BATCH_HINT[taskUiState]
  const batchDisabled = !canPropose || taskUiState !== "IDLE"
  const allowedActionKeys = new Set(readiness?.allowedActions ?? [])
  const availableActions = focusedObject
    ? actions.filter(
        (action) =>
          allowedActionKeys.has(action.actionKey) &&
          (action.applicableObjectTypes.length === 0 ||
            action.applicableObjectTypes.includes(focusedObject.objectType))
      )
    : []
  const selectedAction =
    availableActions.find((action) => action.actionKey === selectedActionKey) ??
    availableActions.at(0)
  const unavailableMessage = disabledReason
    ? disabledReason
    : !focusedObject
      ? "先在结构或图谱中聚焦一个对象"
      : readiness?.blockedReason
        ? readiness.blockedReason
        : actionsLoading
          ? "正在加载可用动作"
          : actions.length === 0
            ? "当前项目模板未声明可用动作"
            : availableActions.length === 0
              ? "当前对象没有服务端允许的专业动作"
              : runAction.isPending
                ? "动作提交中"
                : null

  function insertSnippet(id: number, content?: string) {
    if (!content) return
    setPrompt((current) => (current.trim() ? `${current.trim()}\n\n${content}` : content))
    setSelectedSnippetIds((current) => (current.includes(id) ? current : [...current, id]))
  }

  function toggleAttachment(mediaVersionId: number, checked: boolean) {
    setAttachmentMediaVersionIds((current) =>
      checked
        ? current.includes(mediaVersionId)
          ? current
          : [...current, mediaVersionId]
        : current.filter((id) => id !== mediaVersionId)
    )
  }

  function execute(confirmed = false) {
    if (!writable || !selectedAction || !focusedObject) return
    runAction.mutate(
      {
        projectId: project.id,
        data: {
          actionKey: selectedAction.actionKey,
          objectId: focusedObject.id,
          prompt: prompt.trim() || undefined,
          attachmentMediaVersionIds,
          expectedProjectVersion: project.version,
          expectedGraphRevision: project.graphRevision,
          confirmed,
          idempotencyKey: crypto.randomUUID()
        }
      },
      {
        onSuccess: () => {
          confirmation.onFalse()
          notify.success("动作已进入执行队列")
        },
        onError: (error) => {
          if (
            !confirmed &&
            error instanceof ApiError &&
            error.code === CONTENT_CONFIRMATION_REQUIRED
          ) {
            confirmation.onTrue()
            return
          }
          notify.error(error instanceof Error ? error.message : "动作执行失败")
        }
      }
    )
  }

  return (
    <div className="border-b bg-background/60 px-4 py-3 sm:px-6">
      <GlassCard glow="none" className="mx-auto max-w-6xl">
        <div className="flex flex-col gap-3 p-3">
          <Collapsible open={context.value} onOpenChange={context.setValue}>
            <div className="flex flex-wrap items-center gap-2">
              <CollapsibleTrigger
                render={<Button type="button" variant="ghost" size="xs" />}
                aria-label={context.value ? "收起创作上下文" : "展开创作上下文"}
              >
                {context.value ? <ChevronUp /> : <ChevronDown />}
                创作上下文
              </CollapsibleTrigger>
              <CollapsibleContent className="flex flex-wrap items-center gap-2">
                <NeonChip aria-disabled="true">角色：自动</NeonChip>
                <NeonChip aria-disabled="true">技能：自动</NeonChip>
                <NeonChip aria-disabled="true">模型：自动</NeonChip>
                <span id={modeHintId} className="text-muted-foreground text-xs">
                  {modeDisabledReason ??
                    "模式只影响尚未提交的新动作；已提交 Run 按创建时模式完成，活跃任务需显式控制"}
                </span>
                <Select
                  value={project.generationMode}
                  disabled={modeDisabledReason !== undefined || switchMode.isPending}
                  onValueChange={(value) => {
                    if (value === "manual" || value === "auto") switchGenerationMode(value)
                  }}
                >
                  <SelectTrigger size="sm" className="w-32" aria-describedby={modeHintId}>
                    <SelectValue>
                      {project.generationMode === "auto" ? "模式：自动" : "模式：手动"}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectGroup>
                      <SelectItem value="manual">模式：手动</SelectItem>
                      <SelectItem value="auto">模式：自动</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </CollapsibleContent>
            </div>
          </Collapsible>

          <label htmlFor={promptId} className="sr-only">
            描述要生成或修改的内容
          </label>
          <Textarea
            id={promptId}
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            placeholder={
              focusedObject
                ? `描述要如何生成或修改「${focusedObject.title || focusedObject.stableKey}」…`
                : "先聚焦一个对象，或描述要推进的项目级动作…"
            }
            className="min-h-20 resize-y border-0 bg-transparent shadow-none focus-visible:ring-0"
            disabled={!writable}
            aria-describedby={!writable ? disabledReasonId : undefined}
          />

          <div className="flex flex-wrap items-center gap-2">
            <DropdownMenu>
              <DropdownMenuTrigger
                render={<Button type="button" variant="outline" size="sm" />}
                disabled={!writable || snippetsLoading}
                aria-describedby={!writable ? disabledReasonId : undefined}
              >
                <Library /> 片段库
                {selectedSnippetIds.length > 0 ? `(${selectedSnippetIds.length})` : null}
              </DropdownMenuTrigger>
              <DropdownMenuContent className="min-w-64">
                <DropdownMenuGroup>
                  <DropdownMenuLabel>插入到当前 Prompt</DropdownMenuLabel>
                  {snippets.length > 0 ? (
                    snippets.map((snippet) => (
                      <DropdownMenuItem
                        key={snippet.id}
                        disabled={!snippet.content}
                        onClick={() => insertSnippet(snippet.id, snippet.content)}
                      >
                        <span className="truncate">{snippet.name}</span>
                      </DropdownMenuItem>
                    ))
                  ) : (
                    <DropdownMenuItem disabled>暂无可用片段</DropdownMenuItem>
                  )}
                </DropdownMenuGroup>
              </DropdownMenuContent>
            </DropdownMenu>

            <DropdownMenu>
              <DropdownMenuTrigger
                render={<Button type="button" variant="outline" size="sm" />}
                disabled={!writable || mediaLoading}
                aria-describedby={!writable ? disabledReasonId : undefined}
              >
                <Paperclip /> 附件
                {attachmentMediaVersionIds.length > 0
                  ? `(${attachmentMediaVersionIds.length})`
                  : null}
              </DropdownMenuTrigger>
              <DropdownMenuContent className="min-w-72">
                <DropdownMenuGroup>
                  <DropdownMenuLabel>选择 MediaVersion</DropdownMenuLabel>
                  {media.length > 0 ? (
                    media.map((item) => (
                      <DropdownMenuCheckboxItem
                        key={item.id}
                        checked={attachmentMediaVersionIds.includes(item.currentVersion.id)}
                        onCheckedChange={(checked) =>
                          toggleAttachment(item.currentVersion.id, checked === true)
                        }
                      >
                        <span className="truncate">{item.name}</span>
                      </DropdownMenuCheckboxItem>
                    ))
                  ) : (
                    <DropdownMenuItem disabled>暂无可用媒体</DropdownMenuItem>
                  )}
                </DropdownMenuGroup>
              </DropdownMenuContent>
            </DropdownMenu>

            <div className="ml-auto flex min-w-0 items-center gap-2">
              {availableActions.length > 0 ? (
                <Select
                  value={selectedAction?.actionKey ?? ""}
                  onValueChange={(value) => setSelectedActionKey(value ?? "")}
                  disabled={!writable}
                >
                  <SelectTrigger
                    size="sm"
                    className="max-w-52"
                    aria-describedby={!writable ? disabledReasonId : undefined}
                  >
                    <SelectValue>{selectedAction?.label ?? "选择动作"}</SelectValue>
                  </SelectTrigger>
                  <SelectContent align="end">
                    <SelectGroup>
                      {availableActions.map((action) => (
                        <SelectItem key={action.actionKey} value={action.actionKey}>
                          {action.label}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  </SelectContent>
                </Select>
              ) : null}
              <Button
                type="button"
                disabled={!writable || !selectedAction || runAction.isPending}
                aria-describedby={unavailableMessage ? disabledReasonId : undefined}
                onClick={() => execute()}
              >
                {runAction.isPending ? <LoaderCircle className="animate-spin" /> : <Sparkles />}
                {actionsLoading ? "加载动作…" : "执行"}
              </Button>
              {unavailableMessage ? (
                <p id={disabledReasonId} className="max-w-72 text-muted-foreground text-sm">
                  {unavailableMessage}
                </p>
              ) : null}
              <Button
                type="button"
                variant="secondary"
                disabled={batchDisabled}
                aria-describedby={batchHintId}
                onClick={() => {
                  if (!batchDisabled) onOpenProposals()
                }}
              >
                <Layers />
                {BATCH_BUTTON_LABEL[taskUiState]}
              </Button>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <p
              id={batchHintId}
              role="status"
              aria-live="polite"
              className="min-w-0 flex-1 text-muted-foreground text-xs"
            >
              {batchHint}
            </p>
            {currentTask !== null ? (
              <Button type="button" variant="ghost" size="xs" onClick={() => setChatterOpen(true)}>
                <MessageSquare />
                {taskUiState === "AWAITING_CONFIRMATION" ? "查看并确认" : "查看任务"}
              </Button>
            ) : null}
          </div>
        </div>
      </GlassCard>

      <ConfirmDialog
        open={confirmation.value}
        onOpenChange={confirmation.setValue}
        title="确认执行此动作"
        description={
          selectedAction?.estimatedCredits === undefined
            ? `「${selectedAction?.label ?? "当前动作"}」需要人工确认，费用以执行结算为准。`
            : `「${selectedAction.label}」预计消耗 ${selectedAction.estimatedCredits} 积分，确认后继续执行。`
        }
        confirmText="确认执行"
        onConfirm={() => execute(true)}
      />
    </div>
  )
}
