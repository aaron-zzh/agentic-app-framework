/**
 * 版本历史 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { versionApi } from "@/lib/api/version"

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
