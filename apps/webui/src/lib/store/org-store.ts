/**
 * 组织范围 Store——仅保存稳定范围身份并同步请求 Header。
 *
 * 组织名、工作区名等服务端数据不进入 Zustand 或 localStorage；展示名称始终从 Query 解析。
 * @author AaronZZH & Kiro
 */

import { create } from "zustand"
import { setBackendOrgContext } from "@/lib/api/rest/backend-client"

export type ScopeSelection =
  | { kind: "all-organizations" }
  | { kind: "all-workspaces"; orgId: string }
  | { kind: "workspace"; orgId: string; workspaceId: string }

export type OrgContextStatus = "idle" | "restoring" | "ready" | "error"

export function hasSuperAdminRole(roles?: string[]): boolean {
  return roles?.some((role) => role.toLowerCase() === "super_admin") ?? false
}

/** 判断是否为只读聚合范围。 */
export function isAggregateScope(scope: ScopeSelection | null): boolean {
  return scope?.kind === "all-organizations" || scope?.kind === "all-workspaces"
}

/** 将范围身份映射为后端请求 Header。 */
export function scopeHeaders(scope: ScopeSelection | null): {
  orgId: string | null
  workspaceId: string | null
} {
  if (!scope) return { orgId: null, workspaceId: null }
  switch (scope.kind) {
    case "all-organizations":
      return { orgId: "all", workspaceId: null }
    case "all-workspaces":
      return { orgId: scope.orgId, workspaceId: "all" }
    case "workspace":
      return { orgId: scope.orgId, workspaceId: scope.workspaceId }
  }
}

function storageKey(userId: string): string {
  return `aaf-org-context:${userId}`
}

function isScopeSelection(value: unknown): value is ScopeSelection {
  if (!value || typeof value !== "object") return false
  const scope = value as Record<string, unknown>
  if (scope.kind === "all-organizations") return Object.keys(scope).length === 1
  if (scope.kind === "all-workspaces" && typeof scope.orgId === "string") {
    return Object.keys(scope).length === 2
  }
  return (
    scope.kind === "workspace" &&
    typeof scope.orgId === "string" &&
    typeof scope.workspaceId === "string" &&
    Object.keys(scope).length === 3
  )
}

interface OrgState {
  status: OrgContextStatus
  activeUserId: string | null
  currentScope: ScopeSelection | null
  error: string | null
  beginRestore: (userId: string) => ScopeSelection | null
  commitScope: (userId: string, scope: ScopeSelection) => void
  setScope: (scope: ScopeSelection) => void
  failRestore: (message: string) => void
  clearOrgContext: (userId?: string) => void
}

export const useOrgStore = create<OrgState>((set, get) => ({
  status: "idle",
  activeUserId: null,
  currentScope: null,
  error: null,
  beginRestore: (userId) => {
    setBackendOrgContext(null, null)
    set({ status: "restoring", activeUserId: userId, currentScope: null, error: null })
    if (typeof window === "undefined") return null
    const key = storageKey(userId)
    const raw = window.localStorage.getItem(key)
    if (!raw) return null
    try {
      const scope: unknown = JSON.parse(raw)
      if (isScopeSelection(scope)) return scope
    } catch {
      // 非法持久化值在下方统一删除。
    }
    window.localStorage.removeItem(key)
    return null
  },
  commitScope: (userId, scope) => {
    const headers = scopeHeaders(scope)
    setBackendOrgContext(headers.orgId, headers.workspaceId)
    if (typeof window !== "undefined") {
      window.localStorage.setItem(storageKey(userId), JSON.stringify(scope))
    }
    set({ status: "ready", activeUserId: userId, currentScope: scope, error: null })
  },
  setScope: (scope) => {
    const userId = get().activeUserId
    const headers = scopeHeaders(scope)
    setBackendOrgContext(headers.orgId, headers.workspaceId)
    if (userId && typeof window !== "undefined") {
      window.localStorage.setItem(storageKey(userId), JSON.stringify(scope))
    }
    set({ status: "ready", currentScope: scope, error: null })
  },
  failRestore: (message) => {
    setBackendOrgContext(null, null)
    set({ status: "error", currentScope: null, error: message })
  },
  clearOrgContext: (userId) => {
    const targetUserId = userId ?? get().activeUserId
    if (targetUserId && typeof window !== "undefined") {
      window.localStorage.removeItem(storageKey(targetUserId))
    }
    setBackendOrgContext(null, null)
    set({ status: "idle", activeUserId: null, currentScope: null, error: null })
  }
}))

/** Zustand 统一只读 selector。 */
export const selectScopeReadOnly = (state: OrgState): boolean =>
  isAggregateScope(state.currentScope)
