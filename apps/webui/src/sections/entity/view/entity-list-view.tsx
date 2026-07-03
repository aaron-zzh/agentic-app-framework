/**
 * EntityListView——实体列表页视图
 * @author AaronZZH & Kiro
 *
 * 布局：Breadcrumbs + Card(Toolbar + Table + Pagination)
 * 行点击 → Resizable 快速查看（URL 不变）
 * 行尾按钮 → 跳转详情页（URL 变化，可返回）
 */

"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Suspense, useEffect, useState } from "react"
import { CustomBreadcrumbs } from "@/components/common/CustomBreadcrumbs"
import { Card } from "@/components/ui/card"
import { ViewEngine } from "@/features/entity-engine/components"
import type { ViewSettings } from "@/features/entity-engine/components/list"
import { useResolvedEntity } from "@/features/entity-engine/hooks/use-resolved-entity"
import type { EntityDef } from "@/features/entity-engine/types"
import { paths } from "@/lib/constants/paths"
import { useUIStore } from "@/lib/store/ui-store"
import { Toolbar } from "@/sections/layout/Toolbar"
import { getListToolbarExtra } from "../list-toolbar-extras"
import { RecordPanel } from "../RecordPanel"

interface Props {
  entity: EntityDef
  view?: string
}

export function EntityListView({ entity, view }: Props) {
  const recordId = useUIStore((s) => s.recordPanelId)
  const recordPanelMode = useUIStore((s) => s.recordPanelMode)
  const close = useUIStore((s) => s.closeRecordPanel)
  const pathname = usePathname()
  const canCreate = entity.access?.create !== false
  const extraAction = getListToolbarExtra(entity.slug)

  // 物化带 dictType 的 select 字段，Toolbar（筛选/搜索）与 ViewEngine（表格/表单渲染）共享同一份结果
  const resolvedEntity = useResolvedEntity(entity)

  // viewSettings 状态提升到此层，同时传给 Toolbar（读写）和 ViewEngine（只读）
  // 用 useEffect 在客户端加载，避免 SSR hydration mismatch
  const [viewSettings, setViewSettings] = useState<ViewSettings>({})
  useEffect(() => {
    const raw = localStorage.getItem(`aaf:view-settings:${entity.slug}`)
    if (!raw) return
    try {
      setViewSettings(JSON.parse(raw) as ViewSettings)
    } catch {
      // ignore
    }
  }, [entity.slug])

  const list = (
    <div className="flex flex-1 flex-col overflow-hidden p-3">
      <CustomBreadcrumbs
        links={[{ name: "首页", href: paths.workspace.root }, { name: entity.label }]}
        action={
          extraAction || canCreate ? (
            <div className="flex items-center gap-2">
              {extraAction}
              {canCreate && (
                <Link
                  href={`${pathname}/new`}
                  className="inline-flex h-8 items-center gap-1 rounded-md bg-primary px-3 font-medium text-primary-foreground text-sm hover:bg-primary/90"
                >
                  + 创建
                </Link>
              )}
            </div>
          ) : undefined
        }
        className="mb-4"
      />

      <Card className="flex flex-1 flex-col overflow-hidden py-0">
        <Suspense>
          <Toolbar
            entity={resolvedEntity}
            viewSettings={viewSettings}
            onViewSettingsChange={setViewSettings}
          />
        </Suspense>
        <div className="flex flex-1 flex-col overflow-hidden">
          <ViewEngine entity={entity} view={view} viewSettings={viewSettings} />
        </div>
      </Card>
    </div>
  )

  if (!recordId) return list

  return (
    <RecordPanel entity={entity} recordId={recordId} onClose={close} mode={recordPanelMode}>
      {list}
    </RecordPanel>
  )
}
