/**
 * RichTextEditor——统一富文本编辑器（基于 Lexical）
 * @author AaronZZH & Kiro
 *
 * 四种 preset：minimal / chatter / richField / document
 *
 * ## ⚠️ 受控 vs 非受控
 *
 * Lexical 本质是非受控编辑器（内部维护自己的 EditorState）。
 * 如果父组件把 `onChange` 的返回值再传回 `value`，每次输入都会触发：
 * `onChange → setState → re-render → 新 value → 重新初始化编辑器`
 * 这会打断中文/日文 IME 的组合输入，导致拼音字母被直接提交。
 *
 * **正确用法**：
 * - 需要实时回显（如字数统计）→ 受控模式，接受 IME 有限制
 * - 只在提交时读值（如表单）→ 非受控模式，用 `useRef` 存值
 *
 * @example
 * ```tsx
 * // ✅ 受控模式——value 变化不触发编辑器重新初始化（仅初始值生效）
 * <RichTextEditor value={html} onChange={setHtml} preset="richField" />
 *
 * // ✅ 非受控模式——不传 value 回编辑器，IME 输入正常（推荐用于表单）
 * const contentRef = useRef("")
 * <RichTextEditor value="" onChange={(v) => { contentRef.current = v }} preset="richField" />
 * // 提交时读 contentRef.current
 *
 * // ✅ 文档编辑（含图片上传）
 * <RichTextEditor value={html} onChange={setHtml} preset="document" uploadEndpoint="/api/upload" />
 *
 * // ✅ 评论输入（含 @mention）
 * <RichTextEditor value={html} onChange={setHtml} preset="chatter" onMentionSearch={searchUsers} />
 * ```
 */

"use client"

import { LexicalComposer } from "@lexical/react/LexicalComposer"
import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { ContentEditable } from "@lexical/react/LexicalContentEditable"
import { LexicalErrorBoundary } from "@lexical/react/LexicalErrorBoundary"
import { HistoryPlugin } from "@lexical/react/LexicalHistoryPlugin"
import { LinkPlugin } from "@lexical/react/LexicalLinkPlugin"
import { ListPlugin } from "@lexical/react/LexicalListPlugin"
import { RichTextPlugin } from "@lexical/react/LexicalRichTextPlugin"
import { useEffect, useRef, useState } from "react"

import { cn } from "@/lib/utils/cn"
import { htmlToEditorState } from "../converters/html"
import { markdownToEditorState } from "../converters/markdown"
import { allNodes } from "../lib/nodes"
import { editorTheme } from "../lib/theme"
import { AIWritePlugin } from "../plugins/AIWritePlugin"
import { DraggableBlockPlugin } from "../plugins/DraggableBlockPlugin"
import { FloatingToolbarPlugin } from "../plugins/FloatingToolbarPlugin"
import { ImagePlugin } from "../plugins/ImagePlugin"
import { MentionPlugin } from "../plugins/MentionPlugin"
import { OnChangePlugin } from "../plugins/OnChangePlugin"
import { SlashMenuPlugin } from "../plugins/SlashMenuPlugin"
import { ToolbarPlugin } from "../plugins/ToolbarPlugin"
import { type PresetName, presets } from "../presets"
import type { RichTextEditorProps } from "../types"

export function RichTextEditor({
  value = "",
  onChange,
  placeholder = "输入内容...",
  disabled = false,
  error,
  minHeight = 200,
  preset: presetName = "richField",
  mode = "html",
  uploadEndpoint,
  onMentionSearch,
  resizable = false,
  fill = false,
  className
}: RichTextEditorProps) {
  const uid = useRef(`${presetName}-${Math.random().toString(36).slice(2)}`)
  const initialConfig = {
    namespace: `rte-${uid.current}`,
    theme: editorTheme,
    nodes: allNodes,
    editable: !disabled,
    onError: (_err: Error) => {} // TODO: 接入错误上报（Sentry）或至少 console.error
  }

  const isInitialized = useRef(false)

  return (
    <div
      className={cn(
        "space-y-1",
        resizable && "resize-y overflow-hidden",
        fill && "h-full",
        className
      )}
    >
      <LexicalComposer initialConfig={initialConfig}>
        <EditorInner
          preset={presetName}
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          disabled={disabled}
          minHeight={minHeight}
          mode={mode}
          fill={fill}
          uploadEndpoint={uploadEndpoint}
          onMentionSearch={onMentionSearch}
          isInitialized={isInitialized}
        />
      </LexicalComposer>
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}

/** 内部组件——在 LexicalComposer 上下文内 */
function EditorInner({
  preset: presetName,
  value,
  onChange,
  placeholder,
  disabled,
  minHeight,
  mode = "html",
  uploadEndpoint,
  onMentionSearch,
  fill,
  isInitialized
}: RichTextEditorProps & {
  preset: PresetName
  isInitialized: React.MutableRefObject<boolean>
}) {
  const preset = presets[presetName]
  const [editor] = useLexicalComposerContext()
  const [anchorElem, setAnchorElem] = useState<HTMLElement | null>(null)

  // 初始值注入（按 mode 选择转换器）
  useEffect(() => {
    if (isInitialized.current || !value) return
    isInitialized.current = true
    if (mode === "markdown" || mode === "plaintext") {
      markdownToEditorState(editor, value)
    } else {
      htmlToEditorState(editor, value)
    }
  }, [editor, value, mode, isInitialized])

  // disabled 状态同步
  useEffect(() => {
    editor.setEditable(!disabled)
  }, [editor, disabled])

  return (
    <div
      className={cn("rounded-md border", disabled && "opacity-60", fill && "flex h-full flex-col")}
    >
      {/* 工具栏 */}
      {preset.showToolbar && !disabled && (
        <ToolbarPlugin features={preset.toolbarFeatures} uploadEndpoint={uploadEndpoint} />
      )}

      {/* 编辑区 */}
      <div className={cn("relative", fill && "flex-1")} ref={(el) => setAnchorElem(el)}>
        <RichTextPlugin
          contentEditable={
            <ContentEditable
              className={cn(
                "w-full rounded-b-md py-2 text-sm outline-none",
                !preset.showToolbar && "rounded-md",
                preset.draggable && !disabled ? "px-7" : "px-3",
                fill && "h-full"
              )}
              style={fill ? undefined : { minHeight }}
              aria-disabled={disabled}
            />
          }
          placeholder={
            <div
              className={cn(
                "pointer-events-none absolute top-2 text-muted-foreground text-sm",
                preset.draggable && !disabled ? "left-7" : "left-3"
              )}
            >
              {placeholder}
            </div>
          }
          ErrorBoundary={LexicalErrorBoundary}
        />
      </div>

      {/* 插件 */}
      <HistoryPlugin />
      <ListPlugin />
      <LinkPlugin />
      {onChange && <OnChangePlugin onChange={onChange} mode={mode} />}
      {preset.image && <ImagePlugin uploadEndpoint={uploadEndpoint} />}
      {preset.mention && <MentionPlugin onSearch={onMentionSearch} />}
      {preset.slashMenu && !disabled && <SlashMenuPlugin />}
      {preset.draggable && !disabled && anchorElem && (
        <DraggableBlockPlugin anchorElem={anchorElem} />
      )}
      {preset.toolbarFeatures.includes("ai") && !disabled && <AIWritePlugin />}
      {preset.floatingToolbar && !disabled && <FloatingToolbarPlugin />}
    </div>
  )
}
