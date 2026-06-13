/**
 * AI 对话 POST SSE 工具——向后端发起 POST 请求并以流式方式接收 text/event-stream 响应。
 *
 * 解析遵循 WHATWG SSE 规范：逐行处理，支持 \r\n / \r / \n 三种换行，
 * 按 `字段:值` 解析（去掉一个可选前导空格），空行分发事件，多行 data 以 \n 拼接。
 */

import axios from "axios"
import { buildApiUrl } from "./config"

export interface AiSseOptions {
  onChunk: (text: string) => void
  onDone?: () => void
  onError?: (err: Error) => void
  signal?: AbortSignal
}

/**
 * 向 `path` 发起 POST SSE 请求，逐 token 调用 `onChunk`。
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

  const contentType = res.headers.get("content-type") ?? ""
  if (!contentType.includes("text/event-stream")) {
    // 后端返回了非 SSE 响应（业务错误等）
    try {
      const json = await res.json()
      onError?.(new Error(json?.message ?? "请求失败"))
    } catch {
      onError?.(new Error("响应格式错误"))
    }
    return
  }

  /**
   * 处理一个完整事件的 data 内容。
   * @returns true 表示出现终止性事件（错误），调用方应停止读取。
   */
  const dispatch = (data: string): boolean => {
    if (!data || data === "[DONE]") return false
    if (data.startsWith("[ERROR]")) {
      onError?.(new Error(data.slice(7)))
      return true
    }
    let parsed: unknown
    try {
      parsed = JSON.parse(data)
    } catch {
      // 非 JSON，视为纯文本 token（兜底）
      onChunk(data)
      return false
    }
    // copywriting 链路：JSON 编码的纯文本 token（保留前导空格等）
    if (typeof parsed === "string") {
      if (parsed.startsWith("[ERROR]")) {
        onError?.(new Error(parsed.slice(7)))
        return true
      }
      onChunk(parsed)
      return false
    }
    // AG-UI 链路：结构化事件对象
    if (parsed !== null && typeof parsed === "object") {
      const evt = parsed as Record<string, unknown>
      if (evt.type === "TEXT_MESSAGE_CONTENT" && typeof evt.delta === "string") {
        onChunk(evt.delta)
      } else if (evt.type === "RUN_ERROR") {
        onError?.(new Error(typeof evt.error === "string" ? evt.error : "运行失败"))
        return true
      }
      // 其他事件类型（RUN_STARTED / TEXT_MESSAGE_END / TOOL_CALL_* 等）忽略
    }
    return false
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buf = "" // 尚未处理的不完整行
  let dataBuffer = "" // 当前事件累积的 data 值（每行末尾带 \n）

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buf += decoder.decode(value, { stream: true })

      // 统一换行为 \n（SSE 规范支持 \r\n / \r / \n）。
      // 结尾孤立的 \r 可能是跨 chunk 的 \r\n 前半，先挂起到下次拼接，避免误判行边界。
      let pending = ""
      if (buf.endsWith("\r")) {
        pending = "\r"
        buf = buf.slice(0, -1)
      }
      const lines = buf.replace(/\r\n?/g, "\n").split("\n")
      buf = (lines.pop() ?? "") + pending // 最后一段是不完整行，留到下次

      for (const line of lines) {
        if (line === "") {
          // 空行 → 分发当前事件（规范：去掉 data 末尾追加的换行）
          if (dataBuffer !== "") {
            const stop = dispatch(dataBuffer.slice(0, -1))
            dataBuffer = ""
            if (stop) return
          }
          continue
        }
        if (line.startsWith(":")) continue // 注释行（心跳）忽略

        // 字段名取第一个冒号之前，值取之后并去掉一个可选前导空格
        const colon = line.indexOf(":")
        const field = colon === -1 ? line : line.slice(0, colon)
        let val = colon === -1 ? "" : line.slice(colon + 1)
        if (val.startsWith(" ")) val = val.slice(1)

        if (field === "data") dataBuffer += `${val}\n`
        // event / id / retry 字段此链路用不到，忽略
      }
    }
    onDone?.()
  } catch (e) {
    if ((e as Error).name !== "AbortError") {
      onError?.(e instanceof Error ? e : new Error(String(e)))
    }
  }
}
