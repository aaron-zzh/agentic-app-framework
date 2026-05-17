/**
 * OnChangePlugin——编辑器内容变更时回调 HTML
 * @author AaronZZH & Kiro
 *
 * 使用 @lexical/react 内置 OnChangePlugin，editorState 回调内调用 $generateHtmlFromNodes 是安全的
 */

"use client"

import { $generateHtmlFromNodes } from "@lexical/html"
import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { OnChangePlugin as LexicalOnChangePlugin } from "@lexical/react/LexicalOnChangePlugin"
import type { EditorState } from "lexical"

interface OnChangePluginProps {
  onChange: (html: string) => void
}

export function OnChangePlugin({ onChange }: OnChangePluginProps) {
  const [editor] = useLexicalComposerContext()

  const handleChange = (editorState: EditorState) => {
    editorState.read(() => {
      onChange($generateHtmlFromNodes(editor))
    })
  }

  return <LexicalOnChangePlugin onChange={handleChange} ignoreSelectionChange />
}
