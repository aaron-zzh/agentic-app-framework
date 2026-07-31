/**
 * 版本历史 API 客户端
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "../backend-client"

export interface RecordVersion {
  id: string
  version: number
  entityType: string
  entityId: string
  data: Record<string, unknown>
  userId: string
  userName?: string
  createdAt: string
  summary?: string
}

export const versionApi = {
  /** 获取记录版本列表 */
  list: (entitySlug: string, id: string) =>
    backendApi.get<RecordVersion[]>(`/${entitySlug}/${id}/versions`),

  /** 恢复到指定版本 */
  restore: (entitySlug: string, id: string, version: number) =>
    backendApi.post<Record<string, unknown>>(`/${entitySlug}/${id}/versions/${version}/restore`)
}

export function useRecordVersions(entitySlug: string, id: string, enabled = true) {
  return useQuery({
    queryKey: [entitySlug, id, "versions"],
    queryFn: () => versionApi.list(entitySlug, id),
    enabled: enabled && !!entitySlug && !!id
  })
}

export function useRestoreVersion(entitySlug: string, id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (version: number) => versionApi.restore(entitySlug, id, version),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [entitySlug, id] })
    }
  })
}
