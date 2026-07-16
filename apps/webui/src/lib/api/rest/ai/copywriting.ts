import { type AiSseOptions, postAiStream } from "../../ai-stream"

export interface CopywritingGenerateRequest {
  /** 创作提示词（主题词或完整写作指令） */
  prompt: string
  modelId?: string
  type?: string
  template?: string
  length?: string
  translateTo?: string
  /** 参考图片 fileKey 列表（OSS 内部 key，由后端解析为签名 URL 传给视觉模型） */
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
  generate: (req: CopywritingGenerateRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/generate", req, opts),

  generateFromAnalysis: (req: CopywritingGenerateFromAnalysisRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/generate-from-analysis", req, opts),

  rewrite: (req: CopywritingRewriteRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/rewrite", req, opts),

  analyze: (req: CopywritingAnalyzeRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/analyze", req, opts)
}
