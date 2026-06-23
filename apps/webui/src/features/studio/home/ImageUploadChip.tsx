/**
 * ImageUploadChip——输入框内嵌图片上传 chip
 *
 * 状态：
 * - 上传中：进度圆圈 + 文件名（截断）
 * - 已完成：缩略图 + 文件名 + × 删除
 *
 * @author AaronZZH & Kiro
 */
"use client"

import { X } from "lucide-react"

interface ImageUploadChipProps {
  /** 文件名 */
  name: string
  /** 上传进度 0-100，100 表示完成 */
  progress: number
  /** 完成后的预览 URL（blob URL 或 OSS URL） */
  previewSrc?: string
  /** 点击删除 */
  onRemove: () => void
}

export function ImageUploadChip({ name, progress, previewSrc, onRemove }: ImageUploadChipProps) {
  const done = progress >= 100 && !!previewSrc
  // 截断过长文件名
  const displayName = name.length > 20 ? `${name.slice(0, 18)}…` : name

  return (
    <span className="inline-flex max-w-[200px] shrink-0 items-center gap-1.5 rounded-full border border-foreground/[0.12] bg-foreground/[0.06] px-2 py-0.5 align-middle text-xs">
      {done ? (
        /* 完成态：缩略图 */
        // biome-ignore lint/performance/noImgElement: thumbnail chip
        <img src={previewSrc} alt={name} className="size-4 rounded-full object-cover" />
      ) : (
        /* 上传中：SVG 进度圆 */
        <svg width="16" height="16" viewBox="0 0 16 16" className="shrink-0" aria-hidden="true">
          <circle
            cx="8"
            cy="8"
            r="6"
            fill="none"
            stroke="currentColor"
            strokeOpacity={0.2}
            strokeWidth="2"
          />
          <circle
            cx="8"
            cy="8"
            r="6"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeDasharray={`${(progress / 100) * 37.7} 37.7`}
            transform="rotate(-90 8 8)"
            className="text-primary transition-[stroke-dasharray] duration-200"
          />
        </svg>
      )}

      <span className="truncate leading-none">{displayName}</span>

      <button
        type="button"
        onClick={onRemove}
        className="ml-0.5 flex shrink-0 items-center justify-center rounded-full opacity-50 hover:opacity-100"
        aria-label="移除图片"
      >
        <X className="size-2.5" />
      </button>
    </span>
  )
}
