"use client"

import { use } from "react"
import { entityRegistry } from "@/features/entity-engine/lib/registry"
import { EntityCreateView } from "@/sections/entity/view/entity-create-view"

interface PageProps {
  params: Promise<{ module: string }>
}

/** 从工作区内导航到实体新建页时，在 @modal 插槽中显示创建弹窗。 */
export default function EntityCreateInterceptRoute({ params }: PageProps) {
  const { module } = use(params)
  const entity = entityRegistry.get(module)

  if (!entity || entity.access?.create === false) return null

  return <EntityCreateView entity={entity} presentation="dialog" />
}
