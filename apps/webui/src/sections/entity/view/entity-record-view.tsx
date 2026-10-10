/**
 * EntityRecordView——实体记录完整详情/编辑页
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"
import { CustomBreadcrumbs } from "@/components/common/CustomBreadcrumbs"
import { PageContainer } from "@/components/common/PageContainer"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  RecordWindowNavigationControls,
  useRecordWindowNavigation,
  ViewEngine
} from "@/features/entity-engine/components"
import { getCustomViews } from "@/features/entity-engine/lib/custom-views"
import type { EntityDef } from "@/features/entity-engine/types"
import { paths } from "@/lib/constants/paths"
import { selectScopeReadOnly, useOrgStore } from "@/lib/store/org-store"
import { getRecordDetailExtra } from "../record-detail-extras"

interface Props {
  entity: EntityDef
  recordId: string
  queryToken?: string
}

export function EntityRecordView({ entity, recordId, queryToken }: Props) {
  const scopeReadOnly = useOrgStore(selectScopeReadOnly)
  const navigation = useRecordWindowNavigation({ entity, recordId, queryToken })
  const pathname = usePathname()
  const searchParams = useSearchParams()
  // 声明式记录级自定义视图：URL view 参数命中时替换默认详情表单
  const recordViews = getCustomViews(entity, "record")
  // 无记录级自定义视图时不读 URL 参数
  const requestedView = recordViews.length > 0 ? searchParams.get("view") : null
  const activeCustomView = recordViews.find((view) => view.key === requestedView)
  const viewHref = (key?: string) => {
    const next = new URLSearchParams(searchParams.toString())
    if (key) {
      next.set("view", key)
    } else {
      next.delete("view")
    }
    const query = next.toString()
    return query ? `${pathname}?${query}` : pathname
  }
  const formId = `entity-record-form-${entity.slug}-${recordId}`
  const canUseExternalSave =
    !activeCustomView &&
    !scopeReadOnly &&
    !entity.overrides?.formView &&
    entity.access?.update !== false
  const detailExtra = activeCustomView
    ? null
    : getRecordDetailExtra(entity.slug, recordId, !canUseExternalSave)
  const viewSwitcher =
    recordViews.length > 0 ? (
      <div className="flex items-center gap-1">
        <Button
          nativeButton={false}
          size="sm"
          variant={activeCustomView ? "ghost" : "secondary"}
          render={<Link href={viewHref()} />}
        >
          详情
        </Button>
        {recordViews.map((view) => (
          <Button
            key={view.key}
            nativeButton={false}
            size="sm"
            variant={activeCustomView?.key === view.key ? "secondary" : "ghost"}
            render={<Link href={viewHref(view.key)} />}
          >
            {view.label}
          </Button>
        ))}
      </div>
    ) : null
  const action =
    canUseExternalSave || navigation.isAvailable || viewSwitcher ? (
      <div className="flex items-center gap-2">
        {viewSwitcher}
        {canUseExternalSave ? (
          <Button type="submit" form={formId}>
            保存
          </Button>
        ) : null}
        {navigation.isAvailable ? (
          <RecordWindowNavigationControls navigation={navigation} iconOnly />
        ) : null}
      </div>
    ) : undefined

  return (
    <PageContainer maxWidth="lg" className="flex flex-1 flex-col gap-4">
      <CustomBreadcrumbs
        links={[
          { name: "首页", href: paths.workspace.root },
          { name: entity.label, href: paths.workspace.module(entity.slug) },
          { name: "详情" }
        ]}
        action={action}
      />
      <Card className="flex flex-1 flex-col overflow-hidden py-0">
        <ViewEngine
          entity={entity}
          view={activeCustomView?.key ?? "form"}
          recordId={recordId}
          queryToken={queryToken}
          readOnly={scopeReadOnly}
          externalFormId={canUseExternalSave ? formId : undefined}
          showRecordWindowPager={false}
        />
      </Card>
      {detailExtra}
    </PageContainer>
  )
}
