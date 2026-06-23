"use client"

import { Download, Loader2 } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import type { LightboxExternalProps, SlideImage } from "yet-another-react-lightbox"
import ReactLightbox, { useLightboxState } from "yet-another-react-lightbox"

export type LightboxProps = LightboxExternalProps

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
        buttons: [<DownloadButton key="download" />, "close"],
        ...toolbar
      }}
      {...props}
    />
  )
}
