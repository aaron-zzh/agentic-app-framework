/**
 * 竖向审批设计器类型定义——钉钉/飞书风格
 * @author AaronZZH
 */

/** 条件表达式 */
export interface ConditionExpression {
  field: string
  operator: "EQ" | "NEQ" | "GT" | "GTE" | "LT" | "LTE" | "IN" | "CONTAINS"
  value: string
}

/** 条件组（支持嵌套） */
export interface ConditionGroup {
  logic: "AND" | "OR"
  conditions: ConditionExpression[]
  groups: ConditionGroup[]
}

/** 审批流节点类型 */
export type ApprovalNodeType = "start" | "approver" | "cc" | "condition" | "end"

/** 审批流分支 */
export interface ApprovalFlowBranch {
  id: string
  name: string
  /** 条件表达式 */
  condition?: ConditionGroup
  /** 分支内的第一个节点 */
  child?: ApprovalFlowNode
}

/** 审批流节点 */
export interface ApprovalFlowNode {
  id: string
  type: ApprovalNodeType
  name: string
  config: Record<string, unknown>
  /** 条件分支时的子分支 */
  branches?: ApprovalFlowBranch[]
  /** 下一个节点 */
  next?: ApprovalFlowNode
}

/** 抄送时机 */
export type CcTiming = "ON_SUBMIT" | "ON_APPROVE"

/** 表单字段类型 */
export type FormFieldType = "text" | "number" | "date" | "select" | "textarea" | "file"

/** 表单字段定义 */
export interface FormFieldDef {
  id: string
  name: string
  label: string
  type: FormFieldType
  required: boolean
  options?: string[]
}

/** 表单模板 */
export interface FormTemplate {
  id: string
  name: string
  fields: FormFieldDef[]
  createdAt?: string
  updatedAt?: string
}
