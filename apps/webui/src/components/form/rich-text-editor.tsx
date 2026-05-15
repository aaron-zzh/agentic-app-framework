/**
 * RichTextEditor——富文本编辑器（基于 Tiptap）
 * @author AaronZZH & Kiro
 * 参考 next-ts Editor 设计
 *
 * 注意：需要安装 @tiptap/react @tiptap/starter-kit @tiptap/extension-image @tiptap/extension-placeholder
 */

"use client"

import { useCallback, useEffect } from "react"

interface RichTextEditorProps {
  value?: string
  onChange?: (html: string) => void
  placeholder?: string
  disabled?: boolean
  error?: string
  /** 最小高度 */
  minHeight?: number
}

/**
 * 富文本编辑器
 * TODO: 待安装 @tiptap/react 后启用完整实现，当前为 textarea 降级
 */
export function RichTextEditor({
  value = "",
  onChange,
  placeholder = "输入内容...",
  disabled,
  error,
  minHeight = 200,
}: RichTextEditorProps) {
  // 降级实现：textarea（Tiptap 依赖未安装时）
  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      onChange?.(e.target.value)
    },
    [onChange]
  )

  return (
    <div className="space-y-1">
      {/* 工具栏占位 */}
      <div className="flex items-center gap-1 rounded-t-md border border-b-0 bg-muted/30 px-2 py-1">
        <ToolbarButton label="B" title="粗体" />
        <ToolbarButton label="I" title="斜体" />
        <ToolbarButton label="H" title="标题" />
        <ToolbarButton label="🔗" title="链接" />
        <ToolbarButton label="📷" title="图片" />
        <ToolbarButton label="<>" title="代码块" />
        <ToolbarButton label="❝" title="引用" />
      </div>

      {/* 编辑区 */}
      <textarea
        className="w-full rounded-b-md border px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50"
        style={{ minHeight }}
        value={value}
        onChange={handleChange}
        placeholder={placeholder}
        disabled={disabled}
      />
      {error && <p className="text-xs text-destructive">{error}</p>}
    </div>
  )
}

/** 工具栏按钮（占位） */
function ToolbarButton({ label, title }: { label: string; title: string }) {
  return (
    <button
      type="button"
      className="h-7 w-7 rounded text-xs hover:bg-muted"
      title={title}
      disabled
    >
      {label}
    </button>
  )
}
