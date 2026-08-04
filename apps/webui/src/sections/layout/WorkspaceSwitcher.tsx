/**
 * WorkspaceSwitcher——组织与工作区范围切换器
 *
 * super_admin 支持三级只读范围：全部组织、组织内全部工作区、特定工作区；
 * 其他用户保持成员组织和具体工作区切换。服务端数据由 TanStack Query 管理，
 * Zustand 仅保存当前选择并同步请求头。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { Building2, ChevronsUpDown, Layers3, Network, Plus } from "lucide-react"
import { useMemo } from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { type OrganizationVO, useOrganizations, useWorkspaces } from "@/lib/api/rest/user"
import { useAuthStore } from "@/lib/store/auth-store"
import {
  ALL_ORGANIZATIONS_ID,
  ALL_WORKSPACES_ID,
  hasSuperAdminRole,
  useOrgStore
} from "@/lib/store/org-store"
import { useUIStore } from "@/lib/store/ui-store"
import { $url } from "@/lib/utils"

export function WorkspaceSwitcher() {
  const queryClient = useQueryClient()
  const roles = useAuthStore((state) => state.user?.roles)
  const isSuperAdmin = hasSuperAdminRole(roles)
  const currentOrgId = useOrgStore((state) => state.currentOrgId)
  const setCurrentOrgId = useOrgStore((state) => state.setCurrentOrgId)
  const currentWorkspace = useUIStore((state) => state.currentWorkspace)
  const setCurrentWorkspace = useUIStore((state) => state.setCurrentWorkspace)
  const { data: organizations } = useOrganizations()
  const orgs = organizations ?? []
  const allOrganizationsSelected = currentOrgId === ALL_ORGANIZATIONS_ID
  const canSelectAllOrganizations = isSuperAdmin || orgs.length > 1
  const { data: workspacePage } = useWorkspaces(
    currentOrgId,
    isSuperAdmin || allOrganizationsSelected
  )

  const workspacesByOrg = useMemo(() => {
    const grouped = new Map<string, NonNullable<typeof workspacePage>["list"]>()
    for (const workspace of workspacePage?.list ?? []) {
      const workspaces = grouped.get(workspace.orgId) ?? []
      workspaces.push(workspace)
      grouped.set(workspace.orgId, workspaces)
    }
    return grouped
  }, [workspacePage])

  const activeOrg = orgs.find((org) => org.id === currentOrgId)
  const activeWorkspaceName = allOrganizationsSelected
    ? "全部组织"
    : currentWorkspace?.id === ALL_WORKSPACES_ID
      ? `${activeOrg?.name ?? "组织"} · 全部工作区`
      : currentWorkspace
        ? `${activeOrg?.name ?? "组织"} · ${currentWorkspace.name}`
        : `${activeOrg?.name ?? "组织"} · 组织共享`

  function refreshScopeData() {
    void queryClient.invalidateQueries()
  }

  function selectAllOrganizations() {
    if (allOrganizationsSelected && currentWorkspace == null) return
    setCurrentWorkspace(null)
    setCurrentOrgId(ALL_ORGANIZATIONS_ID)
    refreshScopeData()
  }

  function canViewAllWorkspaces(org: OrganizationVO): boolean {
    return isSuperAdmin || org.memberRole === "owner" || org.memberRole === "admin"
  }

  function selectOrganization(org: OrganizationVO) {
    setCurrentOrgId(org.id)
    setCurrentWorkspace(
      canViewAllWorkspaces(org)
        ? { id: ALL_WORKSPACES_ID, name: "全部工作区", orgId: org.id }
        : null
    )
    refreshScopeData()
  }

  function selectWorkspace(orgId: string, workspaceId: string, workspaceName: string) {
    if (currentOrgId === orgId && currentWorkspace?.id === workspaceId) return
    setCurrentOrgId(orgId)
    setCurrentWorkspace({ id: workspaceId, name: workspaceName, orgId })
    refreshScopeData()
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <button
            type="button"
            className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-accent"
          />
        }
      >
        <Avatar className="size-6 rounded-md after:hidden">
          {!allOrganizationsSelected && (
            <AvatarImage
              src={activeOrg?.logo ?? $url.cdn("/assets/icons/ChatBc.png")}
              alt={activeWorkspaceName}
              className="object-cover"
            />
          )}
          <AvatarFallback className="rounded-md bg-primary/10 font-semibold text-primary text-xs">
            {allOrganizationsSelected ? "全" : (activeOrg?.name?.slice(0, 1) ?? "W")}
          </AvatarFallback>
        </Avatar>
        <span className="max-w-48 truncate font-medium text-sm">{activeWorkspaceName}</span>
        <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
      </DropdownMenuTrigger>

      <DropdownMenuContent align="start" className="w-72 overflow-hidden p-0">
        <div className="p-1.5">
          <DropdownMenuGroup>
            <DropdownMenuLabel>数据范围</DropdownMenuLabel>
            {canSelectAllOrganizations && (
              <DropdownMenuItem
                onClick={selectAllOrganizations}
                className="gap-2.5 rounded-md px-2 py-2"
              >
                <Building2 className="text-muted-foreground" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm">全部组织</div>
                  <div className="truncate text-muted-foreground text-xs">
                    {isSuperAdmin ? "平台全部组织只读视角" : "成员组织聚合只读视角"}
                  </div>
                </div>
                {allOrganizationsSelected && <Badge variant="outline">当前</Badge>}
              </DropdownMenuItem>
            )}

            {orgs.map((org) => {
              const workspaces = workspacesByOrg.get(org.id) ?? []
              const organizationWide = canViewAllWorkspaces(org)
              const organizationSelected =
                currentOrgId === org.id &&
                (organizationWide
                  ? currentWorkspace?.id === ALL_WORKSPACES_ID
                  : currentWorkspace == null)

              return (
                <div key={org.id}>
                  <DropdownMenuItem
                    onClick={() => selectOrganization(org)}
                    className="mt-1 gap-2.5 rounded-md px-2 py-2"
                  >
                    <Layers3 className="text-muted-foreground" />
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm">{org.name}</div>
                      <div className="truncate text-muted-foreground text-xs">
                        {organizationWide ? "全部工作区" : "组织共享"}
                      </div>
                    </div>
                    {organizationSelected && <Badge variant="outline">当前</Badge>}
                  </DropdownMenuItem>

                  {workspaces.map((workspace) => (
                    <DropdownMenuItem
                      key={workspace.id}
                      onClick={() => selectWorkspace(org.id, workspace.id, workspace.name)}
                      className="gap-2.5 rounded-md px-2 py-2 pl-6"
                    >
                      <Network className="text-muted-foreground" />
                      <div className="min-w-0 flex-1 truncate text-sm">{workspace.name}</div>
                      {currentOrgId === org.id && currentWorkspace?.id === workspace.id && (
                        <Badge variant="outline">当前</Badge>
                      )}
                    </DropdownMenuItem>
                  ))}
                </div>
              )
            })}
          </DropdownMenuGroup>
        </div>

        <DropdownMenuSeparator className="my-0 h-0 border-t border-dashed bg-transparent" />

        <div className="p-1.5">
          <DropdownMenuGroup>
            <DropdownMenuItem className="gap-2 rounded-md px-2 py-2">
              <div className="flex size-7 items-center justify-center rounded-md border border-muted-foreground/40 border-dashed bg-background/50">
                <Plus className="text-muted-foreground" />
              </div>
              <span className="text-sm">创建工作区</span>
            </DropdownMenuItem>
          </DropdownMenuGroup>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
