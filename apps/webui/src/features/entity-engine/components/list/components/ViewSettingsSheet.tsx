/**
 * ViewSettingsSheet——视图设置抽屉
 * @author AaronZZH & Kiro
 *
 * 功能：基础设置（折行/列固定/操作列固定）+ 自定义显示列（搜索+拖拽排序+勾选）
 * 配置持久化到 localStorage
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import type { DragEndEvent } from "@dnd-kit/core"
import { closestCenter, DndContext, PointerSensor, useSensor, useSensors } from "@dnd-kit/core"
import {
  arrayMove,
  SortableContext,
  useSortable,
  verticalListSortingStrategy
} from "@dnd-kit/sortable"
import { CSS } from "@dnd-kit/utilities"
import { GripVertical, HelpCircle, Search, Settings } from "lucide-react"
import { useCallback, useId, useState } from "react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Separator } from "@/components/ui/separator"
import {
  Sheet,
  SheetContent,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  SheetTrigger
} from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import type { DataFieldDef, EntityDef } from "@/lib/types/entity"
import { cn } from "@/lib/utils/cn"

const STORAGE_KEY_PREFIX = "aaf:view-settings:"

export interface ViewSettings {
  /** 表格内容自动折行 */
  wordWrap?: boolean
  /** 数据列固定：none / first / first-two */
  columnFreeze?: "none" | "first" | "first-two"
  /** 操作列固定 */
  actionColumnFixed?: boolean
  /** 快速筛选字段名列表 */
  quickFilterFields?: string[]
  /** Tab 字段 */
  tabField?: string
  /** 是否启用列头排序 */
  enableSort?: boolean
  /** 拖放排序模式 */
  draggable?: boolean
  /** 分组字段 */
  groupBy?: string
  /** 服务端分页 */
  serverPagination?: boolean
  /** 自定义列顺序和可见性 */
  columns?: { name: string; visible: boolean; order: number; width?: number }[]
  /** 点击行的行为：panel=侧边快速查看（默认）detail=跳转详情页 drawer=底部抽屉 none=无 */
  rowClickAction?: "panel" | "detail" | "drawer" | "none"
}

function loadSettings(entitySlug: string): ViewSettings {
  if (typeof window === "undefined") return {}
  const raw = localStorage.getItem(`${STORAGE_KEY_PREFIX}${entitySlug}`)
  if (!raw) return {}
  try {
    return JSON.parse(raw)
  } catch {
    return {}
  }
}

function saveSettings(entitySlug: string, settings: ViewSettings) {
  localStorage.setItem(`${STORAGE_KEY_PREFIX}${entitySlug}`, JSON.stringify(settings))
}

interface ViewSettingsSheetProps {
  entity: EntityDef
  onSettingsChange?: (settings: ViewSettings) => void
}

