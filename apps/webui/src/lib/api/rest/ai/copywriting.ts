/**
 * 文案生成与资产 API。
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { type AiSseOptions, postAiStream } from "../../ai-stream"
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

export interface CopywritingGenerateRequest {
  /** 创作提示词（主题词或完整写作指令） */
  prompt: string
  modelId?: string
  type?: string
  template?: string
  length?: string
  translateTo?: string
  /** 参考图片内部资源 key 列表；后端据此映射 AgentScope ImageBlock，不接收前端 URL。 */
  referenceImageKeys?: string[]
}

export interface CopywritingGenerateFromAnalysisRequest {
  /** 爆款结构分析结果（analyze 的输出） */
  analysis: string
  modelId?: string
  /** 本次创作的主题/调整说明 */
  userNotes?: string
}

export interface CopywritingRewriteRequest {
  content: string
  modelId?: string
}

export interface CopywritingAnalyzeRequest {
  content: string
  modelId?: string
}

export const copywritingApi = {
  assets: (params: CopywritingAssetParams) =>
    backendApi.get<CopywritingAssetPage>("/aigc/copywriting/assets", { params }),

  generate: (req: CopywritingGenerateRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/generate", req, opts),

  generateFromAnalysis: (req: CopywritingGenerateFromAnalysisRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/generate-from-analysis", req, opts),

  rewrite: (req: CopywritingRewriteRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/rewrite", req, opts),

  analyze: (req: CopywritingAnalyzeRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/analyze", req, opts)
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
