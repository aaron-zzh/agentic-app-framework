/**
 * 音频处理工具——浏览器端采集的 Float32 PCM 转 DashScope 流式 ASR 所需的 16bit PCM。
 * @author AaronZZH & Kiro
 */

/**
 * Float32 音频样本（[-1, 1]）转 16bit 小端 PCM（Int16Array）。
 *
 * 用于将 Web Audio API 采集的 Float32 数据转为 DashScope 实时 ASR/Omni 所需的 PCM16 格式。
 */
export function float32ToPcm16(float32: Float32Array): Int16Array {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    const s = Math.max(-1, Math.min(1, float32[i]))
    int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
  }
  return int16
}
