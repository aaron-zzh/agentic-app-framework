/**
 * EntityApproval——实体详情页审批集成（面板+流程图）
 * 当实体配置了 workflow 时，在详情页展示审批操作面板和流程可视化
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { ChevronDown, ChevronUp, GitBranch } from "lucide-react"
import { useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { ApprovalPanel } from "@/features/flow-editor/components/approval-panel"
import { ExecutionPanel } from "@/features/flow-editor/components/execution-panel"
import type { ExecutionState } from "@/features/flow-editor/types"
import type { EntityWorkflowConfig } from "@/lib/types/entity/entity"

const BASE = process.env.NEXT_PUBLIC_API_URL ?? ""

/** 流程状态信息 */
interface WorkflowStatus {
  processInstanceId: string
  status: "running" | "completed" | "rejected" | "suspended" | "none"
  currentTaskId?: string
  currentAssignee?: string
  startTime?: string
}

interface EntityApprovalProps {
  config: EntityWorkflowConfig
  entityId: string
  currentUserId: string
}

/** 查询实体关联的流程实例状态 */
function useEntityWorkflowStatus(entityType: string, entityId: string) {
  return useQuery<WorkflowStatus>({
    queryKey: ["workflow-status", entityType, entityId],
    queryFn: async () => {
      const res = await fetch(
        `${BASE}/api/system/workflow/status?entityType=${entityType}&entityId=${entityId}`
      )
      if (!res.ok) return { processInstanceId: "", status: "none" as const }
      return res.json()
    }
  })
}

/** 实体审批集成组件 */
export function EntityApproval({ config, entityId, currentUserId }: EntityApprovalProps) {
  const { data: workflowStatus, isLoading } = useEntityWorkflowStatus(config.entityType, entityId)
  const [flowChartOpen, setFlowChartOpen] = useState(false)

  if (isLoading) return null
  if (!workflowStatus || workflowStatus.status === "none") {
    return <StartApprovalButton config={config} entityId={entityId} currentUserId={currentUserId} />
  }

  const { processInstanceId, status, currentTaskId, currentAssignee } = workflowStatus
  const isAssignee = currentAssignee === currentUserId
  const isRunning = status === "running"

  /** 状态 Badge */
  const statusMap: Record<
    string,
    { label: string; variant: "default" | "secondary" | "destructive" }
  > = {
    running: { label: "审批中", variant: "default" },
    completed: { label: "已通过", variant: "secondary" },
    rejected: { label: "已驳回", variant: "destructive" },
    suspended: { label: "已暂停", variant: "secondary" }
  }
  const badge = statusMap[status] ?? { label: status, variant: "secondary" as const }

  /** 简化的执行状态（用于流程图高亮） */
  const executionState: ExecutionState = {
    status: isRunning ? "running" : status === "completed" ? "completed" : "failed",
    currentNodeId: currentTaskId ?? undefined,
    completedNodes: [],
    failedNodes: status === "rejected" ? [currentTaskId ?? ""] : []
  }

  return (
    <Card>
      <CardHeader className="pb-3">
        <div className="flex items-center justify-between">
          <CardTitle className="flex items-center gap-2 text-base">
            <GitBranch className="h-4 w-4" />
            审批流程
            <Badge variant={badge.variant}>{badge.label}</Badge>
          </CardTitle>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 审批操作面板（仅运行中展示） */}
        {isRunning && (
          <ApprovalPanel
            processInstanceId={processInstanceId}
            taskId={currentTaskId}
            isAssignee={isAssignee}
            isInitiator={true}
            currentUserId={currentUserId}
          />
        )}

        {/* 流程图可视化（可折叠） */}
        {config.showFlowChart && (
          <Collapsible open={flowChartOpen} onOpenChange={setFlowChartOpen}>
            <CollapsibleTrigger asChild>
              <Button variant="ghost" size="sm" className="w-full justify-between">
                流程图
                {flowChartOpen ? (
                  <ChevronUp className="h-4 w-4" />
                ) : (
                  <ChevronDown className="h-4 w-4" />
                )}
              </Button>
            </CollapsibleTrigger>
            <CollapsibleContent>
              <div className="mt-2 h-[300px] rounded border">
                <ExecutionPanel
                  instanceId={processInstanceId}
                  executionState={executionState}
                  nodeRegistry={{}}
                />
              </div>
            </CollapsibleContent>
          </Collapsible>
        )}
      </CardContent>
    </Card>
  )
}

/** 发起审批按钮（无关联流程时展示） */
function StartApprovalButton({
  config,
  entityId,
  _currentUserId
}: {
  config: EntityWorkflowConfig
  entityId: string
  _currentUserId: string
}) {
  const [loading, setLoading] = useState(false)

  async function handleStart() {
    setLoading(true)
    try {
      await fetch(`${BASE}/api/system/workflow/start`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          entityType: config.entityType,
          entityId: Number(entityId),
          assignee: ""
        })
      })
      // 刷新页面状态
      window.location.reload()
    } finally {
      setLoading(false)
    }
  }

  return (
    <Button variant="outline" size="sm" onClick={handleStart} disabled={loading}>
      <GitBranch className="mr-1.5 h-3.5 w-3.5" />
      发起审批
    </Button>
  )
}
