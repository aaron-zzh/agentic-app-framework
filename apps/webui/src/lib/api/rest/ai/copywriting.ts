import { type AiSseOptions, postAiStream } from "../../ai-stream"

export interface CopywritingGenerateRequest {
  topic: string
  modelId?: string
  type?: string
  template?: string
  length?: string
}

export interface CopywritingRewriteRequest {
  content: string
  modelId?: string
}

export const copywritingApi = {
  generate: (req: CopywritingGenerateRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/generate", req, opts),

  rewrite: (req: CopywritingRewriteRequest, opts: AiSseOptions) =>
    postAiStream("/aigc/copywriting/rewrite", req, opts)
}
