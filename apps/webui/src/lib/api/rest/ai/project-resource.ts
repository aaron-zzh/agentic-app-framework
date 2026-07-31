/**
 * 项目资源关联 API、DTO 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { notify } from "@/lib/notification"

import { backendApi } from "../backend-client"

export interface UserProjectResourceVO {
  id: number
  projectId: number
  resourceType: string
  resourceId: number
  role?: string
  sortOrder: number
  resourceName?: string
  resourceCoverUrl?: string
  createTime: string
}

export interface LinkProjectResourceDTO {
  resourceType: string
  resourceId: number
  role?: string
  sortOrder?: number
}

function projectResourcesKey(projectId: number) {
  return ["project-resources", projectId] as const
}

export const projectResourceApi = {
  list: (projectId: number): Promise<UserProjectResourceVO[]> =>
    backendApi.get(`/aigc/projects/${projectId}/resources`),
  link: (projectId: number, dto: LinkProjectResourceDTO): Promise<UserProjectResourceVO> =>
    backendApi.post(`/aigc/projects/${projectId}/resources`, dto),
  unlink: (projectId: number, resourceLinkId: number): Promise<void> =>
    backendApi.delete(`/aigc/projects/${projectId}/resources/${resourceLinkId}`)
}

export function useProjectResources(projectId: number | null) {
  return useQuery({
    queryKey: projectResourcesKey(projectId as number),
    queryFn: () => projectResourceApi.list(projectId as number),
    enabled: projectId !== null
  })
}

export function useLinkProjectResource(projectId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: LinkProjectResourceDTO) => projectResourceApi.link(projectId, dto),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: projectResourcesKey(projectId) }),
    onError: () => notify.error("关联资源失败")
  })
}

export function useUnlinkProjectResource(projectId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (resourceLinkId: number) => projectResourceApi.unlink(projectId, resourceLinkId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: projectResourcesKey(projectId) }),
    onError: () => notify.error("解除关联失败")
  })
}
