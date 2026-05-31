/**
 * 语音配置——STT/TTS 模式选择（浏览器原生 or 后端接口）
 * 持久化到 localStorage
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { persist } from "zustand/middleware"
import { buildApiUrl } from "@/lib/api/base"
import { useAuthStore } from "@/lib/store/auth-store"

export type VoiceMode = "browser" | "server"

interface VoiceConfigStore {
  sttMode: VoiceMode
  ttsMode: VoiceMode
  ttsVoice: string
  setSttMode: (mode: VoiceMode) => void
  setTtsMode: (mode: VoiceMode) => void
  setTtsVoice: (voice: string) => void
}

export const useVoiceConfig = create<VoiceConfigStore>()(
  persist(
    (set) => ({
      sttMode: "browser",
      ttsMode: "browser",
      ttsVoice: "Cherry",
      setSttMode: (sttMode) => set({ sttMode }),
      setTtsMode: (ttsMode) => set({ ttsMode }),
      setTtsVoice: (ttsVoice) => set({ ttsVoice })
    }),
    { name: "aaf-voice-config" }
  )
)

/** 获取认证头 */
function getAuthHeaders(): Record<string, string> {
  const token = useAuthStore.getState().accessToken
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/** 调用后端 STT 接口 */
export async function serverStt(audioBlob: Blob, lang = "zh-CN"): Promise<string> {
  const form = new FormData()
  form.append("audio", audioBlob, "audio.wav")
  form.append("lang", lang)
  const res = await fetch(buildApiUrl("/voice/stt"), {
    method: "POST",
    headers: getAuthHeaders(),
    body: form
  })
  if (!res.ok) throw new Error(`STT 失败: ${res.status}`)
  const data = (await res.json()) as { data: string }
  return data.data
}

/** 调用后端 TTS 流式接口，返回 AudioBuffer */
export async function serverTtsStream(
  text: string,
  voice = "Cherry",
  onChunk: (chunk: ArrayBuffer) => void
): Promise<void> {
  const res = await fetch(buildApiUrl("/voice/tts/stream"), {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify({ text, voice })
  })
  if (!res.ok || !res.body) throw new Error(`TTS 失败: ${res.status}`)
  const reader = res.body.getReader()
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    onChunk(value.buffer)
  }
}
