/**
 * 自动化规则 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

/** 触发器类型 */
export type TriggerType =
  | "on_create"
  | "on_update"
  | "field_change"
  | "schedule"
  | "delay"

/** 触发器配置 */
export interface AutomationTrigger {
  type: TriggerType
  field?: string
  cron?: string
  delayDays?: number
}

/** 条件 */
export interface AutomationCondition {
  field: string
  operator: string
  value?: string
}

/** 操作类型 */
export type ActionType =
  | "update_field"
  | "send_notification"
  | "send_email"
  | "create_record"
  | "start_workflow"
  | "call_webhook"
  | "assign_user"

/** 操作 */
export interface AutomationAction {
  type: ActionType
  config: Record<string, unknown>
}

/** 自动化规则 */
export interface AutomationRule {
  id: string
  name: string
  entitySlug: string
  enabled: boolean
  trigger: AutomationTrigger
  conditions: AutomationCondition[]
  actions: AutomationAction[]
  createdAt: string
}

/** 创建/更新请求 */
export interface AutomationRuleInput {
  name: string
  entitySlug: string
  trigger: AutomationTrigger
  conditions: AutomationCondition[]
  actions: AutomationAction[]
}

/** 执行日志 */
export interface AutomationLog {
  id: string
  ruleId: string
  ruleName?: string
  triggerType: TriggerType
  status: "success" | "failed" | "skipped"
  errorMessage?: string
  executedAt: string
}

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"

async function req<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init
  })
  if (!res.ok) throw new ApiError(res.status, `请求失败: ${res.statusText}`)
  const json = await res.json()
  if (json.code !== 0) throw new ApiError(json.code, json.message ?? "未知错误")
  return json.data as T
}

export const automationApi = {
  /** 获取规则列表 */
  list: (entitySlug?: string) =>
    req<AutomationRule[]>(
      `/automation/rules${entitySlug ? `?entitySlug=${entitySlug}` : ""}`
    ),

  /** 创建规则 */
  create: (data: AutomationRuleInput) =>
    req<AutomationRule>("/automation/rules", {
      method: "POST",
      body: JSON.stringify(data)
    }),

  /** 更新规则 */
  update: (id: string, data: AutomationRuleInput) =>
    req<AutomationRule>(`/automation/rules/${id}`, {
      method: "PUT",
      body: JSON.stringify(data)
    }),

  /** 删除规则 */
  delete: (id: string) =>
    req<void>(`/automation/rules/${id}`, { method: "DELETE" }),

  /** 启用/禁用 */
  toggle: (id: string, enabled: boolean) =>
    req<void>(`/automation/rules/${id}/toggle?enabled=${enabled}`, {
      method: "PUT"
    }),

  /** 测试运行 */
  testRun: (id: string) =>
    req<AutomationLog>(`/automation/rules/${id}/test`, { method: "POST" }),

  /** 获取执行日志 */
  logs: (ruleId?: string) =>
    req<AutomationLog[]>(
      `/automation/logs${ruleId ? `?ruleId=${ruleId}` : ""}`
    )
}
