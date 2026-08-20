/**
 * 技能根元数据与不可变版本编辑 Dialog，供技能选择器和资产页复用。
 *
 * 编辑已有技能时只更新根元数据，并基于当前内容追加新版本，不修改历史版本。
 *
 * @example
 * <SkillEditorDialog open={open} onOpenChange={setOpen} initial={skill} />
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useId, useState } from "react"

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
  type AiSkillVersionStatus,
  type AiSkillVisibility,
  type AiSkillVO,
  type CreateAiSkillInput,
  type UpdateAiSkillInput,
  useCreateAiSkill,
  useUpdateAiSkill
} from "@/lib/api/rest/ai"

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
  const [locale, setLocale] = useState("zh-CN")
  const [visibility, setVisibility] = useState<AiSkillVisibility>("PRIVATE")
  const [content, setContent] = useState("")
  const [inputSchema, setInputSchema] = useState("")
  const [outputSchema, setOutputSchema] = useState("")
  const [toolAccessMode, setToolAccessMode] = useState<AiSkillToolAccessMode>("RESTRICT")
  const [changeSummary, setChangeSummary] = useState("")
  const [versionStatus, setVersionStatus] = useState<AiSkillVersionStatus>("DRAFT")
  const createSkill = useCreateAiSkill()
  const updateSkill = useUpdateAiSkill()

  useEffect(() => {
    if (!open) return
    setCode(initial?.code ?? "")
    setName(initial?.name ?? "")
    setSummary(initial?.summary ?? "")
    setLocale(initial?.locale ?? "zh-CN")
    setVisibility(initial?.visibility ?? "PRIVATE")
    setContent(latestVersion?.content ?? "")
    setInputSchema(latestVersion?.inputSchema ?? "")
    setOutputSchema(latestVersion?.outputSchema ?? "")
    setToolAccessMode(latestVersion?.toolAccessMode ?? "RESTRICT")
    setChangeSummary("")
    setVersionStatus(latestVersion?.status ?? "DRAFT")
  }, [initial, latestVersion, open])

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
      locale: locale.trim(),
      visibility,
      ...(categoryCodes ? { categoryCodes } : {}),
      content: content.trim(),
      inputSchema: inputSchema.trim(),
      outputSchema: outputSchema.trim(),
      toolAccessMode,
      changeSummary: optionalText(changeSummary),
      status: versionStatus
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

          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-content`}>版本正文</Label>
            <Textarea
              id={`${uid}-content`}
              value={content}
              onChange={(event) => setContent(event.target.value)}
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
