"use client"

import dynamic from "next/dynamic"

const PdfViewer = dynamic(() => import("./PdfViewer"), { ssr: false })

export default function Page() {
  return (
    <div className="p-6">
      <h1 className="mb-4 font-semibold text-xl">示例：PDF 预览</h1>
      <PdfViewer />
    </div>
  )
}
