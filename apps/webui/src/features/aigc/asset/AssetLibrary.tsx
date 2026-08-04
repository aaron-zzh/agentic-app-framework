/**
 * Studio 媒体/资产库。
 *
 * media 展示所有持久化媒体；asset 仅展示用户明确保存的资产。
 * @author AaronZZH & Kiro
 */

"use client"

import { Box, ImageIcon, Music, RefreshCw, Search, Video } from "lucide-react"
import { useState } from "react"
import VideoPlugin from "yet-another-react-lightbox/plugins/video"
import { Lightbox, useLightbox } from "@/components/lightbox"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { MediaType, MediaVO } from "@/features/aigc/types"
import { useAssetList, useMediaList } from "@/lib/api/rest/media"

const PAGE_SIZE = 20
const MEDIA_TYPES: Array<{ value: MediaType | "ALL"; label: string }> = [
  { value: "ALL", label: "全部" },
  { value: "IMAGE", label: "图像" },
  { value: "VIDEO", label: "视频" },
  { value: "AUDIO", label: "配音" },
  { value: "MUSIC", label: "音乐" },
  { value: "MODEL_3D", label: "3D" }
]

export interface AssetLibraryProps {
  collection?: "media" | "asset"
}

export function AssetLibrary({ collection = "media" }: AssetLibraryProps) {
  return collection === "asset" ? <SavedAssetLibrary /> : <MediaLibrary />
}

function MediaLibrary() {
  const [pageNo, setPageNo] = useState(1)
  const [keyword, setKeyword] = useState("")
  const [mediaType, setMediaType] = useState<MediaType | "ALL">("ALL")
  const { data, isLoading, refetch } = useMediaList({
    pageNo,
    pageSize: PAGE_SIZE,
    keyword: keyword.trim() || undefined,
    mediaType: mediaType === "ALL" ? undefined : mediaType
  })

  return (
    <LibraryView
      title="作品"
      description="生成结果已持久化为媒体；需要长期复用时可在结果卡保存为资产。"
      items={data?.list ?? []}
      total={data?.total ?? 0}
      pageNo={pageNo}
      keyword={keyword}
      mediaType={mediaType}
      isLoading={isLoading}
      onKeywordChange={(value) => {
        setKeyword(value)
        setPageNo(1)
      }}
      onTypeChange={(value) => {
        setMediaType(value)
        setPageNo(1)
      }}
      onPageChange={setPageNo}
      onRefresh={() => refetch()}
    />
  )
}

function SavedAssetLibrary() {
  const [pageNo, setPageNo] = useState(1)
  const [keyword, setKeyword] = useState("")
  const [mediaType, setMediaType] = useState<MediaType | "ALL">("ALL")
  const { data, isLoading, refetch } = useAssetList({
    pageNo,
    pageSize: PAGE_SIZE,
    keyword: keyword.trim() || undefined,
    mediaType: mediaType === "ALL" ? undefined : mediaType
  })

  return (
    <LibraryView
      title="素材资产"
      description="这里只展示已明确保存为资产的媒体，文案内容不会进入此列表。"
      items={(data?.list ?? []).map((asset) => asset.media)}
      total={data?.total ?? 0}
      pageNo={pageNo}
      keyword={keyword}
      mediaType={mediaType}
      isLoading={isLoading}
      saved
      onKeywordChange={(value) => {
        setKeyword(value)
        setPageNo(1)
      }}
      onTypeChange={(value) => {
        setMediaType(value)
        setPageNo(1)
      }}
      onPageChange={setPageNo}
      onRefresh={() => refetch()}
    />
  )
}

interface LibraryViewProps {
  title: string
  description: string
  items: MediaVO[]
  total: number
  pageNo: number
  keyword: string
  mediaType: MediaType | "ALL"
  isLoading: boolean
  saved?: boolean
  onKeywordChange: (value: string) => void
  onTypeChange: (value: MediaType | "ALL") => void
  onPageChange: (page: number) => void
  onRefresh: () => void
}

