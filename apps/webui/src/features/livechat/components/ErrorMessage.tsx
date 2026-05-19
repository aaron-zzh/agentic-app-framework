/**
 * ErrorMessage——错误消息渲染组件
 * 展示错误类型（网络/模型/配额）和重试按钮
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { AlertCircle, Ban, RefreshCw, Server, Wifi } from "lucide-react"

export type ErrorType = "network" | "model" | "quota" | "unknown"

interface ErrorMessageProps {
  /** 错误类型 */
  type?: ErrorType
  /** 错误详情 */
  message?: string
  /** 重试回调 */
  onRetry?: () => void
}

const ERROR_CONFIG: Record<
  ErrorType,
  { icon: typeof AlertCircle; label: string; description: string }
> = {
  network: {
    icon: Wifi,
    label: "网络错误",
    description: "无法连接到服务器，请检查网络连接"
  },
  model: {
    icon: Server,
    label: "模型错误",
    description: "AI 模型服务暂时不可用"
  },
  quota: {
    icon: Ban,
    label: "配额超限",
    description: "请求次数已达上限，请稍后再试"
  },
  unknown: {
    icon: AlertCircle,
    label: "未知错误",
    description: "发生了意外错误"
  }
}

/**
 * 错误消息
 * 根据错误类型展示对应图标和描述，提供重试按钮
 */
export function ErrorMessage({ type = "unknown", message, onRetry }: ErrorMessageProps) {
  const config = ERROR_CONFIG[type]
  const Icon = config.icon

  return (
    <div className="my-2 flex items-start gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm">
      <Icon className="mt-0.5 size-4 shrink-0 text-destructive" />
      <div className="flex-1">
        <p className="font-medium text-destructive">{config.label}</p>
        <p className="mt-0.5 text-muted-foreground">{message ?? config.description}</p>
      </div>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="inline-flex shrink-0 items-center gap-1 rounded-md border border-border px-2 py-1 text-xs hover:bg-muted"
        >
          <RefreshCw className="size-3" />
          重试
        </button>
      )}
    </div>
  )
}
