/**
 * 流程可视化面板——运行中流程实时状态图 + 节点耗时 + 执行时间线
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { Badge } from "@/components/ui/badge"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { ExecutionState, FlowDefinition } from "../types"
import { FlowEditor } from "./flow-editor"

const BASE = process.env.NEXT_PUBLIC_API_URL ?? ""

/** 执行轨迹节点 */
interface TraceNode {
  nodeId: string
  nodeName: string
  status: "completed" | "running" | "failed" | "pending"
  startTime: string
  endTime?: string
  durationMs: number
}

/** 时间线条目 */
interface TimelineEntry {
  timestamp: string
  event: string
  nodeId?: string
  nodeName?: string
  detail?: string
}

interface ExecutionPanelProps {
  instanceId: string
  definition?: FlowDefinition
  executionState: ExecutionState
  nodeRegistry: Record<string, unknown>
}

export function ExecutionPanel({
  instanceId,
  definition,
  executionState,
  nodeRegistry
}: ExecutionPanelProps) {
  /** 查询执行轨迹 */
  const { data: trace } = useQuery({
    queryKey: ["workflow-trace", instanceId],
    queryFn: async () => {
      const res = await fetch(`${BASE}/api/workflow/instances/${instanceId}/execution-trace`)
      if (!res.ok) throw new Error("获取执行轨迹失败")
      const json = await res.json()
      return json.data as TraceNode[]
    },
    enabled: !!instanceId,
    refetchInterval: executionState.status === "running" ? 2000 : false
  })

  /** 查询时间线 */
  const { data: timeline } = useQuery({
    queryKey: ["workflow-timeline", instanceId],
    queryFn: async () => {
      const res = await fetch(`${BASE}/api/workflow/instances/${instanceId}/timeline`)
      if (!res.ok) throw new Error("获取时间线失败")
      const json = await res.json()
      return json.data as TimelineEntry[]
    },
    enabled: !!instanceId,
    refetchInterval: executionState.status === "running" ? 2000 : false
  })

  return (
    <Tabs defaultValue="graph" className="flex h-full flex-col">
      <TabsList className="mx-3 mt-2">
        <TabsTrigger value="graph">流程图</TabsTrigger>
        <TabsTrigger value="trace">节点耗时</TabsTrigger>
        <TabsTrigger value="timeline">时间线</TabsTrigger>
      </TabsList>

      {/* 流程图（只读 + 执行状态高亮） */}
      <TabsContent value="graph" className="flex-1 overflow-hidden">
        {definition ? (
          <FlowEditor
            mode="workflow"
            nodeRegistry={nodeRegistry as never}
            initialData={definition}
            onChange={() => {}}
            readonly
            executionState={executionState}
          />
        ) : (
          <div className="text-muted-foreground flex h-full items-center justify-center text-sm">
            无流程定义
          </div>
        )}
      </TabsContent>

      {/* 节点耗时统计 */}
      <TabsContent value="trace" className="flex-1 overflow-hidden">
        <ScrollArea className="h-full p-3">
          <div className="space-y-2">
            {(trace ?? []).map((node) => (
              <div
                key={node.nodeId}
                className="flex items-center justify-between rounded border p-2"
              >
                <div className="flex items-center gap-2">
                  <StatusDot status={node.status} />
                  <span className="text-sm">{node.nodeName}</span>
                </div>
                <span className="text-muted-foreground text-xs">
                  {node.durationMs > 0 ? `${node.durationMs}ms` : "-"}
                </span>
              </div>
            ))}
            {(!trace || trace.length === 0) && (
              <p className="text-muted-foreground py-4 text-center text-sm">暂无执行数据</p>
            )}
          </div>
        </ScrollArea>
      </TabsContent>

      {/* 执行时间线 */}
      <TabsContent value="timeline" className="flex-1 overflow-hidden">
        <ScrollArea className="h-full p-3">
          <div className="space-y-2">
            {(timeline ?? []).map((entry, i) => (
              <div key={`${entry.timestamp}-${i}`} className="flex gap-3 text-sm">
                <span className="text-muted-foreground w-20 shrink-0 text-xs">
                  {formatTime(entry.timestamp)}
                </span>
                <div>
                  <span>{entry.event}</span>
                  {entry.nodeName && (
                    <Badge variant="outline" className="ml-2 text-xs">
                      {entry.nodeName}
                    </Badge>
                  )}
                  {entry.detail && (
                    <p className="text-muted-foreground mt-0.5 text-xs">{entry.detail}</p>
                  )}
                </div>
              </div>
            ))}
            {(!timeline || timeline.length === 0) && (
              <p className="text-muted-foreground py-4 text-center text-sm">暂无时间线数据</p>
            )}
          </div>
        </ScrollArea>
      </TabsContent>
    </Tabs>
  )
}

/** 状态圆点 */
function StatusDot({ status }: { status: string }) {
  const colors: Record<string, string> = {
    completed: "bg-green-500",
    running: "bg-blue-500 animate-pulse",
    failed: "bg-red-500",
    pending: "bg-gray-300"
  }
  return <span className={`inline-block h-2 w-2 rounded-full ${colors[status] ?? "bg-gray-300"}`} />
}

/** 格式化时间戳为 HH:mm:ss */
function formatTime(timestamp: string): string {
  try {
    return new Date(timestamp).toLocaleTimeString("zh-CN", { hour12: false })
  } catch {
    return timestamp
  }
}
