/**
 * FloatingToolbarPlugin——选中文字时显示悬浮工具栏
 * @author AaronZZH & Kiro
 *
 * 选中文字后在选区上方显示：AI / 粗体 / 斜体 / 链接
 */

"use client"

import { $isLinkNode, TOGGLE_LINK_COMMAND } from "@lexical/link"
import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { mergeRegister } from "@lexical/utils"
import {
  $getSelection,
  $isRangeSelection,
  COMMAND_PRIORITY_LOW,
  FORMAT_TEXT_COMMAND,
  SELECTION_CHANGE_COMMAND
} from "lexical"
import { Bold, Italic, Link, Sparkles } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { createPortal } from "react-dom"
import { cn } from "@/lib/utils/cn"
import { OPEN_AI_WRITE_COMMAND } from "./AIWritePlugin"

interface FloatingToolbarState {
  bold: boolean
  italic: boolean
  isLink: boolean
}

export function FloatingToolbarPlugin() {
  const [editor] = useLexicalComposerContext()
  const toolbarRef = useRef<HTMLDivElement>(null)
  const [mounted, setMounted] = useState(false)
  useEffect(() => setMounted(true), [])
  const [show, setShow] = useState(false)
  const [pos, setPos] = useState({ top: 0, left: 0 })
  const [state, setState] = useState<FloatingToolbarState>({
    bold: false,
    italic: false,
    isLink: false
  })

  const updateToolbar = useCallback(() => {
    // IME 输入中不显示工具栏
    if (editor.isComposing()) {
      setShow(false)
      return
    }
    // 在 read 上下文内获取 Lexical 状态
    const selection = $getSelection()
    if (!$isRangeSelection(selection) || selection.isCollapsed()) {
      setShow(false)
      return
    }
    const bold = selection.hasFormat("bold")
    const italic = selection.hasFormat("italic")
    const isLink = $isLinkNode(selection.anchor.getNode().getParent())
    setState({ bold, italic, isLink })

    // DOM 操作用 requestAnimationFrame 延迟，确保 DOM 已更新
    requestAnimationFrame(() => {
      const nativeSelection = window.getSelection()
      if (!nativeSelection || nativeSelection.rangeCount === 0) {
        setShow(false)
        return
      }
      const rect = nativeSelection.getRangeAt(0).getBoundingClientRect()
      if (!rect.width) {
        setShow(false)
        return
      }
      const toolbarWidth = toolbarRef.current?.offsetWidth ?? 160
      setPos({
        top: rect.top - 44, // fixed 定位，不需要加 scrollY
        left: Math.max(8, rect.left + rect.width / 2 - toolbarWidth / 2)
      })
      setShow(true)
    })
  }, [editor.isComposing])

  useEffect(() => {
    return mergeRegister(
      editor.registerUpdateListener(({ editorState }) => {
        editorState.read(() => updateToolbar())
      }),
      editor.registerCommand(
        SELECTION_CHANGE_COMMAND,
        () => {
          editor.getEditorState().read(() => updateToolbar())
          return false
        },
        COMMAND_PRIORITY_LOW
      )
    )
  }, [editor, updateToolbar])

  if (!mounted) return null

  return createPortal(
    // biome-ignore lint/a11y/noStaticElementInteractions: onMouseDown 仅阻止失焦，非交互元素
    <div
      ref={toolbarRef}
      className="fixed z-50 flex items-center gap-0.5 rounded-md border bg-popover p-1 shadow-md transition-opacity"
      style={{
        top: pos.top,
        left: pos.left,
        opacity: show ? 1 : 0,
        pointerEvents: show ? "auto" : "none"
      }}
      onMouseDown={(e) => e.preventDefault()}
    >
      {/* AI */}
      <FloatBtn
        title="AI 写作"
        onClick={() => editor.dispatchCommand(OPEN_AI_WRITE_COMMAND, undefined)}
        className="text-primary"
      >
        <Sparkles className="h-3.5 w-3.5" />
      </FloatBtn>

      <div className="mx-0.5 h-4 w-px bg-border" />

      {/* 粗体 */}
      <FloatBtn
        title="粗体"
        active={state.bold}
        onClick={() => editor.dispatchCommand(FORMAT_TEXT_COMMAND, "bold")}
      >
        <Bold className="h-3.5 w-3.5" />
      </FloatBtn>

      {/* 斜体 */}
      <FloatBtn
        title="斜体"
        active={state.italic}
        onClick={() => editor.dispatchCommand(FORMAT_TEXT_COMMAND, "italic")}
      >
        <Italic className="h-3.5 w-3.5" />
      </FloatBtn>

      {/* 链接 */}
      <FloatBtn
        title="链接"
        active={state.isLink}
        onClick={() => {
          if (state.isLink) {
            editor.dispatchCommand(TOGGLE_LINK_COMMAND, null)
          } else {
            const url = window.prompt("输入链接地址")
            if (url) editor.dispatchCommand(TOGGLE_LINK_COMMAND, { url })
          }
        }}
      >
        <Link className="h-3.5 w-3.5" />
      </FloatBtn>
    </div>,
    document.body
  )
}

function FloatBtn({
  children,
  title,
  active,
  onClick,
  className
}: {
  children: React.ReactNode
  title: string
  active?: boolean
  onClick: () => void
  className?: string
}) {
  return (
    <button
      type="button"
      title={title}
      onClick={onClick}
      className={cn(
        "flex h-7 w-7 items-center justify-center rounded transition-colors",
        active ? "bg-accent text-accent-foreground" : "hover:bg-accent",
        className
      )}
    >
      {children}
    </button>
  )
}
