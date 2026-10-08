/**
 * 音色切换对话框
 * @author Kiro
 */

"use client"

import { Play } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Slider } from "@/components/ui/slider"
import { useChangeVoice, usePresetVoices } from "@/lib/api/rest/digital-human"
import type { PresetVoice, VoiceConfig } from "../types"

interface VoiceChangeDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  digitalHumanId: string
  currentVoice: VoiceConfig
}

export function VoiceChangeDialog({
  open,
  onOpenChange,
  digitalHumanId,
  currentVoice
}: VoiceChangeDialogProps) {
  const { data: voices } = usePresetVoices()
  const changeVoiceMutation = useChangeVoice()

  const [selectedVoiceId, setSelectedVoiceId] = useState(currentVoice.presetVoiceId)
  const [speechRate, setSpeechRate] = useState(currentVoice.speechRate)
  const [pitchRate, setPitchRate] = useState(currentVoice.pitchRate)
  const [volume, setVolume] = useState(currentVoice.volume)

  const handleSubmit = async () => {
    await changeVoiceMutation.mutateAsync({
      id: digitalHumanId,
      data: {
        presetVoiceId: selectedVoiceId,
        speechRate,
        pitchRate,
        volume
      }
    })
    onOpenChange(false)
  }

  const playSample = (url: string | undefined) => {
    if (!url) return
    const audio = new Audio(url)
    audio.play()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>切换音色</DialogTitle>
          <DialogDescription>选择新的预设音色并调整音色参数</DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {/* 音色选择 */}
          <div>
            <Label>选择音色</Label>
            <div className="mt-2 grid grid-cols-2 gap-3">
              {voices?.map((voice: PresetVoice) => (
                <div
                  key={voice.id}
                  className={`flex items-center gap-3 rounded-lg border p-3 transition-colors ${
                    selectedVoiceId === voice.id ? "border-primary bg-primary/5" : "hover:bg-muted"
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => setSelectedVoiceId(voice.id)}
                    className="flex flex-1 text-left"
                  >
                    <span>
                      <span className="block font-medium">{voice.name}</span>
                      <span className="block text-muted-foreground text-xs">
                        {voice.description}
                      </span>
                    </span>
                  </button>
                  <Button
                    type="button"
                    size="icon"
                    variant="ghost"
                    disabled={!voice.sampleAudioUrl}
                    onClick={() => playSample(voice.sampleAudioUrl)}
                  >
                    <Play className="size-4" />
                  </Button>
                </div>
              ))}
            </div>
          </div>

          {/* 语速 */}
          <div className="space-y-2">
            <div className="flex justify-between">
              <Label>语速</Label>
              <span className="text-muted-foreground text-sm">{speechRate}</span>
            </div>
            <Slider
              value={[speechRate]}
              onValueChange={(v) => setSpeechRate(Array.isArray(v) ? (v[0] ?? speechRate) : v)}
              min={-500}
              max={500}
              step={50}
            />
          </div>

          {/* 音调 */}
          <div className="space-y-2">
            <div className="flex justify-between">
              <Label>音调</Label>
              <span className="text-muted-foreground text-sm">{pitchRate}</span>
            </div>
            <Slider
              value={[pitchRate]}
              onValueChange={(v) => setPitchRate(Array.isArray(v) ? (v[0] ?? pitchRate) : v)}
              min={-500}
              max={500}
              step={50}
            />
          </div>

          {/* 音量 */}
          <div className="space-y-2">
            <div className="flex justify-between">
              <Label>音量</Label>
              <span className="text-muted-foreground text-sm">{volume}</span>
            </div>
            <Slider
              value={[volume]}
              onValueChange={(v) => setVolume(Array.isArray(v) ? (v[0] ?? volume) : v)}
              min={0}
              max={100}
              step={5}
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={changeVoiceMutation.isPending}>
            {changeVoiceMutation.isPending ? "保存中..." : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
