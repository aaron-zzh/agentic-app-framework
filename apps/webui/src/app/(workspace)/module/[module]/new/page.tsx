/**
 * 实体新建页——独立路由 /module/{slug}/new
 * @author AaronZZH & Kiro
 *
 * Next.js App Router 静态段（new）优先于动态段 [id]，所以本路由会优先匹配。
 * 与 [id]/page.tsx（编辑/详情）解耦，避免把 "new" 当作 ID 误传到后端。
 */

import { notFound } from "next/navigation"
import { entityRegistry } from "@/features/entity-engine"
import { EntityCreateView } from "@/sections/entity/view"

interface PageProps {
  params: Promise<{ module: string }>
}

export default async function ModuleCreatePage({ params }: PageProps) {
  const { module } = await params

  const entity = entityRegistry.get(module)
  if (!entity) return notFound()

  // 实体未声明可创建权限时，不允许进入新建页
  if (entity.access?.create === false) return notFound()

  return <EntityCreateView entity={entity} />
}
