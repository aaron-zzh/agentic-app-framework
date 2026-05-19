/**
 * ContextInjector——上下文感知注入器
 * 收集页面上下文、选中文本，注入到对话 system prompt
 * @author AaronZZH & Kiro
 */

"use client"

import { MessageSquarePlus } from "lucide-react"
import { usePathname, useSearchParams } from "next/navigation"
import { useCallback, useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { useAIAwarenessStore } from "@/lib/store/ai-awareness-store"

/** 页面上下文数据 */
export interface PageContext {
  pathname: string
  entity?: string
  view?: string
  selectedText?: string
}

/**
 * 上下文收集 Hook
 * 自动同步路由信息到 ai-awareness-store
 */
export function useContextCollector(): PageContext {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const setPageContext = useAIAwarenessStore((s) => s.setPageContext)

  /** 从路由解析当前实体和视图 */
  const entity = extractEntity(pathname)
  const view = searchParams.get("view") ?? undefined

  useEffect(() => {
    setPageContext({
      currentEntity: entity,
      currentView: view
    })
  }, [entity, view, setPageContext])

  return { pathname, entity, view }
}

/** 从路径中提取实体名（/workspace/document → document） */
function extractEntity(pathname: string): string | undefined {
  const segments = pathname.split("/").filter(Boolean)
  const workspaceIdx = segments.indexOf("workspace")
  if (workspaceIdx >= 0 && segments.length > workspaceIdx + 1) {
    return segments[workspaceIdx + 1]
  }
  return undefined
}

/**
 * 构建注入到对话的上下文描述
 */
export function buildContextPrompt(ctx: PageContext): string {
  const parts: string[] = []
  if (ctx.entity) parts.push(`当前模块：${ctx.entity}`)
  if (ctx.view) parts.push(`当前视图：${ctx.view}`)
  if (ctx.selectedText) parts.push(`用户选中文本：「${ctx.selectedText}」`)
  if (parts.length === 0) return ""
  return `[页面上下文] ${parts.join("；")}`
}

/** SendToChat 浮动按钮 Props */
interface SendToChatButtonProps {
  /** 点击后将选中文本发送到对话 */
  onSendContext: (text: string) => void
}

/**
 * SendToChat 浮动按钮
 * 用户选中文本后浮现，点击将选中内容作为上下文发送到对话
 */
export function SendToChatButton({ onSendContext }: SendToChatButtonProps) {
  const [selection, setSelection] = useState<{ text: string; x: number; y: number } | null>(null)

  const handleMouseUp = useCallback(() => {
    const sel = window.getSelection()
    const text = sel?.toString().trim()
    if (text && text.length > 0) {
      const range = sel?.getRangeAt(0)
      const rect = range?.getBoundingClientRect()
      if (rect) {
        setSelection({ text, x: rect.left + rect.width / 2, y: rect.top - 40 })
      }
    } else {
      setSelection(null)
    }
  }, [])

  const handleMouseDown = useCallback(() => {
    setSelection(null)
  }, [])

  useEffect(() => {
    document.addEventListener("mouseup", handleMouseUp)
    document.addEventListener("mousedown", handleMouseDown)
    return () => {
      document.removeEventListener("mouseup", handleMouseUp)
      document.removeEventListener("mousedown", handleMouseDown)
    }
  }, [handleMouseUp, handleMouseDown])

  if (!selection) return null

  return (
    <div
      className="fade-in-0 zoom-in-95 fixed z-50 animate-in"
      style={{ left: selection.x, top: selection.y, transform: "translateX(-50%)" }}
    >
      <Button
        size="sm"
        variant="secondary"
        className="shadow-lg"
        onClick={() => {
          onSendContext(selection.text)
          setSelection(null)
        }}
      >
        <MessageSquarePlus className="size-3.5" />
        发送到对话
      </Button>
    </div>
  )
}

/**
 * ContextInjector 组件
 * 挂载后自动收集页面上下文并同步到 store
 */
export function ContextInjector({ onSendContext }: { onSendContext?: (text: string) => void }) {
  useContextCollector()

  return onSendContext ? <SendToChatButton onSendContext={onSendContext} /> : null
}
