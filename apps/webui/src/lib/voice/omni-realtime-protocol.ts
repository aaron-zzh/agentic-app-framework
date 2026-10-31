/**
 * Omni Realtime v1 就绪事件解析，阻止格式不匹配的会话开始采集。
 *
 * @example
 * const ready = parseOmniRealtimeReadyEvent(event.data)
 * if (ready === null) websocket.close()
 * @author AaronZZH & Kiro
 */

const OMNI_REALTIME_PROTOCOL = "omni-realtime.v1"
const SERVER_ENDPOINTING = "server"
const PCM_S16LE = "pcm_s16le"
const INPUT_SAMPLE_RATE_HZ = 16000
const OUTPUT_SAMPLE_RATE_HZ = 24000
const MONO_CHANNELS = 1

interface OmniRealtimeAudioFormat {
  encoding: typeof PCM_S16LE
  sampleRateHz: number
  channels: number
}

/** 已验证的 Omni Realtime v1 浏览器就绪事件。 */
export interface OmniRealtimeReadyEvent {
  type: "ready"
  protocol: typeof OMNI_REALTIME_PROTOCOL
  endpointing: typeof SERVER_ENDPOINTING
  inputAudioFormat: OmniRealtimeAudioFormat
  outputAudioFormat: OmniRealtimeAudioFormat
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
}

function hasAudioFormat(value: unknown, sampleRateHz: number): value is OmniRealtimeAudioFormat {
  if (!isRecord(value)) return false

  return (
    value.encoding === PCM_S16LE &&
    value.sampleRateHz === sampleRateHz &&
    value.channels === MONO_CHANNELS
  )
}

/** 仅接受浏览器 server endpointing 的完整 Omni Realtime v1 ready 合同。 */
export function parseOmniRealtimeReadyEvent(data: unknown): OmniRealtimeReadyEvent | null {
  if (typeof data !== "string") return null

  try {
    const parsed: unknown = JSON.parse(data)
    if (!isRecord(parsed)) return null

    const { type, protocol, endpointing, inputAudioFormat, outputAudioFormat } = parsed
    if (
      type !== "ready" ||
      protocol !== OMNI_REALTIME_PROTOCOL ||
      endpointing !== SERVER_ENDPOINTING ||
      !hasAudioFormat(inputAudioFormat, INPUT_SAMPLE_RATE_HZ) ||
      !hasAudioFormat(outputAudioFormat, OUTPUT_SAMPLE_RATE_HZ)
    ) {
      return null
    }

    return {
      type,
      protocol,
      endpointing,
      inputAudioFormat,
      outputAudioFormat
    }
  } catch {
    return null
  }
}
