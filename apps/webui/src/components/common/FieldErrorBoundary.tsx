/**
 * FieldErrorBoundary（Layer 3）——字段级错误边界
 * @author AaronZZH & Kiro
 */

"use client"

import { Component, type ReactNode } from "react"

interface Props {
  children: ReactNode
  fieldName?: string
}

interface State {
  error: Error | null
}

/** 字段级错误边界——单个字段组件报错时降级为文本展示 */
export class FieldErrorBoundary extends Component<Props, State> {
  override state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  override render() {
    if (this.state.error) {
      return (
        <div className="space-y-1 rounded border border-destructive/30 bg-destructive/5 p-2">
          <p className="text-destructive text-xs">
            字段{this.props.fieldName ? `"${this.props.fieldName}"` : ""}渲染异常
          </p>
          <button
            type="button"
            onClick={() => this.setState({ error: null })}
            className="text-muted-foreground text-xs underline"
          >
            重试
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
