/**
 * useEntityDetail——获取单条实体记录
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import type { EntityDef } from "@/lib/types/entity"
import { _mockEntityData } from "@/lib/_mock/entities"

export function useEntityDetail(entity: EntityDef, id: string | undefined) {
  return useQuery<Record<string, unknown> | null>({
    queryKey: [entity.slug, "detail", id],
    queryFn: async () => {
      if (!id) return null
      // TODO: 后端就绪后用 fetchDetail(entity.apiPath, id)
      const mock = _mockEntityData[entity.slug]
      if (mock) {
        return mock.find((r) => r.id === id) ?? null
      }
      const res = await fetch(`/api/${entity.apiPath}/${id}`)
      if (!res.ok) throw new Error("获取记录失败")
      const json = await res.json()
      return json.data ?? json
    },
    enabled: !!id
  })
}
