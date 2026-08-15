/**
 * 技能创建与编辑 Dialog，供技能选择器、文案目录和资产页复用。
 *
 * @example
 * <SkillEditorDialog open={open} onOpenChange={setOpen} defaultCategory="COPYWRITING" />
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
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import {
  type AiSkillStatus,
  type AiSkillVO,
  type CreateAiSkillInput,
  useCreateAiSkill,
  useUpdateAiSkill
} from "@/lib/api/rest/ai"

export interface SkillEditorDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  initial?: AiSkillVO | null
  defaultCategory?: string
  onSaved?: (skill: AiSkillVO) => void
}

function optionalText(value: string): string | undefined {
  const normalized = value.trim()
  return normalized || undefined
}

function parsePriority(value: string): number | undefined {
  const normalized = value.trim()
  if (!normalized) return undefined
  const parsed = Number(normalized)
  return Number.isFinite(parsed) ? parsed : undefined
}

function isAiSkillStatus(value: string | null): value is AiSkillStatus {
  return value === "active" || value === "inactive"
}

export function SkillEditorDialog({
  open,
  onOpenChange,
  initial,
  defaultCategory,
  onSaved
}: SkillEditorDialogProps) {
  const uid = useId()
  const [name, setName] = useState("")
  const [category, setCategory] = useState(defaultCategory ?? "")
  const [description, setDescription] = useState("")
  const [systemPrompt, setSystemPrompt] = useState("")
  const [isPublic, setIsPublic] = useState(false)
  const [triggerIntent, setTriggerIntent] = useState("")
  const [priority, setPriority] = useState("0")
  const [status, setStatus] = useState<AiSkillStatus>("active")
  const createSkill = useCreateAiSkill()
  const updateSkill = useUpdateAiSkill()

  useEffect(() => {
    if (!open) return
    setName(initial?.name ?? "")
    setCategory(initial?.category ?? defaultCategory ?? "")
    setDescription(initial?.description ?? "")
    setSystemPrompt(initial?.systemPrompt ?? "")
    setIsPublic(initial?.isPublic ?? false)
    setTriggerIntent(initial?.triggerIntent ?? "")
    setPriority(String(initial?.priority ?? 0))
    setStatus(initial?.status ?? "active")
  }, [defaultCategory, initial, open])

  function handleSave() {
    if (!name.trim() || (initial && !initial.ownedByCurrentUser)) return

    const input: CreateAiSkillInput = {
      name: name.trim(),
      category: optionalText(category),
      description: optionalText(description),
      systemPrompt: optionalText(systemPrompt),
      isPublic,
      triggerIntent: optionalText(triggerIntent),
      priority: parsePriority(priority)
    }
    const onSuccess = (skill: AiSkillVO) => {
      onSaved?.(skill)
      onOpenChange(false)
    }

    if (initial) {
      updateSkill.mutate({ id: initial.id, ...input, status }, { onSuccess })
      return
    }
    createSkill.mutate(input, { onSuccess })
  }

  const isPending = createSkill.isPending || updateSkill.isPending
  const canSave = Boolean(name.trim()) && (!initial || initial.ownedByCurrentUser)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{initial ? "编辑技能" : "新建技能"}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4 py-2">
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

          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-category`}>分类</Label>
            <Input
              id={`${uid}-category`}
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              placeholder="例如：COPYWRITING"
              disabled={isPending}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-description`}>描述</Label>
            <Textarea
              id={`${uid}-description`}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="说明技能用途和适用场景"
              className="min-h-20"
              disabled={isPending}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`${uid}-system-prompt`}>系统提示词</Label>
            <Textarea
              id={`${uid}-system-prompt`}
              value={systemPrompt}
              onChange={(event) => setSystemPrompt(event.target.value)}
              placeholder="输入技能执行时使用的系统提示词"
              className="min-h-32"
              disabled={isPending}
            />
          </div>

          <div className="flex items-center justify-between gap-4 rounded-lg border p-3">
            <div className="flex flex-col gap-0.5">
              <Label htmlFor={`${uid}-public`}>公开</Label>
              <p className="text-muted-foreground text-xs">允许当前工作区成员发现和使用</p>
            </div>
            <Switch
              id={`${uid}-public`}
              checked={isPublic}
              onCheckedChange={setIsPublic}
              aria-label="公开技能"
              disabled={isPending}
            />
          </div>

          <Accordion>
            <AccordionItem value="advanced" className="rounded-lg border px-3">
              <AccordionTrigger className="py-3 text-sm hover:no-underline">
                高级设置
              </AccordionTrigger>
              <AccordionContent className="flex flex-col gap-3 pb-3">
                <div className="flex flex-col gap-1.5">
                  <Label htmlFor={`${uid}-intent`}>触发意图</Label>
                  <Input
                    id={`${uid}-intent`}
                    value={triggerIntent}
                    onChange={(event) => setTriggerIntent(event.target.value)}
                    placeholder="描述何时触发该技能"
                    disabled={isPending}
                  />
                </div>

                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div className="flex flex-col gap-1.5">
                    <Label htmlFor={`${uid}-priority`}>优先级</Label>
                    <Input
                      id={`${uid}-priority`}
                      type="number"
                      value={priority}
                      onChange={(event) => setPriority(event.target.value)}
                      disabled={isPending}
                    />
                  </div>
                  {initial ? (
                    <div className="flex flex-col gap-1.5">
                      <Label htmlFor={`${uid}-status`}>状态</Label>
                      <Select
                        value={status}
                        onValueChange={(value) => {
                          if (isAiSkillStatus(value)) setStatus(value)
                        }}
                        disabled={isPending}
                      >
                        <SelectTrigger id={`${uid}-status`} className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectGroup>
                            <SelectItem value="active">启用</SelectItem>
                            <SelectItem value="inactive">停用</SelectItem>
                          </SelectGroup>
                        </SelectContent>
                      </Select>
                    </div>
                  ) : null}
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
            {isPending ? "保存中…" : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
