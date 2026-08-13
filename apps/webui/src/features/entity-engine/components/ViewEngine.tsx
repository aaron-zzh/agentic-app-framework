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

import { useQueryClient } from "@tanstack/react-query"
import type { OnChangeFn, SortingState } from "@tanstack/react-table"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { useRouter } from "next/navigation"
import type { ComponentType } from "react"
import { toast } from "sonner"
import { ViewErrorBoundary } from "@/components/common/ViewErrorBoundary"
import { fromEntityDef, useCrudUpdate } from "@/lib/api/rest/crud"
import {
  encodeFilterParams,
  entityQueryWindowPrefix,
  type PageResult,
  useEntityDetail,
  useEntityList,
  useEntityQueryWindow,
  useEntitySearchParams,
  useFilterParams
} from "@/lib/api/rest/entity"
import { paths } from "@/lib/constants/paths"
import { cn } from "@/lib/utils/cn"
import { useResolvedEntity } from "../hooks/use-resolved-entity"
import { getViewComponent } from "../lib/component-registry"
import { parseSortParam, resolveSortingUpdater, serializeSorting } from "../lib/sort-params"
import type { EntityDef, FormViewOverrideProps } from "../types"
import { CalendarView } from "./calendar"
import { CanvasView } from "./canvas"
import { EntityApproval } from "./EntityApproval"
import { FormView } from "./form"
import { KanbanView } from "./kanban"
import type { ViewSettings } from "./list"
import { ListView } from "./list"
import { PivotView } from "./pivot"

/** 支持的视图类型 */
export type ViewType =
  | "list"
  | "form"
  | "kanban"
  | "pivot"
  | "graph"
  | "chart"
  | "calendar"
  | "canvas"

interface ViewEngineProps {
  entity: EntityDef
  view?: string
  /** 记录 ID（表单视图时传入） */
  recordId?: string
  /** 查询窗口标识（表单视图时用于缓存复用） */
  queryToken?: string
  /** 在不改变路由的前提下切换详情记录 */
  onRecordChange?: (recordId: string) => void
  /** 视图设置（由 EntityListView 传入） */
  viewSettings?: ViewSettings
  /** 视图级只读展示态（表单视图时生效，用于列表内嵌详情等纯查看场景） */
  readOnly?: boolean
  /** 是否在内容区显示查询窗口导航 */
  showRecordWindowPager?: boolean
  /** 供页面外部保存按钮关联的详情表单 ID。 */
  externalFormId?: string
}

/** 视图引擎：根据 view 参数选择渲染器 */
export function ViewEngine({
  entity,
  view = "list",
  recordId,
  queryToken,
  onRecordChange,
  viewSettings,
  readOnly,
  externalFormId,
  showRecordWindowPager = true
}: ViewEngineProps) {
  return (
    <ViewErrorBoundary>
      <ViewEngineInner
        entity={entity}
        view={view}
        recordId={recordId}
        queryToken={queryToken}
        onRecordChange={onRecordChange}
        viewSettings={viewSettings}
        readOnly={readOnly}
        externalFormId={externalFormId}
        showRecordWindowPager={showRecordWindowPager}
      />
    </ViewErrorBoundary>
  )
}

/** 内部渲染逻辑 */
function ViewEngineInner({
  entity,
  view = "list",
  recordId,
  queryToken,
  onRecordChange,
  viewSettings,
  readOnly,
  externalFormId,
  showRecordWindowPager = true
}: ViewEngineProps) {
  // 物化带 dictType 的 select 字段：从字典拉取数据填充 options，下游视图组件无需改动
  const resolvedEntity = useResolvedEntity(entity)

  // 优先使用实体级自定义覆盖
  if (view === "list" && entity.overrides?.listView) {
    const Override = entity.overrides.listView
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
      return (
        <ConnectedListView
          entity={resolvedEntity}
          viewSettings={viewSettings}
          readOnly={readOnly}
        />
      )
    case "kanban":
      return <KanbanView entity={resolvedEntity} />
    case "form":
      return (
        <ConnectedFormView
          entity={resolvedEntity}
          recordId={recordId}
          queryToken={queryToken}
          onRecordChange={onRecordChange}
          Override={entity.overrides?.formView}
          readOnly={readOnly}
          externalFormId={externalFormId}
          showRecordWindowPager={showRecordWindowPager}
        />
      )
    case "pivot":
      return <PivotView entity={resolvedEntity} />
    case "calendar":
      return <ConnectedCalendarView entity={resolvedEntity} />
    case "canvas":
      return <CanvasView entity={resolvedEntity} recordId={recordId} />
    default:
      return <ViewPlaceholder entity={resolvedEntity} view={view} />
  }
}

