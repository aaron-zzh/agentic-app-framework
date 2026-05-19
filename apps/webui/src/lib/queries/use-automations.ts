/**
 * 自动化规则 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { automationApi, type AutomationRuleInput } from "@/lib/api/automation"

const RULES_KEY = ["automation-rules"]
const LOGS_KEY = ["automation-logs"]

/** 查询规则列表 */
export function useAutomationRules(entitySlug?: string) {
  return useQuery({
    queryKey: [...RULES_KEY, entitySlug],
    queryFn: () => automationApi.list(entitySlug)
  })
}

/** 查询执行日志 */
export function useAutomationLogs(ruleId?: string) {
  return useQuery({
    queryKey: [...LOGS_KEY, ruleId],
    queryFn: () => automationApi.logs(ruleId)
  })
}

/** 创建规则 */
export function useCreateAutomationRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: AutomationRuleInput) => automationApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: RULES_KEY })
  })
}

/** 更新规则 */
export function useUpdateAutomationRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: AutomationRuleInput }) =>
      automationApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: RULES_KEY })
  })
}

/** 删除规则 */
export function useDeleteAutomationRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => automationApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: RULES_KEY })
  })
}

/** 启用/禁用规则 */
export function useToggleAutomationRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      automationApi.toggle(id, enabled),
    onSuccess: () => qc.invalidateQueries({ queryKey: RULES_KEY })
  })
}

/** 测试运行 */
export function useTestAutomationRule() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => automationApi.testRun(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: LOGS_KEY })
  })
}
