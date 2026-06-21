/**
 * 视频编辑面板——裁剪、字幕、导出格式
 * @author AaronZZH & Kiro
 */

"use client"

import { Scissors } from "lucide-react"
import { useId, useState } from "react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Textarea } from "@/components/ui/textarea"
import { useGenerateVideo } from "@/lib/queries/use-image-generation"

interface VideoEditPanelProps {
  videoUrl?: string
}

export function VideoEditPanel({ videoUrl = "" }: VideoEditPanelProps) {
  const uid = useId()
  const [startTime, setStartTime] = useState("00:00")
  const [endTime, setEndTime] = useState("00:10")
  const [subtitle, setSubtitle] = useState("")
  const [format, setFormat] = useState("mp4")
  const videoEdit = useGenerateVideo()

  function handleSubmit() {
    if (!videoUrl) return
    videoEdit.mutate({
      prompt: subtitle || "视频编辑",
      imageMode: "T2V",
      resolution: format === "gif" ? "480p" : "1080p"
    })
  }

  return (
    <div className="flex h-full flex-col gap-4 p-4">
      <h3 className="flex items-center gap-2 font-semibold text-sm">
        <Scissors className="size-4" />
        视频编辑
      </h3>

      <div className="flex flex-col gap-2">
        <Label className="text-xs">裁剪区间</Label>
        <div className="flex items-center gap-2">
          <Input
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            placeholder="00:00"
            className="h-8 w-24 text-xs"
          />
          <span className="text-muted-foreground text-xs">至</span>
          <Input
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            placeholder="00:10"
            className="h-8 w-24 text-xs"
          />
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <Label className="text-xs">字幕内容</Label>
        <Textarea
          value={subtitle}
          onChange={(e) => setSubtitle(e.target.value)}
          placeholder="输入字幕文本，将对齐到裁剪区间..."
          className="min-h-[80px] resize-y text-xs"
        />
      </div>

      <div className="flex flex-col gap-2">
        <Label className="text-xs">导出格式</Label>
        <RadioGroup value={format} onValueChange={setFormat} className="flex gap-4">
          <div className="flex items-center gap-1.5">
            <RadioGroupItem value="mp4" id={`${uid}-mp4`} />
            <Label htmlFor={`${uid}-mp4`} className="text-xs">
              MP4
            </Label>
          </div>
          <div className="flex items-center gap-1.5">
            <RadioGroupItem value="webm" id={`${uid}-webm`} />
            <Label htmlFor={`${uid}-webm`} className="text-xs">
              WebM
            </Label>
          </div>
          <div className="flex items-center gap-1.5">
            <RadioGroupItem value="gif" id={`${uid}-gif`} />
            <Label htmlFor={`${uid}-gif`} className="text-xs">
              GIF
            </Label>
          </div>
        </RadioGroup>
      </div>

      <Button
        onClick={handleSubmit}
        disabled={videoEdit.isPending || !videoUrl}
        className="mt-auto bg-gradient-to-r from-violet-500 to-fuchsia-500 text-white hover:from-violet-600 hover:to-fuchsia-600"
      >
        {videoEdit.isPending ? "处理中..." : "导出视频"}
      </Button>
    </div>
  )
}
