/**
 * org-store 单测——验证稳定范围映射、只读派生和按用户持久化。
 * @author AaronZZH & Kiro
 */

import { beforeEach, describe, expect, it, vi } from "vitest"

const { setBackendOrgContext } = vi.hoisted(() => ({ setBackendOrgContext: vi.fn() }))

vi.mock("@/lib/api/rest/backend-client", () => ({ setBackendOrgContext }))

import {
  isAggregateScope,
  type ScopeSelection,
  scopeHeaders,
  selectScopeReadOnly,
  useOrgStore
} from "./org-store"

const scopes: Array<[ScopeSelection, string | null, string | null, boolean]> = [
  [{ kind: "all-organizations" }, "all", null, true],
  [{ kind: "all-workspaces", orgId: "org-1" }, "org-1", "all", true],
  [{ kind: "workspace", orgId: "org-1", workspaceId: "workspace-1" }, "org-1", "workspace-1", false]
]

describe("org-store", () => {
  beforeEach(() => {
    window.localStorage.clear()
    setBackendOrgContext.mockClear()
    useOrgStore.setState({
      status: "idle",
      activeUserId: null,
      currentScope: null,
      error: null
    })
  })

  it.each(scopes)("范围 %j 映射正确 Header", (scope, orgId, workspaceId, readOnly) => {
    expect(scopeHeaders(scope)).toEqual({ orgId, workspaceId })
    expect(isAggregateScope(scope)).toBe(readOnly)

    useOrgStore.getState().commitScope("user-1", scope)

    expect(setBackendOrgContext).toHaveBeenLastCalledWith(orgId, workspaceId)
    expect(selectScopeReadOnly(useOrgStore.getState())).toBe(readOnly)
  })

  it("只按当前用户 key 恢复稳定范围", () => {
    const scope: ScopeSelection = { kind: "workspace", orgId: "org-1", workspaceId: "ws-1" }
    window.localStorage.setItem("aaf-org-context:user-a", JSON.stringify(scope))
    window.localStorage.setItem(
      "aaf-org-context:user-b",
      JSON.stringify({ kind: "all-workspaces", orgId: "org-2" })
    )

    expect(useOrgStore.getState().beginRestore("user-a")).toEqual(scope)
    expect(useOrgStore.getState().currentScope).toBeNull()
    expect(setBackendOrgContext).toHaveBeenLastCalledWith(null, null)
  })

  it("非法持久化值会被删除且不会写入 Header", () => {
    window.localStorage.setItem(
      "aaf-org-context:user-1",
      JSON.stringify({ kind: "workspace", orgId: "org-1", name: "陈旧名称" })
    )

    expect(useOrgStore.getState().beginRestore("user-1")).toBeNull()
    expect(window.localStorage.getItem("aaf-org-context:user-1")).toBeNull()
    expect(useOrgStore.getState().status).toBe("restoring")
  })

  it("登出只删除当前用户范围并清空 Header", () => {
    window.localStorage.setItem(
      "aaf-org-context:user-1",
      JSON.stringify({ kind: "workspace", orgId: "org-1", workspaceId: "ws-1" })
    )
    window.localStorage.setItem(
      "aaf-org-context:user-2",
      JSON.stringify({ kind: "all-workspaces", orgId: "org-2" })
    )
    useOrgStore.setState({ activeUserId: "user-1" })

    useOrgStore.getState().clearOrgContext()

    expect(window.localStorage.getItem("aaf-org-context:user-1")).toBeNull()
    expect(window.localStorage.getItem("aaf-org-context:user-2")).not.toBeNull()
    expect(setBackendOrgContext).toHaveBeenLastCalledWith(null, null)
  })
})
