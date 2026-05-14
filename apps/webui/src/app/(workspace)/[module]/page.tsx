/**
 * 动态路由入口——根据 URL module 参数渲染对应实体视图
 * @author AaronZZH & Kiro
 */

import { notFound } from "next/navigation"

import { entityRegistry } from "@/features/entity-engine"
import { ViewEngine } from "@/features/entity-engine/components"

interface PageProps {
  params: Promise<{ module: string }>
  searchParams: Promise<{ view?: string }>
}

export default async function ModulePage({ params, searchParams }: PageProps) {
  const { module } = await params
  const { view } = await searchParams

  const entity = entityRegistry.get(module)
  if (!entity) return notFound()

  return <ViewEngine entity={entity} view={view} />
}
