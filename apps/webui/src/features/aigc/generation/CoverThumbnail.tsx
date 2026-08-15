/**
 * AIGC 资产封面缩略图：动态图片加载失败时回退到调用方提供的内容。
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { useState } from "react"
import { cn } from "@/lib/utils/cn"

interface CoverThumbnailProps {
  src?: string | null
  alt: string
  fallback: ReactNode
  className?: string
  imageClassName?: string
}

/** 渲染允许任意动态 URL 的封面，并在无 URL 或加载失败时显示回退内容。 */
export function CoverThumbnail({
  src,
  alt,
  fallback,
  className,
  imageClassName
}: CoverThumbnailProps) {
  const normalizedSrc = src?.trim() || null
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const imageSrc = normalizedSrc && failedSrc !== normalizedSrc ? normalizedSrc : null

  return (
    <div
      className={cn(
        "flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-foreground/8 text-foreground/60",
        className
      )}
    >
      {imageSrc ? (
        // biome-ignore lint/performance/noImgElement: AIGC 封面允许任意后端动态 URL，无法配置 next/image 域名白名单
        <img
          src={imageSrc}
          alt={alt}
          className={cn("size-full object-cover", imageClassName)}
          onError={() => setFailedSrc(imageSrc)}
        />
      ) : (
        fallback
      )}
    </div>
  )
}
