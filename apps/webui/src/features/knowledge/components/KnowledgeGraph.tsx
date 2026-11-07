/**
 * 知识图谱可视化：使用独立 Three.js 引擎展示真实节点和关系。
 * @author AaronZZH & Kiro
 */

"use client"

import { useTabs } from "@aaf/hooks"
import type {
  GraphDimension,
  GraphVisualizationProps,
  VisualizationGraph
} from "@aaf/knowledge-visualization"
import { Box, Layers3, RefreshCw, Square } from "lucide-react"
import dynamic from "next/dynamic"
import { useTheme } from "next-themes"
import { useEffect, useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { useKnowledgeGraph } from "@/lib/api/rest/knowledge/knowledge"
import type { GraphEdge, GraphNode } from "@/lib/types/knowledge"

interface KnowledgeGraphProps {
  knowledgeBaseId: string
}

interface SelectedRelation {
  direction: "outgoing" | "incoming"
  edge: GraphEdge
  related: GraphNode | undefined
}

type KnowledgeGraphVisualizationProps = GraphVisualizationProps<GraphNode, GraphEdge>

const ThreeKnowledgeGraph = dynamic<KnowledgeGraphVisualizationProps>(
  () => import("@aaf/knowledge-visualization").then((module) => module.GraphVisualization),
  {
    ssr: false,
    loading: () => <Skeleton className="h-full min-h-[540px] w-full rounded-2xl" />
  }
)

const DIMENSION_LABELS: Record<GraphDimension, string> = {
  "2d": "2D",
  "2.5d": "2.5D",
  "3d": "3D"
}

export function KnowledgeGraph({ knowledgeBaseId }: KnowledgeGraphProps) {
  const { data, error, isError, isLoading, refetch } = useKnowledgeGraph(knowledgeBaseId)
  const dimension = useTabs("2.5d")
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [hoveredNodeId, setHoveredNodeId] = useState<string | null>(null)
  const [renderError, setRenderError] = useState<Error | null>(null)
  const [rendererRevision, setRendererRevision] = useState(0)
  const { resolvedTheme } = useTheme()
  const colorScheme = resolvedTheme === "dark" ? "dark" : "light"

  const degreeById = useMemo(() => {
    const degrees = new Map<string, number>()
    for (const edge of data?.edges ?? []) {
      degrees.set(edge.sourceId, (degrees.get(edge.sourceId) ?? 0) + 1)
      degrees.set(edge.targetId, (degrees.get(edge.targetId) ?? 0) + 1)
    }
    return degrees
  }, [data])

  const graph = useMemo<VisualizationGraph<GraphNode, GraphEdge>>(
    () => ({
      nodes: (data?.nodes ?? []).map((node) => ({
        id: node.id,
        label: node.name,
        color: colorForEntityType(node.type),
        layer: layerForEntityType(node.type),
        size: sizeForDegree(degreeById.get(node.id) ?? 0),
        data: node
      })),
      // 关系线不指定颜色，由引擎按两端节点色渐变并压暗
      edges: (data?.edges ?? []).map((edge) => ({
        id: edge.id,
        source: edge.sourceId,
        target: edge.targetId,
        label: edge.predicate,
        strength: Math.max(0.35, edge.confidence),
        data: edge
      }))
    }),
    [data, degreeById]
  )

  const typeLegend = useMemo(() => {
    const counts = new Map<string, number>()
    for (const node of data?.nodes ?? []) {
      const type = normalizeEntityType(node.type)
      counts.set(type, (counts.get(type) ?? 0) + 1)
    }
    return [...counts.entries()].sort((left, right) => right[1] - left[1])
  }, [data])

  const nodesById = useMemo(
    () => new Map((data?.nodes ?? []).map((node) => [node.id, node])),
    [data]
  )
  const selected = selectedNodeId ? (nodesById.get(selectedNodeId) ?? null) : null
  const hovered = hoveredNodeId ? (nodesById.get(hoveredNodeId) ?? null) : null
  const selectedRelations = useMemo<SelectedRelation[]>(() => {
    if (!selected || !data) return []

    const relations: SelectedRelation[] = []
    for (const edge of data.edges) {
      if (edge.sourceId === selected.id) {
        relations.push({
          direction: "outgoing",
          edge,
          related: nodesById.get(edge.targetId)
        })
      } else if (edge.targetId === selected.id) {
        relations.push({
          direction: "incoming",
          edge,
          related: nodesById.get(edge.sourceId)
        })
      }
    }
    return relations
  }, [data, nodesById, selected])

  // biome-ignore lint/correctness/useExhaustiveDependencies: 切换图谱维度时需重置临时渲染状态
  useEffect(() => {
    setRenderError(null)
    setHoveredNodeId(null)
  }, [dimension.value])

  useEffect(() => {
    if (selectedNodeId && !nodesById.has(selectedNodeId)) setSelectedNodeId(null)
    if (hoveredNodeId && !nodesById.has(hoveredNodeId)) setHoveredNodeId(null)
  }, [hoveredNodeId, nodesById, selectedNodeId])

  if (isLoading) {
    return <Skeleton className="h-[540px] w-full rounded-2xl" />
  }

  if (isError) {
    return (
      <Empty className="min-h-[540px]">
        <EmptyHeader>
          <EmptyTitle>知识图谱加载失败</EmptyTitle>
          <EmptyDescription>{error.message}</EmptyDescription>
        </EmptyHeader>
        <Button type="button" variant="outline" onClick={() => void refetch()}>
          <RefreshCw />
          重新加载
        </Button>
      </Empty>
    )
  }

  if (!data || data.nodes.length === 0) {
    return (
      <Empty className="min-h-[540px]">
        <EmptyHeader>
          <EmptyTitle>暂无图谱数据</EmptyTitle>
          <EmptyDescription>文档处理完成后将展示抽取出的实体关系</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  if (renderError) {
    return (
      <Empty className="min-h-[540px]">
        <EmptyHeader>
          <EmptyTitle>知识图谱渲染失败</EmptyTitle>
          <EmptyDescription>{renderError.message}</EmptyDescription>
        </EmptyHeader>
        <Button
          type="button"
          variant="outline"
          onClick={() => {
            setRenderError(null)
            setRendererRevision((revision) => revision + 1)
          }}
        >
          <RefreshCw />
          重新渲染
        </Button>
      </Empty>
    )
  }

  const activeDimension = dimension.value as GraphDimension

  return (
    <div className="@container flex @4xl:flex-row flex-col gap-4">
      <div className="relative @4xl:h-[620px] h-[540px] min-h-[540px] flex-1 overflow-hidden rounded-2xl border bg-muted/20">
        <div className="absolute top-3 left-3 z-10 flex max-w-[calc(100%-1.5rem)] flex-wrap items-center gap-2 rounded-xl border bg-background/90 p-2 shadow-sm backdrop-blur">
          <ToggleGroup
            value={[activeDimension]}
            onValueChange={(values: string[]) => {
              const next = values.at(-1)
              if (isGraphDimension(next)) dimension.onChange(next)
            }}
            variant="outline"
            size="sm"
            spacing={0}
          >
            <ToggleGroupItem value="2d" aria-label="切换到二维知识图谱">
              <Square />
              2D
            </ToggleGroupItem>
            <ToggleGroupItem value="2.5d" aria-label="切换到二点五维知识图谱">
              <Layers3 />
              2.5D
            </ToggleGroupItem>
            <ToggleGroupItem value="3d" aria-label="切换到三维知识图谱">
              <Box />
              3D
            </ToggleGroupItem>
          </ToggleGroup>
          <Badge className="@md:flex hidden" variant="secondary">
            {data.nodes.length} 节点
          </Badge>
          <Badge className="@md:flex hidden" variant="outline">
            {data.edges.length} 关系
          </Badge>
        </div>

        <ThreeKnowledgeGraph
          key={rendererRevision}
          graph={graph}
          dimension={activeDimension}
          layout="force"
          colorScheme={colorScheme}
          selectedNodeId={selectedNodeId}
          ariaLabel={`${DIMENSION_LABELS[activeDimension]} 知识图谱，共 ${data.nodes.length} 个节点`}
          onNodePick={(node) => setSelectedNodeId(node?.id ?? null)}
          onNodeHover={(node) => setHoveredNodeId(node?.id ?? null)}
          onError={setRenderError}
        />

        {hovered ? (
          <div className="pointer-events-none absolute bottom-3 left-3 max-w-72 rounded-lg border bg-background/90 px-3 py-2 shadow-sm backdrop-blur">
            <p className="truncate font-medium text-sm">{hovered.name}</p>
            <p className="text-muted-foreground text-xs">{entityTypeLabel(hovered.type)}</p>
            {hovered.description ? (
              <p className="mt-1 line-clamp-2 text-muted-foreground text-xs">
                {hovered.description}
              </p>
            ) : null}
          </div>
        ) : null}

        <ul className="pointer-events-none absolute right-3 bottom-3 z-10 flex max-w-[50%] flex-wrap justify-end gap-x-3 gap-y-1 rounded-lg border bg-background/90 px-3 py-2 text-xs shadow-sm backdrop-blur">
          {typeLegend.map(([type, count]) => (
            <li key={type} className="flex items-center gap-1.5">
              <span
                className="size-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: toCssColor(colorForEntityType(type)) }}
              />
              <span>{entityTypeLabel(type)}</span>
              <span className="text-muted-foreground">{count}</span>
            </li>
          ))}
        </ul>
      </div>

      {selected ? (
        <Card className="@4xl:w-80 shrink-0" size="sm">
          <CardHeader>
            <CardTitle>{selected.name}</CardTitle>
            <CardDescription>知识实体详情</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4 text-sm">
            <dl className="flex flex-col gap-3">
              <div>
                <dt className="text-muted-foreground">类型</dt>
                <dd className="mt-1">
                  <Badge variant="secondary">{entityTypeLabel(selected.type)}</Badge>
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">描述</dt>
                <dd className="mt-1 leading-relaxed">{selected.description || "暂无描述"}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">节点 ID</dt>
                <dd className="mt-1 break-all font-mono text-xs">{selected.id}</dd>
              </div>
            </dl>

            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between gap-2">
                <h3 className="font-medium">关联关系</h3>
                <Badge variant="outline">{selectedRelations.length}</Badge>
              </div>
              {selectedRelations.length > 0 ? (
                <ul className="flex flex-col gap-2">
                  {selectedRelations.slice(0, 6).map(({ direction, edge, related }) => (
                    <li key={edge.id} className="rounded-lg bg-muted/60 p-2">
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate font-medium text-xs">{edge.predicate}</span>
                        <span className="shrink-0 text-muted-foreground text-xs">
                          {(edge.confidence * 100).toFixed(0)}%
                        </span>
                      </div>
                      <div className="mt-1.5 flex items-center gap-1.5 text-xs">
                        <Badge variant="outline">
                          {direction === "outgoing" ? "出向" : "入向"}
                        </Badge>
                        <span className="truncate text-muted-foreground">
                          {related?.name ??
                            (direction === "outgoing" ? edge.targetId : edge.sourceId)}
                        </span>
                      </div>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-muted-foreground text-xs">暂无关联关系</p>
              )}
            </div>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}

function isGraphDimension(value: string | undefined): value is GraphDimension {
  return value === "2d" || value === "2.5d" || value === "3d"
}

/** 与后端 ExtractedEntityType 枚举一一对应，未知类型归入 OTHER；统一用 400 色阶，暗色背景上足够亮，亮色背景上不刺眼。 */
const ENTITY_TYPE_STYLES: Record<string, { color: number; label: string }> = {
  PERSON: { color: 0xa78bfa, label: "人物" },
  ORGANIZATION: { color: 0x60a5fa, label: "组织" },
  LOCATION: { color: 0x34d399, label: "地点" },
  EVENT: { color: 0xf87171, label: "事件" },
  PRODUCT: { color: 0xfb923c, label: "产品" },
  TECHNOLOGY: { color: 0x22d3ee, label: "技术" },
  SYSTEM: { color: 0x2dd4bf, label: "系统" },
  DOCUMENT: { color: 0xfbbf24, label: "文档" },
  CONCEPT: { color: 0x818cf8, label: "概念" },
  METRIC: { color: 0xf472b6, label: "指标" },
  ROLE: { color: 0xa3e635, label: "角色" },
  OTHER: { color: 0x94a3b8, label: "其他" }
}

function normalizeEntityType(type: string): string {
  const normalized = type.trim().toUpperCase()
  return normalized in ENTITY_TYPE_STYLES ? normalized : "OTHER"
}

function colorForEntityType(type: string): number {
  return ENTITY_TYPE_STYLES[normalizeEntityType(type)]?.color ?? 0x94a3b8
}

function entityTypeLabel(type: string): string {
  return ENTITY_TYPE_STYLES[normalizeEntityType(type)]?.label ?? type
}

function toCssColor(color: number): string {
  return `#${color.toString(16).padStart(6, "0")}`
}

/** 连接度越高节点越大，开方压缩避免枢纽节点过大。 */
function sizeForDegree(degree: number): number {
  return 4 + Math.min(7, Math.sqrt(degree) * 1.8)
}

function layerForEntityType(type: string): number {
  const normalized = normalizeEntityType(type)
  if (normalized === "DOCUMENT") return -1
  if (normalized === "EVENT") return 1
  if (normalized === "CONCEPT") return 2
  return 0
}
