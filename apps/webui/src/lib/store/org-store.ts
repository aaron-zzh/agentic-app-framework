/**
 * 组织上下文 Store——统一管理当前组织与工作区选择。
 *
 * 组织和工作区作为一个原子上下文持久化；切换时先同步请求 Header，再更新状态。
 * 组织和工作区列表仍由 TanStack Query 管理，不复制到 Zustand。
 *
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { persist } from "zustand/middleware"
import { setBackendOrgContext } from "@/lib/api/rest/backend-client"
import type { OrganizationVO } from "@/lib/api/rest/user"

export const ALL_ORGANIZATIONS_ID = "all"
export const ALL_WORKSPACES_ID = "all"

export interface WorkspaceSelection {
  id: string
  name: string
  orgId: string
  logo?: string
}

export function hasSuperAdminRole(roles?: string[]): boolean {
  return roles?.some((role) => role.toLowerCase() === "super_admin") ?? false
}

interface OrgState {
  /** 当前选中的组织 ID；all 表示当前用户可访问组织的聚合只读视角 */
  currentOrgId: string | null
  /** 当前选中的具体工作区；null 表示组织共享范围 */
  currentWorkspace: WorkspaceSelection | null
  /** 原子切换组织与工作区，并同步两个请求 Header */
  setOrgContext: (orgId: string, workspace: WorkspaceSelection | null) => void
  /** 清除组织与工作区上下文及请求 Header */
  clearOrgContext: () => void
  /**
   * 确保存在有效的当前组织范围。
   *
   * super_admin 或加入多个组织的用户可保留 all；单组织用户回退到具体组织。
   */
  ensureDefaultOrg: (orgs: OrganizationVO[], roles?: string[]) => void
}

export const useOrgStore = create<OrgState>()(
  persist(
    (set, get) => ({
      currentOrgId: null,
      currentWorkspace: null,
      setOrgContext: (orgId, workspace) => {
        if (workspace != null && workspace.orgId !== orgId) {
          throw new Error("工作区不属于目标组织")
        }
        setBackendOrgContext(orgId, workspace?.id ?? null)
        set({ currentOrgId: orgId, currentWorkspace: workspace })
      },
      clearOrgContext: () => {
        setBackendOrgContext(null, null)
        set({ currentOrgId: null, currentWorkspace: null })
      },
      ensureDefaultOrg: (orgs, roles) => {
        const { currentOrgId, currentWorkspace, setOrgContext } = get()
        if (
          currentOrgId === ALL_ORGANIZATIONS_ID &&
          (hasSuperAdminRole(roles) || orgs.length > 1)
        ) {
          if (currentWorkspace != null) setOrgContext(ALL_ORGANIZATIONS_ID, null)
          return
        }
        if (orgs.length === 0) return
        const stillValid =
          currentOrgId != null && orgs.some((organization) => organization.id === currentOrgId)
        if (stillValid) {
          if (currentWorkspace != null && currentWorkspace.orgId !== currentOrgId) {
            setOrgContext(currentOrgId, null)
          }
          return
        }
        setOrgContext(orgs[0].id, null)
      }
    }),
    {
      name: "aaf-org-context",
      partialize: (state) => ({
        currentOrgId: state.currentOrgId,
        currentWorkspace: state.currentWorkspace
      }),
      onRehydrateStorage: () => (state) => {
        setBackendOrgContext(
          state?.currentOrgId ?? null,
          state?.currentWorkspace?.id ?? null
        )
      }
    }
  )
)

const initialContext = useOrgStore.getState()
setBackendOrgContext(initialContext.currentOrgId, initialContext.currentWorkspace?.id ?? null)
