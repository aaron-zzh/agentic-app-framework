"use client"

/**
 * IMEFixPlugin——修复中文/日文输入法首字符被提前插入的问题。
 *
 * 原因：Chrome 在 compositionstart 之前触发 keydown，Lexical 在 keydown 时
 * 已经把字母插入编辑器，之后 IME 又把同样字母作为候选词显示，导致重复。
 *
 * 解法：在捕获阶段拦截 composing 状态的 keydown，阻止 Lexical 处理。
 */

import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { useEffect } from "react"

export function IMEFixPlugin() {
  const [editor] = useLexicalComposerContext()

  useEffect(() => {
    return editor.registerRootListener((rootElement, prevRootElement) => {
      const handler = (e: KeyboardEvent) => {
        if (e.isComposing) e.stopPropagation()
      }
      prevRootElement?.removeEventListener("keydown", handler, true)
      rootElement?.addEventListener("keydown", handler, true)
    })
  }, [editor])

  return null
}
