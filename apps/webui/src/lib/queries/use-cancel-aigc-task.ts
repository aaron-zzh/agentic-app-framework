/**
 * useCancelAigcTask——取消 AIGC 任务
 * DELETE /api/aigc/tasks/{id}
 * @author AaronZZH & Kiro
 */

import { useMutation, useQueryClient } from "@tanstack/react-query"
import { request } from "@/lib/api/rest/entity/crud"

export function useCancelAigcTask() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (taskId: number) => request<void>(`/aigc/tasks/${taskId}`, { method: "DELETE" }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["aigc-tasks"] })
      qc.invalidateQueries({ queryKey: ["aigc", "tasks", "history"] })
    }
  })
}
