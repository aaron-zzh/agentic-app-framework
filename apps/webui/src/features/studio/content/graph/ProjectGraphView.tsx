/**
 * Content Studio 项目图谱视图。
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Background,
  Controls,
  Handle,
  MiniMap,
  type NodeProps,
  Position,
  ReactFlow,
  type Viewport
} from "@xyflow/react"
import {
  ChevronDown,
  ChevronRight,
  Ellipsis,
  Eye,
  FilePenLine,
  FileText,
  Layers3,
  PanelTopOpen,
  Plus,
  ScanLine,
  Sparkles,
  Trash2
} from "lucide-react"
import { createContext, memo, useContext, useId, useMemo } from "react"
import "@xyflow/react/dist/style.css"
import { GlassCard } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type {
  AigcObjectReadiness,
  AigcProjectGraph,
  AigcRelationLayer
} from "@/lib/api/rest/ai/aigc"
import { cn } from "@/lib/utils/index"
import { getObjectTypeConfig } from "../project-type-config"
import {
  type ContentGraphNodeData,
  OBJECT_STATUS_LABELS,
  OUTPUT_CONTRACT_LABELS,
  objectMediaActionPresentation,
  objectStructureActionState,
  READINESS_LABELS,
  RELATION_LAYER_LABELS,
  SEMANTIC_ROLE_LABELS,
  toGraphProjection
} from "./graph-projection"
import { useProjectGraphViewState } from "./view-state-store"

const LAYER_OPTIONS: { value: AigcRelationLayer; label: string }[] = (
  Object.entries(RELATION_LAYER_LABELS) as [AigcRelationLayer, string][]
).map(([value, label]) => ({ value, label }))

const STATUS_VARIANT = {
  EMPTY: "outline",
  CANDIDATE_AVAILABLE: "default",
  ADOPTED: "secondary"
} as const

interface GraphNodeActions {
  projectId: number
  readOnly: boolean
  selectionMode: boolean
  selectableObjectIds: ReadonlySet<number>
  generatableObjectIds: ReadonlySet<number>
  readinessByObjectId: ReadonlyMap<number, AigcObjectReadiness>
  onOpenDetails?: (id: number) => void
  onOpenCanvas?: (id: number) => void
  onAnnotateImage?: (id: number) => void
  onGenerateObject?: (id: number) => void
  onUpdateContract?: (id: number) => void
  onRemoveObject?: (id: number) => void
  onAppendChildObject?: (id: number) => void
}

const GraphNodeActionsContext = createContext<GraphNodeActions | null>(null)

function ObjectTypeIcon({ type }: { type: NonNullable<ContentGraphNodeData["objectType"]> }) {
  const Icon = getObjectTypeConfig(type).icon
  return <Icon />
}

function ContentDomainNodeComponent({ data, selected }: NodeProps) {
  const node = data as ContentGraphNodeData
  const actions = useContext(GraphNodeActionsContext)
  const reasonIdPrefix = useId()
  const toggleCollapsedGroup = useProjectGraphViewState(
    actions?.projectId ?? 0,
    (state) => state.toggleCollapsedGroup
  )
  const isGroup = node.kind === "group"
  const objectId = node.objectId
  const canOpenCanvas = node.objectType === "canvas_board" && actions?.onOpenCanvas !== undefined
  const canAnnotate =
    (node.objectType === "image_deliverable" || node.objectType === "shot_keyframe") &&
    actions?.onAnnotateImage !== undefined
  const selectable =
    objectId !== undefined && (!actions?.selectionMode || actions.selectableObjectIds.has(objectId))
  const readiness = objectId === undefined ? undefined : actions?.readinessByObjectId.get(objectId)
  const readOnly = actions?.readOnly ?? true
  const mediaAction =
    node.outputContract === "MEDIA_VERSION" && objectId !== undefined
      ? objectMediaActionPresentation({
          readiness,
          generatable: actions?.generatableObjectIds.has(objectId) ?? false,
          readOnly
        })
      : null
  const contractState = objectStructureActionState({
    readiness,
    actionKey: "object.contract",
    readOnly
  })
  const removeState = objectStructureActionState({
    readiness,
    actionKey: "object.remove",
    readOnly
  })
  const appendChildState = objectStructureActionState({
    readiness,
    actionKey: "object.append.child",
    readOnly
  })
  const structureActionsVisible =
    !readOnly &&
    (actions?.onUpdateContract !== undefined ||
      actions?.onRemoveObject !== undefined ||
      actions?.onAppendChildObject !== undefined)
  const mediaReasonId = `${reasonIdPrefix}-media`
  const actionReasons = [
    mediaAction?.reason ? { id: mediaReasonId, text: `生成：${mediaAction.reason}` } : null,
    structureActionsVisible && contractState.reason
      ? { id: `${reasonIdPrefix}-contract`, text: `合同：${contractState.reason}` }
      : null,
    structureActionsVisible && removeState.reason
      ? { id: `${reasonIdPrefix}-remove`, text: `移除：${removeState.reason}` }
      : null,
    structureActionsVisible && appendChildState.reason
      ? { id: `${reasonIdPrefix}-append-child`, text: `添加下级：${appendChildState.reason}` }
      : null
  ].filter((reason): reason is { id: string; text: string } => reason !== null)

  return (
    <GlassCard
      glow={node.blockedCount > 0 ? "rose" : isGroup ? "violet" : "none"}
      className={cn(
        "w-56 border border-border/60 transition-shadow",
        isGroup && "w-60",
        selected && "ring-2 ring-primary",
        actions?.selectionMode && selectable && "cursor-pointer hover:ring-2 hover:ring-primary/50",
        actions?.selectionMode && !selectable && "opacity-45"
      )}
    >
      <Handle type="target" position={Position.Left} className="!size-2 !bg-muted-foreground" />
      <div className="flex flex-col gap-2 p-3">
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <p className="flex items-center gap-2 truncate font-medium text-sm">
              {node.objectType ? <ObjectTypeIcon type={node.objectType} /> : null}
              {node.title}
            </p>
            {node.zoomTier !== "global" && node.description ? (
              <p className="truncate text-muted-foreground text-xs">{node.description}</p>
            ) : null}
          </div>
          {isGroup && !actions?.selectionMode ? (
            <Button
              type="button"
              variant="ghost"
              size="icon-xs"
              className="nodrag shrink-0"
              aria-label={node.collapsed ? `展开${node.title}` : `折叠${node.title}`}
              onClick={() => toggleCollapsedGroup(node.stage)}
            >
              {node.collapsed ? <ChevronRight /> : <ChevronDown />}
            </Button>
          ) : node.status ? (
            <Badge variant={STATUS_VARIANT[node.status]}>{OBJECT_STATUS_LABELS[node.status]}</Badge>
          ) : null}
        </div>
        {node.zoomTier === "global" ? (
          <p className="text-muted-foreground text-xs">{node.objectCount} 个对象</p>
        ) : (
          <p
            className={cn(
              "text-muted-foreground text-xs",
              node.zoomTier === "overview" && "line-clamp-2"
            )}
          >
            {node.summary}
          </p>
        )}
        {!isGroup && node.zoomTier !== "global" ? (
          <div className="flex flex-wrap gap-1">
            {node.semanticRole ? (
              <Badge variant="outline">{SEMANTIC_ROLE_LABELS[node.semanticRole]}</Badge>
            ) : null}
            {node.outputContract ? (
              <Badge variant="secondary">{OUTPUT_CONTRACT_LABELS[node.outputContract]}</Badge>
            ) : null}
            {node.readinessState ? (
              <Badge variant={node.blockedCount > 0 ? "destructive" : "outline"}>
                {READINESS_LABELS[node.readinessState]}
              </Badge>
            ) : null}
          </div>
        ) : null}
        {node.blockedReason ? (
          <p className="line-clamp-2 text-destructive text-xs">{node.blockedReason}</p>
        ) : null}
        {!isGroup && objectId !== undefined && !actions?.selectionMode ? (
          <div className="nodrag flex flex-col gap-1">
            <div className="flex gap-1">
              <Button
                type="button"
                variant="outline"
                size="xs"
                className="flex-1"
                onClick={(event) => {
                  event.stopPropagation()
                  actions?.onOpenDetails?.(objectId)
                }}
              >
                {node.outputContract === "DOCUMENT_VERSION" ? <FileText /> : <Eye />}
                {node.outputContract === "DOCUMENT_VERSION" ? "手动创建" : "详情"}
              </Button>
              {mediaAction ? (
                <Button
                  type="button"
                  variant="outline"
                  size="xs"
                  className="flex-1"
                  disabled={mediaAction.disabled}
                  aria-describedby={mediaAction.reason ? mediaReasonId : undefined}
                  onClick={(event) => {
                    event.stopPropagation()
                    if (mediaAction.intent === "candidate") actions?.onOpenDetails?.(objectId)
                    else actions?.onGenerateObject?.(objectId)
                  }}
                >
                  <Sparkles />
                  {mediaAction.label}
                </Button>
              ) : null}
              {canOpenCanvas || canAnnotate ? (
                <Button
                  type="button"
                  variant="outline"
                  size="xs"
                  className="flex-1"
                  disabled={actions?.readOnly}
                  onClick={(event) => {
                    event.stopPropagation()
                    if (canOpenCanvas) actions?.onOpenCanvas?.(objectId)
                    else actions?.onAnnotateImage?.(objectId)
                  }}
                >
                  {canOpenCanvas ? <PanelTopOpen /> : <ScanLine />}
                  {canOpenCanvas ? "画布" : "标注"}
                </Button>
              ) : null}
              {structureActionsVisible ? (
                <DropdownMenu>
                  <DropdownMenuTrigger
                    className={cn(buttonVariants({ variant: "outline", size: "icon-xs" }))}
                    aria-label={`${node.title} 结构操作`}
                    onClick={(event) => event.stopPropagation()}
                  >
                    <Ellipsis />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="min-w-56">
                    <DropdownMenuGroup>
                      <DropdownMenuLabel>结构操作</DropdownMenuLabel>
                      <DropdownMenuItem
                        disabled={contractState.disabled}
                        onClick={() => actions?.onUpdateContract?.(objectId)}
                      >
                        <FilePenLine />
                        合同
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        disabled={removeState.disabled}
                        onClick={() => actions?.onRemoveObject?.(objectId)}
                      >
                        <Trash2 />
                        移除
                      </DropdownMenuItem>
                      <DropdownMenuItem
                        disabled={appendChildState.disabled}
                        onClick={() => actions?.onAppendChildObject?.(objectId)}
                      >
                        <Plus />
                        添加下级
                      </DropdownMenuItem>
                    </DropdownMenuGroup>
                  </DropdownMenuContent>
                </DropdownMenu>
              ) : null}
            </div>
            {actionReasons.length > 0 ? (
              <div className="flex flex-col gap-0.5">
                {actionReasons.map((reason) => (
                  <p
                    key={reason.id}
                    id={reason.id}
                    className="text-[11px] text-muted-foreground leading-snug"
                  >
                    {reason.text}
                  </p>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
        {node.zoomTier === "detail" && !isGroup ? (
          <div className="flex items-center justify-between text-[11px] text-muted-foreground">
            <span>
              {actions?.selectionMode ? (selectable ? "点击选择" : "无可用图片") : "点击聚焦"}
            </span>
            <span>#{node.objectId}</span>
          </div>
        ) : null}
      </div>
      <Handle type="source" position={Position.Right} className="!size-2 !bg-muted-foreground" />
    </GlassCard>
  )
}

const ContentDomainNode = memo(ContentDomainNodeComponent)
const NODE_TYPES = { contentDomain: ContentDomainNode }

export interface ProjectGraphViewProps {
  projectId?: number
  graph: AigcProjectGraph
  readiness?: AigcObjectReadiness[]
  focusObjectId?: number
  readOnly?: boolean
  selectionMode?: boolean
  selectableObjectIds?: number[]
  generatableObjectIds?: number[]
  onFocusObject?: (id: number) => void
  onSelectObject?: (id: number) => void
  onOpenDetails?: (id: number) => void
  onOpenCanvas?: (id: number) => void
  onAnnotateImage?: (id: number) => void
  onGenerateObject?: (id: number) => void
  onUpdateContract?: (id: number) => void
  onRemoveObject?: (id: number) => void
  onAppendChildObject?: (id: number) => void
}

function tierForZoom(zoom: number) {
  if (zoom < 0.55) return "global" as const
  if (zoom < 0.95) return "overview" as const
  return "detail" as const
}

export function ProjectGraphView({
  projectId: requestedProjectId,
  graph,
  readiness = [],
  focusObjectId,
  readOnly = false,
  selectionMode = false,
  selectableObjectIds = [],
  generatableObjectIds = [],
  onFocusObject,
  onSelectObject,
  onOpenDetails,
  onOpenCanvas,
  onAnnotateImage,
  onGenerateObject,
  onUpdateContract,
  onRemoveObject,
  onAppendChildObject
}: ProjectGraphViewProps) {
  const projectId = requestedProjectId ?? graph.project.id
  const viewport = useProjectGraphViewState(projectId, (state) => state.viewport)
  const collapsedGroups = useProjectGraphViewState(projectId, (state) => state.collapsedGroups)
  const activeLayers = useProjectGraphViewState(projectId, (state) => state.activeLayers)
  const zoomTier = useProjectGraphViewState(projectId, (state) => state.zoomTier)
  const setViewport = useProjectGraphViewState(projectId, (state) => state.setViewport)
  const setZoomTier = useProjectGraphViewState(projectId, (state) => state.setZoomTier)
  const toggleLayer = useProjectGraphViewState(projectId, (state) => state.toggleLayer)
  const projection = useMemo(
    () =>
      toGraphProjection(graph, {
        collapsedGroups: selectionMode ? [] : collapsedGroups,
        activeLayers: selectionMode ? LAYER_OPTIONS.map((option) => option.value) : activeLayers,
        readiness,
        focusObjectId: selectionMode ? undefined : focusObjectId,
        zoomTier: selectionMode ? "detail" : zoomTier
      }),
    [graph, collapsedGroups, activeLayers, readiness, focusObjectId, selectionMode, zoomTier]
  )
  const selectableObjectIdSet = useMemo(() => new Set(selectableObjectIds), [selectableObjectIds])
  const generatableObjectIdSet = useMemo(
    () => new Set(generatableObjectIds),
    [generatableObjectIds]
  )
  const readinessByObjectId = useMemo(
    () => new Map(readiness.map((item) => [item.projectObjectId, item] as const)),
    [readiness]
  )
  const nodeActions = useMemo(
    () => ({
      projectId,
      readOnly,
      selectionMode,
      selectableObjectIds: selectableObjectIdSet,
      generatableObjectIds: generatableObjectIdSet,
      readinessByObjectId,
      onOpenDetails,
      onOpenCanvas,
      onAnnotateImage,
      onGenerateObject,
      onUpdateContract,
      onRemoveObject,
      onAppendChildObject
    }),
    [
      projectId,
      readOnly,
      selectionMode,
      selectableObjectIdSet,
      generatableObjectIdSet,
      readinessByObjectId,
      onOpenDetails,
      onOpenCanvas,
      onAnnotateImage,
      onGenerateObject,
      onUpdateContract,
      onRemoveObject,
      onAppendChildObject
    ]
  )

  function handleMoveEnd(_event: MouseEvent | TouchEvent | null, nextViewport: Viewport) {
    setViewport(nextViewport)
    setZoomTier(tierForZoom(nextViewport.zoom))
  }

  return (
    <GraphNodeActionsContext.Provider value={nodeActions}>
      <div className="relative h-full min-h-[520px] overflow-hidden rounded-2xl border bg-background/40">
        {!selectionMode ? (
          <div className="absolute top-3 left-3 z-10 flex flex-wrap items-center gap-2 rounded-xl border bg-background/90 p-2 shadow-sm backdrop-blur">
            <Layers3 className="size-4 text-muted-foreground" />
            <ToggleGroup
              value={activeLayers}
              onValueChange={(values: string[]) => {
                for (const option of LAYER_OPTIONS) {
                  if (values.includes(option.value) !== activeLayers.includes(option.value)) {
                    toggleLayer(option.value)
                  }
                }
              }}
              variant="outline"
              size="sm"
              spacing={0}
            >
              {LAYER_OPTIONS.map((layer) => (
                <ToggleGroupItem
                  key={layer.value}
                  value={layer.value}
                  aria-label={`${layer.label}图层`}
                >
                  {layer.label}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
            <Badge variant="secondary">
              {zoomTier === "global" ? "全局" : zoomTier === "overview" ? "概览" : "详情"}
            </Badge>
          </div>
        ) : null}

        <ReactFlow
          nodes={projection.nodes}
          edges={projection.edges}
          nodeTypes={NODE_TYPES}
          viewport={viewport}
          onMove={selectionMode ? undefined : handleMoveEnd}
          onNodeClick={(_event, node) => {
            const objectId = (node.data as ContentGraphNodeData).objectId
            if (objectId === undefined) return
            if (selectionMode) {
              if (selectableObjectIdSet.has(objectId)) onSelectObject?.(objectId)
              return
            }
            onFocusObject?.(objectId)
          }}
          nodesDraggable={false}
          nodesConnectable={false}
          edgesReconnectable={false}
          elementsSelectable
          minZoom={0.25}
          maxZoom={1.6}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={24} size={1} />
          <Controls position="bottom-left" />
          <MiniMap
            pannable
            zoomable
            nodeColor={(node) =>
              (node.data as ContentGraphNodeData).kind === "group"
                ? "var(--color-primary)"
                : "var(--color-muted-foreground)"
            }
          />
        </ReactFlow>
      </div>
    </GraphNodeActionsContext.Provider>
  )
}
