/**
 * 项目-资源关联 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"
import { notify } from "@/lib/notification"

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

interface LinkResourceDTO {
  resourceType: string
  resourceId: number
  role?: string
  sortOrder?: number
}

function projectResourcesKey(projectId: number) {
  return ["project-resources", projectId] as const
}

/** 查询项目所有关联资源 */
export function useProjectResources(projectId: number | null) {
  return useQuery({
    queryKey: projectResourcesKey(projectId as number),
    queryFn: () => backendApi.get<UserProjectResourceVO[]>(`/aigc/projects/${projectId}/resources`),
    enabled: projectId !== null
  })
}

/** 关联资源到项目 */
export function useLinkProjectResource(projectId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: LinkResourceDTO) =>
      backendApi.post<UserProjectResourceVO>(`/aigc/projects/${projectId}/resources`, dto),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: projectResourcesKey(projectId) })
    },
    onError: () => {
      notify.error("关联资源失败")
    }
  })
}

/** 解除资源关联 */
export function useUnlinkProjectResource(projectId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (resourceLinkId: number) =>
      backendApi.delete<void>(`/aigc/projects/${projectId}/resources/${resourceLinkId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: projectResourcesKey(projectId) })
    },
    onError: () => {
      notify.error("解除关联失败")
    }
  })
}
