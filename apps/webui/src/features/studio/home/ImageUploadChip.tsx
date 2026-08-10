/**
 * ImageUploadChip——媒体生成输入框内的方形图片附件。
 *
 * 状态：
 * - 上传中：方形本地预览 + 进度蒙层
 * - 已完成：方形缩略图，可点击打开 Lightbox
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { X } from "lucide-react"
import { Lightbox } from "@/components/lightbox/lightbox"
import { useLightbox } from "@/components/lightbox/use-lightbox"

interface ImageUploadChipProps {
  /** 文件名 */
  name: string
  /** 上传进度 0-100，100 表示完成 */
  progress: number
  /** 预览 URL（blob URL 或 OSS URL） */
  previewSrc?: string
  /** 点击删除 */
  onRemove: () => void
}

export function ImageUploadChip({ name, progress, previewSrc, onRemove }: ImageUploadChipProps) {
  const slides = previewSrc ? [{ src: previewSrc }] : []
  const { open, index, onOpen, onClose } = useLightbox(slides)
  const normalizedProgress = Math.min(100, Math.max(0, Math.round(progress)))
  const done = normalizedProgress >= 100

  return (
    <>
      <div
        className="group relative size-14 shrink-0 overflow-hidden rounded-md border border-foreground/12 bg-foreground/6"
        title={name}
      >
        {previewSrc ? (
          <button
            type="button"
            onClick={() => onOpen(previewSrc)}
            className="flex size-full cursor-zoom-in items-center justify-center"
            aria-label={`预览附件 ${name}`}
          >
            {/* biome-ignore lint/performance/noImgElement: blob/OSS 动态附件预览 */}
            <img src={previewSrc} alt={name} className="size-full object-cover" />
          </button>
        ) : (
          <div className="flex size-full items-center justify-center text-muted-foreground text-xs">
            图片
          </div>
        )}

        {!done ? (
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/45 text-white text-xs tabular-nums backdrop-blur-[1px]">
            {normalizedProgress}%
          </div>
        ) : null}

        <button
          type="button"
          onClick={onRemove}
          className="absolute top-1 right-1 flex size-4 items-center justify-center rounded-full bg-black/60 text-white transition-colors hover:bg-destructive"
          aria-label={`移除附件 ${name}`}
        >
          <X className="size-3" />
        </button>
      </div>

      <Lightbox
        open={open}
        index={index}
        slides={slides}
        close={onClose}
        toolbar={{ buttons: ["close"] }}
      />
    </>
  )
}
