/**
 * 权限类型——实体级和字段级权限控制
 * @author AaronZZH & Kiro
 */

/** 实体级权限（后端根据当前用户计算后返回） */
export interface EntityAccess {
  read: boolean
  create: boolean
  update: boolean
  delete: boolean
  fieldAccess?: Record<string, FieldAccess>
}

/** 字段级权限 */
export interface FieldAccess {
  /** 是否可见（false = 不渲染） */
  visible: boolean
  /** 是否可编辑（false = 只读展示） */
  editable: boolean
}
