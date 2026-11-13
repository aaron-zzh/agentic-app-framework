/**
 * 技能根元数据与不可变版本编辑 Dialog，供技能选择器和资产页复用。
 *
 * 编辑已有技能时只更新根元数据，并基于当前内容追加新版本，不修改历史版本。
 * 工具白名单随版本提交（清空即提交空数组）；正文可从已选工具与系统内置工具中搜索插入工具名。
 *
 * @example
 * <SkillEditorDialog open={open} onOpenChange={setOpen} initial={skill} />
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useId, useRef, useState } from "react"

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger
} from "@/components/ui/accordion"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
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
import {
  type AiSkillToolAccessMode,
  type AiSkillToolRequirementInput,
  type AiSkillToolRequirementVO,
  type AiSkillVersionStatus,
  type AiSkillVisibility,
  type AiSkillVO,
  type CreateAiSkillInput,
  type UpdateAiSkillInput,
  useAiRoleToolOptions,
  useCreateAiSkill,
  useUpdateAiSkill
} from "@/lib/api/rest/ai"
import { SYSTEM_TOOL_LABELS, SYSTEM_TOOL_NAMES } from "@/lib/constants/system-tools"
import { notify } from "@/lib/notification"

import { type SkillToolInsertItem, SkillToolInsertMenu } from "./SkillToolInsertMenu"
import { type SkillToolDraft, SkillToolRequirementsField } from "./SkillToolRequirementsField"

const VISIBILITY_OPTIONS: ReadonlyArray<{ value: AiSkillVisibility; label: string }> = [
  { value: "PRIVATE", label: "仅自己" },
  { value: "WORKSPACE", label: "工作区" },
  { value: "PUBLIC", label: "公开" }
]

const VERSION_STATUS_OPTIONS: ReadonlyArray<{ value: AiSkillVersionStatus; label: string }> = [
  { value: "DRAFT", label: "草稿" },
  { value: "IN_REVIEW", label: "审核中" },
  { value: "APPROVED", label: "已通过" },
  { value: "REJECTED", label: "已拒绝" },
  { value: "RETIRED", label: "已退役" }
]

const TOOL_ACCESS_OPTIONS: ReadonlyArray<{ value: AiSkillToolAccessMode; label: string }> = [
  { value: "RESTRICT", label: "限制到版本声明的工具" },
  { value: "INHERIT", label: "继承角色与 Agent 工具范围" }
]

export interface SkillEditorDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  initial?: AiSkillVO | null
  requiredCategoryCode?: string
  onSaved?: (skill: AiSkillVO) => void
}

function optionalText(value: string): string | undefined {
  const normalized = value.trim()
  return normalized || undefined
}

function isVisibility(value: string | null): value is AiSkillVisibility {
  return value === "PRIVATE" || value === "WORKSPACE" || value === "PUBLIC"
}

function isVersionStatus(value: string | null): value is AiSkillVersionStatus {
  return (
    value === "DRAFT" ||
    value === "IN_REVIEW" ||
    value === "APPROVED" ||
    value === "REJECTED" ||
    value === "RETIRED"
  )
}

function isToolAccessMode(value: string | null): value is AiSkillToolAccessMode {
  return value === "RESTRICT" || value === "INHERIT"
}

/** 系统内置工具，供正文插入菜单使用，显示中文名。 */
const SYSTEM_TOOL_ITEMS: SkillToolInsertItem[] = Object.entries(SYSTEM_TOOL_LABELS).map(
  ([name, label]) => ({ name, label })
)

/** sortOrder 起始值与步长：按已选顺序生成 100、110、120… */
const TOOL_SORT_START = 100
const TOOL_SORT_STEP = 10

