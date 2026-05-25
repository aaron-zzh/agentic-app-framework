/**
 * useAction hook——执行 API 调用并管理状态。
 * 通用 Action 执行器，配合 ActionButton/ActionDialog 使用。
 */

"use client"

import { useState } from "react"
import { request } from "@/lib/api/client"

export interface ActionConfig {
  /** 唯一标识 */
  id: string
  /** 按钮文本 */
  label: string
  /** 类型：dialog（弹窗表单）/ api_call（直接调用）/ redirect */
  type: "dialog" | "api_call" | "redirect"
  /** API 路径（如 "/v1/api-keys"） */
  api: string
  /** HTTP 方法 */
  method?: "POST" | "PUT" | "DELETE"
  /** 弹窗表单字段定义 */
  form?: Record<string, FieldConfig>
  /** 确认提示（api_call 类型时弹确认框） */
  confirm?: string
  /** 成功后行为 */
  onSuccess?: "refresh" | "showOnce" | "toast"
  /** 默认上下文（自动填入表单的隐藏字段） */
  context?: Record<string, unknown>
}

export interface FieldConfig {
  type: "string" | "number" | "select" | "hidden"
  label?: string
  required?: boolean
  placeholder?: string
  options?: string[]
  defaultValue?: unknown
}

export interface ActionResult {
  success: boolean
  data?: Record<string, unknown>
  error?: string
}

export function useAction(config: ActionConfig) {
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<ActionResult | null>(null)

  async function execute(formData?: Record<string, unknown>): Promise<ActionResult> {
    setLoading(true)
    setResult(null)

    try {
      // 合并上下文和表单数据
      const body = { ...config.context, ...formData }
      const data = await request<Record<string, unknown>>(config.api, {
        method: config.method ?? "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      })
      const res: ActionResult = { success: true, data }
      setResult(res)
      return res
    } catch (err) {
      const res: ActionResult = {
        success: false,
        error: err instanceof Error ? err.message : "操作失败",
      }
      setResult(res)
      return res
    } finally {
      setLoading(false)
    }
  }

  function reset() {
    setResult(null)
  }

  return { execute, loading, result, reset }
}
