/**
 * 流程定义 CRUD + 模板 + 部署 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { flowToBpmn } from "../lib/bpmn-converter"
import type { FlowDefinition, FlowTemplate } from "../types"

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new Error(`请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new Error(json.message ?? "未知错误")
  return json.data as T
}

/** 流程定义 VO */
interface FlowDefVO {
  id: string
  name: string
  mode: string
  definition: FlowDefinition
  deploymentId?: string
  createdAt: string
  updatedAt: string
}

// ===== 流程定义 CRUD =====

/** 查询流程定义列表 */
export function useFlowList() {
  return useQuery({
    queryKey: ["flows"],
    queryFn: () => req<FlowDefVO[]>("/flows")
  })
}

/** 查询单个流程定义 */
export function useFlowDetail(id?: string) {
  return useQuery({
    queryKey: ["flows", id],
    queryFn: () => req<FlowDefVO>(`/flows/${id}`),
    enabled: !!id
  })
}

/** 保存流程定义 */
export function useFlowSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { id?: string; name: string; mode: string; definition: FlowDefinition }) =>
      body.id
        ? req<FlowDefVO>(`/flows/${body.id}`, { method: "PUT", body: JSON.stringify(body) })
        : req<FlowDefVO>("/flows", { method: "POST", body: JSON.stringify(body) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["flows"] })
    }
  })
}

/** 删除流程定义 */
export function useFlowDelete() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => req<void>(`/flows/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["flows"] })
    }
  })
}

// ===== 部署 =====

/** 部署流程到 Flowable 引擎 */
export function useFlowDeploy() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (params: { id: string; name: string; definition: FlowDefinition }) => {
      const bpmnXml = flowToBpmn(params.definition, params.id)
      return req<{ deploymentId: string }>(`/flows/${params.id}/deploy`, {
        method: "POST",
        body: JSON.stringify({ name: params.name, bpmnXml })
      })
    },
    onSuccess: (_data, vars) => {
      qc.invalidateQueries({ queryKey: ["flows", vars.id] })
    }
  })
}

// ===== 模板 =====

/** 查询流程模板列表 */
export function useFlowTemplates(mode?: string) {
  return useQuery({
    queryKey: ["flow-templates", mode],
    queryFn: () => req<FlowTemplate[]>(`/flow-templates${mode ? `?mode=${mode}` : ""}`)
  })
}

/** 从模板创建流程 */
export function useCreateFromTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { templateId: string; name: string }) =>
      req<FlowDefVO>("/flows/from-template", { method: "POST", body: JSON.stringify(body) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["flows"] })
    }
  })
}

/** 保存为模板 */
export function useSaveAsTemplate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: {
      name: string
      description: string
      mode: string
      definition: FlowDefinition
    }) => req<FlowTemplate>("/flow-templates", { method: "POST", body: JSON.stringify(body) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["flow-templates"] })
    }
  })
}
