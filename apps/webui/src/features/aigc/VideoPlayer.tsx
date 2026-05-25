/**
 * 视频预览播放器——HTML5 video + 自定义控制栏 + 分幕标记
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { Maximize2, Pause, Play } from "lucide-react"
import { Button } from "@/components/ui/button"
import type { VideoScene } from "./VideoTimeline"

interface VideoPlayerProps {
  src?: string
  scenes?: VideoScene[]
  onSceneChange?: (scene: VideoScene) => void
}

export function VideoPlayer({ src, scenes = [], onSceneChange }: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [playing, setPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)

  const togglePlay = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    if (video.paused) {
      video.play()
      setPlaying(true)
    } else {
      video.pause()
      setPlaying(false)
    }
  }, [])

  const handleTimeUpdate = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    setCurrentTime(video.currentTime)
  }, [])

  const handleLoadedMetadata = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    setDuration(video.duration)
  }, [])

  const handleSeek = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      const video = videoRef.current
      if (!video || duration === 0) return
      const rect = e.currentTarget.getBoundingClientRect()
      const ratio = (e.clientX - rect.left) / rect.width
      video.currentTime = ratio * duration
    },
    [duration]
  )

  const handleFullscreen = useCallback(() => {
    videoRef.current?.requestFullscreen()
  }, [])

  /** 分幕标记点击跳转 */
  const handleSceneClick = useCallback(
    (scene: VideoScene) => {
      const video = videoRef.current
      if (!video) return
      video.currentTime = scene.startTime
      onSceneChange?.(scene)
    },
    [onSceneChange]
  )

  /** 检测当前幕变化 */
  useEffect(() => {
    if (!scenes.length) return
    const active = scenes.find(
      (s) => currentTime >= s.startTime && currentTime < s.endTime
    )
    if (active) onSceneChange?.(active)
  }, [currentTime, scenes, onSceneChange])

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0
  const totalDuration = scenes.length > 0 ? scenes[scenes.length - 1].endTime : duration

  return (
    <div className="flex flex-col overflow-hidden rounded-lg border border-border/50 bg-black">
      {/* 视频区域 */}
      <div className="relative flex aspect-video items-center justify-center">
        {src ? (
          <video
            ref={videoRef}
            src={src}
            className="size-full object-contain"
            onTimeUpdate={handleTimeUpdate}
            onLoadedMetadata={handleLoadedMetadata}
            onEnded={() => setPlaying(false)}
          />
        ) : (
          <div className="flex flex-col items-center gap-2 text-muted-foreground">
            <Play className="size-10 opacity-30" />
            <span className="text-xs">等待视频生成</span>
          </div>
        )}
      </div>

      {/* 控制栏 */}
      <div className="flex flex-col gap-1 bg-card/80 px-3 py-2">
        {/* 进度条 + 分幕标记 */}
        <div
          className="group relative h-2 cursor-pointer rounded-full bg-muted"
          onClick={handleSeek}
          onKeyDown={(e) => { if (e.key === "Enter") handleSeek(e as unknown as React.MouseEvent<HTMLDivElement>) }}
        >
          {/* 已播放进度 */}
          <div
            className="absolute inset-y-0 left-0 rounded-full bg-primary transition-all"
            style={{ width: `${progress}%` }}
          />
          {/* 分幕标记 */}
          {totalDuration > 0 &&
            scenes.map((scene) => (
              <button
                key={scene.id}
                type="button"
                className="absolute top-1/2 size-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-foreground/60 hover:bg-primary"
                style={{ left: `${(scene.startTime / totalDuration) * 100}%` }}
                onClick={(e) => {
                  e.stopPropagation()
                  handleSceneClick(scene)
                }}
              />
            ))}
        </div>

        {/* 按钮行 */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="sm" className="size-7 p-0" onClick={togglePlay}>
              {playing ? <Pause className="size-4" /> : <Play className="size-4" />}
            </Button>
            <span className="text-xs text-muted-foreground">
              {formatTime(currentTime)} / {formatTime(duration || totalDuration)}
            </span>
          </div>
          <Button variant="ghost" size="sm" className="size-7 p-0" onClick={handleFullscreen}>
            <Maximize2 className="size-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, "0")}`
}
