/**
 * AI 角色（Role）API、类型与 TanStack Query Hook：角色库、助理角色绑定、我的私有角色、管理端发布。
 *
 * @example
 * const { data } = useAiRoleLibrary()
 * const bind = useBindAssistantRole()
 * bind.mutate({ assistantId: data.assistantId, roleId: 12 })
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { notify } from "@/lib/notification"

import { backendApi } from "../backend-client"

const ROLE_QUERY_KEY = ["ai-roles"] as const
/** 助理可用性查询（聊天框角色选择器使用），绑定变化后需要同步失效。 */
const ASSISTANT_AVAILABLE_KEY = ["ai", "assistants", "available"] as const

export type AiRoleVisibility = "SYSTEM" | "PUBLIC" | "PRIVATE"
export type AiRoleStatus = "active" | "disabled"
export type AiRoleSkillActivationMode = "ALWAYS" | "ON_DEMAND"

export interface AiRoleSkillBinding {
  skillKey: string
  activationMode: AiRoleSkillActivationMode
}

export interface AiRoleVO {
  id: number
  code: string
  name: string
  description: string | null
  skillBindings: AiRoleSkillBinding[]
  /** 工具授权池，JSON 字符串数组 */
  toolWhitelist: string | null
  status: AiRoleStatus
  visibility: AiRoleVisibility
  /** 私有角色的所有者；SYSTEM / PUBLIC 为空 */
  ownerId: number | null
  createTime: string
  updateTime: string
}

export interface AiRoleLibraryItem {
  role: AiRoleVO
  bound: boolean
  enabled: boolean
  defaultRole: boolean
}

export interface AiRoleLibraryVO {
  /** 绑定关系所对应的助理；用户没有默认助理时为空 */
  assistantId: number | null
  boundCount: number
  maxRoles: number
  items: AiRoleLibraryItem[]
}

export interface AssistantRoleBindingVO {
  roleId: number
  /** 角色已下架或不可见时为空 */
  role: AiRoleVO | null
  enabled: boolean
  defaultRole: boolean
  sortOrder: number
  usable: boolean
}

export interface UpdateAssistantRoleBindingInput {
  sortOrder?: number
  enabled?: boolean
  /** 只能设为 true，默认角色由切换产生 */
  defaultRole?: boolean
}

export interface MyAiRoleInput {
  name: string
  description?: string
  skillBindings: AiRoleSkillBinding[]
  /** JSON 字符串数组，如 ["context.load"] */
  toolWhitelist?: string
}

export interface AiRoleToolOption {
  name: string
  category: string | null
  riskLevel: string | null
  requireConfirm: boolean
  readOnly: boolean
}

export interface PublishAiRoleInput {
  /** 必须以 public.role. 开头；缺省由服务端生成 */
  code?: string
  name?: string
}

export const roleApi = {
  library: (assistantId?: number): Promise<AiRoleLibraryVO> =>
    backendApi.get(`/ai/roles/library${assistantId ? `?assistantId=${assistantId}` : ""}`),
  bindings: (assistantId: number): Promise<AssistantRoleBindingVO[]> =>
    backendApi.get(`/ai/my/assistants/${assistantId}/roles`),
  bind: (assistantId: number, roleId: number): Promise<AssistantRoleBindingVO> =>
    backendApi.post(`/ai/my/assistants/${assistantId}/roles`, { roleId }),
  updateBinding: (
    assistantId: number,
    roleId: number,
    input: UpdateAssistantRoleBindingInput
  ): Promise<AssistantRoleBindingVO> =>
    backendApi.put(`/ai/my/assistants/${assistantId}/roles/${roleId}`, input),
  unbind: (assistantId: number, roleId: number): Promise<void> =>
    backendApi.delete(`/ai/my/assistants/${assistantId}/roles/${roleId}`),
  listMine: (): Promise<AiRoleVO[]> => backendApi.get("/ai/my/roles"),
  toolOptions: (): Promise<AiRoleToolOption[]> => backendApi.get("/ai/my/roles/tool-options"),
  createMine: (input: MyAiRoleInput): Promise<AiRoleVO> => backendApi.post("/ai/my/roles", input),
  updateMine: (id: number, input: MyAiRoleInput): Promise<AiRoleVO> =>
    backendApi.put(`/ai/my/roles/${id}`, input),
  deleteMine: (id: number): Promise<void> => backendApi.delete(`/ai/my/roles/${id}`),
  publish: (id: number, input: PublishAiRoleInput): Promise<AiRoleVO> =>
    backendApi.post("/ai/roles/actions/publish", { id, ...input })
}

