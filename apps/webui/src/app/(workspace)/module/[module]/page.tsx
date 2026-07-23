/**
 * 实体列表页——根据 URL module 参数渲染对应实体视图
 * @author AaronZZH & Kiro
 */

import { EntityModuleRoute } from "@/sections/entity/view"

interface PageProps {
  params: Promise<{ module: string }>
  searchParams: Promise<{ view?: string }>
}

export default async function ModulePage({ params, searchParams }: PageProps) {
  const { module } = await params
  const { view } = await searchParams

  return <EntityModuleRoute kind="list" module={module} view={view} />
}
