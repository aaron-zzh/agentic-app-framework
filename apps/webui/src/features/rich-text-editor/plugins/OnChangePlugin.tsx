/**
 * OnChangePlugin——编辑器内容变更时回调（支持 html / markdown / plaintext）
 * @author AaronZZH & Kiro
 */

"use client"

import { $generateHtmlFromNodes } from "@lexical/html"
import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { OnChangePlugin as LexicalOnChangePlugin } from "@lexical/react/LexicalOnChangePlugin"
import { $getRoot, type EditorState } from "lexical"
import { MARKDOWN_TRANSFORMERS } from "../converters/markdown"
import { $convertToMarkdownString } from "@lexical/markdown"
import type { EditorMode } from "../types"

interface OnChangePluginProps {
  onChange: (value: string) => void
  mode?: EditorMode
}

export function OnChangePlugin({ onChange, mode = "html" }: OnChangePluginProps) {
  const [editor] = useLexicalComposerContext()

  const handleChange = (editorState: EditorState) => {
    editorState.read(() => {
      if (mode === "markdown") {
        onChange($convertToMarkdownString(MARKDOWN_TRANSFORMERS))
      } else if (mode === "plaintext") {
        onChange($getRoot().getTextContent())
      } else {
        onChange($generateHtmlFromNodes(editor))
      }
    })
  }

  return <LexicalOnChangePlugin onChange={handleChange} ignoreSelectionChange />
}
