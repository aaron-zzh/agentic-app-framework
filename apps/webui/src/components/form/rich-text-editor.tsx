/**
 * RichTextEditor——表单富文本字段（消费 features/rich-text-editor）
 * 使用 dynamic import + ssr:false 避免 Lexical Hydration 错误
 * @author AaronZZH & Kiro
 */

"use client"

import dynamic from "next/dynamic"
import type { RichTextEditorProps } from "@/features/rich-text-editor"

// Lexical 编辑器依赖 DOM API，不能 SSR，必须 ssr:false
const RichTextEditorClient = dynamic(
  () => import("@/features/rich-text-editor").then((m) => ({ default: m.RichTextEditor })),
  { ssr: false }
)

export function RichTextEditor(props: RichTextEditorProps) {
  return <RichTextEditorClient {...props} />
}
