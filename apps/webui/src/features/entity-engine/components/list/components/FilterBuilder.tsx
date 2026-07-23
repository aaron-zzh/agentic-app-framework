/**
 * 筛选构建器——由服务端能力元数据驱动的高级条件创建弹窗
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { Plus } from "lucide-react"
import { useCallback, useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import type { CrudFilterFieldMeta } from "@/lib/api/rest/crud"
import type { DataFieldDef, EntityDef, FilterCondition } from "@/lib/types/entity"
import { buildDateRangeFilter } from "../../../lib/date-range-filter"

export type { FilterCondition } from "@/lib/types/entity"

const OPERATOR_LABELS: Record<string, string> = {
  eq: "等于",
  in: "属于",
  notIn: "不属于",
  contains: "包含",
  startsWith: "开头是",
  gt: "大于",
  gte: "大于等于",
  lt: "小于",
  lte: "小于等于",
  between: "介于",
  isNull: "为 NULL",
  isNotNull: "非 NULL",
  isEmpty: "为空",
  isNotEmpty: "不为空",
  isTrue: "为真",
  isFalse: "为假"
}

const VALUELESS_OPERATORS = new Set([
  "isNull",
  "isNotNull",
  "isEmpty",
  "isNotEmpty",
  "isTrue",
  "isFalse"
])

interface FilterBuilderProps {
  entity: EntityDef
  filters: FilterCondition[]
  onChange: (filters: FilterCondition[]) => void
  capabilities?: CrudFilterFieldMeta[]
}

/** 高级筛选仅负责创建条件；已应用条件统一由 SearchBar 内的可关闭 Chip 展示。 */
export function FilterBuilder({
  entity,
  filters,
  onChange,
  capabilities = []
}: FilterBuilderProps) {
  const { value: dialogOpen, setValue: setDialogOpen } = useBoolean()
  const [draftFilter, setDraftFilter] = useState<FilterCondition | null>(null)

  const allFields = entity.fields.filter((field): field is DataFieldDef => "name" in field)
  const configuredFields = entity.listView.filterableFields
  const configuredFilterableFields = configuredFields?.length
    ? allFields.filter((field) => configuredFields.includes(field.name))
    : allFields
  const filterableFields = configuredFilterableFields.filter((field) =>
    capabilities.some(
      (capability) => capability.field === field.name && capability.operators.length > 0
    )
  )
  const draftField = draftFilter
    ? filterableFields.find((field) => field.name === draftFilter.field)
    : undefined
  const draftCapability = draftField ? findCapability(capabilities, draftField.name) : undefined
  const isDateRangeDraft = draftField?.type === "date" && draftFilter?.operator === "between"
  const hasDraftValues = isDateRangeDraft
    ? draftFilter.values.some(Boolean)
    : draftFilter?.operator === "between"
      ? draftFilter.values.length === 2 && draftFilter.values.every(Boolean)
      : (draftFilter?.values.length ?? 0) > 0
  const canApplyDraft =
    draftFilter !== null && (VALUELESS_OPERATORS.has(draftFilter.operator) || hasDraftValues)

  const openAddFilter = useCallback(() => {
    const field = filterableFields[0]
    const operator = field && findCapability(capabilities, field.name)?.operators[0]
    if (!field || !operator) return
    setDraftFilter({ field: field.name, operator: operator.value, values: [] })
    setDialogOpen(true)
  }, [capabilities, filterableFields, setDialogOpen])

  const updateDraftField = useCallback(
    (fieldName: string) => {
      const field = filterableFields.find((candidate) => candidate.name === fieldName)
      const operator = field && findCapability(capabilities, field.name)?.operators[0]
      if (!field || !operator) return
      setDraftFilter({ field: field.name, operator: operator.value, values: [] })
    },
    [capabilities, filterableFields]
  )

  const closeDialog = useCallback(() => {
    setDialogOpen(false)
    setDraftFilter(null)
  }, [setDialogOpen])

  const applyDraftFilter = useCallback(() => {
    if (!draftFilter || !canApplyDraft) return
    const nextFilter = isDateRangeDraft
      ? buildDateRangeFilter(
          draftFilter.field,
          draftFilter.values[0] ?? "",
          draftFilter.values[1] ?? ""
        )
      : draftFilter
    if (!nextFilter) return

    onChange([...filters, nextFilter])
    closeDialog()
  }, [canApplyDraft, closeDialog, draftFilter, filters, isDateRangeDraft, onChange])

  if (!filterableFields.length) return null

  return (
    <>
      <Button type="button" variant="outline" size="sm" onClick={openAddFilter}>
        <Plus data-icon="inline-start" />
        高级搜索
      </Button>
      <Dialog
        open={dialogOpen}
        onOpenChange={(nextOpen) => {
          if (nextOpen) {
            setDialogOpen(true)
          } else {
            closeDialog()
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>添加筛选</DialogTitle>
          </DialogHeader>
          {draftFilter && draftField && draftCapability && (
            <div className="space-y-4">
              <select
                aria-label="筛选字段"
                className="h-9 w-full rounded border px-2 text-sm"
                value={draftFilter.field}
                onChange={(event) => updateDraftField(event.target.value)}
              >
                {filterableFields.map((field) => (
                  <option key={field.name} value={field.name}>
                    {field.label ?? field.name}
                  </option>
                ))}
              </select>
              <select
                aria-label="筛选操作符"
                className="h-9 w-full rounded border px-2 text-sm"
                value={draftFilter.operator}
                onChange={(event) =>
                  setDraftFilter((current) =>
                    current ? { ...current, operator: event.target.value, values: [] } : current
                  )
                }
              >
                {draftCapability.operators.map((operator) => (
                  <option key={operator.value} value={operator.value}>
                    {OPERATOR_LABELS[operator.value] ?? operator.value}
                  </option>
                ))}
              </select>
              {!VALUELESS_OPERATORS.has(draftFilter.operator) && (
                <DraftFilterValueInput
                  key={`${draftFilter.field}-${draftFilter.operator}`}
                  field={draftField}
                  operator={draftFilter.operator}
                  values={draftFilter.values}
                  variables={draftCapability.variables}
                  onChange={(values) =>
                    setDraftFilter((current) => (current ? { ...current, values } : current))
                  }
                />
              )}
            </div>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={closeDialog}>
              取消
            </Button>
            <Button type="button" onClick={applyDraftFilter} disabled={!canApplyDraft}>
              应用筛选
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function findCapability(
  capabilities: CrudFilterFieldMeta[],
  fieldName: string
): CrudFilterFieldMeta | undefined {
  return capabilities.find((capability) => capability.field === fieldName)
}

function DraftFilterValueInput({
  field,
  operator,
  values,
  variables,
  onChange
}: {
  field: DataFieldDef
  operator: string
  values: string[]
  variables: string[]
  onChange: (values: string[]) => void
}) {
  if (field.type === "select") {
    return (
      <SelectValueInput field={field} operator={operator} values={values} onChange={onChange} />
    )
  }

  const variableListId = variables.length > 0 ? `draft-${field.name}-filter-variables` : undefined
  if (operator === "between") {
    if (field.type === "date") {
      return <DateRangeValueInput values={values} variables={variables} onChange={onChange} />
    }

    const updateValue = (index: number, value: string) => {
      const nextValues = [values[0] ?? "", values[1] ?? ""]
      nextValues[index] = value
      onChange(nextValues)
    }

    return (
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-center">
        <input
          aria-label="筛选起始值"
          type={inputType(field, variables)}
          list={variableListId}
          className="h-9 w-full min-w-0 rounded border px-2 text-sm"
          value={values[0] ?? ""}
          onChange={(event) => updateValue(0, event.target.value)}
          placeholder="起始值"
        />
        <span className="text-muted-foreground text-xs">至</span>
        <input
          aria-label="筛选结束值"
          type={inputType(field, variables)}
          list={variableListId}
          className="h-9 w-full min-w-0 rounded border px-2 text-sm"
          value={values[1] ?? ""}
          onChange={(event) => updateValue(1, event.target.value)}
          placeholder="结束值"
        />
        <VariableOptions id={variableListId} variables={variables} />
      </div>
    )
  }

  const placeholder =
    variables.length > 0
      ? "YYYY-MM-DD 或内置变量"
      : operator === "in" || operator === "notIn"
        ? "多个值以逗号分隔"
        : "值"
  return (
    <>
      <input
        aria-label="筛选值"
        type={inputType(field, variables)}
        list={variableListId}
        className="h-9 w-full rounded border px-2 text-sm"
        value={values[0] ?? ""}
        onChange={(event) => {
          const value = event.target.value
          onChange(
            operator === "in" || operator === "notIn"
              ? value
                  .split(",")
                  .map((item) => item.trim())
                  .filter(Boolean)
              : value
                ? [value]
                : []
          )
        }}
        placeholder={placeholder}
      />
      <VariableOptions id={variableListId} variables={variables} />
    </>
  )
}

function DateRangeValueInput({
  values,
  variables,
  onChange
}: {
  values: string[]
  variables: string[]
  onChange: (values: string[]) => void
}) {
  const updateValue = (index: number, value: string) => {
    const nextValues = [values[0] ?? "", values[1] ?? ""]
    nextValues[index] = value
    onChange(nextValues)
  }
  const getDateValue = (index: number) => {
    const value = values[index] ?? ""
    return variables.includes(value) ? "" : value
  }
  const getVariableValue = (index: number) => {
    const value = values[index] ?? ""
    return variables.includes(value) ? value : ""
  }
  const getVariableLabel = (variable: string) => {
    switch (variable) {
      case "$now":
        return "当前时间"
      case "$todayStart":
        return "今日开始"
      case "$tomorrowStart":
        return "明日开始"
      case "$nowPlus3Days":
        return "三天后"
      default:
        return variable
    }
  }

  return (
    <div className="grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-center">
      <div className="flex min-w-0 flex-col gap-1">
        <input
          aria-label="筛选起始值"
          type="date"
          className="h-9 w-full min-w-0 rounded border px-2 text-sm"
          value={getDateValue(0)}
          onChange={(event) => updateValue(0, event.target.value)}
        />
        {variables.length > 0 && (
          <select
            aria-label="起始日期变量"
            className="h-9 w-full min-w-0 rounded border px-2 text-sm"
            value={getVariableValue(0)}
            onChange={(event) => updateValue(0, event.target.value)}
          >
            <option value="">选择日期变量</option>
            {variables.map((variable) => (
              <option key={variable} value={variable}>
                {getVariableLabel(variable)}
              </option>
            ))}
          </select>
        )}
      </div>
      <span className="text-muted-foreground text-xs">至</span>
      <div className="flex min-w-0 flex-col gap-1">
        <input
          aria-label="筛选结束值"
          type="date"
          className="h-9 w-full min-w-0 rounded border px-2 text-sm"
          value={getDateValue(1)}
          onChange={(event) => updateValue(1, event.target.value)}
        />
        {variables.length > 0 && (
          <select
            aria-label="结束日期变量"
            className="h-9 w-full min-w-0 rounded border px-2 text-sm"
            value={getVariableValue(1)}
            onChange={(event) => updateValue(1, event.target.value)}
          >
            <option value="">选择日期变量</option>
            {variables.map((variable) => (
              <option key={variable} value={variable}>
                {getVariableLabel(variable)}
              </option>
            ))}
          </select>
        )}
      </div>
    </div>
  )
}

function SelectValueInput({
  field,
  operator,
  values,
  onChange
}: {
  field: Extract<DataFieldDef, { type: "select" }>
  operator: string
  values: string[]
  onChange: (values: string[]) => void
}) {
  const options = field.options ?? []
  if (operator === "between") {
    const updateValue = (index: number, value: string) => {
      const nextValues = [values[0] ?? "", values[1] ?? ""]
      nextValues[index] = value
      onChange(nextValues)
    }

    return (
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-center">
        <select
          aria-label="筛选起始值"
          className="h-9 w-full min-w-0 rounded border px-2 text-sm"
          value={values[0] ?? ""}
          onChange={(event) => updateValue(0, event.target.value)}
        >
          <option value="">请选择</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <span className="text-muted-foreground text-xs">至</span>
        <select
          aria-label="筛选结束值"
          className="h-9 w-full min-w-0 rounded border px-2 text-sm"
          value={values[1] ?? ""}
          onChange={(event) => updateValue(1, event.target.value)}
        >
          <option value="">请选择</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
    )
  }

  if (operator === "in" || operator === "notIn") {
    return (
      <select
        multiple
        aria-label={`${field.label ?? field.name}多个值`}
        className="h-28 w-full rounded border px-2 text-sm"
        value={values}
        onChange={(event) =>
          onChange(Array.from(event.currentTarget.selectedOptions, (option) => option.value))
        }
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    )
  }

  return (
    <select
      aria-label={`${field.label ?? field.name}值`}
      className="h-9 w-full rounded border px-2 text-sm"
      value={values[0] ?? ""}
      onChange={(event) => onChange(event.target.value ? [event.target.value] : [])}
    >
      <option value="">请选择</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  )
}

function VariableOptions({ id, variables }: { id: string | undefined; variables: string[] }) {
  if (!id) return null

  return (
    <datalist id={id}>
      {variables.map((variable) => (
        <option key={variable} value={variable} />
      ))}
    </datalist>
  )
}

function inputType(field: DataFieldDef, variables: string[]): "date" | "number" | "text" {
  if (variables.length > 0) return "text"
  if (field.type === "date") return "date"
  if (field.type === "number") return "number"
  return "text"
}
