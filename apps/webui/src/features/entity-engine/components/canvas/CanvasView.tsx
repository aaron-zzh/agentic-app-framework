/**
 * 画板视图主组件——集成 tldraw 画板引擎
 * 提供无限画布、基础绘制工具、选择与变换等核心能力
 * @author AaronZZH & Kiro
 */

"use client"

import { type Editor, Tldraw } from "tldraw"
import { useCallback, useState } from "react"

import type { EntityDef } from "@/lib/types/entity"
import { CanvasAIPanel } from "./CanvasAIPanel"
import { CanvasCollaborators } from "./CanvasCollaborators"
import { CanvasExportButton } from "./CanvasExportButton"
import { useCanvasCollaboration } from "./use-canvas-collaboration"

export interface CanvasViewProps {
  entity: EntityDef
  /** 画板记录 ID（用于协作同步） */
  recordId?: string
}

/**
 * 画板视图——基于 tldraw 的无限画布
 * 支持：矩形/圆形/箭头/文本/自由画笔/便签 + 缩放/平移/网格对齐 + 多选/对齐/分布/分组
 */
export function CanvasView({ entity, recordId }: CanvasViewProps) {
  const [editor, setEditor] = useState<Editor | null>(null)
  const config = entity.canvasView
  const collaboration = config?.collaboration && recordId

  // 协作同步（Yjs CRDT）
  const { store, collaborators } = useCanvasCollaboration({
    enabled: !!collaboration,
    roomId: recordId ? `canvas-${entity.slug}-${recordId}` : undefined
  })

  const handleMount = useCallback((editorInstance: Editor) => {
    setEditor(editorInstance)
  }, [])

  return (
    <div className="relative h-full min-h-[600px] w-full">
      <Tldraw onMount={handleMount} store={collaboration ? store : undefined} />

      {/* 协作者头像列表 */}
      {collaboration && <CanvasCollaborators collaborators={collaborators} />}

      {/* AI 辅助面板 */}
      {config?.aiAssist && editor && <CanvasAIPanel editor={editor} entity={entity} />}

      {/* 导出按钮 */}
      {editor && (
        <CanvasExportButton editor={editor} formats={config?.exportFormats ?? ["png", "svg"]} />
      )}
    </div>
  )
}
