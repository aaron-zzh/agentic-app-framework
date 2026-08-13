/**
 * WorkspaceSwitcher 单测——验证管理员三级范围切换与请求头状态同步
 * @author AaronZZH & Kiro
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { useAuthStore } from "@/lib/store/auth-store"
import { ALL_ORGANIZATIONS_ID, useOrgStore } from "@/lib/store/org-store"
import { WorkspaceSwitcher } from "./WorkspaceSwitcher"

const apiMocks = vi.hoisted(() => ({
  useOrganizations: vi.fn(),
  useWorkspaces: vi.fn()
}))

vi.mock("@/lib/api/rest/user", () => apiMocks)

function renderSwitcher() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } }
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <WorkspaceSwitcher />
    </QueryClientProvider>
  )
}

describe("WorkspaceSwitcher", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({
      user: {
        id: "1",
        username: "admin",
        email: "admin@example.com",
        nickname: "管理员",
        roles: ["SUPER_ADMIN"]
      }
    })
    useOrgStore.setState({ currentOrgId: "1", currentWorkspace: null })
    apiMocks.useOrganizations.mockReturnValue({
      data: [
        { id: "1", name: "组织一", slug: "org-1" },
        { id: "2", name: "组织二", slug: "org-2" }
      ]
    })
    apiMocks.useWorkspaces.mockReturnValue({
      data: {
        list: [
          {
            id: "21",
            orgId: "2",
            name: "工作区二",
            slug: "workspace-2",
            createBy: "1",
            createTime: "2026-08-04T00:00:00"
          }
        ],
        total: 1
      }
    })
  })

  it("组织项仅显示组织名称，不显示范围副标题", async () => {
    renderSwitcher()

    fireEvent.click(screen.getByRole("button"))

    expect(await screen.findByText("组织一")).toBeInTheDocument()
    expect(screen.queryByText("全部工作区")).not.toBeInTheDocument()
  })

  it("super_admin 选择全部组织时清空具体工作区", async () => {
    renderSwitcher()

    fireEvent.click(screen.getByRole("button"))
    fireEvent.click(await screen.findByText("全部组织"))

    expect(useOrgStore.getState().currentOrgId).toBe(ALL_ORGANIZATIONS_ID)
    expect(useOrgStore.getState().currentWorkspace).toBeNull()
  })

  it("选择其他组织的具体工作区时同步组织和工作区", async () => {
    renderSwitcher()

    fireEvent.click(screen.getByRole("button"))
    fireEvent.click(await screen.findByText("工作区二"))

    expect(useOrgStore.getState().currentOrgId).toBe("2")
    expect(useOrgStore.getState().currentWorkspace).toEqual({
      id: "21",
      name: "工作区二",
      orgId: "2"
    })
  })
})
