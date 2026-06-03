/**
 * SSE 流式解析共享工具——从 ReadableStream 中逐行解析 SSE data: 事件
 * @author Kiro
 *
 * @example
 * const res = await fetch(url, { method: "POST", body })
 * await streamSSE(res.body!, (data) => {
 *   const parsed = JSON.parse(data)
 *   // 处理每条 SSE 数据
 * })
 */

export interface StreamSSEOptions {
  /** 收到 data: 行时的回调（已去除 "data: " 前缀） */
  onData: (data: string) => void
  /** 收到 [DONE] 标记时的回调 */
  onDone?: () => void
  /** 解析单行数据失败时的回调（默认静默忽略） */
  onError?: (error: unknown, rawLine: string) => void
}

/**
 * 从 ReadableStream 中流式读取 SSE 格式数据
 * 处理 buffer 拼接、行分割、data: 前缀解析
 */
export async function streamSSE(
  body: ReadableStream<Uint8Array>,
  options: StreamSSEOptions | ((data: string) => void)
): Promise<void> {
  const opts: StreamSSEOptions = typeof options === "function" ? { onData: options } : options

  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split("\n")
    // 保留最后一行（可能不完整）
    buffer = lines.pop() ?? ""

    for (const line of lines) {
      if (!line.startsWith("data:")) continue
      const data = line.slice(5).trim()
      if (!data) continue
      if (data === "[DONE]") {
        opts.onDone?.()
        continue
      }
      try {
        opts.onData(data)
      } catch (err) {
        opts.onError?.(err, line)
      }
    }
  }
}
