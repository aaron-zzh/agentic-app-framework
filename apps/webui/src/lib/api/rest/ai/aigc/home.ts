/**
 * Studio 首页生产概况 API。
 *
 * @example
 * const { data } = useAigcStudioHomeSummary()
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { backendApi } from "../../backend-client"

export interface AigcStudioHomeSummary {
  activeProjectCount: number
  activeTaskCount: number
  failedTaskCount: number
  mediaCount: number
  workCount: number
}

export const aigcStudioHomeKeys = {
  all: ["aigc.studio.home"] as const,
  summary: ["aigc.studio.home", "summary"] as const
}

export const aigcStudioHomeApi = {
  summary: () => backendApi.get<AigcStudioHomeSummary>("/aigc/studio/home-summary")
}

/** 查询 Studio 首页生产概况。 */
export function useAigcStudioHomeSummary() {
  return useQuery({
    queryKey: aigcStudioHomeKeys.summary,
    queryFn: aigcStudioHomeApi.summary
  })
}
