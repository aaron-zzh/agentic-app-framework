/**
 * PDF 逐页预览（react-pdf），宽度随容器自适应；仅客户端渲染，经 index 的 dynamic 包装使用。
 * @author AaronZZH & Kiro
 */
"use client"

import { useResizeObserver } from "@wojtekmaj/react-hooks"
import type { PDFDocumentProxy } from "pdfjs-dist"
import { useCallback, useState } from "react"
import { Document, Page, pdfjs } from "react-pdf"
import "react-pdf/dist/Page/AnnotationLayer.css"
import "react-pdf/dist/Page/TextLayer.css"

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url
).toString()

const options = {
  cMapUrl: "/cmaps/",
  standardFontDataUrl: "/standard_fonts/"
}

const MAX_WIDTH = 800

/** 远程文件需鉴权时传 `{ url, httpHeaders }`；对象须保持引用稳定，否则会重复加载。 */
export type PdfSource = string | File | { url: string; httpHeaders?: Record<string, string> }

export interface PdfViewerProps {
  file: PdfSource
  className?: string
}

export default function PdfViewerImpl({ file, className }: PdfViewerProps) {
  const [numPages, setNumPages] = useState<number>()
  const [containerRef, setContainerRef] = useState<HTMLElement | null>(null)
  const [containerWidth, setContainerWidth] = useState<number>()

  const onResize = useCallback<ResizeObserverCallback>((entries) => {
    const [entry] = entries
    if (entry) setContainerWidth(entry.contentRect.width)
  }, [])

  useResizeObserver(containerRef, {}, onResize)

  function onLoadSuccess({ numPages }: PDFDocumentProxy) {
    setNumPages(numPages)
  }

  return (
    <div ref={setContainerRef} className={className ?? "w-full"}>
      <Document
        file={file}
        onLoadSuccess={onLoadSuccess}
        options={options}
        className="flex flex-col items-center gap-4"
        error={<p className="text-destructive text-sm">PDF 加载失败</p>}
        loading={<p className="text-muted-foreground text-sm">加载中…</p>}
      >
        {Array.from({ length: numPages ?? 0 }, (_, i) => (
          <Page
            key={`page_${i + 1}`}
            pageNumber={i + 1}
            width={containerWidth ? Math.min(containerWidth, MAX_WIDTH) : MAX_WIDTH}
            className="shadow-lg"
          />
        ))}
      </Document>
    </div>
  )
}
