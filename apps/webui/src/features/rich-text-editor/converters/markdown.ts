/**
 * Markdown ↔ EditorState 序列化工具
 * @author AaronZZH & Kiro
 */

import {
  $convertFromMarkdownString,
  $convertToMarkdownString,
  BOLD_ITALIC_STAR,
  BOLD_ITALIC_UNDERSCORE,
  BOLD_STAR,
  BOLD_UNDERSCORE,
  CODE,
  HEADING,
  ITALIC_STAR,
  ITALIC_UNDERSCORE,
  LINK,
  ORDERED_LIST,
  QUOTE,
  STRIKETHROUGH,
  UNORDERED_LIST,
} from "@lexical/markdown"
import type { LexicalEditor } from "lexical"

/** 启用的 Markdown 转换规则（与 allNodes 注册的节点对齐） */
export const MARKDOWN_TRANSFORMERS = [
  HEADING,
  QUOTE,
  UNORDERED_LIST,
  ORDERED_LIST,
  CODE,
  BOLD_ITALIC_STAR,
  BOLD_ITALIC_UNDERSCORE,
  BOLD_STAR,
  BOLD_UNDERSCORE,
  ITALIC_STAR,
  ITALIC_UNDERSCORE,
  STRIKETHROUGH,
  LINK,
]

/** Markdown 字符串 → 写入编辑器 */
export function markdownToEditorState(editor: LexicalEditor, markdown: string): void {
  editor.update(() => {
    $convertFromMarkdownString(markdown, MARKDOWN_TRANSFORMERS)
  })
}

/** EditorState → Markdown 字符串 */
export function editorStateToMarkdown(editor: LexicalEditor): string {
  return editor.getEditorState().read(() =>
    $convertToMarkdownString(MARKDOWN_TRANSFORMERS)
  )
}
