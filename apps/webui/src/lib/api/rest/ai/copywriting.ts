/**
 * 文案生成与资产 API。
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { backendApi } from "../backend-client"

export type CopywritingAssetLinkStatus = "ALL" | "LINKED" | "UNLINKED"

export interface CopywritingAssetProject {
  projectId: number
  projectName: string
}

export interface CopywritingAsset {
  documentId: number
  title: string
  summary: string
  updateTime: string
  projects: CopywritingAssetProject[]
}

export interface CopywritingAssetPage {
  list: CopywritingAsset[]
  total: number
  pageNo: number
  pageSize: number
  hasMore: boolean
}

export interface CopywritingAssetParams {
  linkStatus: CopywritingAssetLinkStatus
  projectId?: number
  keyword?: string
  pageNo: number
  pageSize: number
}

export const copywritingApi = {
  assets: (params: CopywritingAssetParams) =>
    backendApi.get<CopywritingAssetPage>("/aigc/copywriting/assets", { params })
}

export const copywritingKeys = {
  all: ["aigc.copywriting.assets"] as const,
  assets: (params: CopywritingAssetParams) => ["aigc.copywriting.assets", "list", params] as const
}

/** 查询当前工作区的文案资产分页。 */
export function useCopywritingAssets(params: CopywritingAssetParams) {
  return useQuery({
    queryKey: copywritingKeys.assets(params),
    queryFn: () => copywritingApi.assets(params)
  })
}
