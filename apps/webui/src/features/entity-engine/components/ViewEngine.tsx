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

import { ViewErrorBoundary } from "@/components/common/ViewErrorBoundary"
import { useEntityDetail } from "@/lib/queries/use-entity-detail"
import { useEntityList } from "@/lib/queries/use-entity-list"
import { useEntitySearchParams } from "@/lib/queries/use-entity-search-params"

import { getViewComponent } from "../lib/component-registry"
import type { EntityDef } from "../types"
import { FormView } from "./form"
import { KanbanView } from "./kanban"
import type { ViewSettings } from "./list"
import { ListView } from "./list"
import { PivotView } from "./pivot"

/** 支持的视图类型 */
export type ViewType = "list" | "form" | "kanban" | "graph" | "chart" | "calendar"

interface ViewEngineProps {
  entity: EntityDef
  view?: string
  /** 记录 ID（表单视图时传入） */
  recordId?: string
  /** 视图设置（由 EntityListView 传入） */
  viewSettings?: ViewSettings
}

/** 视图引擎：根据 view 参数选择渲染器 */
export function ViewEngine({ entity, view = "list", recordId, viewSettings }: ViewEngineProps) {
  return (
    <ViewErrorBoundary>
      <ViewEngineInner
        entity={entity}
        view={view}
        recordId={recordId}
        viewSettings={viewSettings}
      />
    </ViewErrorBoundary>
  )
}

/** 内部渲染逻辑 */
function ViewEngineInner({ entity, view = "list", recordId, viewSettings }: ViewEngineProps) {
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
      return <ConnectedListView entity={entity} viewSettings={viewSettings} />
    case "kanban":
      return <KanbanView entity={entity} />
    case "form":
      return <ConnectedFormView entity={entity} recordId={recordId} />
    case "pivot":
      return <PivotView entity={entity} />
    default:
      return <ViewPlaceholder entity={entity} view={view} />
  }
}

/** 列表视图——连接数据层 + URL 状态 */
function ConnectedListView({
  entity,
  viewSettings
}: {
  entity: EntityDef
  viewSettings?: ViewSettings
}) {
  const [params, setParams] = useEntitySearchParams()
  const serverPagination = viewSettings?.serverPagination ?? false

  const { data, isLoading, pagination } = useEntityList(entity, {
    // 服务端分页：传 page/pageSize；一次性查询：只传排序/搜索，后端返回全量
    ...(serverPagination && { page: params.page, pageSize: params.pageSize }),
    sort: params.sort ?? undefined,
    search: params.search ?? undefined
  })

  return (
    <ListView
      entity={entity}
      data={data}
      loading={isLoading}
      viewSettings={viewSettings}
      // 服务端分页时，把翻页/改页大小的控制权交给 URL 参数
      serverPagination={serverPagination ? pagination : undefined}
      onPageChange={serverPagination ? (page) => setParams({ page }) : undefined}
      onPageSizeChange={serverPagination ? (pageSize) => setParams({ pageSize }) : undefined}
    />
  )
}

/** 表单视图——连接数据层 */
function ConnectedFormView({ entity, recordId }: { entity: EntityDef; recordId?: string }) {
  const { data, isLoading } = useEntityDetail(entity, recordId)
  return <FormView key={recordId} entity={entity} data={data ?? undefined} loading={isLoading} />
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
