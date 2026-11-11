/**
 * useRoleNames——roleKey → 角色名映射，数据来自当前用户可用助理列表（TanStack Query）
 * @author AaronZZH & Kiro
 */
"use client"

import { useMemo } from "react"
import { useAssistants } from "@/lib/api/rest/ai"

export function useRoleNames(): (roleKey: string | undefined) => string | undefined {
  const { data: assistants } = useAssistants()
  const names = useMemo(() => {
    const map = new Map<string, string>()
    for (const assistant of assistants ?? []) {
      for (const role of assistant.roles ?? []) {
        if (!map.has(role.roleKey)) map.set(role.roleKey, role.name)
      }
    }
    return map
  }, [assistants])
  return (roleKey) => (roleKey ? names.get(roleKey) : undefined)
}
