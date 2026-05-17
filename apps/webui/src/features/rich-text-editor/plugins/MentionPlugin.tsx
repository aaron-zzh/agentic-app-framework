/**
 * MentionPlugin——@提及用户（chatter preset 用）
 * @author AaronZZH & Kiro
 *
 * 输入 @ 后弹出用户搜索下拉，选择后插入 @用户名 文本
 */

"use client"

import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { $getSelection, $isRangeSelection, TextNode } from "lexical"
import { useCallback, useEffect, useRef, useState } from "react"

interface MentionUser {
  id: string
  name: string
  avatar?: string
}

interface MentionPluginProps {
  /** 搜索用户，返回匹配列表 */
  onSearch?: (query: string) => Promise<MentionUser[]>
}

export function MentionPlugin({ onSearch }: MentionPluginProps) {
  const [editor] = useLexicalComposerContext()
  const [query, setQuery] = useState<string | null>(null)
  const [users, setUsers] = useState<MentionUser[]>([])
  const [anchorRect, setAnchorRect] = useState<DOMRect | null>(null)
  const triggerRef = useRef<TextNode | null>(null)

  // 监听输入，检测 @ 触发
  useEffect(() => {
    return editor.registerUpdateListener(({ editorState }) => {
      editorState.read(() => {
        const selection = $getSelection()
        if (!$isRangeSelection(selection)) {
          setQuery(null)
          return
        }

        const anchor = selection.anchor
        const node = anchor.getNode()
        if (!(node instanceof TextNode)) {
          setQuery(null)
          return
        }

        const text = node.getTextContent().slice(0, anchor.offset)
        const atIdx = text.lastIndexOf("@")
        if (atIdx === -1) {
          setQuery(null)
          return
        }

        const q = text.slice(atIdx + 1)
        // @ 后有空格则关闭
        if (q.includes(" ")) {
          setQuery(null)
          return
        }

        triggerRef.current = node
        setQuery(q)

        // 获取光标位置
        const domSelection = window.getSelection()
        if (domSelection?.rangeCount) {
          setAnchorRect(domSelection.getRangeAt(0).getBoundingClientRect())
        }
      })
    })
  }, [editor])

  // 搜索用户
  useEffect(() => {
    if (query === null || !onSearch) {
      setUsers([])
      return
    }
    onSearch(query).then(setUsers)
  }, [query, onSearch])

  const insertMention = useCallback(
    (user: MentionUser) => {
      editor.update(() => {
        const node = triggerRef.current
        if (!node) return
        const text = node.getTextContent()
        const atIdx = text.lastIndexOf("@")
        // 替换 @query 为 @用户名
        node.setTextContent(`${text.slice(0, atIdx)}@${user.name}`)
        node.selectEnd()
      })
      setQuery(null)
    },
    [editor]
  )

  if (query === null || !users.length || !anchorRect) return null

  return (
    <div
      className="fixed z-50 min-w-40 rounded-md border bg-popover p-1 shadow-md"
      style={{ top: anchorRect.bottom + 4, left: anchorRect.left }}
    >
      {users.map((u) => (
        <div
          key={u.id}
          role="option"
          aria-selected={false}
          tabIndex={0}
          className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm hover:bg-accent"
          onClick={() => insertMention(u)}
          onKeyDown={(e) => e.key === "Enter" && insertMention(u)}
        >
          {u.avatar && (
            // biome-ignore lint/performance/noImgElement: 头像小图，next/image 不适合动态 URL
            <img src={u.avatar} alt={u.name} className="h-5 w-5 rounded-full" />
          )}
          <span>{u.name}</span>
        </div>
      ))}
    </div>
  )
}
