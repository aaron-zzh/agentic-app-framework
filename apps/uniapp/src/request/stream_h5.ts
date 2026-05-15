import type { StreamOptions } from './stream'
// #ifdef H5
/**
 * H5 SSE 流式通信
 * 基于 @microsoft/fetch-event-source，支持 POST + 自定义 headers
 */
import { fetchEventSource } from '@microsoft/fetch-event-source'

/**
 * 发起 SSE POST 请求（H5）
 * @param url - 请求地址
 * @param data - 请求体
 * @param headers - 请求头
 * @param options - 回调选项
 * @param ctrl - AbortController，调用 ctrl.abort() 中断
 */
export async function streamPostH5(
  url: string,
  data: Record<string, unknown>,
  headers: Record<string, string>,
  options: StreamOptions,
  ctrl: AbortController,
): Promise<void> {
  await fetchEventSource(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(data),
    openWhenHidden: true,
    signal: ctrl.signal,
    onmessage: (ev) => {
      try {
        const json = JSON.parse(ev.data)
        if (json.data)
          options.onData(json.data)
      }
      catch {
        // 忽略非 JSON 数据行（如 [DONE]）
      }
    },
    onerror: (err) => {
      options.onError?.(err)
      throw err // 阻止 fetchEventSource 自动重试
    },
    onclose: () => options.onComplete?.(),
  })
}
// #endif
