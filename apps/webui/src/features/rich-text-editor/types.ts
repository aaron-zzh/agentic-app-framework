/**
 * 富文本编辑器类型定义
 * @author AaronZZH & Kiro
 */
import type React from "react"

export type ToolbarFeature =
  | "format"
  | "heading"
  | "list"
  | "link"
  | "code"
  | "quote"
  | "history"
  | "ai"
  | "image"

export interface MentionUser {
  id: string
  name: string
  avatar?: string
}

/** 编辑器序列化模式：html（默认）/ markdown / plaintext */
export type EditorMode = "html" | "markdown" | "plaintext"

/** 通过 ref 暴露的编辑器命令句柄 */
export interface RichTextEditorHandle {
  /** 在当前光标位置插入文本（流式场景使用） */
  insertText: (text: string) => void
  /** 清空编辑器内容 */
  clear: () => void
  /** 读取当前内容（按指定格式序列化） */
  getContent: (mode: "html" | "markdown") => string
  /** 写入内容（不重建编辑器，保留 undo 历史之前的状态可撤销） */
  setValue: (text: string, mode?: "html" | "markdown") => void
}

export interface RichTextEditorProps {
  ref?: React.Ref<RichTextEditorHandle>
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
  disabled?: boolean
  error?: string
  minHeight?: number
  preset?: import("./presets").PresetName
  /** 序列化模式：html（默认）/ markdown / plaintext */
  mode?: EditorMode
  /** 初始值的格式（不传则与 mode 一致） */
  initialValueMode?: EditorMode
  /** mention 用户搜索（chatter preset 用） */
  onMentionSearch?: (query: string) => Promise<MentionUser[]>
  /** 允许用户拖拽调整编辑器高度 */
  resizable?: boolean
  /** 根容器额外 className */
  className?: string
  /** 撑满父容器高度（flex-1 场景） */
  fill?: boolean
  /** 去掉外层边框 */
  noBorder?: boolean
}