/** 正文中以反引号包裹、不含空白的 token。 */
const BACKTICK_TOKEN_PATTERN = /`([^`\s]+)`/g

/** 版本已声明的工具需求 → 表单草稿，按 sortOrder 保持原顺序。 */
function toToolDrafts(requirements: AiSkillToolRequirementVO[] | undefined): SkillToolDraft[] {
  if (!requirements) return []
  return [...requirements]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((requirement) => ({
      toolName: requirement.toolName || requirement.toolId,
      required: requirement.required
    }))
}

/** 表单草稿 → 提交入参，toolId 与 toolName 均取工具名；何时调用由正文说明，不提交用途。 */
function toToolRequirementInputs(drafts: SkillToolDraft[]): AiSkillToolRequirementInput[] {
  return drafts.map((draft, index) => ({
    toolId: draft.toolName,
    toolName: draft.toolName,
    required: draft.required,
    sortOrder: TOOL_SORT_START + index * TOOL_SORT_STEP
  }))
}

/**
 * 找出正文引用了、但未加入白名单的已知业务工具。
 * 仅匹配已知工具名，系统内置工具不需要声明，因此不提示。
 */
function findUndeclaredToolReferences(
  content: string,
  knownToolNames: ReadonlySet<string>,
  selectedToolNames: ReadonlySet<string>
): string[] {
  const missing = new Set<string>()
  for (const match of content.matchAll(BACKTICK_TOKEN_PATTERN)) {
    const token = match[1]
    if (
      knownToolNames.has(token) &&
      !selectedToolNames.has(token) &&
      !SYSTEM_TOOL_NAMES.has(token)
    ) {
      missing.add(token)
    }
  }
  return Array.from(missing)
}

export function SkillEditorDialog({
  open,
  onOpenChange,
  initial,
  requiredCategoryCode,
  onSaved
}: SkillEditorDialogProps) {
  const uid = useId()
  const latestVersion = initial?.latestVersion ?? initial?.currentVersion ?? null
  const [code, setCode] = useState("")
  const [name, setName] = useState("")
  const [summary, setSummary] = useState("")
  const [instancePrompt, setInstancePrompt] = useState("")
  const [locale, setLocale] = useState("zh-CN")
  const [visibility, setVisibility] = useState<AiSkillVisibility>("PRIVATE")
  const [content, setContent] = useState("")
  const [inputSchema, setInputSchema] = useState("")
  const [outputSchema, setOutputSchema] = useState("")
  const [toolAccessMode, setToolAccessMode] = useState<AiSkillToolAccessMode>("RESTRICT")
  const [changeSummary, setChangeSummary] = useState("")
  const [versionStatus, setVersionStatus] = useState<AiSkillVersionStatus>("DRAFT")
  const [toolDrafts, setToolDrafts] = useState<SkillToolDraft[]>([])
  const contentRef = useRef<HTMLTextAreaElement>(null)
  // 正文最近一次光标/选区位置；未与正文交互时为 null，插入工具名时追加到末尾
  const contentSelectionRef = useRef<{ start: number; end: number } | null>(null)
  const createSkill = useCreateAiSkill()
  const updateSkill = useUpdateAiSkill()
  const { data: toolOptions = [] } = useAiRoleToolOptions(open)
  // 系统内置工具由框架自动提供，不进入白名单可选项
  const businessToolOptions = toolOptions.filter((tool) => !SYSTEM_TOOL_NAMES.has(tool.name))
  const selectedToolItems: SkillToolInsertItem[] = toolDrafts.map((draft) => ({
    name: draft.toolName,
    label: toolOptions.find((tool) => tool.name === draft.toolName)?.displayName ?? draft.toolName
  }))

  useEffect(() => {
    if (!open) return
    setCode(initial?.code ?? "")
    setName(initial?.name ?? "")
    setSummary(initial?.summary ?? "")
    setInstancePrompt(initial?.instancePrompt ?? "")
    setLocale(initial?.locale ?? "zh-CN")
    setVisibility(initial?.visibility ?? "PRIVATE")
    setContent(latestVersion?.content ?? "")
    setInputSchema(latestVersion?.inputSchema ?? "")
    setOutputSchema(latestVersion?.outputSchema ?? "")
    setToolAccessMode(latestVersion?.toolAccessMode ?? "RESTRICT")
    setChangeSummary("")
    setVersionStatus(latestVersion?.status ?? "DRAFT")
    setToolDrafts(toToolDrafts(latestVersion?.toolRequirements))
    contentSelectionRef.current = null
  }, [initial, latestVersion, open])

  /** 记录正文当前光标/选区，弹出菜单夺走焦点后仍可在原位置插入。 */
  function rememberContentSelection(element: HTMLTextAreaElement) {
    contentSelectionRef.current = { start: element.selectionStart, end: element.selectionEnd }
  }

  /** 在正文光标处插入 `工具名`，插入后光标移到插入内容之后；无光标时追加到末尾。 */
  function insertToolName(toolName: string) {
    const token = `\`${toolName}\``
    const selection = contentSelectionRef.current
    const start = selection ? Math.min(selection.start, content.length) : content.length
    const end = selection ? Math.min(selection.end, content.length) : content.length
    const nextContent = content.slice(0, start) + token + content.slice(end)
    const caret = start + token.length
    setContent(nextContent)
    contentSelectionRef.current = { start: caret, end: caret }
    // 等待受控 value 提交到 DOM 后再恢复焦点与光标
    requestAnimationFrame(() => {
      const element = contentRef.current
      if (!element) return
      element.focus()
      element.setSelectionRange(caret, caret)
    })
  }

  function handleSave() {
    if (!canSave) return

    const categoryCodes = requiredCategoryCode
      ? Array.from(
          new Set([
            ...(initial?.categories.map((category) => category.code) ?? []),
            requiredCategoryCode
          ])
        )
      : undefined
    const input: CreateAiSkillInput = {
      code: code.trim(),
      name: name.trim(),
      summary: summary.trim(),
      instancePrompt: initial ? instancePrompt.trim() : optionalText(instancePrompt),
      locale: locale.trim(),
      visibility,
      ...(categoryCodes ? { categoryCodes } : {}),
      content: content.trim(),
      inputSchema: inputSchema.trim(),
      outputSchema: outputSchema.trim(),
      toolAccessMode,
      toolRequirements: toToolRequirementInputs(toolDrafts),
      changeSummary: optionalText(changeSummary),
      status: versionStatus
    }
    // 软校验：正文引用了已知业务工具但未加入白名单时提示，不阻断保存
    const undeclaredTools = findUndeclaredToolReferences(
      input.content,
      new Set(toolOptions.map((tool) => tool.name)),
      new Set(selectedToolItems.map((item) => item.name))
    )
    if (undeclaredTools.length > 0) {
      notify.warning(`正文引用了未选择的工具：${undeclaredTools.join("、")}`)
    }
    const onSuccess = (skill: AiSkillVO) => {
      onSaved?.(skill)
      onOpenChange(false)
    }

    if (initial) {
      const updateInput: UpdateAiSkillInput = input
      updateSkill.mutate({ id: initial.id, ...updateInput }, { onSuccess })
      return
    }

    createSkill.mutate(input, { onSuccess })
  }

  const isPending = createSkill.isPending || updateSkill.isPending
  const canSave =
    Boolean(code.trim()) &&
    Boolean(name.trim()) &&
    Boolean(summary.trim()) &&
    Boolean(locale.trim()) &&
    Boolean(content.trim()) &&
    (!initial || initial.ownedByCurrentUser)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{initial ? "编辑技能" : "新建技能"}</DialogTitle>
          <DialogDescription>
            {initial
              ? "根元数据将直接更新；执行内容会保存为新的不可变版本。"
              : "创建稳定技能根对象，并写入首个不可变版本。"}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-2">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-code`}>代码</Label>
              <Input
                id={`${uid}-code`}
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder="例如：social-post"
                disabled={isPending || Boolean(initial)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-name`}>名称</Label>
              <Input
                id={`${uid}-name`}
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="输入技能名称"
                disabled={isPending}
              />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-summary`}>摘要</Label>
            <Textarea
              id={`${uid}-summary`}
              value={summary}
              onChange={(event) => setSummary(event.target.value)}
              placeholder="用一句话说明适用场景，建议采用 USE WHEN ... 表述"
              className="min-h-20"
              disabled={isPending}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-instance-prompt`}>实例提示词</Label>
            <Textarea
              id={`${uid}-instance-prompt`}
              value={instancePrompt}
              onChange={(event) => setInstancePrompt(event.target.value)}
              placeholder="选择此技能后，展示在创作输入框中的示例需求"
              className="min-h-20"
              disabled={isPending}
            />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-locale`}>语言地区</Label>
              <Input
                id={`${uid}-locale`}
                value={locale}
                onChange={(event) => setLocale(event.target.value)}
                placeholder="例如：zh-CN"
                disabled={isPending}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-visibility`}>可见范围</Label>
              <Select
                value={visibility}
                onValueChange={(value) => {
                  if (isVisibility(value)) setVisibility(value)
                }}
                disabled={isPending}
              >
                <SelectTrigger id={`${uid}-visibility`} className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    {VISIBILITY_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                </SelectContent>
              </Select>
            </div>
          </div>

          <SkillToolRequirementsField
            value={toolDrafts}
            onChange={setToolDrafts}
            options={businessToolOptions}
            disabled={isPending}
          />

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between gap-2">
              <Label htmlFor={`${uid}-content`}>版本正文</Label>
              <SkillToolInsertMenu
                selectedTools={selectedToolItems}
                systemTools={SYSTEM_TOOL_ITEMS}
                onInsert={insertToolName}
                disabled={isPending}
              />
            </div>
            <Textarea
              id={`${uid}-content`}
              ref={contentRef}
              value={content}
              onChange={(event) => {
                setContent(event.target.value)
                rememberContentSelection(event.currentTarget)
              }}
              onSelect={(event) => rememberContentSelection(event.currentTarget)}
              placeholder="输入 Markdown 格式的执行知识与约束"
              className="min-h-48 font-mono"
              disabled={isPending}
            />
          </div>

          <Accordion>
            <AccordionItem value="version-contract" className="rounded-lg border px-3">
              <AccordionTrigger className="py-3 text-sm hover:no-underline">
                版本契约与治理
              </AccordionTrigger>
              <AccordionContent className="flex flex-col gap-3 pb-3">
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-tool-access`}>工具访问模式</Label>
                    <Select
                      value={toolAccessMode}
                      onValueChange={(value) => {
                        if (isToolAccessMode(value)) setToolAccessMode(value)
                      }}
                      disabled={isPending}
                    >
                      <SelectTrigger id={`${uid}-tool-access`} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {TOOL_ACCESS_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-version-status`}>版本状态</Label>
                    <Select
                      value={versionStatus}
                      onValueChange={(value) => {
                        if (isVersionStatus(value)) setVersionStatus(value)
                      }}
                      disabled={isPending}
                    >
                      <SelectTrigger id={`${uid}-version-status`} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {VERSION_STATUS_OPTIONS.map((option) => (
                            <SelectItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectItem>
                          ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-input-schema`}>输入 JSON Schema</Label>
                    <Textarea
                      id={`${uid}-input-schema`}
                      value={inputSchema}
                      onChange={(event) => setInputSchema(event.target.value)}
                      placeholder='{"type":"object"}'
                      className="min-h-32 font-mono text-xs"
                      disabled={isPending}
                    />
                  </div>
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-output-schema`}>输出 JSON Schema</Label>
                    <Textarea
                      id={`${uid}-output-schema`}
                      value={outputSchema}
                      onChange={(event) => setOutputSchema(event.target.value)}
                      placeholder='{"type":"object"}'
                      className="min-h-32 font-mono text-xs"
                      disabled={isPending}
                    />
                  </div>
                </div>

                <div className="flex flex-col gap-1.5">
                  <Label htmlFor={`${uid}-change-summary`}>版本变更说明</Label>
                  <Input
                    id={`${uid}-change-summary`}
                    value={changeSummary}
                    onChange={(event) => setChangeSummary(event.target.value)}
                    placeholder={initial ? "说明相对上一版本的变化" : "例如：初始版本"}
                    disabled={isPending}
                  />
                </div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isPending}>
            取消
          </Button>
          <Button onClick={handleSave} disabled={isPending || !canSave}>
            {isPending ? "保存中…" : initial ? "保存并创建版本" : "创建"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