/** 角色库：当前用户可绑定的内置、公开与自己的私有角色，标注是否已绑定。 */
export function useAiRoleLibrary(assistantId?: number) {
  return useQuery({
    queryKey: [...ROLE_QUERY_KEY, "library", assistantId ?? "default"] as const,
    queryFn: () => roleApi.library(assistantId)
  })
}

/** 助理已绑定的角色，含已下架的角色。 */
export function useAssistantRoleBindings(assistantId: number | null | undefined) {
  return useQuery({
    queryKey: [...ROLE_QUERY_KEY, "bindings", assistantId ?? 0] as const,
    queryFn: () => roleApi.bindings(assistantId ?? 0),
    enabled: typeof assistantId === "number"
  })
}

/** 当前用户的私有角色。 */
export function useMyAiRoles() {
  return useQuery({
    queryKey: [...ROLE_QUERY_KEY, "mine"] as const,
    queryFn: roleApi.listMine
  })
}

/** 创建私有角色时可选的工具。 */
export function useAiRoleToolOptions(enabled = true) {
  return useQuery({
    queryKey: [...ROLE_QUERY_KEY, "tool-options"] as const,
    queryFn: roleApi.toolOptions,
    enabled,
    staleTime: 5 * 60 * 1000
  })
}

function useInvalidateRoles() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ROLE_QUERY_KEY })
    queryClient.invalidateQueries({ queryKey: ASSISTANT_AVAILABLE_KEY })
  }
}

/** 绑定角色。 */
export function useBindAssistantRole() {
  const invalidate = useInvalidateRoles()
  return useMutation({
    mutationFn: ({ assistantId, roleId }: { assistantId: number; roleId: number }) =>
      roleApi.bind(assistantId, roleId),
    onSuccess: () => {
      invalidate()
      notify.success("角色已绑定")
    }
  })
}

/** 调整绑定：排序、启停、设为默认。 */
export function useUpdateAssistantRoleBinding() {
  const invalidate = useInvalidateRoles()
  return useMutation({
    mutationFn: ({
      assistantId,
      roleId,
      ...input
    }: { assistantId: number; roleId: number } & UpdateAssistantRoleBindingInput) =>
      roleApi.updateBinding(assistantId, roleId, input),
    onSuccess: () => {
      invalidate()
      notify.success("角色绑定已更新")
    }
  })
}

/** 解绑角色。 */
export function useUnbindAssistantRole() {
  const invalidate = useInvalidateRoles()
  return useMutation({
    mutationFn: ({ assistantId, roleId }: { assistantId: number; roleId: number }) =>
      roleApi.unbind(assistantId, roleId),
    onSuccess: () => {
      invalidate()
      notify.success("角色已解绑")
    }
  })
}

/** 创建私有角色。 */
export function useCreateMyAiRole() {
  const invalidate = useInvalidateRoles()
  return useMutation({
    mutationFn: roleApi.createMine,
    onSuccess: () => {
      invalidate()
      notify.success("私有角色创建成功")
    }
  })
}

/** 更新私有角色。 */
export function useUpdateMyAiRole() {
  const invalidate = useInvalidateRoles()
  return useMutation({
    mutationFn: ({ id, ...input }: { id: number } & MyAiRoleInput) => roleApi.updateMine(id, input),
    onSuccess: () => {
      invalidate()
      notify.success("私有角色更新成功")
    }
  })
}

/** 删除私有角色。 */
export function useDeleteMyAiRole() {
  const invalidate = useInvalidateRoles()
  return useMutation({
    mutationFn: roleApi.deleteMine,
    onSuccess: () => {
      invalidate()
      notify.success("私有角色已删除")
    }
  })
}

/** 管理员把用户的私有角色发布为公开角色。 */
export function usePublishAiRole() {
  const invalidate = useInvalidateRoles()
  return useMutation({
    mutationFn: ({ id, ...input }: { id: number } & PublishAiRoleInput) =>
      roleApi.publish(id, input),
    onSuccess: () => {
      invalidate()
      notify.success("角色已发布为公开角色")
    }
  })
}
