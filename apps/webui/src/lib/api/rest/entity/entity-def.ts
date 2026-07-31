/**
 * EntityDef 管理 API 客户端
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "../backend-client"

/** 后端存储的实体定义记录。 */
export interface EntityDefRecord {
  id: number
  /** 服务端按受信任代码资源丰富的运行期 slug。 */
  slug: string
  /** 服务端按受信任代码资源丰富的运行期客户端 API 路径。 */
  apiPath: string
  config: unknown
  builtin: boolean
  enabled: boolean
  createTime: string
  updateTime: string
}

/** 受信任代码资源的最小运行期描述，用于解析没有 EntityDef 的关联目标。 */
export interface EntityResourceDescriptor {
  resource: string
  slug: string
  apiPath: string
  /** 服务端从资源展示 VO 投影的 EntityDef 可引用字段。 */
  fields: string[]
  /** 是否可作为跨实体记录来源。 */
  referenceable: boolean
}

/** 工作区实体元数据 bootstrap 响应。 */
export interface EntityDefBootstrap {
  definitions: EntityDefRecord[]
  resources: EntityResourceDescriptor[]
}

/** 创建请求体。 */
export interface EntityDefInput {
  slug: string
  config: Record<string, unknown>
  enabled?: boolean
}

/** 更新请求体。 */
export interface EntityDefUpdateInput {
  config?: Record<string, unknown>
  enabled?: boolean
}

export const entityDefApi = {
  /** 获取工作区 EntityDef 与受信任资源目录。 */
  bootstrap: () => backendApi.get<EntityDefBootstrap>("/entity-defs/bootstrap"),

  /** 获取已启用的代码实体定义。 */
  list: () => backendApi.get<EntityDefRecord[]>("/entity-defs"),

  /** 获取单个实体定义。 */
  get: (id: number) => backendApi.get<EntityDefRecord>(`/entity-defs/${id}`),

  /** 创建已注册代码资源的元数据定义。 */
  create: (data: EntityDefInput) => backendApi.post<EntityDefRecord>("/entity-defs", data),

  /** 更新非内置实体的元数据定义。 */
  update: (id: number, data: EntityDefUpdateInput) =>
    backendApi.put<EntityDefRecord>(`/entity-defs/${id}`, data),

  /** 删除非内置实体定义。 */
  delete: (id: number) => backendApi.delete<void>(`/entity-defs/${id}`)
}

const ENTITY_DEFS_KEY = ["entity-defs"]
const ENTITY_BOOTSTRAP_KEY = ["entity-def-bootstrap"]

interface UseEntityDefsOptions {
  enabled?: boolean
}

/** 查询工作区视图引擎需要的完整实体元数据。 */
export function useEntityBootstrap({ enabled = true }: UseEntityDefsOptions = {}) {
  return useQuery({
    queryKey: ENTITY_BOOTSTRAP_KEY,
    queryFn: () => entityDefApi.bootstrap(),
    enabled
  })
}

/** 查询实体编辑器需要的 EntityDef 记录。 */
export function useEntityDefs({ enabled = true }: UseEntityDefsOptions = {}) {
  return useQuery({
    queryKey: ENTITY_DEFS_KEY,
    queryFn: () => entityDefApi.list(),
    enabled
  })
}

/** 查询单个实体定义 */
export function useEntityDef(id: number | undefined) {
  return useQuery({
    queryKey: [...ENTITY_DEFS_KEY, id],
    queryFn: () => entityDefApi.get(id as number),
    enabled: id !== undefined
  })
}

/** 创建实体定义 */
export function useCreateEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: EntityDefInput) => entityDefApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
      qc.invalidateQueries({ queryKey: ENTITY_BOOTSTRAP_KEY })
    }
  })
}

/** 更新实体定义 */
export function useUpdateEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: EntityDefUpdateInput }) =>
      entityDefApi.update(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
      qc.invalidateQueries({ queryKey: ENTITY_BOOTSTRAP_KEY })
    }
  })
}

/** 删除实体定义 */
export function useDeleteEntityDef() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => entityDefApi.delete(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ENTITY_DEFS_KEY })
      qc.invalidateQueries({ queryKey: ENTITY_BOOTSTRAP_KEY })
    }
  })
}
