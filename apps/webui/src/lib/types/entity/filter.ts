/**
 * 筛选条件类型——列表视图筛选器使用
 * @author AaronZZH & Kiro
 */

/** 筛选条件 */
export interface FilterCondition {
  field: string
  operator: string
  value: string
}
