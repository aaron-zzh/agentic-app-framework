"use client"

import { Download, ImagePlus, Loader2 } from "lucide-react"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { toast } from "sonner"
import type { LightboxExternalProps, SlideImage } from "yet-another-react-lightbox"
import ReactLightbox, { useLightboxState } from "yet-another-react-lightbox"

export type LightboxProps = LightboxExternalProps

/** 引用按钮：仅图片 slide 展示，点击跳转到图像创作页并作为参考图 */
function ReferenceButton() {
  const router = useRouter()
  const { currentSlide } = useLightboxState()
  const isImage = !!currentSlide && (currentSlide as { type?: string }).type !== "video"
  const url = isImage ? (currentSlide as SlideImage).src : undefined

  if (!url) return null

  return (
    <button
      type="button"
      className="yarl__button"
      onClick={() => router.push(`/studio/create?mode=image&refUrl=${encodeURIComponent(url)}`)}
      title="引用为参考图"
    >
      <ImagePlus size={24} />
    </button>
  )
}

function DownloadButton() {
  const { currentSlide } = useLightboxState()
  const url = (currentSlide as SlideImage | undefined)?.src
  const [loading, setLoading] = useState(false)

  if (!url) return null

  const handleDownload = async () => {
    setLoading(true)
    toast.loading("下载中...", { id: "lightbox-download" })
    try {
      const res = await fetch(url)
      const blob = await res.blob()
      const a = document.createElement("a")
      a.href = URL.createObjectURL(blob)
      a.download = url.split("/").pop()?.split("?")[0] ?? "image"
      a.click()
      URL.revokeObjectURL(a.href)
      toast.success("下载完成", { id: "lightbox-download" })
    } catch {
      toast.dismiss("lightbox-download")
      window.open(url, "_blank")
    } finally {
      setLoading(false)
    }
  }

  return (
    <button
      type="button"
      className="yarl__button"
      onClick={handleDownload}
      disabled={loading}
      title="下载"
    >
      {loading ? <Loader2 size={24} className="animate-spin" /> : <Download size={24} />}
    </button>
  )
}

/**
 * Lightbox 图片/视频预览组件，基于 yet-another-react-lightbox。
 * CSS 已在 global.css 全局引入。
 */
export function Lightbox({ plugins = [], toolbar, ...props }: LightboxProps) {
  return (
    <ReactLightbox
      animation={{ swipe: 240 }}
      controller={{ closeOnBackdropClick: true }}
      plugins={plugins}
      toolbar={{
        buttons: [<ReferenceButton key="reference" />, <DownloadButton key="download" />, "close"],
        ...toolbar
      }}
      {...props}
    />
  )
}
