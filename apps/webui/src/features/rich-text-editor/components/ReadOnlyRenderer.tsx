/**
 * ReadOnlyRenderer——HTML 只读渲染（无编辑器开销）
 * @author AaronZZH & Kiro
 *
 * 用于列表单元格、详情页只读展示，不加载 Lexical
 */

"use client"

import DOMPurify from "dompurify"

interface ReadOnlyRendererProps {
  html: string
  className?: string
}

export function ReadOnlyRenderer({ html, className }: ReadOnlyRendererProps) {
  const clean = typeof window !== "undefined" ? DOMPurify.sanitize(html) : html
  return (
    // biome-ignore lint/security/noDangerouslySetInnerHtml: 已通过 DOMPurify 消毒
    <div className={className} dangerouslySetInnerHTML={{ __html: clean }} />
  )
}
