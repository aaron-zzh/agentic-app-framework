/**
 * 微信小程序 SSE 流式通信
 * 基于 wx.request + enableChunked: true
 */

/** SSE 回调选项 */
export interface StreamOptions {
  onData: (chunk: string) => void
  onError?: (err: unknown) => void
  onComplete?: () => void
}

/** 解码 ArrayBuffer 为 UTF-8 字符串（小程序不支持 TextDecoder） */
function decodeUTF8(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let str = ''
  let i = 0
  while (i < bytes.length) {
    const b = bytes[i]
    if (b < 0x80) {
      str += String.fromCharCode(b)
      i++
    }
    else if (b >= 0xC0 && b < 0xE0) {
      str += String.fromCharCode(((b & 0x1F) << 6) | (bytes[i + 1] & 0x3F))
      i += 2
    }
    else if (b >= 0xE0 && b < 0xF0) {
      str += String.fromCharCode(((b & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F))
      i += 3
    }
    else {
      const cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3F) << 12) | ((bytes[i + 2] & 0x3F) << 6) | (bytes[i + 3] & 0x3F)
      str += String.fromCodePoint(cp)
      i += 4
    }
  }
  return str
}

/** 解析 SSE 数据块，返回 data 字段拼接结果 */
let _buffer = ''
function parseSSE(raw: string): string {
  _buffer += raw
  const chunks = _buffer.split('\n\n').filter(s => s.trim())

  if (!_buffer.endsWith('\n\n') && chunks.length > 0) {
    _buffer = chunks.pop() ?? ''
  }
  else {
    _buffer = ''
  }

  return chunks
    .filter(c => c.includes('data:'))
    .map((c) => {
      try {
        const json = JSON.parse(c.replace(/^data:\s*/, ''))
        return json.data ?? ''
      }
      catch {
        return ''
      }
    })
    .join('')
}

/** 重置 buffer（每次新请求前调用） */
export function resetBuffer(): void {
  _buffer = ''
}

/**
 * 发起 SSE POST 请求（微信小程序）
 * @returns requestTask，可调用 .abort() 中断
 */
export function streamPost(
  url: string,
  data: Record<string, unknown>,
  headers: Record<string, string>,
  options: StreamOptions,
): WechatMiniprogram.RequestTask {
  resetBuffer()

  const onChunk = (res: { data: ArrayBuffer }) => {
    const text = decodeUTF8(res.data)
    const parsed = parseSSE(text)
    if (parsed)
      options.onData(parsed)
  }

  const task = wx.request({
    url,
    method: 'POST',
    header: { 'Accept': 'text/event-stream', 'Content-Type': 'application/json', ...headers },
    data,
    enableChunked: true,
    responseType: 'text',
    timeout: 60000,
    fail: err => options.onError?.(err),
    complete: () => {
      // @ts-expect-error uni-app types missing offChunkReceived
      task?.offChunkReceived(onChunk)
      options.onComplete?.()
    },
  })

  // @ts-expect-error uni-app types missing onChunkReceived
  task.onChunkReceived(onChunk)
  return task
}
