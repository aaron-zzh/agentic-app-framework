/**
 * 流程定义 CRUD + 模板 + 部署 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import type { PageResult } from "@/lib/api/types"
import type { FlowDefinition } from "../types"

/** 后端流程定义传输结构。 */
interface FlowDefWireVO {
  id: number
  name: string
  description?: string
  mode: string
  definition: string
  status: string
  deploymentId?: string
  publishedAt?: string
  agentCallable: boolean
  requireConfirm: boolean
  createTime: string
  updateTime: string
}

/** 编辑器使用的流程定义。 */
export interface FlowDefVO {
  id: string
  name: string
  description?: string
  mode: string
  definition: FlowDefinition
  status: string
  deploymentId?: string
  publishedAt?: string
  agentCallable: boolean
  requireConfirm: boolean
  createTime: string
  updateTime: string
}

function parseDefinition(definition: string): FlowDefinition {
  const parsed: unknown = JSON.parse(definition)
  if (!parsed || typeof parsed !== "object") throw new Error("工作流定义格式错误")
  const candidate = parsed as Partial<FlowDefinition>
  if (!Array.isArray(candidate.nodes) || !Array.isArray(candidate.edges)) {
    throw new Error("工作流定义缺少节点或连线")
  }
  return candidate as FlowDefinition
}

function mapFlow(vo: FlowDefWireVO): FlowDefVO {
  return {
    ...vo,
    id: String(vo.id),
    definition: parseDefinition(vo.definition)
  }
}

// ===== 流程定义 CRUD =====

/** 查询流程定义列表 */
export function useFlowList() {
  return useQuery({
    queryKey: ["flows"],
    queryFn: async () => {
      const page = await backendApi.get<PageResult<FlowDefWireVO>>(
        "/ai/workflows?pageNo=1&pageSize=100"
      )
      return page.list.map(mapFlow)
    }
  })
}

/** 查询单个流程定义 */
export function useFlowDetail(id?: string) {
  return useQuery({
    queryKey: ["flows", id],
    queryFn: async () => mapFlow(await backendApi.get<FlowDefWireVO>(`/ai/workflows/${id}`)),
    enabled: !!id
  })
}

/** 保存流程定义 */
export function useFlowSave() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (body: {
      id?: string
      name: string
      description?: string
      mode: string
      definition: FlowDefinition
      agentCallable?: boolean
      requireConfirm?: boolean
    }) => {
      const payload = { ...body, id: undefined, definition: JSON.stringify(body.definition) }
      const saved = body.id
        ? await backendApi.put<FlowDefWireVO>(`/ai/workflows/${body.id}`, payload)
        : await backendApi.post<FlowDefWireVO>("/ai/workflows", payload)
      return mapFlow(saved)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["flows"] })
    }
  })
}

/** 删除流程定义 */
export function useFlowDelete() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => backendApi.delete<void>(`/ai/workflows/${id}`),
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
    mutationFn: async (id: string) =>
      mapFlow(await backendApi.post<FlowDefWireVO>(`/ai/workflows/${id}/deploy`)),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({ queryKey: ["flows"] })
      qc.invalidateQueries({ queryKey: ["flows", id] })
    }
  })
}
