/**
 * 文档关系图谱组件（React Flow）
 * @author AaronZZH & Kiro
 */
"use client"

import { useCallback, useEffect, useMemo } from "react"
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Node,
  type Edge,
  useNodesState,
  useEdgesState,
} from "@xyflow/react"
import "@xyflow/react/dist/style.css"
import { useDocRelations } from "@/lib/queries/use-documents"
import { Skeleton } from "@/components/ui/skeleton"

interface Props {
  docId: number
  onSelectDoc: (id: number) => void
}

export function DocRelationGraph({ docId, onSelectDoc }: Props) {
  const { data, isLoading } = useDocRelations(docId)

  const { initialNodes, initialEdges } = useMemo(() => {
    if (!data) return { initialNodes: [] as Node[], initialEdges: [] as Edge[] }

    const rfNodes: Node[] = data.nodes.map((node, i) => ({
      id: String(node.id),
      data: { label: node.title, docId: node.id },
      position: {
        x: node.isCenter ? 300 : 100 + (i % 3) * 200,
        y: node.isCenter ? 200 : 50 + Math.floor(i / 3) * 150,
      },
      style: node.isCenter
        ? {
            background: "hsl(var(--primary))",
            color: "white",
            border: "none",
            borderRadius: 8,
            padding: "8px 12px",
          }
        : {
            background: "hsl(var(--card))",
            border: "1px solid hsl(var(--border))",
            borderRadius: 8,
            padding: "8px 12px",
          },
    }))

    const rfEdges: Edge[] = data.edges.map((edge, i) => ({
      id: `e-${i}`,
      source: String(edge.source),
      target: String(edge.target),
      label: edge.type === "wikilink" ? "双链" : "引用",
      animated: edge.type === "wikilink",
    }))

    return { initialNodes: rfNodes, initialEdges: rfEdges }
  }, [data])

  const [rfNodes, setRfNodes, onNodesChange] = useNodesState(initialNodes)
  const [rfEdges, setRfEdges, onEdgesChange] = useEdgesState(initialEdges)

  // 数据变化时同步更新节点和边
  useEffect(() => {
    setRfNodes(initialNodes)
    setRfEdges(initialEdges)
  }, [initialNodes, initialEdges, setRfNodes, setRfEdges])

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => {
      const id = node.data.docId as number
      onSelectDoc(id)
    },
    [onSelectDoc]
  )

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  if (!data || data.nodes.length === 0) {
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground">
        <p>暂无关系数据</p>
      </div>
    )
  }

  return (
    <div className="h-full w-full">
      <ReactFlow
        nodes={rfNodes}
        edges={rfEdges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={onNodeClick}
        fitView
      >
        <Background />
        <Controls />
        <MiniMap />
      </ReactFlow>
    </div>
  )
}
