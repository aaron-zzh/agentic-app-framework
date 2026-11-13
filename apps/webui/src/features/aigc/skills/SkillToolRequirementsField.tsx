/**
 * 技能版本工具白名单字段：从平台可选业务工具中搜索、批量勾选，并设置每个已选工具是否必需。
 *
 * 可选项由调用方传入（已排除系统内置工具）；已选顺序即提交时的 sortOrder 顺序。
 * 何时调用哪个工具写在技能正文里，这里不单独填写用途。界面优先显示中文名，提交仍用工具名。
 *
 * @example
 * <SkillToolRequirementsField value={tools} onChange={setTools} options={businessTools} />
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { useState } from "react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Switch } from "@/components/ui/switch"
import type { AiRoleToolOption } from "@/lib/api/rest/ai/role"

/** 表单内的工具需求草稿，toolId 与 toolName 提交时均取工具名。 */
export interface SkillToolDraft {
  toolName: string
  required: boolean
}

export interface SkillToolRequirementsFieldProps {
  value: SkillToolDraft[]
  onChange: (value: SkillToolDraft[]) => void
  /** 可选业务工具（不含系统内置工具） */
  options: AiRoleToolOption[]
  disabled?: boolean
}

/** 工具读写与确认属性标签。 */
function ToolBadges({ option }: { option: AiRoleToolOption | undefined }) {
  if (!option) return <Badge variant="outline">不在可选范围</Badge>
  return (
    <>
      <Badge variant="secondary">{option.readOnly ? "只读" : "写入"}</Badge>
      {option.requireConfirm && <Badge variant="secondary">需确认</Badge>}
      {option.riskLevel && <Badge variant="outline">{option.riskLevel}</Badge>}
    </>
  )
}

/** 工具名称展示：中文名为主，工具名为副；没有中文名时只显示工具名。 */
function ToolName({ toolName, displayName }: { toolName: string; displayName: string | null }) {
  if (!displayName) return <code className="truncate font-mono text-sm">{toolName}</code>
  return (
    <span className="flex min-w-0 items-baseline gap-2">
      <span className="truncate text-sm">{displayName}</span>
      <code className="truncate font-mono text-muted-foreground text-xs">{toolName}</code>
    </span>
  )
}

export function SkillToolRequirementsField({
  value,
  onChange,
  options,
  disabled
}: SkillToolRequirementsFieldProps) {
  const [query, setQuery] = useState("")
  const optionsByName = new Map(options.map((option) => [option.name, option]))
  const selectedNames = new Set(value.map((item) => item.toolName))
  const keyword = query.trim().toLowerCase()
  const filteredOptions = keyword
    ? options.filter(
        (option) =>
          option.name.toLowerCase().includes(keyword) ||
          (option.displayName?.toLowerCase().includes(keyword) ?? false) ||
          (option.category?.toLowerCase().includes(keyword) ?? false)
      )
    : options
  const unselectedFiltered = filteredOptions.filter((option) => !selectedNames.has(option.name))

  const toggleTool = (toolName: string, checked: boolean) => {
    onChange(
      checked
        ? [...value, { toolName, required: true }]
        : value.filter((item) => item.toolName !== toolName)
    )
  }

  const selectAllFiltered = () => {
    onChange([
      ...value,
      ...unselectedFiltered.map((option) => ({ toolName: option.name, required: true }))
    ])
  }

  const updateDraft = (toolName: string, patch: Partial<SkillToolDraft>) => {
    onChange(value.map((item) => (item.toolName === toolName ? { ...item, ...patch } : item)))
  }

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="font-medium text-sm">工具白名单（{value.length}）</legend>
      <p className="text-muted-foreground text-xs">
        声明技能所需的业务工具，何时调用写在正文里；系统内置工具由框架自动提供，无需选择。
      </p>

      <div className="flex flex-wrap items-center gap-2">
        <Input
          aria-label="搜索可选工具"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="搜索中文名、工具名或分类"
          className="h-8 min-w-48 flex-1"
          disabled={disabled}
        />
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={selectAllFiltered}
          disabled={disabled || unselectedFiltered.length === 0}
        >
          全选当前结果
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => onChange([])}
          disabled={disabled || value.length === 0}
        >
          清空已选
        </Button>
      </div>

      {options.length === 0 ? (
        <p className="text-muted-foreground text-sm">暂无可选择的工具</p>
      ) : filteredOptions.length === 0 ? (
        <p className="text-muted-foreground text-sm">无匹配工具</p>
      ) : (
        <ScrollArea className="max-h-48 rounded-md border">
          <ul className="grid gap-2 p-2 sm:grid-cols-2">
            {filteredOptions.map((tool) => (
              <li key={tool.name} className="flex items-start gap-3 rounded-md border p-2">
                <Checkbox
                  checked={selectedNames.has(tool.name)}
                  aria-label={`选择工具 ${tool.displayName ?? tool.name}`}
                  onCheckedChange={(checked) => toggleTool(tool.name, checked === true)}
                  disabled={disabled}
                />
                <div className="min-w-0 flex-1">
                  <ToolName toolName={tool.name} displayName={tool.displayName} />
                  <div className="flex flex-wrap gap-1 pt-1">
                    <ToolBadges option={tool} />
                  </div>
                </div>
              </li>
            ))}
          </ul>
        </ScrollArea>
      )}

      {value.length > 0 && (
        <ul className="flex flex-col gap-2">
          {value.map((item) => {
            const option = optionsByName.get(item.toolName)
            const label = option?.displayName ?? item.toolName
            return (
              <li
                key={item.toolName}
                className="flex flex-wrap items-center gap-2 rounded-md border p-2"
              >
                <ToolName toolName={item.toolName} displayName={option?.displayName ?? null} />
                <ToolBadges option={option} />
                <div className="ml-auto flex items-center gap-2 text-xs">
                  <span>必需</span>
                  <Switch
                    checked={item.required}
                    aria-label={`${label} 是否必需`}
                    onCheckedChange={(checked) => updateDraft(item.toolName, { required: checked })}
                    disabled={disabled}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`移除工具 ${label}`}
                    onClick={() => toggleTool(item.toolName, false)}
                    disabled={disabled}
                  >
                    <X />
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </fieldset>
  )
}
