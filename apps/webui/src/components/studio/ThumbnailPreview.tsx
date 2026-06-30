import { X } from "lucide-react"

interface ThumbnailPreviewProps {
  /** 图片 URL */
  src: string
  /** 图片 alt 文本 */
  alt: string
  /** 点击图片时触发（预览/lightbox） */
  onPreview: () => void
  /** 点击删除按钮时触发 */
  onRemove: () => void
  /** 容器额外 className */
  className?: string
}

/**
 * 缩略图预览组件：固定 size-14 容器，点击图片预览，右上角红色删除按钮。
 * 用于 studio/create 各页面的参考图 / 首帧 / 尾帧等单图缩略图场景。
 */
export function ThumbnailPreview({
  src,
  alt,
  onPreview,
  onRemove,
  className
}: ThumbnailPreviewProps) {
  return (
    <div className={`group relative size-14 rounded-md border bg-muted ${className ?? ""}`}>
      <button type="button" className="size-full cursor-zoom-in rounded-md p-0" onClick={onPreview}>
        {/* biome-ignore lint/performance/noImgElement: 动态 URL 来自用户上传，无法用 next/image 优化 */}
        <img src={src} alt={alt} className="size-full overflow-hidden rounded-md object-cover" />
      </button>
      <button
        type="button"
        onClick={onRemove}
        className="absolute -top-1 -right-1 hidden size-4 items-center justify-center rounded-full bg-destructive text-destructive-foreground group-hover:flex"
      >
        <X className="size-3" />
      </button>
    </div>
  )
}
