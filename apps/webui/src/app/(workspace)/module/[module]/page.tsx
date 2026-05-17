/**
 * 实体列表页——根据 URL module 参数渲染对应实体视图
 * @author AaronZZH & Kiro
 */

import { notFound } from "next/navigation"
import { entityRegistry } from "@/features/entity-engine"
import { EntityListView } from "@/sections/entity/view"

interface PageProps {
  params: Promise<{ module: string }>
  searchParams: Promise<{ view?: string }>
}

export default async function ModulePage({ params, searchParams }: PageProps) {
  const { module } = await params
  const { view } = await searchParams

  const entity = entityRegistry.get(module)
  if (!entity) return notFound()

  return <EntityListView entity={entity} view={view} />
}
