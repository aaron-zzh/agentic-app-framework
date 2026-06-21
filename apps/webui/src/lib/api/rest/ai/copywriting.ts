import { type AiSseOptions, postAiStream } from "../../ai-stream"

export interface CopywritingGenerateRequest {
  topic: string
  modelId?: string
  type?: string
  template?: string
  length?: string
  translateTo?: string
  referenceAnalysis?: string
  userNotes?: string
  /** 参考图片 fileKey 列表（OSS 内部 key，由后端解析为签名 URL 传给视觉模型） */
  referenceImageKeys?: string[]
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

  rewrite: (req: CopywritingRewriteRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/rewrite", req, opts),

  analyze: (req: CopywritingAnalyzeRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/analyze", req, opts)
}
