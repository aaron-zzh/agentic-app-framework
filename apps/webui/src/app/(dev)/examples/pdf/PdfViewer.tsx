"use client"

import { useResizeObserver } from "@wojtekmaj/react-hooks"
import type { PDFDocumentProxy } from "pdfjs-dist"
import { useCallback, useState } from "react"
import { Document, Page, pdfjs } from "react-pdf"

import { $url } from "@/lib/utils"
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

export default function PdfViewer() {
  const [file, setFile] = useState<string | File>($url.cdn("/assets/docs/sample.pdf"))
  const [numPages, setNumPages] = useState<number>()
  const [containerRef, setContainerRef] = useState<HTMLElement | null>(null)
  const [containerWidth, setContainerWidth] = useState<number>()

  const onResize = useCallback<ResizeObserverCallback>((entries) => {
    const [entry] = entries
    if (entry) setContainerWidth(entry.contentRect.width)
  }, [])

  useResizeObserver(containerRef, {}, onResize)

  function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const next = e.target.files?.[0]
    if (next) setFile(next)
  }

  function onLoadSuccess({ numPages }: PDFDocumentProxy) {
    setNumPages(numPages)
  }

  return (
    <div className="flex flex-col items-center gap-4 p-4">
      <div className="flex items-center gap-2">
        <label htmlFor="pdf-file" className="font-medium text-sm">
          加载本地文件：
        </label>
        <input
          id="pdf-file"
          type="file"
          accept=".pdf"
          onChange={onFileChange}
          className="text-sm"
        />
      </div>

      <div ref={setContainerRef} className="w-full max-w-3xl">
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
    </div>
  )
}
