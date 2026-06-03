/**
 * 行级数据权限规则 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

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

const PATH = "/admin/data-access-rules"

export const dataAccessApi = {
  /** 获取所有规则 */
  list: () => backendApi.get<DataAccessRule[]>(PATH),

  /** 创建规则 */
  create: (data: DataAccessRuleInput) => backendApi.post<DataAccessRule>(PATH, data),

  /** 更新规则 */
  update: (id: string, data: DataAccessRuleInput) =>
    backendApi.put<DataAccessRule>(`${PATH}/${id}`, data),

  /** 删除规则 */
  delete: (id: string) => backendApi.delete<void>(`${PATH}/${id}`)
}
