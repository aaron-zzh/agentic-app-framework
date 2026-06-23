"use client"

import { FileText, X } from "lucide-react"

interface TextChipProps {
  text: string
  onRemove: () => void
}

/** 长文本内嵌 chip——显示前8字 + 总字数，点 × 清空 */
export function TextChip({ text, onRemove }: TextChipProps) {
  const preview = text.slice(0, 8)
  const count = text.length
  return (
    <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full border border-foreground/[0.12] bg-foreground/[0.06] px-2 py-0.5 align-middle text-xs">
      <FileText className="size-3 shrink-0 opacity-60" />
      <span className="leading-none">
        {preview}… <span className="opacity-50">{count}字</span>
      </span>
      <button
        type="button"
        onClick={onRemove}
        className="ml-0.5 flex shrink-0 items-center justify-center rounded-full opacity-50 hover:opacity-100"
        aria-label="清空文本"
      >
        <X className="size-2.5" />
      </button>
    </span>
  )
}
