/**
 * useEntityDetail——获取单条实体记录
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { _mockEntityData } from "@/lib/_mock/entities"
import { request } from "@/lib/api/client"
import type { EntityDef } from "@/lib/types/entity"

export function useEntityDetail(entity: EntityDef, id: string | undefined) {
  return useQuery<Record<string, unknown> | null>({
    queryKey: [entity.slug, "detail", id],
    queryFn: async () => {
      if (!id) return null
      // mock 仅在开发环境且后端未就绪时使用
      if (process.env.NODE_ENV === "development") {
        const mock = _mockEntityData[entity.slug]
        if (mock) {
          return mock.find((r) => r.id === id) ?? null
        }
      }
      const json = await request<{ data?: Record<string, unknown> }>(`${entity.apiPath}/${id}`)
      return (json as Record<string, unknown>).data ?? json
    },
    enabled: !!id
  })
}
