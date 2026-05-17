/**
 * EntityListView——实体列表页视图
 * @author AaronZZH & Kiro
 *
 * 行点击 → Resizable 快速查看（URL 不变）
 * 行尾按钮 → 跳转详情页（URL 变化，可返回）
 */

"use client"

import { Suspense } from "react"
import { ViewEngine } from "@/features/entity-engine/components"
import type { EntityDef } from "@/features/entity-engine/types"
import { useUIStore } from "@/lib/store/ui-store"
import { Toolbar } from "@/sections/layout/Toolbar"
import { RecordPanel } from "../RecordPanel"

interface Props {
  entity: EntityDef
  view?: string
}

export function EntityListView({ entity, view }: Props) {
  const recordId = useUIStore((s) => s.recordPanelId)
  const close = useUIStore((s) => s.closeRecordPanel)

  const list = (
    <div className="flex flex-1 flex-col overflow-hidden">
      <Suspense>
        <Toolbar entity={entity} />
      </Suspense>
      <div className="flex-1 overflow-auto">
        <ViewEngine entity={entity} view={view} />
      </div>
    </div>
  )

  if (!recordId) return list

  return (
    <RecordPanel entity={entity} recordId={recordId} onClose={close}>
      {list}
    </RecordPanel>
  )
}
