/**
 * ToolbarPlugin——工具栏状态同步与格式操作
 * @author AaronZZH & Kiro
 */

"use client"

import { $createCodeNode, $isCodeNode } from "@lexical/code"
import { $isLinkNode, TOGGLE_LINK_COMMAND } from "@lexical/link"
import {
  $isListNode,
  INSERT_ORDERED_LIST_COMMAND,
  INSERT_UNORDERED_LIST_COMMAND,
  ListNode
} from "@lexical/list"
import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { $createHeadingNode, $createQuoteNode, $isHeadingNode } from "@lexical/rich-text"
import { $setBlocksType } from "@lexical/selection"
import { $findMatchingParent, $getNearestNodeOfType, mergeRegister } from "@lexical/utils"
import {
  $createParagraphNode,
  $getSelection,
  $insertNodes,
  $isRangeSelection,
  $isRootOrShadowRoot,
  CAN_REDO_COMMAND,
  CAN_UNDO_COMMAND,
  COMMAND_PRIORITY_CRITICAL,
  FORMAT_TEXT_COMMAND,
  REDO_COMMAND,
  SELECTION_CHANGE_COMMAND,
  UNDO_COMMAND
} from "lexical"
import { Sparkles } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useImageUpload } from "@/lib/hooks/use-image-upload"
import { cn } from "@/lib/utils/cn"
import { $createImageNode } from "../nodes/ImageNode"
import { OPEN_AI_WRITE_COMMAND } from "./AIWritePlugin"

type BlockType = "paragraph" | "h1" | "h2" | "h3" | "quote" | "code" | "ul" | "ol"

interface ToolbarState {
  bold: boolean
  italic: boolean
  underline: boolean
  blockType: BlockType
  isLink: boolean
  canUndo: boolean
  canRedo: boolean
}

interface ToolbarPluginProps {
  /** 显示哪些工具按钮 */
  features: (
    | "format"
    | "heading"
    | "list"
    | "link"
    | "code"
    | "quote"
    | "history"
    | "ai"
    | "image"
  )[]
  /** 图片上传接口 */
  uploadEndpoint?: string
}

