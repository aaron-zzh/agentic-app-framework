/**
 * useAuth 单测——验证鉴权恢复时的默认组织与工作区初始化
 * @author AaronZZH & Kiro
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { act, renderHook } from "@testing-library/react"
import type { PropsWithChildren } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { useAuthStore } from "@/lib/store/auth-store"
import { useOrgStore } from "@/lib/store/org-store"
import { useAuth } from "./use-auth"

const apiMocks = vi.hoisted(() => ({
  me: vi.fn(),
  listOrganizations: vi.fn(),
  defaultContext: vi.fn(),
  listWorkspaces: vi.fn()
}))

vi.mock("@/lib/api/rest/user", () => ({
  authApi: {
    me: apiMocks.me
  },
  organizationApi: {
    list: apiMocks.listOrganizations,
    defaultContext: apiMocks.defaultContext
  },
  workspaceApi: {
    list: apiMocks.listWorkspaces
  }
}))

vi.mock("./mock-user", () => ({
  isMockAuthEnabled: () => false,
  MOCK_AUTH_ACCESS_TOKEN: "mock-access-token",
  MOCK_AUTH_REFRESH_TOKEN: "mock-refresh-token",
  mockedUser: null
}))

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  })

  return function Wrapper({ children }: PropsWithChildren) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

const userInfo = {
  user: {
    id: "1",
    username: "admin",
    email: "admin@example.com",
    nickname: "管理员"
  },
  roles: ["SUPER_ADMIN"]
}

const defaultContext = {
  orgId: "org-personal",
  orgName: "admin",
  workspaceId: "workspace-default",
  workspaceName: "默认工作区"
}

describe("useAuth 默认工作区初始化", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    useAuthStore.setState({
      accessToken: "access-token",
      refreshToken: "refresh-token",
      user: null,
      isAuthenticated: true,
      isChecking: false
    })
    useOrgStore.setState({
      status: "idle",
      activeUserId: null,
      currentScope: null,
      error: null
    })
    apiMocks.me.mockResolvedValue(userInfo)
    apiMocks.listOrganizations.mockResolvedValue([
      { id: "org-personal", name: "admin", slug: "personal-1" },
      { id: "org-team", name: "团队", slug: "team" }
    ])
    apiMocks.defaultContext.mockResolvedValue(defaultContext)
    apiMocks.listWorkspaces.mockResolvedValue({ list: [], total: 0 })
  })

  it("当前无有效上下文时选择当前用户的默认工作区", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.checkAuth()
    })

    expect(useOrgStore.getState().currentScope).toEqual({
      kind: "workspace",
      orgId: "org-personal",
      workspaceId: "workspace-default"
    })
  })

  it("当前已有有效具体工作区时保留用户选择", async () => {
    const persistedScope = {
      kind: "workspace",
      orgId: "org-team",
      workspaceId: "workspace-team"
    } as const
    window.localStorage.setItem("aaf-org-context:1", JSON.stringify(persistedScope))
    apiMocks.listWorkspaces.mockResolvedValue({
      list: [{ id: "workspace-team", orgId: "org-team", name: "团队工作区" }],
      total: 1
    })
    const { result } = renderHook(() => useAuth(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.checkAuth()
    })

    expect(useOrgStore.getState().currentScope).toEqual(persistedScope)
  })
})
