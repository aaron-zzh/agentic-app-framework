"use client"

/**
 * SSR-safe RichTextEditor 包装——Lexical 依赖 DOM API，必须 ssr:false
 */

import dynamic from "next/dynamic"
import type React from "react"
import type { RichTextEditorHandle, RichTextEditorProps } from "../types"

// dynamic 不保留 ref 类型，需要手动断言
const RichTextEditorImpl = dynamic(() => import("./RichTextEditor"), {
  ssr: false,
  loading: () => <div className="min-h-[200px] animate-pulse rounded-md border bg-muted" />
}) as React.ComponentType<RichTextEditorProps & { ref?: React.Ref<RichTextEditorHandle> }>

export function RichTextEditor(props: RichTextEditorProps) {
  return <RichTextEditorImpl {...props} />
}