function LibraryView({
  title,
  description,
  items,
  total,
  pageNo,
  keyword,
  mediaType,
  isLoading,
  saved = false,
  onKeywordChange,
  onTypeChange,
  onPageChange,
  onRefresh
}: LibraryViewProps) {
  const slides = items
    .filter((media) => media.mediaType === "IMAGE" || media.mediaType === "VIDEO")
    .map((media) =>
      media.mediaType === "VIDEO"
        ? {
            type: "video" as const,
            sources: [{ src: media.currentVersion.url, type: media.currentVersion.mimeType ?? "video/mp4" }]
          }
        : { src: media.currentVersion.url }
    )
  const { open, index, onOpen, onClose } = useLightbox(slides)
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="flex h-full flex-col gap-4 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-semibold text-xl">{title}</h1>
          <p className="mt-1 text-muted-foreground text-sm">{description}</p>
        </div>
        <Button variant="outline" onClick={onRefresh}>
          <RefreshCw />
          刷新
        </Button>
      </header>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-64 flex-1">
          <Search className="absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
            placeholder="搜索名称"
            className="pl-9"
          />
        </div>
        <Tabs value={mediaType} onValueChange={(value) => onTypeChange(value as MediaType | "ALL")}>
          <TabsList>
            {MEDIA_TYPES.map((type) => (
              <TabsTrigger key={type.value} value={type.value}>
                {type.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {Array.from({ length: 10 }).map((_, itemIndex) => (
            <Skeleton key={`media-skeleton-${itemIndex}`} className="aspect-square rounded-xl" />
          ))}
        </div>
      ) : items.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-12 text-center">
            <ImageIcon className="text-muted-foreground" />
            <p className="font-medium">暂无内容</p>
            <p className="text-muted-foreground text-sm">完成生成或保存资产后将在这里展示。</p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {items.map((media) => (
            <MediaCard key={media.id} media={media} saved={saved} onPreview={onOpen} />
          ))}
        </div>
      )}

      {total > PAGE_SIZE && (
        <div className="mt-auto flex items-center justify-center gap-3 pt-2">
          <Button variant="outline" disabled={pageNo <= 1} onClick={() => onPageChange(pageNo - 1)}>
            上一页
          </Button>
          <span className="text-muted-foreground text-sm">
            {pageNo} / {pageCount}
          </span>
          <Button
            variant="outline"
            disabled={pageNo >= pageCount}
            onClick={() => onPageChange(pageNo + 1)}
          >
            下一页
          </Button>
        </div>
      )}

      <Lightbox open={open} index={index} slides={slides} close={onClose} plugins={[VideoPlugin]} />
    </div>
  )
}

function MediaCard({
  media,
  saved,
  onPreview
}: {
  media: MediaVO
  saved: boolean
  onPreview: (url: string) => void
}) {
  const version = media.currentVersion
  const isAudio = media.mediaType === "AUDIO" || media.mediaType === "MUSIC"

  return (
    <Card className="gap-3">
      <CardHeader>
        <CardTitle className="truncate text-sm">{media.name}</CardTitle>
        <CardDescription>{media.mediaType}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {media.mediaType === "IMAGE" ? (
          <button
            type="button"
            className="aspect-square overflow-hidden rounded-lg bg-muted"
            onClick={() => onPreview(version.url)}
          >
            {/* biome-ignore lint/performance/noImgElement: 动态媒体缩略图 */}
            <img
              src={version.thumbnailUrl ?? version.url}
              alt={media.name}
              className="size-full object-cover"
            />
          </button>
        ) : media.mediaType === "VIDEO" ? (
          <button
            type="button"
            className="relative aspect-square overflow-hidden rounded-lg bg-muted"
            onClick={() => onPreview(version.url)}
          >
            <video src={version.url} muted preload="metadata" className="size-full object-cover" />
            <Video className="absolute inset-1/2 -translate-1/2 text-white" />
          </button>
        ) : isAudio ? (
          <div className="flex aspect-square flex-col items-center justify-center gap-3 rounded-lg bg-muted p-3">
            <Music className="text-muted-foreground" />
            <audio controls src={version.url} className="w-full">
              <track kind="captions" />
            </audio>
          </div>
        ) : (
          <a
            href={version.url}
            target="_blank"
            rel="noreferrer"
            className="flex aspect-square flex-col items-center justify-center gap-2 rounded-lg bg-muted text-muted-foreground hover:text-foreground"
          >
            <Box />
            打开 3D
          </a>
        )}
        {(saved || media.assetId !== null) && <Badge variant="secondary">已保存资产</Badge>}
      </CardContent>
    </Card>
  )
}
