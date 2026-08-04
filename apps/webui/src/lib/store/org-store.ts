/**
 * 组织状态 Store——管理当前选中的组织范围
 *
 * 仅存储 currentOrgId（客户端 UI 状态），组织列表由 TanStack Query 管理。
 * 切换组织时外部需调用 queryClient.invalidateQueries() 刷新数据。
 *
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { persist } from "zustand/middleware"
import { setBackendOrgId } from "@/lib/api/rest/backend-client"
import type { OrganizationVO } from "@/lib/api/rest/user"

export const ALL_ORGANIZATIONS_ID = "all"
export const ALL_WORKSPACES_ID = "all"

export function hasSuperAdminRole(roles?: string[]): boolean {
  return roles?.some((role) => role.toLowerCase() === "super_admin") ?? false
}

interface OrgState {
  /** 当前选中的组织 ID；all 表示 super_admin 全组织只读视角 */
  currentOrgId: string | null
  /** 切换当前组织 */
  setCurrentOrgId: (orgId: string) => void
  /**
   * 确保存在有效的当前组织范围。
   *
   * super_admin 可保留 all；其他用户必须回退到所属组织列表中的具体组织。
   */
  ensureDefaultOrg: (orgs: OrganizationVO[], roles?: string[]) => void
}

export const useOrgStore = create<OrgState>()(
  persist(
    (set, get) => ({
      currentOrgId: null,
      setCurrentOrgId: (orgId) => {
        setBackendOrgId(orgId)
        set({ currentOrgId: orgId })
      },
      ensureDefaultOrg: (orgs, roles) => {
        const current = get().currentOrgId
        if (current === ALL_ORGANIZATIONS_ID && hasSuperAdminRole(roles)) return
        if (orgs.length === 0) return
        const stillValid = current != null && orgs.some((org) => org.id === current)
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
