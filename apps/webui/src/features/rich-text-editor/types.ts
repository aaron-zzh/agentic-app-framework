/**
 * 富文本编辑器类型定义
 * @author AaronZZH & Kiro
 */

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

export interface RichTextEditorProps {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
  disabled?: boolean
  error?: string
  minHeight?: number
  preset?: import("./presets").PresetName
  /** 序列化模式：html（默认）/ markdown / plaintext */
  mode?: EditorMode
  /** 图片上传端点（document preset 用） */
  uploadEndpoint?: string
  /** mention 用户搜索（chatter preset 用） */
  onMentionSearch?: (query: string) => Promise<MentionUser[]>
  /** 允许用户拖拽调整编辑器高度 */
  resizable?: boolean
  /** 根容器额外 className */
  className?: string
  /** 撑满父容器高度（flex-1 场景） */
  fill?: boolean
}
