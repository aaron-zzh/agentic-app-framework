/**
 * 成长任务 API 与 TanStack Query Hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { invalidateCreditQueries } from "@/lib/api/rest/billing/credits"

import { backendApi } from "../backend-client"

export interface GrowthTaskVO {
  id: number
  code: string
  name: string
  description?: string
  icon?: string
  category: "ONBOARDING" | "DAILY" | "ACHIEVEMENT"
  triggerEvent?: string
  targetCount: number
  rewardCredits: number
  rewardOutfit?: string
  sortOrder: number
  userProgress: number
  userStatus: "PENDING" | "COMPLETED" | "CLAIMED"
  userCompletedTime?: string
  userClaimedTime?: string
}

const GROWTH_TASK_KEY = ["user", "growth", "tasks"] as const

export const growthApi = {
  listTasks: (): Promise<GrowthTaskVO[]> => backendApi.get("/user/growth/tasks"),
  claimTask: (taskId: number): Promise<void> =>
    backendApi.post(`/user/growth/tasks/${taskId}/claim`, {})
}

export function useGrowthTasks() {
  return useQuery({
    queryKey: GROWTH_TASK_KEY,
    queryFn: growthApi.listTasks
  })
}

export function useClaimGrowthTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: growthApi.claimTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: GROWTH_TASK_KEY })
      invalidateCreditQueries(queryClient)
    }
  })
}
