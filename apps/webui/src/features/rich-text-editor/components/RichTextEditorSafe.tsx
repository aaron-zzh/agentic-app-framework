"use client"

/**
 * SSR-safe RichTextEditor 包装——Lexical 依赖 DOM API，必须 ssr:false
 */

import dynamic from "next/dynamic"
import type { RichTextEditorProps } from "../types"

const RichTextEditorImpl = dynamic(
  () => import("./RichTextEditor").then((m) => ({ default: m.RichTextEditor })),
  {
    ssr: false,
    loading: () => <div className="min-h-[200px] animate-pulse rounded-md border bg-muted" />
  }
)

export function RichTextEditor(props: RichTextEditorProps) {
  return <RichTextEditorImpl {...props} />
}
