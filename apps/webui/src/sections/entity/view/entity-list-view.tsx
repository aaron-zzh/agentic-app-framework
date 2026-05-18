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
import { Suspense } from "react"
import { CustomBreadcrumbs } from "@/components/common/CustomBreadcrumbs"
import { Card } from "@/components/ui/card"
import { ViewEngine } from "@/features/entity-engine/components"
import type { EntityDef } from "@/features/entity-engine/types"
import { paths } from "@/lib/constants/paths"
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
  const pathname = usePathname()
  const canCreate = entity.access?.create !== false

  const list = (
    <div className="flex flex-1 flex-col overflow-hidden p-4">
      <CustomBreadcrumbs
        heading={entity.label}
        links={[{ name: "首页", href: paths.workspace.root }, { name: entity.label }]}
        action={
          canCreate ? (
            <Link
              href={`${pathname}/new`}
              className="inline-flex h-8 items-center gap-1 rounded-md bg-primary px-3 font-medium text-primary-foreground text-sm hover:bg-primary/90"
            >
              + 创建
            </Link>
          ) : undefined
        }
        className="mb-4"
      />

      <Card className="flex flex-1 flex-col overflow-hidden">
        <Suspense>
          <Toolbar entity={entity} />
        </Suspense>
        <div className="flex-1 overflow-auto">
          <ViewEngine entity={entity} view={view} />
        </div>
      </Card>
    </div>
  )

  if (!recordId) return list

  return (
    <RecordPanel entity={entity} recordId={recordId} onClose={close}>
      {list}
    </RecordPanel>
  )
}
