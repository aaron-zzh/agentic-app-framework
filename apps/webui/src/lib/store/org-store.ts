/**
 * 组织状态 Store——管理当前选中的组织 ID
 *
 * 仅存储 currentOrgId（客户端 UI 状态），组织列表由 TanStack Query 管理。
 * 切换组织时外部需调用 queryClient.invalidateQueries() 刷新数据。
 *
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { persist } from "zustand/middleware"
import { setBackendOrgId } from "@/lib/api/rest/backend-client"

interface OrgState {
  /** 当前选中的组织 ID */
  currentOrgId: string | null
  /** 切换当前组织 */
  setCurrentOrgId: (orgId: string) => void
}

export const useOrgStore = create<OrgState>()(
  persist(
    (set) => ({
      currentOrgId: null,
      setCurrentOrgId: (orgId) => {
        setBackendOrgId(orgId)
        set({ currentOrgId: orgId })
      }
    }),
    {
      name: "aaf-org",
      onRehydrateStorage: () => (state) => {
        setBackendOrgId(state?.currentOrgId ?? null)
      }
    }
  )
)

setBackendOrgId(useOrgStore.getState().currentOrgId)
