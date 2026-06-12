/**
 * PromptInput——提示词输入框
 *
 * 功能：
 * - 基于 Lexical minimal 模式的纯文本输入
 * - 项目提示词以可关闭标签形式嵌入编辑器头部
 * - 字数统计（不计标签内容）
 * - 点击标签弹出 Popover 预览完整内容
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { LexicalComposer } from "@lexical/react/LexicalComposer"
import { useLexicalComposerContext } from "@lexical/react/LexicalComposerContext"
import { ContentEditable } from "@lexical/react/LexicalContentEditable"
import { LexicalErrorBoundary } from "@lexical/react/LexicalErrorBoundary"
import { PlainTextPlugin } from "@lexical/react/LexicalPlainTextPlugin"
import {
  $createParagraphNode,
  $createTextNode,
  $getRoot,
  DecoratorNode,
  type LexicalNode,
  type NodeKey,
  type SerializedLexicalNode
} from "lexical"
import { X } from "lucide-react"
import { type JSX, useCallback, useEffect, useRef, useState } from "react"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { editorTheme } from "@/features/rich-text-editor/lib/theme"
import { OnChangePlugin } from "@/features/rich-text-editor/plugins/OnChangePlugin"
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
        // biome-ignore lint/suspicious/noExplicitAny: Lexical 内部 nodeMap API
        const node = (editor.getEditorState() as any)._nodeMap.get(nodeKey) as
          | LexicalNode
          | undefined
        node?.remove()
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

/** 外部 value → 编辑器内容同步（仅在 value 变化且非用户输入时覆写） */
function ExternalValuePlugin({ value }: { value: string }) {
  const [editor] = useLexicalComposerContext()
  const prevRef = useRef("")

  useEffect(() => {
    if (value === prevRef.current) return
    prevRef.current = value
    // 读取编辑器当前纯文本，相同则跳过（用户输入已触发 onChange → store，无需再写回）
    const currentText = editor.getEditorState().read(() => $getRoot().getTextContent())
    if (currentText === value) return
    editor.update(() => {
      const root = $getRoot()
      for (const child of root.getChildren()) {
        if (!(child instanceof ProjectPromptNode)) child.remove()
      }
      const p = $createParagraphNode()
      p.append($createTextNode(value))
      root.append(p)
    })
  }, [editor, value])

  return null
}

function CharCountPlugin({ onCount }: { onCount: (n: number) => void }) {
  const [editor] = useLexicalComposerContext()

  useEffect(() => {
    return editor.registerUpdateListener(({ editorState }) => {
      editorState.read(() => {
        const root = $getRoot()
        let count = 0
        for (const child of root.getAllTextNodes()) {
          count += child.getTextContent().length
        }
        onCount(count)
      })
    })
  }, [editor, onCount])

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

      // 移除所有旧 ProjectPromptNode
      const toRemove: ProjectPromptNode[] = []
      root.getChildren().forEach((child) => {
        if (child instanceof ProjectPromptNode)
          toRemove.push(child)
          // biome-ignore lint/suspicious/noExplicitAny: Lexical paragraph children
        ;(child as any).getChildren?.()?.forEach((c: LexicalNode) => {
          if (c instanceof ProjectPromptNode) toRemove.push(c)
        })
      })
      for (const n of toRemove) n.remove()

      if (!projectPrompt?.content.trim()) return

      let firstChild = root.getFirstChild()
      if (!firstChild) {
        const para = $createParagraphNode()
        root.append(para)
        firstChild = para
      }

      const tagNode = $createProjectPromptNode(
        projectPrompt.label,
        projectPrompt.content,
        onDismissRef.current
      )
      // biome-ignore lint/suspicious/noExplicitAny: Lexical paragraph API
      const firstTextChild = (firstChild as any).getFirstChild?.()
      if (firstTextChild) {
        firstTextChild.insertBefore(tagNode)
      } else {
        // biome-ignore lint/suspicious/noExplicitAny: Lexical paragraph append
        ;(firstChild as any).append(tagNode)
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
  /** 用户关闭项目提示词标签时回调 */
  onDismissProjectPrompt?: () => void
  /** 最大字数限制（0 = 不限制） */
  maxLength?: number
  className?: string
  minHeight?: number
}

export function PromptInput({
  value: _value,
  onChange,
  placeholder = "描述你想生成的内容...",
  projectPrompt = null,
  onDismissProjectPrompt,
  maxLength = 500,
  className,
  minHeight = 100
}: PromptInputProps) {
  const [charCount, setCharCount] = useState(0)
  const [dismissed, setDismissed] = useState(false)
  const handleDismiss = useCallback(() => {
    setDismissed(true)
    onDismissProjectPrompt?.()
  }, [onDismissProjectPrompt])

  // 项目提示词内容变化时重置 dismissed
  const promptKey = `${projectPrompt?.label}::${projectPrompt?.content}`
  const prevPromptKeyRef = useRef(promptKey)
  if (prevPromptKeyRef.current !== promptKey) {
    prevPromptKeyRef.current = promptKey
    if (dismissed) setDismissed(false)
  }

  const activePrompt = dismissed ? null : projectPrompt

  const initialConfig = {
    namespace: `prompt-input-${Math.random().toString(36).slice(2)}`,
    theme: editorTheme,
    nodes: [ProjectPromptNode],
    onError: (_err: Error) => {}
  }

  return (
    <LexicalComposer initialConfig={initialConfig}>
      <div
        className={cn(
          "flex flex-col rounded-md border bg-background focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-0",
          className
        )}
      >
        {/* 编辑区 */}
        <div className="relative flex-1">
          <PlainTextPlugin
            contentEditable={
              <ContentEditable
                className="w-full px-3 py-2 text-sm outline-none"
                style={{ minHeight }}
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
              onClick={() => setDismissed(false)}
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
        <OnChangePlugin onChange={onChange} mode="plaintext" />
        <ExternalValuePlugin value={_value} />
        <CharCountPlugin onCount={setCharCount} />
        <ProjectPromptPlugin projectPrompt={activePrompt} onDismiss={handleDismiss} />
      </div>
    </LexicalComposer>
  )
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
