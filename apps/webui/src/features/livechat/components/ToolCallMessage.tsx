/**
 * ToolCallMessage——工具调用消息渲染组件
 * 展示 Agent 工具调用的状态、名称、参数和结果
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { CheckCircle2, Loader2, Wrench } from "lucide-react"

interface ToolCallMessageProps {
  /** 工具名称 */
  name: string
  /** 工具参数（JSON 字符串） */
  args?: string
  /** 工具执行结果 */
  result?: string
  /** 是否正在执行 */
  isRunning?: boolean
}

/**
 * 工具调用消息
 * 展示工具调用中/已完成状态，以及名称、参数、结果
 */
export function ToolCallMessage({ name, args, result, isRunning }: ToolCallMessageProps) {
  return (
    <div className="my-2 rounded-lg border border-border bg-muted/50 p-3 text-sm">
      {/* 状态头 */}
      <div className="flex items-center gap-2">
        {isRunning ? (
          <Loader2 className="size-4 animate-spin text-muted-foreground" />
        ) : (
          <CheckCircle2 className="size-4 text-green-600" />
        )}
        <Wrench className="size-3.5 text-muted-foreground" />
        <span className="font-medium">{name}</span>
        <span className="text-muted-foreground">{isRunning ? "调用中…" : "已完成"}</span>
      </div>

      {/* 参数 */}
      {args && (
        <details className="mt-2">
          <summary className="cursor-pointer text-muted-foreground text-xs">参数</summary>
          <pre className="mt-1 overflow-x-auto rounded bg-muted p-2 text-xs">
            {formatJson(args)}
          </pre>
        </details>
      )}

      {/* 结果 */}
      {result && (
        <details className="mt-2" open>
          <summary className="cursor-pointer text-muted-foreground text-xs">结果</summary>
          <pre className="mt-1 overflow-x-auto rounded bg-muted p-2 text-xs">
            {formatJson(result)}
          </pre>
        </details>
      )}
    </div>
  )
}

/** 尝试格式化 JSON 字符串，失败则原样返回 */
function formatJson(str: string): string {
  try {
    return JSON.stringify(JSON.parse(str), null, 2)
  } catch {
    return str
  }
}
