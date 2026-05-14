/**
 * 记录详情/编辑页——表单视图入口
 * @author AaronZZH & Kiro
 */

import { notFound } from "next/navigation"

import { entityRegistry } from "@/features/entity-engine"
import { ViewEngine } from "@/features/entity-engine/components"

interface PageProps {
  params: Promise<{ module: string; id: string }>
}

export default async function RecordPage({ params }: PageProps) {
  const { module, id } = await params

  const entity = entityRegistry.get(module)
  if (!entity) return notFound()

  return <ViewEngine entity={entity} view="form" recordId={id} />
}
