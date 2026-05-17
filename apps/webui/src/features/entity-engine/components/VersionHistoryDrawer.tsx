/**
 * VersionHistoryDrawer——版本历史侧边抽屉 + Diff View
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * <VersionHistoryDrawer entitySlug="document" id="123" fields={entity.fields} />
 * ```
 */

"use client"

import { History, RotateCcw } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Empty, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger
} from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import type { DataFieldDef } from "@/features/entity-engine/types"
import { notify } from "@/lib/notification"
import { useRecordVersions, useRestoreVersion } from "@/lib/queries/use-versions"
import { cn } from "@/lib/utils/cn"

interface Props {
  entitySlug: string
  id: string
  fields?: DataFieldDef[]
}

export function VersionHistoryDrawer({ entitySlug, id, fields = [] }: Props) {
  const [open, setOpen] = useState(false)
  const [compareA, setCompareA] = useState<number | null>(null)
  const [compareB, setCompareB] = useState<number | null>(null)

  const { data: versions, isLoading } = useRecordVersions(entitySlug, id, open)
  const { mutate: restore, isPending } = useRestoreVersion(entitySlug, id)

  const handleRestore = (version: number) => {
    restore(version, {
      onSuccess: () => {
        notify.success(`已恢复到版本 v${version}`)
        setOpen(false)
      },
      onError: () => notify.error("恢复失败，请重试")
    })
  }

  // 选中两个版本进行对比
  const toggleCompare = (version: number) => {
    if (compareA === version) {
      setCompareA(compareB)
      setCompareB(null)
      return
    }
    if (compareB === version) {
      setCompareB(null)
      return
    }
    if (compareA === null) {
      setCompareA(version)
    } else if (compareB === null) {
      setCompareB(version)
    } else {
      // 已选两个，替换较旧的
      setCompareA(compareB)
      setCompareB(version)
    }
  }

  const versionA = versions?.find((v) => v.version === compareA)
  const versionB = versions?.find((v) => v.version === compareB)

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={
          <Button variant="outline" size="sm">
            <History className="size-4" />
            版本历史
          </Button>
        }
      />
      <SheetContent side="right" className="w-[480px] sm:max-w-[480px]">
        <SheetHeader>
          <SheetTitle>版本历史</SheetTitle>
          <SheetDescription>
            {compareA !== null && compareB !== null
              ? `对比 v${Math.min(compareA, compareB)} → v${Math.max(compareA, compareB)}`
              : "点击版本查看详情，选择两个版本进行对比"}
          </SheetDescription>
        </SheetHeader>

        <div className="flex flex-1 flex-col gap-4 overflow-hidden p-4">
          {/* Diff View */}
          {compareA !== null && compareB !== null && versionA && versionB && (
            <DiffView
              fields={fields}
              versionA={versionA.version < versionB.version ? versionA : versionB}
              versionB={versionA.version < versionB.version ? versionB : versionA}
            />
          )}

          {/* 版本时间线 */}
          <ScrollArea className="flex-1">
            {isLoading ? (
              <VersionSkeleton />
            ) : !versions?.length ? (
              <Empty className="py-8">
                <EmptyHeader>
                  <EmptyTitle>暂无版本记录</EmptyTitle>
                </EmptyHeader>
              </Empty>
            ) : (
              <ol className="relative border-l border-dashed pl-4">
                {versions.map((v) => {
                  const isSelected = compareA === v.version || compareB === v.version
                  return (
                    <li key={v.id} className="mb-4 last:mb-0">
                      <span className="absolute -left-1.5 mt-1.5 size-3 rounded-full border-2 border-background bg-primary" />
                      <div
                        className={cn(
                          "rounded-lg border p-3 transition-colors",
                          isSelected && "border-primary bg-primary/5"
                        )}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="flex items-center gap-2">
                              <Badge variant="outline" className="text-[10px]">
                                v{v.version}
                              </Badge>
                              {v.summary && <span className="truncate text-sm">{v.summary}</span>}
                            </div>
                            <p className="mt-1 text-muted-foreground text-xs">
                              {v.userName ?? v.userId} ·{" "}
                              {new Date(v.createdAt).toLocaleString("zh-CN")}
                            </p>
                          </div>
                          <div className="flex shrink-0 gap-1">
                            <Button
                              variant={isSelected ? "default" : "outline"}
                              size="sm"
                              className="h-7 text-xs"
                              onClick={() => toggleCompare(v.version)}
                            >
                              {isSelected ? "取消" : "对比"}
                            </Button>
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-7 text-xs"
                              disabled={isPending}
                              onClick={() => handleRestore(v.version)}
                            >
                              <RotateCcw className="size-3" />
                              恢复
                            </Button>
                          </div>
                        </div>
                      </div>
                    </li>
                  )
                })}
              </ol>
            )}
          </ScrollArea>
        </div>
      </SheetContent>
    </Sheet>
  )
}

/** 字段级 Diff 对比 */
function DiffView({
  fields,
  versionA,
  versionB
}: {
  fields: DataFieldDef[]
  versionA: { version: number; data: Record<string, unknown> }
  versionB: { version: number; data: Record<string, unknown> }
}) {
  const changedFields = fields.filter(
    (f) => JSON.stringify(versionA.data[f.name]) !== JSON.stringify(versionB.data[f.name])
  )

  if (changedFields.length === 0) {
    return (
      <div className="rounded-lg border p-4 text-center text-muted-foreground text-sm">
        两个版本内容相同
      </div>
    )
  }

  return (
    <div className="rounded-lg border">
      <div className="flex border-b px-3 py-2 font-medium text-muted-foreground text-xs">
        <span className="w-24 shrink-0">字段</span>
        <span className="flex-1">v{versionA.version}</span>
        <span className="flex-1">v{versionB.version}</span>
      </div>
      {changedFields.map((f) => (
        <DiffRow
          key={f.name}
          label={f.label ?? f.name}
          oldValue={versionA.data[f.name]}
          newValue={versionB.data[f.name]}
          fieldType={f.type}
        />
      ))}
    </div>
  )
}

function DiffRow({
  label,
  oldValue,
  newValue,
  fieldType
}: {
  label: string
  oldValue: unknown
  newValue: unknown
  fieldType: string
}) {
  return (
    <div className="flex border-b px-3 py-2 text-sm last:border-0">
      <span className="w-24 shrink-0 text-muted-foreground text-xs">{label}</span>
      <DiffCell value={oldValue} fieldType={fieldType} variant="removed" />
      <DiffCell value={newValue} fieldType={fieldType} variant="added" />
    </div>
  )
}

function DiffCell({
  value,
  fieldType: _fieldType,
  variant
}: {
  value: unknown
  fieldType: string
  variant: "added" | "removed"
}) {
  const display = value === null || value === undefined ? "（空）" : String(value)
  return (
    <span
      className={cn(
        "flex-1 rounded px-1.5 py-0.5 text-xs",
        variant === "removed" && "bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-400",
        variant === "added" && "bg-green-50 text-green-700 dark:bg-green-950/30 dark:text-green-400"
      )}
    >
      {display}
    </span>
  )
}

function VersionSkeleton() {
  return (
    <div className="space-y-3">
      {[1, 2, 3].map((i) => (
        <Skeleton key={i} className="h-16 w-full rounded-lg" />
      ))}
    </div>
  )
}
