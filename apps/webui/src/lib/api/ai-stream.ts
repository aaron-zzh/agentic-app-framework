/**
 * AI 对话 POST SSE 工具——向后端发起 POST 请求并以流式方式接收 text/event-stream 响应。
 *
 * 使用场景：文案生成、改写、对话流式输出等所有 AI 流式接口。
 * 认证：从 axios 默认头读取 Bearer token，自动附加到请求头。
 */

import axios from "axios"
import { buildApiUrl } from "./config"

export interface AiSseOptions {
  /** 每个 token 回调 */
  onChunk: (text: string) => void
  /** 流结束回调 */
  onDone?: () => void
  /** 错误回调 */
  onError?: (err: Error) => void
  /** AbortSignal，用于取消请求 */
  signal?: AbortSignal
}

/**
 * 向 `path` 发起 POST SSE 请求，逐 token 调用 `onChunk`。
 *
 * @param path  相对路径，如 `/aigc/copywriting/generate`
 * @param body  请求体（JSON 序列化）
 * @param opts  回调选项
 */
export async function postAiStream(path: string, body: unknown, opts: AiSseOptions): Promise<void> {
  const { onChunk, onDone, onError, signal } = opts
  const auth = axios.defaults.headers.common.Authorization as string | undefined

  let res: Response
  try {
    res = await fetch(buildApiUrl(path), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(auth ? { Authorization: auth } : {})
      },
      body: JSON.stringify(body),
      signal
    })
  } catch (e) {
    onError?.(e instanceof Error ? e : new Error(String(e)))
    return
  }

  if (!res.ok || !res.body) {
    onError?.(new Error(`HTTP ${res.status}`))
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const raw = decoder.decode(value, { stream: true })
      for (const line of raw.split("\n")) {
        // SSE 格式：`data: <text>`；纯文本 chunk 直接使用
        const text = line.startsWith("data:") ? line.slice(5).trim() : line.trim()
        if (text && text !== "[DONE]") onChunk(text)
      }
    }
    onDone?.()
  } catch (e) {
    if ((e as Error).name !== "AbortError") {
      onError?.(e instanceof Error ? e : new Error(String(e)))
    }
  }
}
