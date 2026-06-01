/**
 * 自动化规则 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

/** 触发器类型 */
export type TriggerType = "on_create" | "on_update" | "field_change" | "schedule" | "delay"

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

export const automationApi = {
  /** 获取规则列表 */
  list: (entitySlug?: string) =>
    backendApi.get<AutomationRule[]>("/automation/rules", { params: { entitySlug } }),

  /** 创建规则 */
  create: (data: AutomationRuleInput) =>
    backendApi.post<AutomationRule>("/automation/rules", data),

  /** 更新规则 */
  update: (id: string, data: AutomationRuleInput) =>
    backendApi.put<AutomationRule>(`/automation/rules/${id}`, data),

  /** 删除规则 */
  delete: (id: string) => backendApi.delete<void>(`/automation/rules/${id}`),

  /** 启用/禁用 */
  toggle: (id: string, enabled: boolean) =>
    backendApi.put<void>(`/automation/rules/${id}/toggle`, undefined, { params: { enabled } }),

  /** 测试运行 */
  testRun: (id: string) => backendApi.post<AutomationLog>(`/automation/rules/${id}/test`),

  /** 获取执行日志 */
  logs: (ruleId?: string) =>
    backendApi.get<AutomationLog[]>("/automation/logs", { params: { ruleId } })
}
