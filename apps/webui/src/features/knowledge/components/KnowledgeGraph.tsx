/**
 * 知识图谱可视化——基于 @xyflow/react 展示节点和边
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
import { Skeleton } from "@/components/ui/skeleton"
import { useKnowledgeGraph } from "@/lib/queries/use-knowledge"
import type { GraphNode } from "@/lib/types/knowledge"

interface KnowledgeGraphProps {
  knowledgeBaseId: string
}

export function KnowledgeGraph({ knowledgeBaseId }: KnowledgeGraphProps) {
  const { data, isLoading } = useKnowledgeGraph(knowledgeBaseId)
  const [selected, setSelected] = useState<GraphNode | null>(null)

  const nodes: Node[] = useMemo(() => {
    if (!data) return []
    return data.nodes.map((n, i) => ({
      id: n.id,
      position: { x: (i % 5) * 200, y: Math.floor(i / 5) * 150 },
      data: { ...n, label: n.label },
      type: "default"
    }))
  }, [data])

  const edges: Edge[] = useMemo(() => {
    if (!data) return []
    return data.edges.map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      label: e.label,
      animated: true
    }))
  }, [data])

  const onNodeClick: NodeMouseHandler = useCallback((_event, node) => {
    setSelected(node.data as unknown as GraphNode)
  }, [])

  if (isLoading) {
    return <Skeleton className="h-96 w-full rounded-lg" />
  }

  if (!data || data.nodes.length === 0) {
    return <p className="py-8 text-center text-muted-foreground text-sm">暂无图谱数据</p>
  }

  return (
    <div className="flex gap-4">
      <div className="h-[500px] flex-1 rounded-lg border">
        <ReactFlow
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

      {/* 详情面板 */}
      {selected && (
        <Card className="w-64 shrink-0">
          <CardHeader>
            <CardTitle className="text-sm">节点详情</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <div>
              <span className="text-muted-foreground">名称：</span>
              {selected.label}
            </div>
            <div>
              <span className="text-muted-foreground">类型：</span>
              {selected.type}
            </div>
            <div>
              <span className="text-muted-foreground">ID：</span>
              <span className="font-mono text-xs">{selected.id}</span>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
