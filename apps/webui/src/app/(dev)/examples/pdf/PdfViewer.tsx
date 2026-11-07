"use client"

import { useState } from "react"
import { PdfViewer as SharedPdfViewer } from "@/components/pdf"
import { $url } from "@/lib/utils"

export default function PdfViewer() {
  const [file, setFile] = useState<string | File>($url.cdn("/assets/docs/sample.pdf"))

  function onFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const next = e.target.files?.[0]
    if (next) setFile(next)
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

      <SharedPdfViewer file={file} className="w-full max-w-3xl" />
    </div>
  )
}
