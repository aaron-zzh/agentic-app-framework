/**
 * WorkspaceSwitcher 单测——验证管理员三级范围切换与请求头状态同步
 * @author AaronZZH & Kiro
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { fireEvent, render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { useAuthStore } from "@/lib/store/auth-store"
import { useOrgStore } from "@/lib/store/org-store"
import { WorkspaceSwitcher } from "./WorkspaceSwitcher"

const apiMocks = vi.hoisted(() => ({
  useOrganizations: vi.fn(),
  useWorkspaces: vi.fn(),
  useUpdateWorkspace: vi.fn()
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
    useOrgStore.setState({
      status: "ready",
      activeUserId: "1",
      currentScope: { kind: "workspace", orgId: "1", workspaceId: "11" },
      error: null
    })
    apiMocks.useUpdateWorkspace.mockReturnValue({
      isPending: false,
      mutate: vi.fn()
    })
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
            ownerId: "1",
            createBy: "1",
            createTime: "2026-08-04T00:00:00"
          }
        ],
        total: 1
      }
    })
  })

  it("组织仅作为分组标题并显示显式只读聚合项", async () => {
    renderSwitcher()

    fireEvent.click(screen.getByRole("button"))

    expect(await screen.findByText("组织一")).toBeInTheDocument()
    expect(screen.getAllByText("全部工作区")).not.toHaveLength(0)
    expect(screen.queryByText("组织共享")).not.toBeInTheDocument()
  })

  it("super_admin 选择全部组织时清空具体工作区", async () => {
    renderSwitcher()

    fireEvent.click(screen.getByRole("button"))
    fireEvent.click(await screen.findByText("全部组织"))

    expect(useOrgStore.getState().currentScope).toEqual({ kind: "all-organizations" })
  })

  it("选择其他组织的具体工作区时同步组织和工作区", async () => {
    renderSwitcher()

    fireEvent.click(screen.getByRole("button"))
    fireEvent.click(await screen.findByText("工作区二"))

    expect(useOrgStore.getState().currentScope).toEqual({
      kind: "workspace",
      orgId: "2",
      workspaceId: "21"
    })
  })

  it("工作区 owner 可通过 Dialog 重命名工作区", async () => {
    renderSwitcher()

    fireEvent.click(screen.getByRole("button"))
    fireEvent.click(await screen.findByRole("button", { name: "重命名工作区二" }))

    expect(await screen.findByRole("heading", { name: "重命名工作区" })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText("工作区名称"), { target: { value: "新工作区" } })
    fireEvent.click(screen.getByRole("button", { name: "保存" }))

    expect(apiMocks.useUpdateWorkspace.mock.results[0]?.value.mutate).toHaveBeenCalledWith(
      { id: "21", orgId: "2", data: { name: "新工作区" } },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) })
    )
  })
})