/** 列表视图——连接数据层 + URL 状态 */
function ConnectedListView({
  entity,
  viewSettings,
  readOnly
}: {
  entity: EntityDef
  viewSettings?: ViewSettings
  readOnly?: boolean
}) {
  const [params, setParams] = useEntitySearchParams()
  const [filters] = useFilterParams()
  const serverPagination =
    viewSettings?.serverPagination ?? entity.listView.serverPagination ?? false

  const search =
    params.search ?? filters.find((filter) => filter.field === "__search")?.values[0] ?? undefined
  const filterParams = encodeFilterParams(filters.filter((filter) => filter.field !== "__search"))
  const effectiveSort = params.sort ?? entity.listView.defaultSort
  const sorting = parseSortParam(effectiveSort)
  const onSortingChange: OnChangeFn<SortingState> = (updater) => {
    const next = resolveSortingUpdater(updater, sorting)
    setParams({ sort: serializeSorting(next) ?? null, page: 1 })
  }

  const { data, isLoading, pagination, queryToken, sortableFields } = useEntityQueryWindow(entity, {
    // 服务端分页：传 page/pageSize；前端分页：传 pageSize=-1，由 /_query 返回过滤后的完整窗口
    ...(serverPagination
      ? { page: params.page, pageSize: params.pageSize }
      : { page: 1, pageSize: -1 }),
    sort: effectiveSort,
    search,
    ...filterParams
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
      sorting={sorting}
      onSortingChange={onSortingChange}
      sortableFields={sortableFields}
      queryToken={queryToken}
      readOnly={readOnly}
    />
  )
}

export function withExpectedVersion(
  detail: Record<string, unknown> | null | undefined,
  values: Record<string, unknown>
): Record<string, unknown> | undefined {
  if (!detail || !("version" in detail)) return values
  const version = detail.version
  if (typeof version !== "number" || !Number.isInteger(version) || version < 0) return undefined
  return { ...values, expectedVersion: version }
}

/** 表单视图——连接数据层（仅编辑模式；新建走独立路由 EntityCreateView） */
function ConnectedFormView({
  entity,
  recordId,
  queryToken,
  onRecordChange,
  Override,
  readOnly,
  externalFormId,
  showRecordWindowPager
}: {
  entity: EntityDef
  recordId?: string
  queryToken?: string
  onRecordChange?: (recordId: string) => void
  Override?: ComponentType<FormViewOverrideProps>
  readOnly?: boolean
  externalFormId?: string
  showRecordWindowPager: boolean
}) {
  const { data, isLoading } = useEntityDetail(entity, recordId, { queryToken })
  const resource = fromEntityDef(entity)
  const { mutate: update, isPending: updating } = useCrudUpdate(resource)

  // EntityDef 显式声明只读时禁用提交；实际更新权限仍由后端 CRUD 授权最终校验。
  const isReadOnly = readOnly || entity.access?.update === false
  const canUpdate = !isReadOnly
  const handleSubmit = canUpdate
    ? (values: Record<string, unknown>) => {
        if (!recordId) return
        const updateData = withExpectedVersion(data, values)
        if (!updateData) {
          toast.error("记录版本无效，请刷新后重试")
          return
        }
        update(
          { id: recordId, data: updateData },
          {
            onSuccess: () => toast.success(`${entity.label}已保存`),
            onError: () => {}
          }
        )
      }
    : undefined
  const detail = Override ? (
    <Override
      key={recordId}
      entity={entity}
      recordId={recordId}
      queryToken={queryToken}
      data={data ?? undefined}
      loading={isLoading}
      saving={updating}
      onSubmit={handleSubmit}
      readOnly={isReadOnly}
    />
  ) : (
    <FormView
      key={recordId}
      entity={entity}
      data={data ?? undefined}
      loading={isLoading || updating}
      onSubmit={handleSubmit}
      externalFormId={externalFormId}
      readOnly={isReadOnly}
    />
  )

  return (
    <div className="flex min-h-0 shrink flex-col gap-4 overflow-hidden">
      {showRecordWindowPager && (
        <RecordWindowPager
          entity={entity}
          recordId={recordId}
          queryToken={queryToken}
          onRecordChange={onRecordChange}
        />
      )}
      {detail}
      {!isReadOnly && !Override && entity.workflow && recordId && (
        <EntityApproval config={entity.workflow} entityId={recordId} currentUserId="current-user" />
      )}
    </div>
  )
}

export interface RecordWindowNavigation {
  statusLabel: string
  isAvailable: boolean
  prevId?: string
  nextId?: string
  prevTitle: string
  nextTitle: string
  navigateToRecord: (id: string) => void
}

export function useRecordWindowNavigation({
  entity,
  recordId,
  queryToken,
  onRecordChange
}: {
  entity: EntityDef
  recordId?: string
  queryToken?: string
  onRecordChange?: (recordId: string) => void
}): RecordWindowNavigation {
  const router = useRouter()
  const queryClient = useQueryClient()
  const queryWindow = queryToken ? findQueryWindow(queryClient, entity, queryToken) : undefined
  const ids = queryWindow?.ids?.map(String) ?? []
  const currentIndex = recordId ? ids.indexOf(recordId) : -1
  const hasWindow = !!queryWindow && currentIndex >= 0
  const prevId = hasWindow && currentIndex > 0 ? ids[currentIndex - 1] : undefined
  const nextId = hasWindow && currentIndex < ids.length - 1 ? ids[currentIndex + 1] : undefined
  const boundaryTitle = getBoundaryTitle(hasWindow, queryWindow?.hasMore)
  const navigateToRecord = (id: string) => {
    if (onRecordChange) {
      onRecordChange(id)
      return
    }
    router.push(recordHref(entity.slug, id, queryToken))
  }

  return {
    statusLabel: hasWindow ? `当前窗口 ${currentIndex + 1} / ${ids.length}` : "当前查询窗口不可用",
    isAvailable: hasWindow,
    prevId,
    nextId,
    prevTitle: prevId ? "上一条" : boundaryTitle,
    nextTitle: nextId ? "下一条" : boundaryTitle,
    navigateToRecord
  }
}

export function RecordWindowNavigationControls({
  navigation,
  iconOnly = false
}: {
  navigation: RecordWindowNavigation
  iconOnly?: boolean
}) {
  if (!navigation.isAvailable) return null

  const { prevId, nextId, prevTitle, nextTitle, navigateToRecord } = navigation

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        disabled={!prevId}
        title={prevTitle}
        aria-label="上一条"
        className={cn(
          "inline-flex h-8 items-center rounded-md border text-sm disabled:cursor-not-allowed disabled:opacity-50",
          iconOnly ? "size-8 justify-center" : "gap-1 px-2"
        )}
        onClick={() => prevId && navigateToRecord(prevId)}
      >
        <ChevronLeft className="size-4" />
        {iconOnly ? null : "上一条"}
      </button>
      <button
        type="button"
        disabled={!nextId}
        title={nextTitle}
        aria-label="下一条"
        className={cn(
          "inline-flex h-8 items-center rounded-md border text-sm disabled:cursor-not-allowed disabled:opacity-50",
          iconOnly ? "size-8 justify-center" : "gap-1 px-2"
        )}
        onClick={() => nextId && navigateToRecord(nextId)}
      >
        {iconOnly ? null : "下一条"}
        <ChevronRight className="size-4" />
      </button>
    </div>
  )
}

