/**
 * 实体记录详情页——表单视图 + RecordPanel 布局
 * @author AaronZZH & Kiro
 */

import { notFound } from "next/navigation"
import { entityRegistry } from "@/features/entity-engine"
import { EntityRecordView } from "@/sections/entity/view"

interface PageProps {
  params: Promise<{ module: string; id: string }>
}

export default async function RecordPage({ params }: PageProps) {
  const { module, id } = await params

  const entity = entityRegistry.get(module)
  if (!entity) return notFound()

  return <EntityRecordView entity={entity} recordId={id} />
}
