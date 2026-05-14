/**
 * ViewEngine 核心渲染器——根据 URL 参数选择视图渲染器
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * // 在动态路由页面中
 * const entity = entityRegistry.get(params.module)
 * <ViewEngine entity={entity} view={searchParams.view} />
 * ```
 */

"use client"

import { getViewComponent } from "../lib/component-registry"
import type { EntityDef } from "../types"
import { ListView } from "./views/ListView"

/** 支持的视图类型 */
export type ViewType = "list" | "form" | "kanban" | "graph" | "chart" | "calendar"

interface ViewEngineProps {
  entity: EntityDef
  view?: string
  /** 记录 ID（表单视图时传入） */
  recordId?: string
}

/** 视图引擎：根据 view 参数选择渲染器 */
export function ViewEngine({ entity, view = "list", recordId }: ViewEngineProps) {
  // 优先使用实体级自定义覆盖
  if (view === "list" && entity.overrides?.listView) {
    const Override = entity.overrides.listView
    return <Override />
  }
  if (view === "form" && entity.overrides?.formView) {
    const Override = entity.overrides.formView
    return <Override />
  }
  if (view === "kanban" && entity.overrides?.kanbanView) {
    const Override = entity.overrides.kanbanView
    return <Override />
  }

  // 从组件注册表获取视图组件
  const ViewComponent = getViewComponent(view)
  if (ViewComponent) {
    return <ViewComponent />
  }

  // 内置视图
  switch (view) {
    case "list":
      return <ListView entity={entity} />
    case "kanban":
      return <ViewPlaceholder entity={entity} view="kanban" />
    case "form":
      return <ViewPlaceholder entity={entity} view="form" recordId={recordId} />
    default:
      return <ViewPlaceholder entity={entity} view={view} />
  }
}

/** 视图占位组件（后续被 ListView/KanbanView/FormView 替换） */
function ViewPlaceholder({
  entity,
  view,
  recordId
}: {
  entity: EntityDef
  view: string
  recordId?: string
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 p-8 text-muted-foreground">
      <p className="font-medium text-lg">{entity.label}</p>
      <p className="text-sm">
        视图：{view}
        {recordId && ` | 记录：${recordId}`}
      </p>
      <p className="text-xs">（待实现）</p>
    </div>
  )
}
