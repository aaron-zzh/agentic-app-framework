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

export interface RichTextEditorProps {
  value?: string
  onChange?: (html: string) => void
  placeholder?: string
  disabled?: boolean
  error?: string
  minHeight?: number
  preset?: import("./presets").PresetName
  /** 图片上传端点（document preset 用） */
  uploadEndpoint?: string
  /** mention 用户搜索（chatter preset 用） */
  onMentionSearch?: (query: string) => Promise<MentionUser[]>
}
