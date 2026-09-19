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
  Eye,
  FileText,
  Layers3,
  PanelTopOpen,
  ScanLine,
  Sparkles
} from "lucide-react"
import { createContext, memo, useContext, useMemo } from "react"
import "@xyflow/react/dist/style.css"
import { GlassCard } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
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
  onOpenDetails?: (id: number) => void
  onOpenCanvas?: (id: number) => void
  onAnnotateImage?: (id: number) => void
  onGenerateObject?: (id: number) => void
}

const GraphNodeActionsContext = createContext<GraphNodeActions | null>(null)

function ObjectTypeIcon({ type }: { type: NonNullable<ContentGraphNodeData["objectType"]> }) {
  const Icon = getObjectTypeConfig(type).icon
  return <Icon />
}

function ContentDomainNodeComponent({ data, selected }: NodeProps) {
  const node = data as ContentGraphNodeData
  const actions = useContext(GraphNodeActionsContext)
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
          <div className="nodrag flex gap-1">
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
            {node.outputContract === "MEDIA_VERSION" ? (
              <Button
                type="button"
                variant="outline"
                size="xs"
                className="flex-1"
                disabled={actions?.readOnly || !actions?.generatableObjectIds.has(objectId)}
                onClick={(event) => {
                  event.stopPropagation()
                  actions?.onGenerateObject?.(objectId)
                }}
              >
                <Sparkles />
                生成
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
  onGenerateObject
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
  const nodeActions = useMemo(
    () => ({
      projectId,
      readOnly,
      selectionMode,
      selectableObjectIds: selectableObjectIdSet,
      generatableObjectIds: generatableObjectIdSet,
      onOpenDetails,
      onOpenCanvas,
      onAnnotateImage,
      onGenerateObject
    }),
    [
      projectId,
      readOnly,
      selectionMode,
      selectableObjectIdSet,
      generatableObjectIdSet,
      onOpenDetails,
      onOpenCanvas,
      onAnnotateImage,
      onGenerateObject
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
