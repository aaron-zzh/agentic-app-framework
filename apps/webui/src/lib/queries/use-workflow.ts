/**
 * 审批工作流 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { workflowApi } from "@/lib/api/workflow"

/** 查询流程状态 */
export function useWorkflowStatus(processInstanceId?: string) {
  return useQuery({
    queryKey: ["workflow", processInstanceId, "status"],
    queryFn: () => workflowApi.getStatus(processInstanceId!),
    enabled: !!processInstanceId
  })
}

/** 查询审批历史 */
export function useWorkflowHistory(processInstanceId?: string) {
  return useQuery({
    queryKey: ["workflow", processInstanceId, "history"],
    queryFn: () => workflowApi.getHistory(processInstanceId!),
    enabled: !!processInstanceId
  })
}

/** 发起审批 */
export function useWorkflowStart() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workflowApi.start,
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["workflow", data.processInstanceId] })
    }
  })
}

/** 审批通过 */
export function useWorkflowComplete(processInstanceId?: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workflowApi.complete,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["workflow", processInstanceId] })
    }
  })
}

/** 驳回 */
export function useWorkflowReject(processInstanceId?: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: workflowApi.reject,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["workflow", processInstanceId] })
    }
  })
}
