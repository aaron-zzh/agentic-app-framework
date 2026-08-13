/**
 * PromptInput——提示词输入框
 *
 * 功能：
 * - 基于 Lexical minimal 模式的纯文本输入
 * - 项目提示词以可关闭标签形式嵌入编辑器头部
 * - 字数统计（用户输入 + 项目提示词内容，与实际提交长度一致）
 * - 点击标签弹出 Popover 预览完整内容
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { LexicalComposer } from "@lexical/react/LexicalComposer"
import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { ContentEditable } from "@lexical/react/LexicalContentEditable"
import { LexicalErrorBoundary } from "@lexical/react/LexicalErrorBoundary"
import { HistoryPlugin } from "@lexical/react/LexicalHistoryPlugin"
import { PlainTextPlugin } from "@lexical/react/LexicalPlainTextPlugin"
import {
  $createParagraphNode,
  $createTextNode,
  $getNodeByKey,
  $getRoot,
  $isElementNode,
  COMMAND_PRIORITY_HIGH,
  DecoratorNode,
  type ElementNode,
  KEY_ENTER_COMMAND,
  type LexicalNode,
  type NodeKey,
  PASTE_COMMAND,
  type SerializedLexicalNode
} from "lexical"
import { X } from "lucide-react"
import { type JSX, useCallback, useEffect, useRef, useState } from "react"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { editorTheme } from "@/features/rich-text-editor/lib/theme"
import { cn } from "@/lib/utils/cn"

// ─────────────────────────────────────────────────────────────────────────────
// ProjectPromptNode：自定义 Lexical 装饰节点，渲染为标签
// ─────────────────────────────────────────────────────────────────────────────

interface SerializedProjectPromptNode extends SerializedLexicalNode {
  label: string
  content: string
}

// 模块级回调 map，存各节点的 onDismiss（函数不能放入 Lexical 节点）
const _dismissCallbacks = new Map<NodeKey, () => void>()

export class ProjectPromptNode extends DecoratorNode<JSX.Element> {
  __label: string
  __content: string

  static override getType(): string {
    return "project-prompt"
  }

  static override clone(node: ProjectPromptNode): ProjectPromptNode {
    return new ProjectPromptNode(node.__label, node.__content, node.__key)
  }

  static override importJSON(serializedNode: SerializedLexicalNode): ProjectPromptNode {
    const node = serializedNode as SerializedProjectPromptNode
    return new ProjectPromptNode(node.label, node.content)
  }

  constructor(label: string, content: string, key?: NodeKey) {
    super(key)
    this.__label = label
    this.__content = content
  }

  override exportJSON(): SerializedProjectPromptNode {
    return {
      type: "project-prompt",
      version: 1,
      label: this.__label,
      content: this.__content
    }
  }

  override createDOM(): HTMLElement {
    const span = document.createElement("span")
    span.style.display = "inline-flex"
    span.style.userSelect = "none"
    return span
  }

  override updateDOM(): boolean {
    return false
  }

  override isInline(): boolean {
    return true
  }

  getContent(): string {
    return this.__content
  }

  override getTextContent(): string {
    return this.__content
  }

  override decorate(): JSX.Element {
    return <ProjectPromptTag label={this.__label} content={this.__content} nodeKey={this.__key} />
  }
}

function $createProjectPromptNode(
  label: string,
  content: string,
  onDismiss?: () => void
): ProjectPromptNode {
  const node = new ProjectPromptNode(label, content)
  if (onDismiss) _dismissCallbacks.set(node.__key, onDismiss)
  return node
}

// ─────────────────────────────────────────────────────────────────────────────
// 标签渲染组件（含 Popover 预览 + 关闭按钮）
// ─────────────────────────────────────────────────────────────────────────────

function ProjectPromptTag({
  label: _label,
  content,
  nodeKey
}: {
  label: string
  content: string
  nodeKey: NodeKey
}) {
  const [editor] = useLexicalComposerContext()

  const handleRemove = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation()
      const onDismiss = _dismissCallbacks.get(nodeKey)
      editor.update(() => {
        $getNodeByKey(nodeKey)?.remove()
        _dismissCallbacks.delete(nodeKey)
      })
      onDismiss?.()
    },
    [editor, nodeKey]
  )

  return (
    <Popover>
      <PopoverTrigger
        nativeButton={false}
        render={
          <span
            className="mr-1 inline-flex cursor-pointer items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 font-medium text-primary text-xs ring-1 ring-primary/30 hover:bg-primary/20"
            contentEditable={false}
            suppressContentEditableWarning
          >
            <span className="max-w-[120px] truncate">{content}</span>
            <span className="shrink-0 opacity-60">{content.length}字</span>
            <button
              type="button"
              onClick={handleRemove}
              className="ml-0.5 rounded-full p-0.5 hover:bg-primary/20"
              aria-label="移除项目提示词"
            >
              <X className="size-3" />
            </button>
          </span>
        }
      />
      <PopoverContent side="top" className="max-w-xs p-3" align="start">
        <p className="mb-1 font-medium text-muted-foreground text-xs">项目提示词</p>
        <p className="max-h-48 overflow-y-auto whitespace-pre-wrap text-sm">{content}</p>
      </PopoverContent>
    </Popover>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 字数统计插件
// ─────────────────────────────────────────────────────────────────────────────

/** 递归提取纯文本，排除 ProjectPromptNode（tag 内容不属于用户输入） */
function getPlainTextExcludingTag(root: LexicalNode): string {
  let text = ""
  const visit = (node: LexicalNode) => {
    if (node instanceof ProjectPromptNode) return
    if ($isElementNode(node)) {
      for (const child of node.getChildren()) visit(child)
    } else {
      text += node.getTextContent()
    }
  }
  visit(root)
  return text
}

