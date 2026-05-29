/**
 * 行级数据权限规则 API 客户端
 * @author AaronZZH & Kiro
 */

import { ApiError } from "./client"

/** 条件操作符 */
export type ConditionOperator = "eq" | "ne" | "gt" | "lt" | "in"

/** 规则效果 */
export type RuleEffect = "filter" | "deny"

/** 数据权限条件 */
export interface DataAccessCondition {
  field: string
  operator: ConditionOperator
  value: string
}

/** 数据权限规则 */
export interface DataAccessRule {
  id: string
  entitySlug: string
  roles: string[]
  condition: DataAccessCondition
  effect: RuleEffect
}

/** 创建/更新请求体 */
export interface DataAccessRuleInput {
  entitySlug: string
  roles: string[]
  condition: DataAccessCondition
  effect: RuleEffect
}

const BASE = process.env.NEXT_PUBLIC_API_URL ?? "/api"
const PATH = "/admin/data-access-rules"

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

export const dataAccessApi = {
  /** 获取所有规则 */
  list: () => req<DataAccessRule[]>(PATH),

  /** 创建规则 */
  create: (data: DataAccessRuleInput) =>
    req<DataAccessRule>(PATH, { method: "POST", body: JSON.stringify(data) }),

  /** 更新规则 */
  update: (id: string, data: DataAccessRuleInput) =>
    req<DataAccessRule>(`${PATH}/${id}`, { method: "PUT", body: JSON.stringify(data) }),

  /** 删除规则 */
  delete: (id: string) => req<void>(`${PATH}/${id}`, { method: "DELETE" })
}
