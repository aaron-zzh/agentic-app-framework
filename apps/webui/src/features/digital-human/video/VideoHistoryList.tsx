"use client"

import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"
import { Calendar, Clock, Play } from "lucide-react"
import Image from "next/image"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { VideoPlayer } from "./VideoPlayer"

interface Video {
  id: string
  title: string
  url: string
  thumbnailUrl?: string
  duration: number
  createdAt: string
  projectId?: string
}

interface VideoHistoryListProps {
  videos: Video[]
  loading?: boolean
}

export function VideoHistoryList({ videos, loading }: VideoHistoryListProps) {
  const [selectedVideo, setSelectedVideo] = useState<Video | null>(null)

  if (loading) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <Card key={i} className="overflow-hidden">
            <div className="aspect-video animate-pulse bg-muted" />
            <CardContent className="p-4">
              <div className="mb-2 h-5 animate-pulse rounded bg-muted" />
              <div className="h-4 w-2/3 animate-pulse rounded bg-muted" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  if (videos.length === 0) {
    return (
      <Card>
        <CardContent className="py-12 text-center">
          <p className="text-muted-foreground">暂无视频历史</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {videos.map((video) => (
          <Card key={video.id} className="group overflow-hidden transition-shadow hover:shadow-lg">
            <div className="relative aspect-video bg-black">
              {video.thumbnailUrl ? (
                <Image
                  src={video.thumbnailUrl}
                  alt={video.title}
                  fill
                  sizes="(min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw"
                  className="object-cover"
                />
              ) : (
                <div className="flex size-full items-center justify-center bg-muted">
                  <Play className="size-12 text-muted-foreground" />
                </div>
              )}
              <div className="absolute inset-0 flex items-center justify-center bg-black/50 opacity-0 transition-opacity group-hover:opacity-100">
                <Button size="lg" onClick={() => setSelectedVideo(video)} className="gap-2">
                  <Play className="size-5" />
                  播放
                </Button>
              </div>
              <div className="absolute right-2 bottom-2 rounded bg-black/80 px-2 py-1 font-mono text-white text-xs">
                {formatDuration(video.duration)}
              </div>
            </div>
            <CardContent className="p-4">
              <h3 className="mb-2 line-clamp-1 font-medium">{video.title}</h3>
              <div className="flex items-center gap-3 text-muted-foreground text-xs">
                <div className="flex items-center gap-1">
                  <Calendar className="size-3" />
                  {formatDistanceToNow(new Date(video.createdAt), {
                    addSuffix: true,
                    locale: zhCN
                  })}
                </div>
                <div className="flex items-center gap-1">
                  <Clock className="size-3" />
                  {formatDuration(video.duration)}
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* 视频播放对话框 */}
      <Dialog open={!!selectedVideo} onOpenChange={() => setSelectedVideo(null)}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>{selectedVideo?.title}</DialogTitle>
          </DialogHeader>
          {selectedVideo && (
            <VideoPlayer
              url={selectedVideo.url}
              title={selectedVideo.title}
              duration={selectedVideo.duration}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}

function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  if (mins > 0) {
    return `${mins}:${secs.toString().padStart(2, "0")}`
  }
  return `0:${secs.toString().padStart(2, "0")}`
}