export function ToolbarPlugin({ features, uploadEndpoint }: ToolbarPluginProps) {
  const [editor] = useLexicalComposerContext()
  const [state, setState] = useState<ToolbarState>({
    bold: false,
    italic: false,
    underline: false,
    blockType: "paragraph",
    isLink: false,
    canUndo: false,
    canRedo: false
  })

  const updateToolbar = useCallback(() => {
    const selection = $getSelection()
    if (!$isRangeSelection(selection)) return

    // 在 read 上下文内提前计算所有值
    const bold = selection.hasFormat("bold")
    const italic = selection.hasFormat("italic")
    const underline = selection.hasFormat("underline")
    const isLink = $isLinkNode(selection.anchor.getNode().getParent())

    const anchorNode = selection.anchor.getNode()
    let element =
      anchorNode.getKey() === "root"
        ? anchorNode
        : $findMatchingParent(anchorNode, (e) => {
            const parent = e.getParent()
            return parent !== null && $isRootOrShadowRoot(parent)
          })
    if (element === null) element = anchorNode.getTopLevelElementOrThrow()

    let blockType: BlockType = "paragraph"
    if ($isListNode(element)) {
      const parentList = $getNearestNodeOfType<ListNode>(anchorNode, ListNode)
      const type = parentList ? parentList.getListType() : element.getListType()
      blockType = type === "bullet" ? "ul" : "ol"
    } else if ($isHeadingNode(element)) {
      blockType = element.getTag() as BlockType
    } else if ($isCodeNode(element)) {
      blockType = "code"
    }

    // read 上下文外再 setState
    setState((prev) => ({ ...prev, bold, italic, underline, isLink, blockType }))
  }, [])

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
        COMMAND_PRIORITY_CRITICAL
      ),
      editor.registerCommand(
        CAN_UNDO_COMMAND,
        (v) => {
          setState((p) => ({ ...p, canUndo: v }))
          return false
        },
        COMMAND_PRIORITY_CRITICAL
      ),
      editor.registerCommand(
        CAN_REDO_COMMAND,
        (v) => {
          setState((p) => ({ ...p, canRedo: v }))
          return false
        },
        COMMAND_PRIORITY_CRITICAL
      )
    )
  }, [editor, updateToolbar])

  // 设置块类型
  const setBlockType = useCallback(
    (type: BlockType) => {
      editor.update(() => {
        const selection = $getSelection()
        if (!$isRangeSelection(selection)) return
        if (type === "ul") {
          editor.dispatchCommand(INSERT_UNORDERED_LIST_COMMAND, undefined)
        } else if (type === "ol") {
          editor.dispatchCommand(INSERT_ORDERED_LIST_COMMAND, undefined)
        } else if (type === "code") {
          $setBlocksType(selection, () => $createCodeNode())
        } else if (type === "quote") {
          $setBlocksType(selection, () => $createQuoteNode())
        } else if (type === "paragraph") {
          $setBlocksType(selection, () => $createParagraphNode())
        } else {
          $setBlocksType(selection, () => $createHeadingNode(type))
        }
      })
    },
    [editor]
  )

  // 切换链接
  const toggleLink = useCallback(() => {
    if (state.isLink) {
      editor.dispatchCommand(TOGGLE_LINK_COMMAND, null)
    } else {
      const url = window.prompt("输入链接地址")
      if (url) editor.dispatchCommand(TOGGLE_LINK_COMMAND, { url })
    }
  }, [editor, state.isLink])

  return (
    <div className="flex flex-wrap items-center gap-0.5 rounded-t-md border border-b-0 bg-muted/30 px-2 py-1">
      {/* 撤销/重做 */}
      {features.includes("history") && (
        <>
          <ToolBtn
            title="撤销"
            active={false}
            disabled={!state.canUndo}
            onClick={() => editor.dispatchCommand(UNDO_COMMAND, undefined)}
          >
            ↩
          </ToolBtn>
          <ToolBtn
            title="重做"
            active={false}
            disabled={!state.canRedo}
            onClick={() => editor.dispatchCommand(REDO_COMMAND, undefined)}
          >
            ↪
          </ToolBtn>
          <Divider />
        </>
      )}

      {/* 标题 */}
      {features.includes("heading") && (
        <>
          <ToolBtn
            title="正文"
            active={state.blockType === "paragraph"}
            onClick={() => setBlockType("paragraph")}
          >
            T
          </ToolBtn>
          <ToolBtn
            title="标题1"
            active={state.blockType === "h1"}
            onClick={() => setBlockType("h1")}
          >
            H1
          </ToolBtn>
          <ToolBtn
            title="标题2"
            active={state.blockType === "h2"}
            onClick={() => setBlockType("h2")}
          >
            H2
          </ToolBtn>
          <ToolBtn
            title="标题3"
            active={state.blockType === "h3"}
            onClick={() => setBlockType("h3")}
          >
            H3
          </ToolBtn>
          <Divider />
        </>
      )}

      {/* 文本格式 */}
      {features.includes("format") && (
        <>
          <ToolBtn
            title="粗体 (⌘B)"
            active={state.bold}
            onClick={() => editor.dispatchCommand(FORMAT_TEXT_COMMAND, "bold")}
          >
            <strong>B</strong>
          </ToolBtn>
          <ToolBtn
            title="斜体 (⌘I)"
            active={state.italic}
            onClick={() => editor.dispatchCommand(FORMAT_TEXT_COMMAND, "italic")}
          >
            <em>I</em>
          </ToolBtn>
          <ToolBtn
            title="下划线 (⌘U)"
            active={state.underline}
            onClick={() => editor.dispatchCommand(FORMAT_TEXT_COMMAND, "underline")}
          >
            <span className="underline">U</span>
          </ToolBtn>
          <Divider />
        </>
      )}

      {/* 列表 */}
      {features.includes("list") && (
        <>
          <ToolBtn
            title="无序列表"
            active={state.blockType === "ul"}
            onClick={() => setBlockType("ul")}
          >
            ≡
          </ToolBtn>
          <ToolBtn
            title="有序列表"
            active={state.blockType === "ol"}
            onClick={() => setBlockType("ol")}
          >
            1.
          </ToolBtn>
          <Divider />
        </>
      )}

      {/* 引用 */}
      {features.includes("quote") && (
        <ToolBtn
          title="引用"
          active={state.blockType === "quote"}
          onClick={() => setBlockType("quote")}
        >
          ❝
        </ToolBtn>
      )}

      {/* 代码块 */}
      {features.includes("code") && (
        <ToolBtn
          title="代码块"
          active={state.blockType === "code"}
          onClick={() => setBlockType("code")}
        >
          &lt;/&gt;
        </ToolBtn>
      )}

      {/* 链接 */}
      {features.includes("link") && (
        <ToolBtn title="链接" active={state.isLink} onClick={toggleLink}>
          🔗
        </ToolBtn>
      )}

      {/* 图片 */}
      {features.includes("image") && <ImageUploadButton uploadEndpoint={uploadEndpoint} />}

      {/* AI 写作 */}
      {features.includes("ai") && (
        <>
          <Divider />
          <ToolBtn
            title="AI 写作 (✨)"
            active={false}
            onClick={() => editor.dispatchCommand(OPEN_AI_WRITE_COMMAND, undefined)}
          >
            <Sparkles className="h-3 w-3" />
          </ToolBtn>
        </>
      )}
    </div>
  )
}

function ToolBtn({
  children,
  title,
  active,
  disabled,
  onClick
}: {
  children: React.ReactNode
  title: string
  active: boolean
  disabled?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "h-7 min-w-7 rounded px-1 text-xs transition-colors",
        active ? "bg-accent text-accent-foreground" : "hover:bg-muted",
        disabled && "cursor-not-allowed opacity-40"
      )}
    >
      {children}
    </button>
  )
}

function Divider() {
  return <div className="mx-0.5 h-4 w-px bg-border" />
}

/** 图片上传按钮——点击触发文件选择（使用通用 useImageUpload） */
function ImageUploadButton({ uploadEndpoint }: { uploadEndpoint?: string }) {
  const [editor] = useLexicalComposerContext()
  const { upload } = useImageUpload({
    usePresign: !uploadEndpoint,
    uploadEndpoint: uploadEndpoint ?? "/api/upload"
  })

  const handleChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    e.target.value = ""

    const result = await upload(file)
    editor.update(() => {
      $insertNodes([$createImageNode(result.url, result.name)])
    })
  }

  return (
    <label
      title="插入图片"
      className="flex h-7 w-7 cursor-pointer items-center justify-center rounded px-1 text-xs hover:bg-muted"
    >
      🖼
      <input type="file" accept="image/*" className="hidden" onChange={handleChange} />
    </label>
  )
}
