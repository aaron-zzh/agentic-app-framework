"use client"

import { Download, Pause, Play, Volume2, VolumeX } from "lucide-react"
import { useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Slider } from "@/components/ui/slider"

interface VideoPlayerProps {
  url: string
  title?: string
  duration?: number
  onDownload?: () => void
}

export function VideoPlayer({ url, title, duration, onDownload }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [playing, setPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [volume, setVolume] = useState(1)
  const [muted, setMuted] = useState(false)

  const togglePlay = () => {
    if (!videoRef.current) return
    if (playing) {
      videoRef.current.pause()
    } else {
      videoRef.current.play()
    }
    setPlaying(!playing)
  }

  const handleTimeUpdate = () => {
    if (videoRef.current) {
      setCurrentTime(videoRef.current.currentTime)
    }
  }

  const handleSeek = (value: number | readonly number[]) => {
    const next = Array.isArray(value) ? value[0] : value
    if (videoRef.current && next !== undefined) {
      videoRef.current.currentTime = next
      setCurrentTime(next)
    }
  }

  const handleVolumeChange = (value: number | readonly number[]) => {
    const next = Array.isArray(value) ? value[0] : value
    if (videoRef.current && next !== undefined) {
      videoRef.current.volume = next
      setVolume(next)
      setMuted(next === 0)
    }
  }

  const toggleMute = () => {
    if (videoRef.current) {
      videoRef.current.muted = !muted
      setMuted(!muted)
    }
  }

  const handleDownload = () => {
    if (onDownload) {
      onDownload()
    } else {
      const a = document.createElement("a")
      a.href = url
      a.download = title || "video.mp4"
      a.click()
    }
  }

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, "0")}`
  }

  const videoDuration = duration || videoRef.current?.duration || 0

  return (
    <Card>
      <CardContent className="p-0">
        <div className="relative aspect-video bg-black">
          <video
            ref={videoRef}
            src={url}
            className="size-full"
            onTimeUpdate={handleTimeUpdate}
            onEnded={() => setPlaying(false)}
            onClick={togglePlay}
          >
            <track kind="captions" srcLang="zh-CN" label="中文字幕" />
          </video>
        </div>

        <div className="space-y-2 p-4">
          <div className="flex items-center gap-2">
            <span className="text-muted-foreground text-xs">{formatTime(currentTime)}</span>
            <Slider
              value={[currentTime]}
              onValueChange={handleSeek}
              max={videoDuration}
              step={0.1}
              className="flex-1"
            />
            <span className="text-muted-foreground text-xs">{formatTime(videoDuration)}</span>
          </div>

          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Button size="icon" variant="ghost" onClick={togglePlay}>
                {playing ? <Pause className="size-4" /> : <Play className="size-4" />}
              </Button>
              <div className="flex items-center gap-2">
                <Button size="icon" variant="ghost" onClick={toggleMute}>
                  {muted ? <VolumeX className="size-4" /> : <Volume2 className="size-4" />}
                </Button>
                <Slider
                  value={[muted ? 0 : volume]}
                  onValueChange={handleVolumeChange}
                  max={1}
                  step={0.1}
                  className="w-24"
                />
              </div>
            </div>
            <Button size="sm" variant="outline" onClick={handleDownload}>
              <Download className="mr-2 size-4" />
              下载
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
