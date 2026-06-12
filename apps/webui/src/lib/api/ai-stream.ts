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
    try {
      const json = await res.json()
      onError?.(new Error(json?.message ?? `HTTP ${res.status}`))
    } catch {
      onError?.(new Error(res.status === 401 ? "登录已过期，请刷新页面重试" : `HTTP ${res.status}`))
    }
    return
  }

  // 检查是否是业务错误（Content-Type 为 JSON 而非 event-stream）
  const contentType = res.headers.get("content-type") ?? ""
  if (!contentType.includes("text/event-stream")) {
    try {
      const text = await res.text()
      try {
        const json = JSON.parse(text)
        if (json?.code && json.code !== 0) {
          onError?.(new Error(json.message ?? "请求失败"))
          return
        }
      } catch {
        // 非 JSON，直接用文本
      }
      onError?.(new Error(text || `HTTP ${res.status}`))
    } catch {
      onError?.(new Error("响应解析失败"))
    }
    return
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buf = ""
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })

      // SSE 标准：事件以空行（\n\n）分隔
      const events = buf.split("\n\n")
      buf = events.pop() ?? ""

      for (const event of events) {
        // 一个事件可能有多个 data: 行，按协议用 \n 拼接
        const dataLines = event
          .split("\n")
          .filter((l) => l.startsWith("data:"))
          .map((l) => {
            const val = l.slice(5)
            // SSE 规范：data: value 中第一个空格是可选分隔符，去掉它
            // 但若整行就是 "data: "（value 为单个空格），保留该空格
            return val.startsWith(" ") && val.length > 1 ? val.slice(1) : val
          })
        if (dataLines.length === 0) continue
        const text = dataLines.join("\n")
        if (!text || text === "[DONE]") continue
        if (text.startsWith("[ERROR]")) {
          onError?.(new Error(text.slice(7)))
          return
        }
        try {
          const evt = JSON.parse(text)
          if (evt !== null && typeof evt === "object") {
            if (evt.type === "TEXT_MESSAGE_CONTENT" && typeof evt.delta === "string") {
              onChunk(evt.delta)
            } else if (evt.type === "RUN_ERROR") {
              onError?.(new Error(evt.error ?? "运行失败"))
              return
            }
          } else {
            onChunk(String(evt))
          }
        } catch {
          onChunk(text)
        }
      }
    }
    onDone?.()
  } catch (e) {
    if ((e as Error).name !== "AbortError") {
      onError?.(e instanceof Error ? e : new Error(String(e)))
    }
  }
}
