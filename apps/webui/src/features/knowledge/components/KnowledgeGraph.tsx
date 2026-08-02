/**
 * 知识图谱可视化——基于 @xyflow/react 展示真实节点和边
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Background,
  Controls,
  type Edge,
  type Node,
  type NodeMouseHandler,
  ReactFlow
} from "@xyflow/react"
import { useCallback, useMemo, useState } from "react"
import "@xyflow/react/dist/style.css"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty"
import { Skeleton } from "@/components/ui/skeleton"
import { useKnowledgeGraph } from "@/lib/api/rest/knowledge/knowledge"
import type { GraphNode } from "@/lib/types/knowledge"

interface KnowledgeGraphProps {
  knowledgeBaseId: string
}

type GraphNodeData = {
  label: string
  graphNode: GraphNode
} & Record<string, unknown>

type KnowledgeFlowNode = Node<GraphNodeData>

export function KnowledgeGraph({ knowledgeBaseId }: KnowledgeGraphProps) {
  const { data, isLoading } = useKnowledgeGraph(knowledgeBaseId)
  const [selected, setSelected] = useState<GraphNode | null>(null)

  const nodes = useMemo<KnowledgeFlowNode[]>(() => {
    if (!data) return []
    return data.nodes.map((node, index) => ({
      id: node.id,
      position: { x: (index % 5) * 200, y: Math.floor(index / 5) * 150 },
      data: { label: node.name, graphNode: node },
      type: "default"
    }))
  }, [data])

  const edges = useMemo<Edge[]>(() => {
    if (!data) return []
    return data.edges.map((edge) => ({
      id: edge.id,
      source: edge.sourceId,
      target: edge.targetId,
      label: `${edge.predicate} · ${(edge.confidence * 100).toFixed(0)}%`,
      animated: true
    }))
  }, [data])

  const onNodeClick: NodeMouseHandler<KnowledgeFlowNode> = useCallback((_event, node) => {
    setSelected(node.data.graphNode)
  }, [])

  if (isLoading) {
    return <Skeleton className="h-96 w-full rounded-lg" />
  }

  if (!data || data.nodes.length === 0) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyTitle>暂无图谱数据</EmptyTitle>
          <EmptyDescription>文档处理完成后将展示抽取出的实体关系</EmptyDescription>
        </EmptyHeader>
      </Empty>
    )
  }

  return (
    <div className="flex gap-4">
      <div className="h-[500px] flex-1 rounded-lg border">
        <ReactFlow<KnowledgeFlowNode>
          nodes={nodes}
          edges={edges}
          onNodeClick={onNodeClick}
          fitView
          proOptions={{ hideAttribution: true }}
        >
          <Background />
          <Controls />
        </ReactFlow>
      </div>

      {selected ? (
        <Card className="w-64 shrink-0">
          <CardHeader>
            <CardTitle className="text-sm">节点详情</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-2 text-sm">
            <div>
              <span className="text-muted-foreground">名称：</span>
              {selected.name}
            </div>
            <div>
              <span className="text-muted-foreground">类型：</span>
              {selected.type}
            </div>
            <div>
              <span className="text-muted-foreground">描述：</span>
              {selected.description || "暂无"}
            </div>
            <div>
              <span className="text-muted-foreground">ID：</span>
              <span className="break-all font-mono text-xs">{selected.id}</span>
            </div>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}