function RecordWindowPager({
  entity,
  recordId,
  queryToken,
  onRecordChange
}: {
  entity: EntityDef
  recordId?: string
  queryToken?: string
  onRecordChange?: (recordId: string) => void
}) {
  const navigation = useRecordWindowNavigation({ entity, recordId, queryToken, onRecordChange })
  if (!queryToken || !navigation.isAvailable) return null

  return (
    <div className="flex items-center justify-between border-b px-4 py-2">
      <div className="text-muted-foreground text-xs">{navigation.statusLabel}</div>
      <RecordWindowNavigationControls navigation={navigation} />
    </div>
  )
}

function findQueryWindow(
  queryClient: ReturnType<typeof useQueryClient>,
  entity: EntityDef,
  queryToken?: string
): PageResult<Record<string, unknown>> | undefined {
  const windows = queryClient.getQueriesData<PageResult<Record<string, unknown>>>({
    queryKey: entityQueryWindowPrefix(entity)
  })
  for (const [, window] of windows) {
    if (!window) continue
    if (queryToken && window.queryToken !== queryToken) continue
    return window
  }
  return undefined
}

function getBoundaryTitle(hasWindow: boolean, hasMore?: boolean) {
  if (!hasWindow) return "当前查询窗口不可用，请从列表重新进入"
  if (hasMore) return "已到当前窗口边界，请返回列表切换分页"
  return "已到当前窗口边界"
}

function recordHref(entitySlug: string, id: string, queryToken?: string) {
  const base = paths.workspace.record(entitySlug, id)
  return queryToken ? `${base}?qw=${encodeURIComponent(queryToken)}` : base
}

/** 日历视图——连接数据层 */
function ConnectedCalendarView({ entity }: { entity: EntityDef }) {
  const { data, isLoading } = useEntityList(entity, {})
  return <CalendarView entity={entity} data={data} loading={isLoading} />
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
