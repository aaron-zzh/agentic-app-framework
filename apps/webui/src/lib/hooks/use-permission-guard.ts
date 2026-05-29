/**
 * usePermissionGuard——基于 EntityAccess 的权限判断工具 hook
 *
 * 消费方式：
 * - canCreate / canUpdate / canDelete → 控制按钮显示
 * - isFieldVisible(fieldName) → 字段是否渲染
 * - isFieldEditable(fieldName) → 字段是否可编辑（false 时只读展示）
 *
 * @author AaronZZH & Kiro
 */

import { useMemo } from "react"
import type { EntityAccess } from "@/lib/api/permission"

export function usePermissionGuard(access: EntityAccess | undefined) {
  return useMemo(() => {
    const canCreate = access?.create ?? false
    const canUpdate = access?.update ?? false
    const canDelete = access?.delete ?? false

    /** 字段是否可见（无配置时默认可见） */
    function isFieldVisible(field: string): boolean {
      return access?.fieldAccess[field]?.visible ?? true
    }

    /** 字段是否可编辑（无配置时跟随实体 update 权限） */
    function isFieldEditable(field: string): boolean {
      return access?.fieldAccess[field]?.editable ?? canUpdate
    }

    return { canCreate, canUpdate, canDelete, isFieldVisible, isFieldEditable }
  }, [access])
}