/** 外部 value → 编辑器内容同步（仅在 value 变化且非用户输入时覆写） */
function ExternalValuePlugin({ value }: { value: string }) {
  const [editor] = useLexicalComposerContext()
  const prevRef = useRef("")

  useEffect(() => {
    if (value === prevRef.current) return
    prevRef.current = value
    // 读取编辑器当前纯文本，排除 tag 内容后与外部 value 比较，相同则跳过
    const currentText = editor.getEditorState().read(() => getPlainTextExcludingTag($getRoot()))
    if (currentText === value) return
    editor.update(() => {
      const root = $getRoot()
      // 保留含 ProjectPromptNode 的段落，仅清空其中的纯文本节点；移除其余段落
      let tagParagraph: ElementNode | null = null
      for (const child of root.getChildren()) {
        const hasTag =
          $isElementNode(child) &&
          child.getChildren().some((nested) => nested instanceof ProjectPromptNode)
        if (hasTag && !tagParagraph && $isElementNode(child)) {
          tagParagraph = child
          for (const nested of child.getChildren()) {
            if (!(nested instanceof ProjectPromptNode)) nested.remove()
          }
        } else {
          child.remove()
        }
      }
      if (tagParagraph) {
        if (value) tagParagraph.append($createTextNode(value))
      } else {
        const p = $createParagraphNode()
        if (value) p.append($createTextNode(value))
        root.append(p)
      }
    })
  }, [editor, value])

  return null
}

/** 编辑器内容变化时回调用户纯文本（排除 tag 内容）+ 用户文本字数（不含 tag，由调用方叠加 tag 长度得到总数） */
function PlainTextChangePlugin({
  onChange,
  onCount
}: {
  onChange: (value: string) => void
  onCount: (n: number) => void
}) {
  const [editor] = useLexicalComposerContext()

  useEffect(() => {
    return editor.registerUpdateListener(({ editorState }) => {
      editorState.read(() => {
        const text = getPlainTextExcludingTag($getRoot())
        onChange(text)
        onCount(text.length)
      })
    })
  }, [editor, onChange, onCount])

  return null
}

