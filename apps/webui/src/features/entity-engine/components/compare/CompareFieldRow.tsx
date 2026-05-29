/**
 * 单字段对比行——展示两条记录同一字段的值差异
 * @author AaronZZH & Kiro
 */

"use client"

import { Badge } from "@/components/ui/badge"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { cn } from "@/lib/utils/index"

import type { FieldDef } from "../../types"

/** 对比状态 */
export type CompareStatus = "equal" | "different" | "similar"

export interface CompareFieldRowProps {
  field: FieldDef
  leftValue: unknown
  rightValue: unknown
  status: CompareStatus
  /** 合并模式下选中的侧 */
  mergeSelection?: "left" | "right"
  /** 合并模式下切换选中 */
  onMergeSelect?: (side: "left" | "right") => void
  /** 是否处于合并模式 */
  merging?: boolean
}

/** 状态对应的样式和标签 */
const statusConfig: Record<
  CompareStatus,
  { label: string; variant: "default" | "destructive" | "outline"; className: string }
> = {
  equal: {
    label: "=",
    variant: "outline",
    className: "text-green-600 border-green-300 bg-green-50"
  },
  different: { label: "≠", variant: "destructive", className: "" },
  similar: {
    label: "≈",
    variant: "default",
    className: "bg-yellow-500 text-white hover:bg-yellow-600"
  }
}

/** 格式化字段值为可展示字符串 */
function formatValue(value: unknown): string {
  if (value === null || value === undefined) return "—"
  if (typeof value === "boolean") return value ? "是" : "否"
  if (Array.isArray(value)) return value.join(", ")
  if (typeof value === "object") return JSON.stringify(value)
  return String(value)
}

/** 单字段对比行 */
export function CompareFieldRow({
  field,
  leftValue,
  rightValue,
  status,
  mergeSelection,
  onMergeSelect,
  merging = false
}: CompareFieldRowProps) {
  const config = statusConfig[status]
  const label = "name" in field ? (field.label ?? field.name) : ""

  return (
    <div className="grid grid-cols-[1fr_auto_1fr_auto] items-center gap-3 border-b px-4 py-3 last:border-b-0">
      {/* 左侧值 */}
      <div
        className={cn(
          "rounded-md px-3 py-2 text-sm",
          merging && mergeSelection === "left" && "bg-primary/10 ring-1 ring-primary",
          merging && mergeSelection !== "left" && "opacity-60"
        )}
      >
        <p className="mb-1 text-muted-foreground text-xs">{label}</p>
        <p className="break-all">{formatValue(leftValue)}</p>
        {merging && (
          <RadioGroup
            value={mergeSelection ?? ""}
            onValueChange={() => onMergeSelect?.("left")}
            className="mt-2"
          >
            <RadioGroupItem value="left" />
          </RadioGroup>
        )}
      </div>

      {/* 状态标记 */}
      <Badge className={cn("shrink-0", config.className)} variant={config.variant}>
        {config.label}
      </Badge>

      {/* 右侧值 */}
      <div
        className={cn(
          "rounded-md px-3 py-2 text-sm",
          merging && mergeSelection === "right" && "bg-primary/10 ring-1 ring-primary",
          merging && mergeSelection !== "right" && "opacity-60"
        )}
      >
        <p className="mb-1 text-muted-foreground text-xs">{label}</p>
        <p className="break-all">{formatValue(rightValue)}</p>
        {merging && (
          <RadioGroup
            value={mergeSelection ?? ""}
            onValueChange={() => onMergeSelect?.("right")}
            className="mt-2"
          >
            <RadioGroupItem value="right" />
          </RadioGroup>
        )}
      </div>

      {/* 字段名列（窄屏隐藏） */}
      <span className="hidden text-muted-foreground text-xs lg:block">{label}</span>
    </div>
  )
}
