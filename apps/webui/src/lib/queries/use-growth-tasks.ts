/**
 * 成长任务 hooks
 * @author AaronZZH & Kiro
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { backendApi } from "@/lib/api/rest/backend-client"

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

const KEY = ["user", "growth", "tasks"] as const

export function useGrowthTasks() {
  return useQuery({
    queryKey: KEY,
    queryFn: () => backendApi.get<GrowthTaskVO[]>("/user/growth/tasks")
  })
}

export function useClaimGrowthTask() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (taskId: number) => backendApi.post<void>(`/user/growth/tasks/${taskId}/claim`, {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY })
      qc.invalidateQueries({ queryKey: ["credits", "balance"] })
    }
  })
}