// ─────────────────────────────────────────────────────────────────────────────
// 项目提示词注入插件
// ─────────────────────────────────────────────────────────────────────────────

function ProjectPromptPlugin({
  projectPrompt,
  onDismiss
}: {
  projectPrompt: { label: string; content: string } | null
  onDismiss: () => void
}) {
  const [editor] = useLexicalComposerContext()
  const onDismissRef = useRef(onDismiss)
  onDismissRef.current = onDismiss

  useEffect(() => {
    editor.update(() => {
      const root = $getRoot()

      // 移除所有旧 ProjectPromptNode（root 直接子节点及其子节点中的 tag）
      const toRemove: ProjectPromptNode[] = []
      for (const child of root.getChildren()) {
        if (child instanceof ProjectPromptNode) {
          toRemove.push(child)
          continue
        }
        if ($isElementNode(child)) {
          for (const nested of child.getChildren()) {
            if (nested instanceof ProjectPromptNode) toRemove.push(nested)
          }
        }
      }
      for (const n of toRemove) {
        _dismissCallbacks.delete(n.getKey())
        n.remove()
      }

      if (!projectPrompt?.content.trim()) return

      const firstChild = root.getFirstChild()
      let firstElement: ElementNode
      if ($isElementNode(firstChild)) {
        firstElement = firstChild
      } else {
        const paragraph = $createParagraphNode()
        if (firstChild) firstChild.insertBefore(paragraph)
        else root.append(paragraph)
        firstElement = paragraph
      }

      const tagNode = $createProjectPromptNode(
        projectPrompt.label,
        projectPrompt.content,
        onDismissRef.current
      )
      const firstTextChild = firstElement.getFirstChild()
      if (firstTextChild) {
        firstTextChild.insertBefore(tagNode)
      } else {
        firstElement.append(tagNode)
      }
    })
  }, [editor, projectPrompt])

  return null
}

// ─────────────────────────────────────────────────────────────────────────────
// PromptInput 主组件
// ─────────────────────────────────────────────────────────────────────────────

export interface PromptInputProps {
  /** 纯文本输入值（不含项目提示词） */
  value: string
  onChange: (value: string) => void
  placeholder?: string
  /** 项目提示词标签（null = 不展示） */
  projectPrompt?: { label: string; content: string } | null
  /** 项目提示词是否被移除（受控）；为 true 时隐藏标签并显示「再次添加」按钮 */
  dismissed?: boolean
  /** 移除状态变化回调（受控）；true=用户移除，false=用户恢复 */
  onDismissedChange?: (dismissed: boolean) => void
  /** Enter（非 Shift）触发提交 */
  onSubmit?: () => void
  /** 最大字数限制（用户输入 + 项目提示词内容总和，与实际提交长度一致；0 = 不限制） */
  maxLength?: number
  className?: string
  minHeight?: number
  maxHeight?: number
}

