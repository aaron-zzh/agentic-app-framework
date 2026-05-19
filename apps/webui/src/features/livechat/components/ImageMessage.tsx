/**
 * ImageMessage——图片消息渲染组件
 * 展示图片缩略图，点击后全屏放大预览
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { X } from "lucide-react"
import { useState } from "react"

interface ImageMessageProps {
  /** 图片 URL */
  src: string
  /** 替代文本 */
  alt?: string
}

/**
 * 图片消息
 * 点击缩略图弹出全屏预览，按 Esc 或点击关闭
 */
export function ImageMessage({ src, alt = "图片" }: ImageMessageProps) {
  const [expanded, setExpanded] = useState(false)

  return (
    <>
      {/* 缩略图 */}
      <button
        type="button"
        className="my-2 block max-w-xs cursor-zoom-in overflow-hidden rounded-lg border border-border"
        onClick={() => setExpanded(true)}
      >
        {/* biome-ignore lint/performance/noImgElement: 动态 URL，next/image 需要配置 domains */}
        <img src={src} alt={alt} className="h-auto max-h-64 w-full object-cover" />
      </button>

      {/* 全屏预览 */}
      {expanded && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80"
          onClick={() => setExpanded(false)}
          onKeyDown={(e) => e.key === "Escape" && setExpanded(false)}
          role="dialog"
          aria-label="图片预览"
          tabIndex={-1}
        >
          <button
            type="button"
            className="absolute top-4 right-4 rounded-full bg-white/20 p-2 text-white hover:bg-white/40"
            onClick={() => setExpanded(false)}
            aria-label="关闭预览"
          >
            <X className="size-5" />
          </button>
          {/* biome-ignore lint/performance/noImgElement: 动态 URL，next/image 需要配置 domains */}
          <img src={src} alt={alt} className="max-h-[90vh] max-w-[90vw] object-contain" />
        </div>
      )}
    </>
  )
}
