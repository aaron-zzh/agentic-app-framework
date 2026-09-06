/**
 * Content Studio 工作台统一创作输入区。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { ChevronDown, ChevronUp, Library, LoaderCircle, Paperclip, Sparkles } from "lucide-react"
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
import type { AigcProject, AigcProjectObject } from "@/lib/api/rest/ai/aigc"
import { useAigcProjectActions, useAigcSnippets, useRunAigcAction } from "@/lib/api/rest/ai/aigc"
import { useMediaList } from "@/lib/api/rest/media"
import { notify } from "@/lib/notification"

const CONTENT_CONFIRMATION_REQUIRED = 8_000_022

export interface ContentActionBarProps {
  project: AigcProject
  focusedObject?: AigcProjectObject
  canAction: boolean
}

export function ContentActionBar({ project, focusedObject, canAction }: ContentActionBarProps) {
  const promptId = useId()
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
  const snippets = snippetPage?.list ?? []
  const media = mediaPage?.list ?? []
  const writable = canAction
  const availableActions = focusedObject
    ? actions.filter(
        (action) =>
          action.applicableObjectTypes.length === 0 ||
          action.applicableObjectTypes.includes(focusedObject.objectType)
      )
    : actions.filter((action) => action.applicableObjectTypes.length === 0)
  const selectedAction =
    availableActions.find((action) => action.actionKey === selectedActionKey) ??
    availableActions.at(0)
  const unavailableMessage =
    !actionsLoading && actions.length === 0
      ? "当前项目蓝图未声明可用动作"
      : !actionsLoading && availableActions.length === 0
        ? focusedObject
          ? "当前对象没有适用动作"
          : "当前项目没有适用的项目级动作"
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
    if (!writable || !selectedAction) return
    runAction.mutate(
      {
        projectId: project.id,
        data: {
          actionKey: selectedAction.actionKey,
          objectId: focusedObject?.id,
          prompt: prompt.trim() || undefined,
          attachmentMediaVersionIds,
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
              <CollapsibleContent className="flex flex-wrap gap-2">
                <NeonChip aria-disabled="true">角色：自动</NeonChip>
                <NeonChip tone="cyan">领域：{project.domainExtensionCode || "通用"}</NeonChip>
                <NeonChip aria-disabled="true">技能：自动</NeonChip>
                <NeonChip aria-disabled="true">模型：自动</NeonChip>
                <NeonChip tone={project.generationMode === "auto" ? "violet" : "neutral"}>
                  模式：{project.generationMode === "auto" ? "自动" : "手动"}
                </NeonChip>
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
          />
          {!writable ? (
            <p className="text-amber-600 text-sm">
              当前项目已进入 {project.status} 阶段，创作与执行动作只读。
            </p>
          ) : null}

          <div className="flex flex-wrap items-center gap-2">
            <DropdownMenu>
              <DropdownMenuTrigger
                render={<Button type="button" variant="outline" size="sm" />}
                disabled={!writable || snippetsLoading}
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
              {unavailableMessage ? (
                <p className="text-muted-foreground text-sm">{unavailableMessage}</p>
              ) : (
                <>
                  {availableActions.length > 0 ? (
                    <Select
                      value={selectedAction?.actionKey ?? ""}
                      onValueChange={(value) => setSelectedActionKey(value ?? "")}
                      disabled={!writable}
                    >
                      <SelectTrigger size="sm" className="max-w-52">
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
                    onClick={() => execute()}
                  >
                    {runAction.isPending ? <LoaderCircle className="animate-spin" /> : <Sparkles />}
                    {actionsLoading ? "加载动作…" : "执行"}
                  </Button>
                </>
              )}
            </div>
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
