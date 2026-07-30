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
import { ChevronDown, ChevronRight, Layers3, PanelTopOpen, ScanLine } from "lucide-react"
import { createContext, memo, useContext, useMemo } from "react"
import "@xyflow/react/dist/style.css"
import { GlassCard } from "@/components/studio"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { ContentProjectGraphVO, ContentRelationLayer } from "@/lib/api/rest/content"
import { cn } from "@/lib/utils/index"
import {
  type ContentGraphNodeData,
  OBJECT_STATUS_LABELS,
  toGraphProjection
} from "./graph-projection"
import { useProjectGraphViewState } from "./view-state-store"

const LAYER_OPTIONS: { value: ContentRelationLayer; label: string }[] = [
  { value: "domain", label: "领域" },
  { value: "reference", label: "引用" },
  { value: "story_order", label: "故事顺序" },
  { value: "execution", label: "执行依赖" }
]

const STATUS_VARIANT = {
  empty: "outline",
  draft: "secondary",
  pending_confirm: "default",
  adopted: "secondary",
  blocked: "destructive",
  done: "secondary"
} as const

interface GraphNodeActions {
  onOpenCanvas: (id: number) => void
  onAnnotateImage: (id: number) => void
}

const GraphNodeActionsContext = createContext<GraphNodeActions | null>(null)

function ContentDomainNodeComponent({ data, selected }: NodeProps) {
  const node = data as ContentGraphNodeData
  const actions = useContext(GraphNodeActionsContext)
  const toggleCollapsedGroup = useProjectGraphViewState((state) => state.toggleCollapsedGroup)
  const isGroup = node.kind === "group"
  const canOpenCanvas = node.objectType === "canvas_board"
  const canAnnotate = node.objectType === "image_deliverable" || node.objectType === "shot_keyframe"

  return (
    <GlassCard
      glow={node.blockedCount > 0 ? "rose" : isGroup ? "violet" : "none"}
      className={cn(
        "w-56 border border-border/60 transition-shadow",
        isGroup && "w-60",
        selected && "ring-2 ring-primary"
      )}
    >
      <Handle type="target" position={Position.Left} className="!size-2 !bg-muted-foreground" />
      <div className="flex flex-col gap-2 p-3">
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <p className="truncate font-medium text-sm">{node.title}</p>
            {node.zoomTier !== "global" && node.description ? (
              <p className="truncate text-muted-foreground text-xs">{node.description}</p>
            ) : null}
          </div>
          {isGroup ? (
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
        {!isGroup && node.objectId !== undefined && (canOpenCanvas || canAnnotate) ? (
          <Button
            type="button"
            variant="outline"
            size="xs"
            className="nodrag w-full"
            onClick={(event) => {
              event.stopPropagation()
              if (canOpenCanvas) actions?.onOpenCanvas(node.objectId as number)
              else actions?.onAnnotateImage(node.objectId as number)
            }}
          >
            {canOpenCanvas ? <PanelTopOpen /> : <ScanLine />}
            {canOpenCanvas ? "打开画布" : "标注"}
          </Button>
        ) : null}
        {node.zoomTier === "detail" && !isGroup ? (
          <div className="flex items-center justify-between text-[11px] text-muted-foreground">
            <span>点击聚焦</span>
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
  graph: ContentProjectGraphVO
  focusObjectId?: number
  onFocusObject: (id: number) => void
  onOpenCanvas: (id: number) => void
  onAnnotateImage: (id: number) => void
}

function tierForZoom(zoom: number) {
  if (zoom < 0.55) return "global" as const
  if (zoom < 0.95) return "overview" as const
  return "detail" as const
}

export function ProjectGraphView({
  graph,
  focusObjectId,
  onFocusObject,
  onOpenCanvas,
  onAnnotateImage
}: ProjectGraphViewProps) {
  const viewport = useProjectGraphViewState((state) => state.viewport)
  const collapsedGroups = useProjectGraphViewState((state) => state.collapsedGroups)
  const activeLayers = useProjectGraphViewState((state) => state.activeLayers)
  const zoomTier = useProjectGraphViewState((state) => state.zoomTier)
  const setViewport = useProjectGraphViewState((state) => state.setViewport)
  const setZoomTier = useProjectGraphViewState((state) => state.setZoomTier)
  const toggleLayer = useProjectGraphViewState((state) => state.toggleLayer)
  const projection = useMemo(
    () =>
      toGraphProjection(graph, {
        collapsedGroups,
        activeLayers,
        focusObjectId,
        zoomTier
      }),
    [graph, collapsedGroups, activeLayers, focusObjectId, zoomTier]
  )
  const nodeActions = useMemo(
    () => ({ onOpenCanvas, onAnnotateImage }),
    [onOpenCanvas, onAnnotateImage]
  )

  function handleMoveEnd(_event: MouseEvent | TouchEvent | null, nextViewport: Viewport) {
    setViewport(nextViewport)
    setZoomTier(tierForZoom(nextViewport.zoom))
  }

  return (
    <GraphNodeActionsContext.Provider value={nodeActions}>
      <div className="relative h-full min-h-[520px] overflow-hidden rounded-2xl border bg-background/40">
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

        <ReactFlow
          nodes={projection.nodes}
          edges={projection.edges}
          nodeTypes={NODE_TYPES}
          defaultViewport={viewport}
          onMoveEnd={handleMoveEnd}
          onNodeClick={(_event, node) => {
            const objectId = (node.data as ContentGraphNodeData).objectId
            if (objectId !== undefined) onFocusObject(objectId)
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
