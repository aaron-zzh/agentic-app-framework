/**
 * ViewErrorBoundary（Layer 2）——视图级错误边界
 * @author AaronZZH & Kiro
 */

"use client"

import { Component, type ReactNode } from "react"

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  error: Error | null
}

/** 视图级错误边界——某个视图渲染失败时显示错误卡片 */
// biome-ignore lint/style/useReactFunctionComponents: ErrorBoundary 必须用 class 组件
export class ViewErrorBoundary extends Component<Props, State> {
  override state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  override render() {
    if (this.state.error) {
      return (
        this.props.fallback ?? (
          <div className="flex flex-col items-center justify-center gap-2 rounded-md border border-destructive/50 bg-destructive/5 p-8">
            <p className="font-medium text-destructive text-sm">视图渲染失败</p>
            <p className="text-muted-foreground text-xs">{this.state.error.message}</p>
            <button
              type="button"
              onClick={() => this.setState({ error: null })}
              className="mt-2 rounded-md border px-3 py-1.5 text-xs hover:bg-accent"
            >
              重试
            </button>
          </div>
        )
      )
    }
    return this.props.children
  }
}
