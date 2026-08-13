/**
 * EntityRecordView——实体记录完整详情/编辑页
 * @author AaronZZH & Kiro
 */

"use client"

import { CustomBreadcrumbs } from "@/components/common/CustomBreadcrumbs"
import { PageContainer } from "@/components/common/PageContainer"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  RecordWindowNavigationControls,
  useRecordWindowNavigation,
  ViewEngine
} from "@/features/entity-engine/components"
import type { EntityDef } from "@/features/entity-engine/types"
import { paths } from "@/lib/constants/paths"
import { getRecordDetailExtra } from "../record-detail-extras"

interface Props {
  entity: EntityDef
  recordId: string
  queryToken?: string
}

export function EntityRecordView({ entity, recordId, queryToken }: Props) {
  const navigation = useRecordWindowNavigation({ entity, recordId, queryToken })
  const formId = `entity-record-form-${entity.slug}-${recordId}`
  const canUseExternalSave = !entity.overrides?.formView && entity.access?.update !== false
  const detailExtra = getRecordDetailExtra(entity.slug, recordId, !canUseExternalSave)
  const action =
    canUseExternalSave || navigation.isAvailable ? (
      <div className="flex items-center gap-2">
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
          view="form"
          recordId={recordId}
          queryToken={queryToken}
          externalFormId={canUseExternalSave ? formId : undefined}
          showRecordWindowPager={false}
        />
      </Card>
      {detailExtra}
    </PageContainer>
  )
}