export function PromptInput({
  value: _value,
  onChange,
  placeholder = "描述你想生成的内容...",
  projectPrompt = null,
  dismissed = false,
  onDismissedChange,
  onSubmit,
  maxLength = 500,
  className,
  minHeight = 100,
  maxHeight
}: PromptInputProps) {
  const [userTextLength, setUserTextLength] = useState(0)
  const handleDismiss = useCallback(() => {
    onDismissedChange?.(true)
  }, [onDismissedChange])

  const activePrompt = dismissed ? null : projectPrompt
  // 总字数 = 用户输入 + 项目提示词内容（两者拼接后才是实际提交给模型的 prompt 长度）
  const charCount = userTextLength + (activePrompt?.content.trim().length ?? 0)

  const initialConfig = {
    namespace: `prompt-input-${Math.random().toString(36).slice(2)}`,
    theme: editorTheme,
    nodes: [ProjectPromptNode],
    onError: (_err: Error) => {}
  }

  return (
    <LexicalComposer initialConfig={initialConfig}>
      <div className={cn("flex min-h-0 flex-col rounded-md border bg-background", className)}>
        {/* 编辑区 */}
        <div className="relative min-h-0 flex-1">
          <PlainTextPlugin
            contentEditable={
              <ContentEditable
                className="h-full w-full overflow-y-auto px-3 py-2 text-sm outline-none"
                style={{ minHeight, maxHeight, overflowY: maxHeight ? "auto" : undefined }}
                aria-label="提示词输入框"
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

        {/* 底部：重新添加按钮 + 字数统计 */}
        <div className="flex items-center justify-between border-t px-3 py-1.5">
          {projectPrompt && dismissed ? (
            <button
              type="button"
              onClick={() => onDismissedChange?.(false)}
              className="text-primary/70 text-xs hover:text-primary"
            >
              + 项目提示词
            </button>
          ) : (
            <span />
          )}
          <span
            className={cn(
              "text-muted-foreground text-xs",
              maxLength > 0 && charCount > maxLength && "text-destructive"
            )}
          >
            {charCount}
            {maxLength > 0 && `/${maxLength}`}
          </span>
        </div>

        {/* 插件 */}
        <PlainTextChangePlugin onChange={onChange} onCount={setUserTextLength} />
        <ExternalValuePlugin value={_value} />
        <ProjectPromptPlugin projectPrompt={activePrompt} onDismiss={handleDismiss} />
        <PastePlugin />
        <HistoryPlugin />
        {onSubmit && <SubmitPlugin onSubmit={onSubmit} />}
      </div>
    </LexicalComposer>
  )
}

/** Enter（非 Shift）触发 onSubmit */
function SubmitPlugin({ onSubmit }: { onSubmit: () => void }) {
  const [editor] = useLexicalComposerContext()
  useEffect(() => {
    return editor.registerCommand(
      KEY_ENTER_COMMAND,
      (e: KeyboardEvent | null) => {
        if (e?.shiftKey) return false
        e?.preventDefault()
        onSubmit()
        return true
      },
      COMMAND_PRIORITY_HIGH
    )
  }, [editor, onSubmit])
  return null
}

/** 粘贴长文本（≥200字）时折叠为 ProjectPromptNode chip */
function PastePlugin() {
  const [editor] = useLexicalComposerContext()
  useEffect(() => {
    return editor.registerCommand(
      PASTE_COMMAND,
      (e: ClipboardEvent | InputEvent | KeyboardEvent) => {
        const text =
          e instanceof ClipboardEvent ? (e.clipboardData?.getData("text/plain") ?? "") : ""
        if (text.length < 200) return false
        e.preventDefault()
        editor.update(() => {
          const root = $getRoot()
          const firstChild = root.getFirstChild()
          let firstParagraph: ElementNode
          if ($isElementNode(firstChild)) {
            firstParagraph = firstChild
          } else {
            const paragraph = $createParagraphNode()
            if (firstChild) firstChild.insertBefore(paragraph)
            else root.append(paragraph)
            firstParagraph = paragraph
          }
          const chip = $createProjectPromptNode(`${text.slice(0, 8)}…`, text)
          firstParagraph.append(chip)
          chip.selectNext()
        })
        return true
      },
      COMMAND_PRIORITY_HIGH
    )
  }, [editor])
  return null
}

/**
 * 从用户输入和项目提示词拼接最终 prompt，提交生成任务时调用
 */
export function buildFinalPrompt(
  userPrompt: string,
  projectPrompt: { content: string } | null | undefined
): string {
  const parts: string[] = []
  if (projectPrompt?.content?.trim()) parts.push(projectPrompt.content.trim())
  if (userPrompt.trim()) parts.push(userPrompt.trim())
  return parts.join(", ")
}
