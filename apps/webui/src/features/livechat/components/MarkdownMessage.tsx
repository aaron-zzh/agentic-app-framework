/**
 * MarkdownMessage——AI 消息 Markdown 渲染组件
 * 基于 @assistant-ui/react-streamdown 的 StreamdownTextPrimitive，
 * 支持流式渲染、代码高亮（shiki）、表格渲染、代码块复制
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { StreamdownTextPrimitive } from "@assistant-ui/react-streamdown"
import type { ComponentPropsWithoutRef } from "react"
import { CodeBlockCopyButton } from "./CodeBlockCopyButton"

/** 代码块包装器：添加复制按钮 */
function CodeBlockWrapper({
  children,
  ...props
}: ComponentPropsWithoutRef<"pre"> & { node?: unknown }) {
  // 从 children 中提取代码文本
  const codeText = extractTextFromChildren(children)

  return (
    <pre {...props} className="group/code relative">
      {children}
      <CodeBlockCopyButton code={codeText} />
    </pre>
  )
}

/** 递归提取子元素中的纯文本 */
function extractTextFromChildren(children: unknown): string {
  if (typeof children === "string") return children
  if (Array.isArray(children)) return children.map(extractTextFromChildren).join("")
  if (children && typeof children === "object" && "props" in children) {
    const element = children as { props?: { children?: unknown } }
    return extractTextFromChildren(element.props?.children ?? "")
  }
  return ""
}

/**
 * Markdown 消息渲染
 * 直接作为 assistant-ui 的 Text 组件使用，自动从 MessagePrimitive 上下文获取文本内容
 */
export function MarkdownMessage() {
  return (
    <StreamdownTextPrimitive
      shikiTheme={["github-light", "github-dark"]}
      containerClassName="prose prose-sm dark:prose-invert max-w-none prose-table:border-collapse prose-th:border prose-th:border-border prose-th:px-3 prose-th:py-2 prose-td:border prose-td:border-border prose-td:px-3 prose-td:py-2"
      components={{ Pre: CodeBlockWrapper }}
    />
  )
}
