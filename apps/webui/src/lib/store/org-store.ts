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
import type { OrganizationVO } from "@/lib/api/rest/user/organization"

interface OrgState {
  /** 当前选中的组织 ID */
  currentOrgId: string | null
  /** 切换当前组织 */
  setCurrentOrgId: (orgId: string) => void
  /**
   * 确保存在有效的当前组织。
   *
   * 登录成功后调用：若当前选中的组织仍在用户所属组织列表中，保持不变（尊重用户上次选择）；
   * 否则按优先级选取列表第一个组织作为默认值。组织列表为空时不做任何操作。
   */
  ensureDefaultOrg: (orgs: OrganizationVO[]) => void
}

export const useOrgStore = create<OrgState>()(
  persist(
    (set, get) => ({
      currentOrgId: null,
      setCurrentOrgId: (orgId) => {
        setBackendOrgId(orgId)
        set({ currentOrgId: orgId })
      },
      ensureDefaultOrg: (orgs) => {
        if (orgs.length === 0) return
        const current = get().currentOrgId
        const stillValid = current != null && orgs.some((o) => o.id === current)
        if (stillValid) return
        const defaultOrgId = orgs[0].id
        setBackendOrgId(defaultOrgId)
        set({ currentOrgId: defaultOrgId })
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
