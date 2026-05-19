/**
 * 语音设置组件
 *
 * 功能：
 * - 语音选择（SpeechSynthesis.getVoices()）
 * - 语言选择
 * - 语速/音调调节
 * - 设置持久化到 localStorage
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { useCallback, useEffect, useState } from "react"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"

const STORAGE_KEY = "aaf-voice-settings"

const LANGUAGES = [
  { value: "zh-CN", label: "中文（普通话）" },
  { value: "zh-TW", label: "中文（台湾）" },
  { value: "en-US", label: "English (US)" },
  { value: "en-GB", label: "English (UK)" },
  { value: "ja-JP", label: "日本語" },
  { value: "ko-KR", label: "한국어" },
]

export interface VoiceSettingsValue {
  voiceURI: string
  lang: string
  rate: number
  pitch: number
}

interface VoiceSettingsProps {
  /** 设置变更回调 */
  onChange?: (settings: VoiceSettingsValue) => void
  className?: string
}

/** 从 localStorage 读取设置 */
function loadSettings(): VoiceSettingsValue {
  if (typeof window === "undefined") {
    return { voiceURI: "", lang: "zh-CN", rate: 1, pitch: 1 }
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw) as VoiceSettingsValue
  } catch {
    // 忽略解析错误
  }
  return { voiceURI: "", lang: "zh-CN", rate: 1, pitch: 1 }
}

/** 保存设置到 localStorage */
function saveSettings(settings: VoiceSettingsValue) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
}

export function VoiceSettings({ onChange, className }: VoiceSettingsProps) {
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>([])
  const [settings, setSettings] = useState<VoiceSettingsValue>(loadSettings)

  /** 加载可用语音列表 */
  useEffect(() => {
    const loadVoices = () => {
      const available = window.speechSynthesis.getVoices()
      setVoices(available)
    }
    loadVoices()
    window.speechSynthesis.addEventListener("voiceschanged", loadVoices)
    return () => window.speechSynthesis.removeEventListener("voiceschanged", loadVoices)
  }, [])

  /** 更新设置 */
  const update = useCallback(
    (patch: Partial<VoiceSettingsValue>) => {
      setSettings((prev) => {
        const next = { ...prev, ...patch }
        saveSettings(next)
        onChange?.(next)
        return next
      })
    },
    [onChange],
  )

  /** 按当前语言过滤语音 */
  const filteredVoices = voices.filter((v) => v.lang.startsWith(settings.lang.split("-")[0]))

  return (
    <div className={`flex flex-col gap-4 ${className ?? ""}`}>
      {/* 语言选择 */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="voice-lang">语言</Label>
        <Select value={settings.lang} onValueChange={(v) => update({ lang: v ?? "zh-CN", voiceURI: "" })}>
          <SelectTrigger id="voice-lang">
            <SelectValue placeholder="选择语言" />
          </SelectTrigger>
          <SelectContent>
            {LANGUAGES.map((l) => (
              <SelectItem key={l.value} value={l.value}>
                {l.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* 语音选择 */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="voice-name">语音</Label>
        <Select value={settings.voiceURI} onValueChange={(v) => update({ voiceURI: v ?? "" })}>
          <SelectTrigger id="voice-name">
            <SelectValue placeholder="选择语音" />
          </SelectTrigger>
          <SelectContent>
            {filteredVoices.map((v) => (
              <SelectItem key={v.voiceURI} value={v.voiceURI}>
                {v.name}
              </SelectItem>
            ))}
            {filteredVoices.length === 0 && (
              <SelectItem value="" disabled>
                无可用语音
              </SelectItem>
            )}
          </SelectContent>
        </Select>
      </div>

      {/* 语速调节 */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="voice-rate">语速：{settings.rate.toFixed(1)}x</Label>
        <input
          id="voice-rate"
          type="range"
          min={0.5}
          max={2}
          step={0.1}
          value={settings.rate}
          onChange={(e) => update({ rate: Number.parseFloat(e.target.value) })}
          className="w-full accent-primary"
        />
      </div>

      {/* 音调调节 */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="voice-pitch">音调：{settings.pitch.toFixed(1)}</Label>
        <input
          id="voice-pitch"
          type="range"
          min={0.5}
          max={2}
          step={0.1}
          value={settings.pitch}
          onChange={(e) => update({ pitch: Number.parseFloat(e.target.value) })}
          className="w-full accent-primary"
        />
      </div>
    </div>
  )
}
