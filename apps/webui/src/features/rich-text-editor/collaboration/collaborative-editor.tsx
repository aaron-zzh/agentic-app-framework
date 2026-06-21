/**
 * CollaborativeEditor——基于 Lexical + Yjs 的协同富文本编辑器
 * @author AaronZZH & Kiro
 *
 * 使用 @lexical/yjs 的 CollaborationPlugin 替代 HistoryPlugin，
 * 通过 y-websocket 同步编辑状态，支持光标位置和用户颜色标识。
 *
 * @example
 * ```tsx
 * <CollaborativeEditor
 *   docId="doc-123"
 *   userId="user-1"
 *   userName="张三"
 *   preset="document"
 * />
 * ```
 */

"use client"

import { CollaborationPlugin } from "@lexical/react/LexicalCollaborationPlugin"
import { LexicalComposer } from "@lexical/react/LexicalComposer"
import { ContentEditable } from "@lexical/react/LexicalContentEditable"
import { LexicalErrorBoundary } from "@lexical/react/LexicalErrorBoundary"
import { LinkPlugin } from "@lexical/react/LexicalLinkPlugin"
import { ListPlugin } from "@lexical/react/LexicalListPlugin"
import { RichTextPlugin } from "@lexical/react/LexicalRichTextPlugin"
import type { Provider } from "@lexical/yjs"
import { useCallback, useRef, useState } from "react"
import type * as Y from "yjs"

import { cn } from "@/lib/utils/cn"
import { allNodes } from "../lib/nodes"
import { editorTheme } from "../lib/theme"
import { AIWritePlugin } from "../plugins/AIWritePlugin"
import { DraggableBlockPlugin } from "../plugins/DraggableBlockPlugin"
import { FloatingToolbarPlugin } from "../plugins/FloatingToolbarPlugin"
import { ImagePlugin } from "../plugins/ImagePlugin"
import { MentionPlugin } from "../plugins/MentionPlugin"
import { SlashMenuPlugin } from "../plugins/SlashMenuPlugin"
import { ToolbarPlugin } from "../plugins/ToolbarPlugin"
import { type PresetName, presets } from "../presets"
import type { MentionUser } from "../types"
import { createYjsProvider } from "./yjs-provider"

export interface CollaborativeEditorProps {
  docId: string
  userId: string
  userName: string
  preset?: PresetName
  placeholder?: string
  onMentionSearch?: (query: string) => Promise<MentionUser[]>
}

export function CollaborativeEditor({
  docId,
  userId,
  userName,
  preset: presetName = "document",
  placeholder = "输入内容...",
  onMentionSearch
}: CollaborativeEditorProps) {
  const preset = presets[presetName]
  const [anchorElem, setAnchorElem] = useState<HTMLElement | null>(null)

  const initialConfig = {
    namespace: `collab-${docId}`,
    theme: editorTheme,
    nodes: allNodes,
    editable: true,
    // CollaborationPlugin 管理编辑器状态，不需要 editorState 初始化
    editorState: null,
    onError: (_err: Error) => {}
  }

  // 缓存 provider 实例，避免重复创建
  const providerRef = useRef<Map<string, ReturnType<typeof createYjsProvider>>>(new Map())

  /** CollaborationPlugin 要求的 providerFactory */
  const providerFactory = useCallback(
    (id: string, yjsDocMap: Map<string, Y.Doc>): Provider => {
      const existing = providerRef.current.get(id)
      if (existing) {
        return existing.provider as unknown as Provider
      }

      const instance = createYjsProvider({ docId: id, userId, userName })
      yjsDocMap.set(id, instance.doc)
      providerRef.current.set(id, instance)

      return instance.provider as unknown as Provider
    },
    [userId, userName]
  )

  return (
    <div className="rounded-md border">
      <LexicalComposer initialConfig={initialConfig}>
        {/* 工具栏 */}
        {preset.showToolbar && <ToolbarPlugin features={preset.toolbarFeatures} />}

        {/* 编辑区 */}
        <div className="relative" ref={(el) => setAnchorElem(el)}>
          <RichTextPlugin
            contentEditable={
              <ContentEditable
                className={cn(
                  "w-full rounded-b-md py-2 text-sm outline-none",
                  !preset.showToolbar && "rounded-md",
                  preset.draggable ? "px-7" : "px-3"
                )}
                style={{ minHeight: 200 }}
              />
            }
            placeholder={
              <div className="pointer-events-none absolute top-2 left-3 text-muted-foreground text-sm">
                {placeholder}
              </div>
            }
            ErrorBoundary={LexicalErrorBoundary}
          />
        </div>

        {/* 协同插件（替代 HistoryPlugin） */}
        <CollaborationPlugin id={docId} providerFactory={providerFactory} shouldBootstrap={false} />

        {/* 其他插件 */}
        <ListPlugin />
        <LinkPlugin />
        {preset.image && <ImagePlugin />}
        {preset.mention && <MentionPlugin onSearch={onMentionSearch} />}
        {preset.slashMenu && <SlashMenuPlugin />}
        {preset.draggable && anchorElem && <DraggableBlockPlugin anchorElem={anchorElem} />}
        {preset.toolbarFeatures.includes("ai") && <AIWritePlugin />}
        {preset.floatingToolbar && <FloatingToolbarPlugin />}
      </LexicalComposer>
    </div>
  )
}
