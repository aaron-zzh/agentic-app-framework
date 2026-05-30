/**
 * EntityRecordView——实体记录完整详情/编辑页
 * @author AaronZZH & Kiro
 */

import { ViewEngine } from "@/features/entity-engine/components"
import type { EntityDef } from "@/features/entity-engine/types"

interface Props {
  entity: EntityDef
  recordId: string
}

export function EntityRecordView({ entity, recordId }: Props) {
  return <ViewEngine entity={entity} view="form" recordId={recordId} />
}
