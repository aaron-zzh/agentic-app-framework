import { useQuery } from "@tanstack/react-query"
import {
  type AiModelVO,
  listImageModels,
  listTextModels,
  listVideoModels
} from "@/lib/api/rest/ai/ai-model"
import { request } from "@/lib/api/rest/entity/crud"

/** 按 capability 拉对应模型列表 */
async function fetchModelsByCapability(capability: string): Promise<AiModelVO[]> {
  switch (capability) {
    case "CHAT":
      return listTextModels()
    case "IMAGE_GEN":
      return listImageModels()
    case "VIDEO_GEN":
      return listVideoModels()
    default:
      return request<AiModelVO[]>(`/ai/models/enabled?capability=${capability}`)
  }
}

export function useAiModels(capability: string) {
  return useQuery({
    queryKey: ["ai", "models", capability],
    queryFn: () => fetchModelsByCapability(capability),
    enabled: !!capability,
    staleTime: 5 * 60 * 1000
  })
}
