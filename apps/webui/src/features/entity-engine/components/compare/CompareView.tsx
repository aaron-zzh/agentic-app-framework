/**
 * 数据对比视图——并排展示两条记录差异，支持合并
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * <CompareView
 *   entity={entity}
 *   leftRecord={recordA}
 *   rightRecord={recordB}
 *   onClose={() => setComparing(false)}
 * />
 * ```
 */

"use client"

import { useCallback, useMemo, useState } from "react"
import { GitMerge, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { useEntityDelete, useEntityMutation } from "@/lib/queries/use-entity-mutations"

import type { EntityDef, FieldDef } from "../../types"
import { CompareFieldRow, type CompareStatus } from "./CompareFieldRow"
import { MergeDialog, type MergeSelection } from "./MergeDialog"

interface CompareViewProps {
  entity: EntityDef
  leftRecord: Record<string, unknown>
  rightRecord: Record<string, unknown>
  onClose?: () => void
}

/** 计算两个字符串的相似度（Dice 系数简化版） */
function stringSimilarity(a: string, b: string): number {
  if (a === b) return 1
  if (a.length < 2 || b.length < 2) return 0
  const bigrams = new Map<string, number>()
  for (let i = 0; i < a.length - 1; i++) {
    const bigram = a.substring(i, i + 2)
    bigrams.set(bigram, (bigrams.get(bigram) ?? 0) + 1)
  }
  let intersect = 0
  for (let i = 0; i < b.length - 1; i++) {
    const bigram = b.substring(i, i + 2)
    const count = bigrams.get(bigram) ?? 0
    if (count > 0) {
      bigrams.set(bigram, count - 1)
      intersect++
    }
  }
  return (2 * intersect) / (a.length - 1 + (b.length - 1))
}

/** 判断两个值的对比状态 */
function getCompareStatus(left: unknown, right: unknown): CompareStatus {
  if (left === right) return "equal"
  if (left == null && right == null) return "equal"
  // 字符串相似度判断
  if (typeof left === "string" && typeof right === "string") {
    const sim = stringSimilarity(left, right)
    if (sim > 0.8) return "similar"
  }
  // JSON 深比较
  if (typeof left === "object" && typeof right === "object") {
    if (JSON.stringify(left) === JSON.stringify(right)) return "equal"
  }
  return "different"
}

/** 获取数据字段（排除布局字段） */
function getDataFields(fields: FieldDef[]): FieldDef[] {
  return fields.filter((f) => "name" in f)
}

/** 数据对比视图主组件 */
export function CompareView({ entity, leftRecord, rightRecord, onClose }: CompareViewProps) {
  const [showDiffOnly, setShowDiffOnly] = useState(false)
  const [merging, setMerging] = useState(false)
  const [mergeDialogOpen, setMergeDialogOpen] = useState(false)
  const [selections, setSelections] = useState<MergeSelection>({})

  const updateMutation = useEntityMutation(entity, leftRecord.id as string)
  const deleteMutation = useEntityDelete(entity)

  const dataFields = useMemo(() => getDataFields(entity.fields), [entity.fields])

  /** 每个字段的对比状态 */
  const fieldStatuses = useMemo(() => {
    const map: Record<string, CompareStatus> = {}
    for (const field of dataFields) {
      const name = (field as { name: string }).name
      map[name] = getCompareStatus(leftRecord[name], rightRecord[name])
    }
    return map
  }, [dataFields, leftRecord, rightRecord])

  /** 过滤后的字段列表 */
  const visibleFields = useMemo(() => {
    if (!showDiffOnly) return dataFields
    return dataFields.filter((f) => {
      const name = (f as { name: string }).name
      return fieldStatuses[name] !== "equal"
    })
  }, [dataFields, showDiffOnly, fieldStatuses])

  /** 切换合并选择 */
  const handleMergeSelect = useCallback((fieldName: string, side: "left" | "right") => {
    setSelections((prev) => ({ ...prev, [fieldName]: side }))
  }, [])

  /** 进入合并模式时，默认全部选左侧 */
  const startMerge = useCallback(() => {
    const initial: MergeSelection = {}
    for (const field of dataFields) {
      const name = (field as { name: string }).name
      initial[name] = "left"
    }
    setSelections(initial)
    setMerging(true)
  }, [dataFields])

  /** 执行合并 */
  const handleConfirmMerge = useCallback(async () => {
    const mergedData: Record<string, unknown> = {}
    for (const field of dataFields) {
      const name = (field as { name: string }).name
      const side = selections[name] ?? "left"
      mergedData[name] = side === "left" ? leftRecord[name] : rightRecord[name]
    }
    // 更新左侧记录
    await updateMutation.mutateAsync(mergedData)
    // 软删除右侧记录
    await deleteMutation.mutateAsync([rightRecord.id as string])
    setMergeDialogOpen(false)
    setMerging(false)
    onClose?.()
  }, [dataFields, selections, leftRecord, rightRecord, updateMutation, deleteMutation, onClose])

  const isLoading = updateMutation.isPending || deleteMutation.isPending

  return (
    <div className="flex flex-col gap-4">
      {/* 顶部工具栏 */}
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-lg">
          数据对比
        </h2>
        <div className="flex items-center gap-4">
          {/* 仅显示差异开关 */}
          <div className="flex items-center gap-2">
            <Switch
              id="diff-only"
              checked={showDiffOnly}
              onCheckedChange={setShowDiffOnly}
            />
            <Label htmlFor="diff-only" className="text-sm">仅显示差异</Label>
          </div>

          {/* 合并按钮 */}
          {!merging ? (
            <Button variant="outline" size="sm" onClick={startMerge}>
              <GitMerge className="mr-1 size-4" />
              合并
            </Button>
          ) : (
            <div className="flex gap-2">
              <Button size="sm" onClick={() => setMergeDialogOpen(true)}>
                确认合并
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setMerging(false)}>
                取消
              </Button>
            </div>
          )}

          {/* 关闭按钮 */}
          {onClose && (
            <Button variant="ghost" size="icon-sm" onClick={onClose}>
              <X className="size-4" />
            </Button>
          )}
        </div>
      </div>

      {/* 对比表格 */}
      <div className="rounded-lg border">
        {/* 表头 */}
        <div className="grid grid-cols-[1fr_auto_1fr_auto] items-center gap-3 border-b bg-muted/50 px-4 py-2 text-sm font-medium text-muted-foreground">
          <span>记录 A（ID: {String(leftRecord.id ?? "—")}）</span>
          <span>差异</span>
          <span>记录 B（ID: {String(rightRecord.id ?? "—")}）</span>
          <span className="hidden lg:block">字段</span>
        </div>

        {/* 字段行 */}
        {visibleFields.length === 0 ? (
          <div className="px-4 py-8 text-center text-muted-foreground">
            两条记录完全相同
          </div>
        ) : (
          visibleFields.map((field) => {
            const name = (field as { name: string }).name
            return (
              <CompareFieldRow
                key={name}
                field={field}
                leftValue={leftRecord[name]}
                rightValue={rightRecord[name]}
                status={fieldStatuses[name]}
                merging={merging}
                mergeSelection={selections[name]}
                onMergeSelect={(side) => handleMergeSelect(name, side)}
              />
            )
          })
        )}
      </div>

      {/* 合并确认弹窗 */}
      <MergeDialog
        open={mergeDialogOpen}
        onOpenChange={setMergeDialogOpen}
        fields={dataFields}
        leftRecord={leftRecord}
        rightRecord={rightRecord}
        selections={selections}
        onConfirm={handleConfirmMerge}
        loading={isLoading}
      />
    </div>
  )
}
