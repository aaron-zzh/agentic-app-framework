/**
 * WorkspaceSwitcher——组织分组、明确范围选择与重命名入口。
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryClient } from "@tanstack/react-query"
import { Building2, ChevronsUpDown, Layers3, LockKeyhole, Network, Pencil } from "lucide-react"
import { type FormEvent, useId, useMemo, useRef, useState } from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import {
  type OrganizationVO,
  useOrganizations,
  useUpdateWorkspace,
  useWorkspaces,
  type WorkspaceVO
} from "@/lib/api/rest/user"
import { useAuthStore } from "@/lib/store/auth-store"
import {
  hasSuperAdminRole,
  isAggregateScope,
  type ScopeSelection,
  useOrgStore
} from "@/lib/store/org-store"
import { $url } from "@/lib/utils"

type RenameTarget = { id: string; orgId: string; name: string }

function ReadOnlyBadge() {
  return (
    <Badge variant="secondary">
      <LockKeyhole className="size-3" />
      只读
    </Badge>
  )
}

function isCurrentScope(current: ScopeSelection | null, target: ScopeSelection): boolean {
  if (!current || current.kind !== target.kind) return false
  if (target.kind === "all-organizations") return true
  if (current.kind === "all-organizations") return false
  if (current.orgId !== target.orgId) return false
  return (
    target.kind !== "workspace" ||
    (current.kind === "workspace" && current.workspaceId === target.workspaceId)
  )
}

export function WorkspaceSwitcher() {
  const queryClient = useQueryClient()
  const user = useAuthStore((state) => state.user)
  const isSuperAdmin = hasSuperAdminRole(user?.roles)
  const status = useOrgStore((state) => state.status)
  const currentScope = useOrgStore((state) => state.currentScope)
  const setScope = useOrgStore((state) => state.setScope)
  const { data: organizations } = useOrganizations()
  const orgs = organizations ?? []
  const canSelectAllOrganizations = isSuperAdmin || orgs.length >= 2
  const currentOrgId =
    currentScope && currentScope.kind !== "all-organizations" ? currentScope.orgId : null
  const queryOrgId = canSelectAllOrganizations ? "all" : (currentOrgId ?? orgs[0]?.id ?? null)
  const { data: workspacePage } = useWorkspaces(
    status === "ready" ? queryOrgId : null,
    status === "ready" && canSelectAllOrganizations
  )
  const [renameTarget, setRenameTarget] = useState<RenameTarget | null>(null)

  const workspacesByOrg = useMemo(() => {
    const grouped = new Map<string, WorkspaceVO[]>()
    for (const workspace of workspacePage?.list ?? []) {
      const items = grouped.get(workspace.orgId) ?? []
      items.push(workspace)
      grouped.set(workspace.orgId, items)
    }
    return grouped
  }, [workspacePage])

  const activeOrg = currentOrgId ? orgs.find((org) => org.id === currentOrgId) : undefined
  const activeWorkspace =
    currentScope?.kind === "workspace"
      ? workspacePage?.list.find(
          (workspace) =>
            workspace.orgId === currentScope.orgId && workspace.id === currentScope.workspaceId
        )
      : undefined
  const activeTitle = (() => {
    switch (currentScope?.kind) {
      case "all-organizations":
        return "全部组织"
      case "all-workspaces":
        return `${activeOrg?.name ?? "组织"} · 全部工作区`
      case "workspace":
        return `${activeOrg?.name ?? "组织"} · ${activeWorkspace?.name ?? "工作区"}`
      default:
        return "选择工作区"
    }
  })()

  function selectScope(scope: ScopeSelection) {
    if (isCurrentScope(currentScope, scope)) return
    setScope(scope)
    void queryClient.invalidateQueries()
  }

  function canViewAllWorkspaces(org: OrganizationVO): boolean {
    return isSuperAdmin || org.memberRole === "owner" || org.memberRole === "admin"
  }

  function canRenameWorkspace(workspace: WorkspaceVO): boolean {
    return currentScope != null && !isAggregateScope(currentScope) && workspace.ownerId === user?.id
  }

  if (status === "restoring") {
    return <Skeleton className="h-9 w-52 rounded-lg" />
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          render={
            <button
              type="button"
              className="flex min-w-0 items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-accent"
            />
          }
        >
          <Avatar className="size-6 rounded-md after:hidden">
            {currentScope?.kind !== "all-organizations" && (
              <AvatarImage
                src={activeOrg?.logo ?? $url.cdn("/assets/icons/ChatBc.png")}
                alt={activeTitle}
                className="object-cover"
              />
            )}
            <AvatarFallback className="rounded-md bg-primary/10 font-semibold text-primary text-xs">
              {currentScope?.kind === "all-organizations"
                ? "全"
                : (activeOrg?.name?.slice(0, 1) ?? "W")}
            </AvatarFallback>
          </Avatar>
          <span className="min-w-0 max-w-48 truncate font-medium text-sm">{activeTitle}</span>
          {isAggregateScope(currentScope) && <ReadOnlyBadge />}
          <ChevronsUpDown className="size-3.5 shrink-0 text-muted-foreground" />
        </DropdownMenuTrigger>

        <DropdownMenuContent
          align="start"
          className="w-[min(18rem,calc(100vw-1rem))] overflow-hidden p-0"
        >
          <div className="p-1.5">
            <DropdownMenuGroup>
              {canSelectAllOrganizations && (
                <>
                  <DropdownMenuItem
                    onClick={() => selectScope({ kind: "all-organizations" })}
                    className="gap-2.5 rounded-md px-2 py-2"
                  >
                    <Building2 className="size-4 text-muted-foreground" />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-1.5">
                        <span className="truncate text-sm">全部组织</span>
                        <ReadOnlyBadge />
                      </div>
                      <div className="truncate text-muted-foreground text-xs">
                        {isSuperAdmin ? "平台全部组织" : "可访问组织与工作区"}
                      </div>
                    </div>
                    {isCurrentScope(currentScope, { kind: "all-organizations" }) && (
                      <Badge variant="outline">当前</Badge>
                    )}
                  </DropdownMenuItem>
                  <DropdownMenuSeparator />
                </>
              )}

              {orgs.map((org) => {
                const workspaces = workspacesByOrg.get(org.id) ?? []
                return (
                  <div key={org.id} className="mt-1 first:mt-0">
                    <DropdownMenuLabel className="flex items-center gap-2 px-2 py-1.5">
                      <Avatar className="size-5 rounded-md after:hidden">
                        <AvatarImage src={org.logo} alt={org.name} className="object-cover" />
                        <AvatarFallback className="rounded-md bg-primary/10 text-[10px] text-primary">
                          {org.name.slice(0, 1)}
                        </AvatarFallback>
                      </Avatar>
                      <Tooltip>
                        <TooltipTrigger render={<span className="min-w-0 flex-1 truncate" />}>
                          {org.name}
                        </TooltipTrigger>
                        <TooltipContent>{org.name}</TooltipContent>
                      </Tooltip>
                    </DropdownMenuLabel>

                    {canViewAllWorkspaces(org) && (
                      <DropdownMenuItem
                        onClick={() => selectScope({ kind: "all-workspaces", orgId: org.id })}
                        className="gap-2.5 rounded-md px-2 py-2"
                      >
                        <Layers3 className="size-4 text-muted-foreground" />
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5">
                            <span className="truncate text-sm">全部工作区</span>
                            <ReadOnlyBadge />
                          </div>
                          <div className="truncate text-muted-foreground text-xs">
                            组织共享及全部工作区
                          </div>
                        </div>
                        {isCurrentScope(currentScope, {
                          kind: "all-workspaces",
                          orgId: org.id
                        }) && <Badge variant="outline">当前</Badge>}
                      </DropdownMenuItem>
                    )}

                    {workspaces.map((workspace) => (
                      <div key={workspace.id} className="flex items-center">
                        <DropdownMenuItem
                          onClick={() =>
                            selectScope({
                              kind: "workspace",
                              orgId: org.id,
                              workspaceId: workspace.id
                            })
                          }
                          className="min-w-0 flex-1 gap-2.5 rounded-md px-2 py-2 pl-6"
                        >
                          <Network className="size-4 text-muted-foreground" />
                          <span className="min-w-0 flex-1 truncate text-sm">{workspace.name}</span>
                          {isCurrentScope(currentScope, {
                            kind: "workspace",
                            orgId: org.id,
                            workspaceId: workspace.id
                          }) && <Badge variant="outline">当前</Badge>}
                        </DropdownMenuItem>
                        {canRenameWorkspace(workspace) && (
                          <RenameActionButton
                            ariaLabel={`重命名${workspace.name}`}
                            onRename={() =>
                              setRenameTarget({
                                id: workspace.id,
                                orgId: workspace.orgId,
                                name: workspace.name
                              })
                            }
                          />
                        )}
                      </div>
                    ))}
                  </div>
                )
              })}
            </DropdownMenuGroup>
          </div>
        </DropdownMenuContent>
      </DropdownMenu>
      <RenameDialog target={renameTarget} onClose={() => setRenameTarget(null)} />
    </>
  )
}

function RenameActionButton({ ariaLabel, onRename }: { ariaLabel: string; onRename: () => void }) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon-xs"
      aria-label={ariaLabel}
      onClick={(event) => {
        event.stopPropagation()
        onRename()
      }}
    >
      <Pencil />
    </Button>
  )
}

function RenameDialog({ target, onClose }: { target: RenameTarget | null; onClose: () => void }) {
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [value, setValue] = useState("")
  const [error, setError] = useState<string | null>(null)
  const updateWorkspace = useUpdateWorkspace()
  const pending = updateWorkspace.isPending
  const trimmed = value.trim()
  const unchanged = target != null && trimmed === target.name.trim()

  function handleOpenChange(open: boolean) {
    if (!open && !pending) onClose()
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!target || pending || !trimmed || unchanged) return
    setError(null)
    updateWorkspace.mutate(
      {
        id: target.id,
        orgId: target.orgId,
        data: { name: trimmed }
      },
      {
        onSuccess: onClose,
        onError: (cause: Error) => {
          setError(cause.message || "保存失败，请稍后重试")
          requestAnimationFrame(() => inputRef.current?.focus())
        }
      }
    )
  }

  return (
    <Dialog
      open={target != null}
      onOpenChange={handleOpenChange}
      onOpenChangeComplete={(open) => {
        if (!open || !target) return
        setValue(target.name)
        setError(null)
        requestAnimationFrame(() => inputRef.current?.select())
      }}
    >
      <DialogContent showCloseButton={!pending}>
        <form onSubmit={handleSubmit} className="contents">
          <DialogHeader>
            <DialogTitle>重命名工作区</DialogTitle>
            <DialogDescription>仅修改显示名称，工作区标识不会改变。</DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor={inputId}>工作区名称</Label>
            <Input
              ref={inputRef}
              id={inputId}
              value={value}
              autoFocus
              disabled={pending}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${inputId}-error` : undefined}
              onFocus={(event) => event.currentTarget.select()}
              onChange={(event) => setValue(event.target.value)}
            />
            {error && (
              <p id={`${inputId}-error`} className="text-destructive text-xs">
                {error}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" disabled={pending} onClick={onClose}>
              取消
            </Button>
            <Button type="submit" disabled={pending || !trimmed || unchanged}>
              {pending ? "保存中…" : "保存"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
