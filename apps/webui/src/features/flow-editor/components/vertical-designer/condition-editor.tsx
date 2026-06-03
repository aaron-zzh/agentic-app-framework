/**
 * 条件表达式编辑器——可视化构建 ConditionGroup
 * @author Kiro
 */

"use client"

import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import type { ConditionExpression, ConditionGroup, FormFieldDef } from "./types"

/** 运算符选项 */
const OPERATORS: { value: ConditionExpression["operator"]; label: string }[] = [
  { value: "EQ", label: "等于" },
  { value: "NEQ", label: "不等于" },
  { value: "GT", label: "大于" },
  { value: "GTE", label: "大于等于" },
  { value: "LT", label: "小于" },
  { value: "LTE", label: "小于等于" },
  { value: "IN", label: "包含于" },
  { value: "CONTAINS", label: "包含" }
]

interface ConditionEditorProps {
  value: ConditionGroup
  onChange: (value: ConditionGroup) => void
  fields: FormFieldDef[]
}

export function ConditionEditor({ value, onChange, fields }: ConditionEditorProps) {
  /** 切换逻辑运算符 */
  function toggleLogic() {
    onChange({ ...value, logic: value.logic === "AND" ? "OR" : "AND" })
  }

  /** 添加条件行 */
  function addCondition() {
    onChange({
      ...value,
      conditions: [...value.conditions, { field: "", operator: "EQ", value: "" }]
    })
  }

  /** 更新条件行 */
  function updateCondition(index: number, patch: Partial<ConditionExpression>) {
    const conditions = value.conditions.map((c, i) => (i === index ? { ...c, ...patch } : c))
    onChange({ ...value, conditions })
  }

  /** 删除条件行 */
  function removeCondition(index: number) {
    onChange({ ...value, conditions: value.conditions.filter((_, i) => i !== index) })
  }

  /** 添加子条件组 */
  function addGroup() {
    onChange({
      ...value,
      groups: [...value.groups, { logic: "AND", conditions: [], groups: [] }]
    })
  }

  /** 更新子条件组 */
  function updateGroup(index: number, group: ConditionGroup) {
    const groups = value.groups.map((g, i) => (i === index ? group : g))
    onChange({ ...value, groups })
  }

  /** 删除子条件组 */
  function removeGroup(index: number) {
    onChange({ ...value, groups: value.groups.filter((_, i) => i !== index) })
  }

  return (
    <div className="space-y-3 rounded-md border p-3">
      {/* 逻辑切换 */}
      <div className="flex items-center gap-2">
        <span className="text-muted-foreground text-xs">满足以下</span>
        <Button variant="outline" size="sm" className="h-6 px-2 text-xs" onClick={toggleLogic}>
          {value.logic === "AND" ? "所有" : "任一"}
        </Button>
        <span className="text-muted-foreground text-xs">条件</span>
      </div>

      {/* 条件行列表 */}
      {value.conditions.map((cond, idx) => (
        <div key={`cond-${idx}`} className="flex items-center gap-2">
          {/* 字段选择 */}
          <Select
            value={cond.field}
            onValueChange={(v) => updateCondition(idx, { field: v ?? undefined })}
          >
            <SelectTrigger className="h-8 w-28 text-xs">
              <SelectValue placeholder="选择字段" />
            </SelectTrigger>
            <SelectContent>
              {fields.map((f) => (
                <SelectItem key={f.name} value={f.name}>
                  {f.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* 运算符 */}
          <Select
            value={cond.operator}
            onValueChange={(v) =>
              updateCondition(idx, { operator: v as ConditionExpression["operator"] })
            }
          >
            <SelectTrigger className="h-8 w-24 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {OPERATORS.map((op) => (
                <SelectItem key={op.value} value={op.value}>
                  {op.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* 值输入 */}
          <input
            className="h-8 flex-1 rounded-md border border-input px-2 text-xs"
            value={cond.value}
            onChange={(e) => updateCondition(idx, { value: e.target.value })}
            placeholder="值"
          />

          {/* 删除 */}
          <Button
            variant="ghost"
            size="sm"
            className="h-6 w-6 p-0 text-muted-foreground hover:text-destructive"
            onClick={() => removeCondition(idx)}
          >
            ×
          </Button>
        </div>
      ))}

      {/* 嵌套子组 */}
      {value.groups.map((group, idx) => (
        <div key={`group-${idx}`} className="relative ml-4">
          <Button
            variant="ghost"
            size="sm"
            className="absolute -top-1 -right-1 h-5 w-5 p-0 text-muted-foreground text-xs hover:text-destructive"
            onClick={() => removeGroup(idx)}
          >
            ×
          </Button>
          <ConditionEditor value={group} onChange={(g) => updateGroup(idx, g)} fields={fields} />
        </div>
      ))}

      {/* 操作按钮 */}
      <div className="flex gap-2">
        <Button variant="outline" size="sm" className="h-7 text-xs" onClick={addCondition}>
          + 添加条件
        </Button>
        <Button variant="outline" size="sm" className="h-7 text-xs" onClick={addGroup}>
          + 添加条件组
        </Button>
      </div>
    </div>
  )
}