export function ViewSettingsSheet({ entity, onSettingsChange }: ViewSettingsSheetProps) {
  const uid = useId()
  const { value: open, setValue: setOpen } = useBoolean()
  const [draft, setDraft] = useState<ViewSettings>(() => loadSettings(entity.slug))
  const [search, setSearch] = useState("")

  const allFields = entity.fields.filter((f): f is DataFieldDef => "name" in f)

  // 列配置：从 draft 或默认值初始化
  // listView.columns 中出现的列默认可见，其余默认隐藏
  const listColumns = entity.listView.columns.map((c) => (typeof c === "string" ? c : c.name))
  const defaultColumns = allFields.map((f, i) => ({
    name: f.name,
    visible: listColumns.includes(f.name),
    order: i,
    width: undefined as number | undefined
  }))
  const columns = draft.columns ?? defaultColumns

  // 实时保存：每次 patch 立即持久化并通知外部
  const patch = useCallback(
    (p: Partial<ViewSettings>) => {
      setDraft((prev) => {
        const next = { ...prev, ...p }
        // 用 setTimeout 把副作用移出渲染周期
        setTimeout(() => {
          saveSettings(entity.slug, next)
          onSettingsChange?.(next)
        }, 0)
        return next
      })
    },
    [entity.slug, onSettingsChange]
  )

  // 拖拽排序
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const oldIndex = columns.findIndex((c) => c.name === active.id)
    const newIndex = columns.findIndex((c) => c.name === over.id)
    const reordered = arrayMove(columns, oldIndex, newIndex).map((c, i) => ({ ...c, order: i }))
    patch({ columns: reordered })
  }

  const toggleColumn = (name: string) => {
    patch({
      columns: columns.map((c) => (c.name === name ? { ...c, visible: !c.visible } : c))
    })
  }

  const filteredColumns = columns.filter((c) => {
    const field = allFields.find((f) => f.name === c.name)
    const label = field?.label ?? c.name
    return label.toLowerCase().includes(search.toLowerCase())
  })

  // 第一列（默认列）不可拖拽、不可隐藏
  const firstColumn = columns[0]

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={
          <button
            type="button"
            title="视图设置"
            className="flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
          />
        }
      >
        <Settings className="size-4" />
      </SheetTrigger>

      <SheetContent
        side="right"
        className="flex w-[400px] flex-col p-0 sm:max-w-[400px]"
        hideOverlay
      >
        <SheetHeader className="border-b px-6 py-4">
          <SheetTitle>设置</SheetTitle>
        </SheetHeader>

        <div className="flex-1 space-y-6 overflow-y-auto px-6 py-4">
          {/* 基础设置 */}
          <section>
            <h3 className="mb-4 font-semibold text-base">基础设置</h3>

            {/* 表格内容折行 */}
            {/* 表格内容折行 */}
            <label htmlFor={`${uid}-wrap`} className="mb-3 flex items-center justify-between">
              <div className="flex items-center gap-1">
                <span className="text-muted-foreground text-sm">自动折行</span>
                <Tooltip>
                  <TooltipTrigger
                    render={
                      <button
                        type="button"
                        className="text-muted-foreground/50 hover:text-muted-foreground"
                      />
                    }
                  >
                    <HelpCircle className="size-3.5" />
                  </TooltipTrigger>
                  <TooltipContent>启用后表格内容自动折行，禁用则截断文本</TooltipContent>
                </Tooltip>
              </div>
              <Switch
                id={`${uid}-wrap`}
                checked={draft.wordWrap ?? false}
                onCheckedChange={(v) => patch({ wordWrap: v })}
              />
            </label>

            {/* 数据列固定 */}
            <div className="mb-4">
              <p className="mb-2 text-sm">表格数据列固定</p>
              <RadioGroup
                value={draft.columnFreeze ?? "none"}
                onValueChange={(v) => patch({ columnFreeze: v as ViewSettings["columnFreeze"] })}
                className="flex gap-4"
              >
                {[
                  { value: "none", label: "不固定" },
                  { value: "first", label: "固定第一列" },
                  { value: "first-two", label: "固定前两列" }
                ].map((opt) => (
                  <div key={opt.value} className="flex items-center gap-1.5">
                    <RadioGroupItem value={opt.value} id={`${uid}-freeze-${opt.value}`} />
                    <Label
                      htmlFor={`${uid}-freeze-${opt.value}`}
                      className="cursor-pointer font-normal text-sm"
                    >
                      {opt.label}
                    </Label>
                  </div>
                ))}
              </RadioGroup>
            </div>

            {/* 操作列固定 */}
            <label
              htmlFor={`${uid}-action-fixed`}
              className="mb-3 flex items-center justify-between"
            >
              <div className="flex items-center gap-1">
                <span className="text-muted-foreground text-sm">固定操作列</span>
                <Tooltip>
                  <TooltipTrigger
                    render={
                      <button
                        type="button"
                        className="text-muted-foreground/50 hover:text-muted-foreground"
                      />
                    }
                  >
                    <HelpCircle className="size-3.5" />
                  </TooltipTrigger>
                  <TooltipContent>操作列固定在最后一列永久可见</TooltipContent>
                </Tooltip>
              </div>
              <Switch
                id={`${uid}-action-fixed`}
                checked={draft.actionColumnFixed ?? true}
                onCheckedChange={(v) => patch({ actionColumnFixed: v })}
              />
            </label>

            {/* 点击行行为 */}
            <div className="mb-3">
              <p className="mb-2 text-muted-foreground text-sm">点击行</p>
              <RadioGroup
                value={draft.rowClickAction ?? "panel"}
                onValueChange={(v) =>
                  patch({ rowClickAction: v as ViewSettings["rowClickAction"] })
                }
                className="flex gap-4"
              >
                {[
                  { value: "panel", label: "侧边" },
                  { value: "detail", label: "跳转" },
                  { value: "drawer", label: "抽屉" },
                  { value: "none", label: "无" }
                ].map((opt) => (
                  <div key={opt.value} className="flex items-center gap-1.5">
                    <RadioGroupItem value={opt.value} id={`${uid}-row-click-${opt.value}`} />
                    <Label
                      htmlFor={`${uid}-row-click-${opt.value}`}
                      className="cursor-pointer font-normal text-sm"
                    >
                      {opt.label}
                    </Label>
                  </div>
                ))}
              </RadioGroup>
            </div>
          </section>

          <Separator />

          {/* 高级设置 */}
          <section>
            <h3 className="mb-4 font-semibold text-base">高级设置</h3>

            {/* 状态 Tab 字段 */}
            {allFields.filter((f) => f.type === "select").length > 0 && (
              <div className="mb-3 flex items-center justify-between">
                <span className="text-muted-foreground text-sm">状态 Tab 字段</span>
                <select
                  className="h-8 rounded-md border bg-background px-2 text-sm"
                  value={draft.tabField ?? entity.listView.tabs?.field ?? ""}
                  onChange={(e) => patch({ tabField: e.target.value || undefined })}
                >
                  <option value="">不显示</option>
                  {allFields
                    .filter((f) => f.type === "select")
                    .map((f) => (
                      <option key={f.name} value={f.name}>
                        {f.label ?? f.name}
                      </option>
                    ))}
                </select>
              </div>
            )}

            {/* 服务端分页 */}
            <label
              htmlFor={`${uid}-server-page`}
              className="mb-3 flex items-center justify-between"
            >
              <div className="flex items-center gap-1">
                <span className="text-muted-foreground text-sm">服务端分页</span>
                <Tooltip>
                  <TooltipTrigger
                    render={
                      <button
                        type="button"
                        className="text-muted-foreground/50 hover:text-muted-foreground"
                      />
                    }
                  >
                    <HelpCircle className="size-3.5" />
                  </TooltipTrigger>
                  <TooltipContent>
                    {(draft.serverPagination ?? false)
                      ? "每页向后端请求数据"
                      : "一次性加载全量数据"}
                  </TooltipContent>
                </Tooltip>
              </div>
              <Switch
                id={`${uid}-server-page`}
                checked={draft.serverPagination ?? false}
                onCheckedChange={(v) => patch({ serverPagination: v })}
              />
            </label>

            {/* 拖放排序 */}
            {entity.listView.orderField && (
              <label htmlFor={`${uid}-drag`} className="mb-3 flex items-center justify-between">
                <div className="flex items-center gap-1">
                  <span className="text-muted-foreground text-sm">允许拖放排序</span>
                  <Tooltip>
                    <TooltipTrigger
                      render={
                        <button
                          type="button"
                          className="text-muted-foreground/50 hover:text-muted-foreground"
                        />
                      }
                    >
                      <HelpCircle className="size-3.5" />
                    </TooltipTrigger>
                    <TooltipContent>按 {entity.listView.orderField} 字段排序</TooltipContent>
                  </Tooltip>
                </div>
                <Switch
                  id={`${uid}-drag`}
                  checked={draft.draggable ?? entity.listView.draggable ?? false}
                  onCheckedChange={(v) => patch({ draggable: v })}
                />
              </label>
            )}

            {/* 分组字段 */}
            {allFields.length > 0 && (
              <div className="mb-3">
                <p className="mb-2 text-muted-foreground text-sm">分组字段</p>
                <div className="flex flex-wrap gap-1.5">
                  <button
                    type="button"
                    onClick={() => patch({ groupBy: undefined })}
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs transition-colors",
                      !draft.groupBy
                        ? "border-primary bg-primary/10 text-primary"
                        : "border-border text-muted-foreground hover:border-primary/50"
                    )}
                  >
                    不分组
                  </button>
                  {allFields.map((f) => (
                    <button
                      key={f.name}
                      type="button"
                      onClick={() => patch({ groupBy: f.name })}
                      className={cn(
                        "rounded-full border px-3 py-1 text-xs transition-colors",
                        draft.groupBy === f.name
                          ? "border-primary bg-primary/10 text-primary"
                          : "border-border text-muted-foreground hover:border-primary/50"
                      )}
                    >
                      {f.label ?? f.name}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* 快速筛选字段 + 排序 */}
            {allFields.length > 0 && (
              <div>
                <label htmlFor={`${uid}-sort`} className="mb-3 flex items-center justify-between">
                  <span className="text-muted-foreground text-sm">允许列头排序</span>
                  <Switch
                    id={`${uid}-sort`}
                    checked={draft.enableSort ?? true}
                    onCheckedChange={(v) => patch({ enableSort: v })}
                  />
                </label>
              </div>
            )}
          </section>

          <Separator />
          <section>
            <h3 className="mb-4 font-semibold text-base">自定义列</h3>

            {/* 列头 */}
            <div className="mb-1 flex items-center gap-2 px-2 text-muted-foreground text-xs">
              <span className="w-4 shrink-0" />
              <span className="flex-1">显示字段</span>
              <span className="w-14 shrink-0 text-center">宽度</span>
              <span className="w-16 shrink-0 text-center">快速筛选</span>
            </div>

            {/* 搜索 */}
            <div className="relative mb-3">
              <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="搜索"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>

            {/* 列列表 */}
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={filteredColumns.map((c) => c.name)}
                strategy={verticalListSortingStrategy}
              >
                <ul className="space-y-1">
                  {filteredColumns.map((col) => {
                    const field = allFields.find((f) => f.name === col.name)
                    const label = field?.label ?? col.name
                    const isFirst = col.name === firstColumn?.name
                    const current = draft.quickFilterFields ?? getDefaultQuickFilterFields(entity)
                    const isFiltered = current.includes(col.name)

                    return (
                      <SortableColumnItem
                        key={col.name}
                        id={col.name}
                        label={label}
                        visible={col.visible}
                        filtered={isFiltered}
                        width={col.width}
                        disabled={isFirst}
                        onToggle={() => !isFirst && toggleColumn(col.name)}
                        onFilterToggle={() =>
                          patch({
                            quickFilterFields: isFiltered
                              ? current.filter((n) => n !== col.name)
                              : [...current, col.name]
                          })
                        }
                        onWidthChange={(w) =>
                          patch({
                            columns: columns.map((c) =>
                              c.name === col.name ? { ...c, width: w } : c
                            )
                          })
                        }
                      />
                    )
                  })}
                </ul>
              </SortableContext>
            </DndContext>
          </section>
        </div>

        <SheetFooter className="border-t px-6 py-4">
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={() => {
              const empty = {}
              setDraft(empty)
              saveSettings(entity.slug, empty)
              onSettingsChange?.(empty)
            }}
          >
            恢复默认设置
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

/** 可拖拽列行 */
function SortableColumnItem({
  id,
  label,
  visible,
  filtered,
  width,
  disabled,
  onToggle,
  onFilterToggle,
  onWidthChange
}: {
  id: string
  label: string
  visible: boolean
  filtered: boolean
  width?: number
  disabled?: boolean
  onToggle: () => void
  onFilterToggle: () => void
  onWidthChange: (w: number | undefined) => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id,
    disabled
  })

  return (
    <li
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        "flex items-center gap-2 rounded-md px-2 py-1.5",
        isDragging && "bg-muted opacity-50",
        !disabled && "hover:bg-muted/50"
      )}
    >
      {/* 拖拽手柄 */}
      <button
        type="button"
        className={cn(
          "shrink-0 cursor-grab text-muted-foreground/40 hover:text-muted-foreground",
          disabled && "invisible"
        )}
        {...attributes}
        {...listeners}
        aria-label="拖拽排序"
      >
        <GripVertical className="size-4" />
      </button>

      {/* 显示列勾选 */}
      <Checkbox
        checked={visible}
        onCheckedChange={onToggle}
        disabled={disabled}
        aria-label={`显示列：${label}`}
      />

      {/* 标签 */}
      <span className={cn("flex-1 text-sm", disabled && "text-muted-foreground")}>
        {label}
        {disabled && <span className="ml-1 text-muted-foreground/60 text-xs">（默认）</span>}
      </span>

      {/* 列宽输入 */}
      <input
        type="number"
        min={50}
        max={600}
        placeholder="宽度"
        value={width ?? ""}
        onChange={(e) => onWidthChange(e.target.value ? Number(e.target.value) : undefined)}
        className="w-14 rounded border bg-background px-1.5 py-0.5 text-center text-muted-foreground text-xs focus:outline-none focus:ring-1 focus:ring-primary"
        aria-label={`${label} 列宽`}
      />

      {/* 快速筛选开关 */}
      <div className="flex w-16 shrink-0 justify-center">
        <Switch
          checked={filtered}
          onCheckedChange={onFilterToggle}
          aria-label={`快速筛选：${label}`}
          className="scale-75"
        />
      </div>
    </li>
  )
}

/** 获取默认快速筛选字段 */
export function getDefaultQuickFilterFields(entity: EntityDef): string[] {
  if (entity.listView.quickFilters?.length) {
    return [...new Set(entity.listView.quickFilters.map((qf) => qf.field))]
  }
  return entity.listView.filterableFields ?? []
}
