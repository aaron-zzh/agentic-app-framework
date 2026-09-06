/**
 * UiBlockPanel——AafUiBlock v1 展示面板（AAF-114 #11407）。
 *
 * 渲染服务端投影的结构化卡片：
 * - INFO_CARD：只读展示，无交互。
 * - CHOICE / FORM：展示服务端 Clarification 的只读快照；本轮只做展示与本地未提交草稿
 *   （选中态/输入态），不接提交——提交闭环（typed value、`delegatedTaskApi.submitInput`
 *   幂等写入）属于 #11408 范围，此处不越界实现。
 *
 * 与 TaskBoardPanel 同级接入 ChatterPanel，不进入 assistant-ui 消息 content
 * （react-ag-ui 0.0.41 的 RunAggregator 不支持外部注入 part，见 design.md #11407 详细设计）。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Textarea } from "@/components/ui/textarea"
import type {
  AafUiBlock,
  AafUiBlockChoiceOption,
  AafUiBlockFormField
} from "@/features/chatter/runtime/ui-block/aaf-ui-block"
import { useAgentRunStore } from "@/features/livechat/runtime/agent-run-store"
import { cn } from "@/lib/utils"

function InfoCard({ block }: { block: Extract<AafUiBlock, { type: "INFO_CARD" }> }) {
  return (
    <div className="rounded-lg border bg-card p-3">
      <p className="font-medium text-sm">{block.title}</p>
      {block.description && (
        <p className="mt-0.5 text-muted-foreground text-xs">{block.description}</p>
      )}
      {block.items && block.items.length > 0 && (
        <dl className="mt-2 space-y-1">
          {block.items.map((item, i) => (
            <div key={`${item.label}-${i}`} className="flex justify-between gap-2 text-xs">
              <dt className="text-muted-foreground">{item.label}</dt>
              <dd className="text-right">{item.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  )
}

/** 本地未提交选中态——仅 UI 草稿，不代表服务端状态；#11408 接入提交后由服务端投影覆盖。 */
function ChoiceCard({ block }: { block: Extract<AafUiBlock, { type: "CHOICE" }> }) {
  const [selected, setSelected] = useState<Set<string>>(new Set())

  function toggle(option: AafUiBlockChoiceOption) {
    setSelected((prev) => {
      const next = new Set(block.multiple ? prev : [])
      if (next.has(option.id)) {
        next.delete(option.id)
      } else {
        next.add(option.id)
      }
      return next
    })
  }

  return (
    <div className="rounded-lg border bg-card p-3">
      <div className="flex items-center justify-between">
        <p className="font-medium text-sm">{block.title}</p>
        <Badge variant="outline">待确认</Badge>
      </div>
      <div className="mt-2 space-y-1.5">
        {block.options.map((option) => (
          <button
            key={option.id}
            type="button"
            onClick={() => toggle(option)}
            className={cn(
              "w-full rounded-md border px-2.5 py-1.5 text-left text-sm transition-colors",
              selected.has(option.id) ? "border-primary bg-primary/5" : "hover:bg-muted/50"
            )}
          >
            <span>{option.label}</span>
            {option.description && (
              <span className="block text-muted-foreground text-xs">{option.description}</span>
            )}
          </button>
        ))}
      </div>
      <p className="mt-2 text-muted-foreground text-xs">提交能力将在后续版本接入。</p>
    </div>
  )
}

/** 单个 FORM 字段的未提交草稿输入；constraints 校验留给 #11408 typed 契约落地后统一处理。 */
function FormFieldInput({
  field,
  value,
  onChange
}: {
  field: AafUiBlockFormField
  value: string
  onChange: (next: string) => void
}) {
  switch (field.type) {
    case "textarea":
      return <Textarea value={value} onChange={(e) => onChange(e.target.value)} rows={3} />
    case "number":
      return (
        <Input
          type="number"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          min={field.constraints?.min}
          max={field.constraints?.max}
        />
      )
    case "checkbox":
      return (
        <Checkbox
          checked={value === "true"}
          onCheckedChange={(checked) => onChange(checked ? "true" : "false")}
        />
      )
    case "select":
      return (
        <RadioGroup value={value} onValueChange={onChange}>
          {(field.options ?? []).map((option) => (
            <div key={option.id} className="flex items-center gap-2">
              <RadioGroupItem value={option.id} id={`${field.key}-${option.id}`} />
              <Label htmlFor={`${field.key}-${option.id}`} className="text-sm">
                {option.label}
              </Label>
            </div>
          ))}
        </RadioGroup>
      )
    default:
      return (
        <Input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          maxLength={field.constraints?.maxLength}
        />
      )
  }
}

function FormCard({ block }: { block: Extract<AafUiBlock, { type: "FORM" }> }) {
  const [draft, setDraft] = useState<Record<string, string>>({})

  return (
    <div className="rounded-lg border bg-card p-3">
      <div className="flex items-center justify-between">
        <p className="font-medium text-sm">{block.title}</p>
        <Badge variant="outline">待确认</Badge>
      </div>
      <div className="mt-2 space-y-2.5">
        {block.fields.map((field) => (
          <div key={field.key}>
            <Label className="text-xs">
              {field.label}
              {field.required && <span className="ml-0.5 text-destructive">*</span>}
            </Label>
            <div className="mt-1">
              <FormFieldInput
                field={field}
                value={draft[field.key] ?? ""}
                onChange={(next) => setDraft((prev) => ({ ...prev, [field.key]: next }))}
              />
            </div>
          </div>
        ))}
      </div>
      <p className="mt-2 text-muted-foreground text-xs">提交能力将在后续版本接入。</p>
    </div>
  )
}

export function UiBlockPanel() {
  const uiBlocks = useAgentRunStore((s) => s.uiBlocks)
  if (uiBlocks.length === 0) return null

  return (
    <div className="space-y-2 border-t px-3 py-2">
      {uiBlocks.map((block) => {
        switch (block.type) {
          case "INFO_CARD":
            return <InfoCard key={block.id} block={block} />
          case "CHOICE":
            return <ChoiceCard key={block.id} block={block} />
          case "FORM":
            return <FormCard key={block.id} block={block} />
          default:
            return null
        }
      })}
    </div>
  )
}
