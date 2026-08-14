/**
 * EntityListView——实体列表页视图
 * @author AaronZZH & Kiro
 *
 * 布局：Breadcrumbs + Card(Toolbar + Table + Pagination)
 * 行点击 → Resizable 快速查看（URL 不变）
 * 行尾按钮 → 跳转详情页（URL 变化，可返回）
 */

"use client"

import { LockKeyhole } from "lucide-react"
import Link from "next/link"
import { Suspense, useEffect, useState } from "react"
import { CustomBreadcrumbs } from "@/components/common/CustomBreadcrumbs"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { EntityActions, ViewEngine } from "@/features/entity-engine/components"
import type { ViewSettings } from "@/features/entity-engine/components/list"
import { useResolvedEntity } from "@/features/entity-engine/hooks/use-resolved-entity"
import type { EntityDef } from "@/features/entity-engine/types"
import { paths } from "@/lib/constants/paths"
import { selectScopeReadOnly, useOrgStore } from "@/lib/store/org-store"
import { useUIStore } from "@/lib/store/ui-store"
import { getListToolbarExtra } from "../list-toolbar-extras"
import { RecordPanel } from "../RecordPanel"
import { Toolbar } from "../Toolbar"

interface Props {
  entity: EntityDef
  view?: string
}

export function EntityListView({ entity, view }: Props) {
  const recordId = useUIStore((s) => s.recordPanelId)
  const recordPanelMode = useUIStore((s) => s.recordPanelMode)
  const recordPanelQueryToken = useUIStore((s) => s.recordPanelQueryToken)
  const openRecordPanel = useUIStore((s) => s.openRecordPanel)
  const close = useUIStore((s) => s.closeRecordPanel)
  const scopeReadOnly = useOrgStore(selectScopeReadOnly)
  const canCreate = !scopeReadOnly && entity.access?.create !== false
  const extraAction = scopeReadOnly ? null : getListToolbarExtra(entity.slug)

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
      <div className="relative mb-4">
        <CustomBreadcrumbs
          links={[{ name: "首页", href: paths.workspace.root }, { name: entity.label }]}
          action={
            scopeReadOnly ? (
              <Badge variant="secondary">
                <LockKeyhole className="size-3" />
                聚合范围只读
              </Badge>
            ) : extraAction || canCreate ? (
              <div className="flex items-center gap-2">
                {extraAction}
                {canCreate && (
                  <Button
                    nativeButton={false}
                    render={<Link href={`${paths.workspace.module(entity.slug)}/new`} />}
                  >
                    + 创建
                  </Button>
                )}
              </div>
            ) : undefined
          }
        />
        {!scopeReadOnly && (
          <div className="pointer-events-none absolute inset-x-0 top-1/2 flex -translate-y-1/2 justify-center">
            <div className="pointer-events-auto">
              <EntityActions entity={entity} position="listToolbar" />
            </div>
          </div>
        )}
      </div>

      <Card className="flex max-h-full min-h-0 shrink flex-col overflow-hidden py-0">
        <Suspense>
          <Toolbar
            entity={resolvedEntity}
            viewSettings={viewSettings}
            onViewSettingsChange={setViewSettings}
          />
        </Suspense>
        <div className="flex min-h-0 shrink flex-col overflow-hidden">
          <ViewEngine
            entity={entity}
            view={view}
            viewSettings={viewSettings}
            readOnly={scopeReadOnly}
          />
        </div>
      </Card>
    </div>
  )

  if (!recordId) return list

  return (
    <RecordPanel
      entity={entity}
      recordId={recordId}
      queryToken={recordPanelQueryToken}
      onClose={close}
      onRecordChange={(nextRecordId) =>
        openRecordPanel(nextRecordId, recordPanelMode, recordPanelQueryToken)
      }
      mode={recordPanelMode}
    >
      {list}
    </RecordPanel>
  )
}
