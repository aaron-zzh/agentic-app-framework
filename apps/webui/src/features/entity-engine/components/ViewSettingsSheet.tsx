/**
 * ViewSettingsSheet——视图设置抽屉
 * @author AaronZZH & Kiro
 *
 * 功能：快速筛选配置、拖放排序、分组字段、默认排序
 * 配置持久化到 localStorage
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { Settings } from "lucide-react"
import { useCallback, useId, useState } from "react"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { Switch } from "@/components/ui/switch"
import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"
import { cn } from "@/lib/utils/cn"

const STORAGE_KEY_PREFIX = "aaf:view-settings:"

export interface ViewSettings {
  /** 快速筛选字段名列表 */
  quickFilterFields?: string[]
  /** Tab 字段（select 字段，显示为状态 Tab） */
  tabField?: string | null
  /** 是否启用列头排序 */
  enableSort?: boolean
  /** 拖放排序模式 */
  draggable?: boolean
  /** 分组字段 */
  groupBy?: string
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
  const [settings, setSettings] = useState<ViewSettings>(() => loadSettings(entity.slug))

  const allFields = entity.fields.filter((f): f is DataFieldDef => "name" in f)
  const selectFields = allFields.filter((f) => f.type === "select")

  const updateSettings = useCallback(
    (patch: Partial<ViewSettings>) => {
      const next = { ...settings, ...patch }
      setSettings(next)
      saveSettings(entity.slug, next)
      onSettingsChange?.(next)
    },
    [settings, entity.slug, onSettingsChange]
  )

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

      <SheetContent side="right" className="w-80 p-0" hideOverlay>
        <SheetHeader className="border-b">
          <SheetTitle>视图设置</SheetTitle>
        </SheetHeader>

        <div className="space-y-6 overflow-y-auto p-4">
          {/* 排序开关 */}
          <section>
            <h4 className="mb-2 font-medium text-sm">交互</h4>
            {selectFields.length > 0 && (
              <div className="mb-2 flex items-center justify-between">
                <span className="text-muted-foreground text-sm">状态 Tab 字段</span>
                <select
                  className="h-8 rounded-md border bg-background px-2 text-sm"
                  value={settings.tabField ?? entity.listView.tabs?.field ?? ""}
                  onChange={(e) => updateSettings({ tabField: e.target.value || null })}
                >
                  <option value="">不显示</option>
                  {selectFields.map((f) => (
                    <option key={f.name} value={f.name}>
                      {f.label ?? f.name}
                    </option>
                  ))}
                </select>
              </div>
            )}
            <label htmlFor={`${uid}-sort`} className="flex items-center justify-between">
              <span className="text-muted-foreground text-sm">允许列头排序</span>
              <Switch
                id={`${uid}-sort`}
                checked={settings.enableSort ?? true}
                onCheckedChange={(v) => updateSettings({ enableSort: v })}
              />
            </label>
          </section>

          {/* 拖放排序 */}
          <section>
            <h4 className="mb-2 font-medium text-sm">排序方式</h4>
            <label htmlFor={`${uid}-drag`} className="flex items-center justify-between">
              <span className="text-muted-foreground text-sm">允许拖放排序</span>
              <Switch
                id={`${uid}-drag`}
                checked={settings.draggable ?? entity.listView.draggable ?? false}
                onCheckedChange={(v) => updateSettings({ draggable: v })}
              />
            </label>
          </section>

          {/* 分组字段 */}
          {allFields.length > 0 && (
            <section>
              <h4 className="mb-2 font-medium text-sm">分组字段</h4>
              <div className="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  onClick={() => updateSettings({ groupBy: undefined })}
                  className={cn(
                    "rounded-full border px-3 py-1 text-xs transition-colors",
                    !settings.groupBy
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
                    onClick={() => updateSettings({ groupBy: f.name })}
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs transition-colors",
                      settings.groupBy === f.name
                        ? "border-primary bg-primary/10 text-primary"
                        : "border-border text-muted-foreground hover:border-primary/50"
                    )}
                  >
                    {f.label ?? f.name}
                  </button>
                ))}
              </div>
            </section>
          )}

          {/* 快速筛选配置 */}
          {allFields.length > 0 && (
            <section>
              <h4 className="mb-2 font-medium text-sm">快速筛选字段</h4>
              <p className="mb-3 text-muted-foreground text-xs">选择在搜索栏上方显示的筛选组件</p>
              <div className="space-y-1.5">
                {allFields.map((field) => {
                  const active = (
                    settings.quickFilterFields ?? getDefaultQuickFilterFields(entity)
                  ).includes(field.name)
                  return (
                    <div
                      key={field.name}
                      className="flex cursor-pointer items-center justify-between rounded-md px-2 py-1.5 hover:bg-muted"
                    >
                      <div>
                        <span className="text-sm">{field.label ?? field.name}</span>
                        <span className="ml-2 text-muted-foreground text-xs">{field.type}</span>
                      </div>
                      <Switch
                        checked={active}
                        onCheckedChange={(checked) => {
                          const current =
                            settings.quickFilterFields ?? getDefaultQuickFilterFields(entity)
                          updateSettings({
                            quickFilterFields: checked
                              ? [...current, field.name]
                              : current.filter((n) => n !== field.name)
                          })
                        }}
                      />
                    </div>
                  )
                })}
              </div>
            </section>
          )}

          {/* 重置 */}
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={() => {
              setSettings({})
              saveSettings(entity.slug, {})
              onSettingsChange?.({})
            }}
          >
            恢复默认设置
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  )
}

/** 获取默认快速筛选字段（从 EntityDef 配置推断） */
export function getDefaultQuickFilterFields(entity: EntityDef): string[] {
  if (entity.listView.quickFilters?.length) {
    return [...new Set(entity.listView.quickFilters.map((qf) => qf.field))]
  }
  return entity.listView.filterableFields ?? []
}
