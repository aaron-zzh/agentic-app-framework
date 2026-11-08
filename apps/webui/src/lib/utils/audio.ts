/**
 * 音频处理工具——浏览器端 Float32 样本与 16bit 小端 PCM 互转（实时 ASR 上行、服务端 TTS 下行）。
 * @author AaronZZH & Kiro
 */

/**
 * Float32 音频样本（[-1, 1]）转 16bit 小端 PCM（Int16Array）。
 *
 * 用于将 Web Audio API 采集的 Float32 数据转为 DashScope 实时 ASR/Omni 所需的 PCM16 格式。
 */
export function float32ToPcm16(float32: Float32Array): Int16Array<ArrayBuffer> {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]))
    int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
  }
  return int16
}

/**
 * 16bit 小端 PCM 字节转 Float32 样本（[-1, 1)）。
 *
 * 字节数必须为偶数；按 DataView 读取，不依赖宿主字节序，也不要求 byteOffset 对齐。
 */
export function pcm16ToFloat32(bytes: Uint8Array): Float32Array<ArrayBuffer> {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const samples = new Float32Array(bytes.byteLength >> 1)
  for (let i = 0; i < samples.length; i++) {
    samples[i] = view.getInt16(i * 2, true) / 0x8000
  }
  return samples
}

/** 流式 PCM16 解码器：处理网络分片在采样中间断开（奇数字节）的情况 */
export interface Pcm16StreamDecoder {
  /** 追加一个网络分片，返回可完整解码的样本；不足一个样本的尾字节留到下一分片 */
  push(chunk: Uint8Array): Float32Array<ArrayBuffer>
}

/**
 * 创建流式 PCM16 解码器。
 *
 * @example
 * const decoder = createPcm16StreamDecoder()
 * const samples = decoder.push(new Uint8Array(chunk))
 */
export function createPcm16StreamDecoder(): Pcm16StreamDecoder {
  let pending: number | null = null
  return {
    push(chunk) {
      if (chunk.byteLength === 0) return new Float32Array(0)
      let bytes: Uint8Array = chunk
      if (pending !== null) {
        bytes = new Uint8Array(chunk.byteLength + 1)
        bytes[0] = pending
        bytes.set(chunk, 1)
        pending = null
      }
      const evenLength = bytes.byteLength & ~1
      if (evenLength < bytes.byteLength) pending = bytes[bytes.byteLength - 1]
      return pcm16ToFloat32(bytes.subarray(0, evenLength))
    }
  }
}
