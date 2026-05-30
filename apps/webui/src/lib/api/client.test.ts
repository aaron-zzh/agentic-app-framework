/**
 * client.ts 单元测试——验证 request() 的认证头注入和 401 刷新重试
 * @author AaronZZH & Kiro
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useAuthStore } from "@/lib/store/auth-store"
import { useOrgStore } from "@/lib/store/org-store"
import { useUIStore } from "@/lib/store/ui-store"
import { ApiError, request } from "./client"

// mock fetch
const mockFetch = vi.fn()
vi.stubGlobal("fetch", mockFetch)

function mockJsonResponse(data: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 200 ? "OK" : "Error",
    json: () => Promise.resolve(data)
  }
}

describe("request()", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 重置 store 状态
    useAuthStore.setState({
      accessToken: null,
      refreshToken: null,
      user: null,
      isAuthenticated: false
    })
    useOrgStore.setState({ currentOrgId: null })
    useUIStore.setState({ currentWorkspace: null })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("应自动注入 Authorization header（当 accessToken 存在时）", async () => {
    useAuthStore.setState({ accessToken: "test-token-123" })
    mockFetch.mockResolvedValueOnce(mockJsonResponse({ code: 0, data: { id: 1 } }))

    await request("/users/1")

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/users/1"),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer test-token-123"
        })
      })
    )
  })

  it("无 accessToken 时不注入 Authorization header", async () => {
    mockFetch.mockResolvedValueOnce(mockJsonResponse({ code: 0, data: null }))

    await request("/public/info")

    const callHeaders = mockFetch.mock.calls[0][1].headers
    expect(callHeaders.Authorization).toBeUndefined()
  })

  it("应注入 X-Workspace-Id header（当 store 中存在时）", async () => {
    useUIStore.setState({ currentWorkspace: { id: "ws-abc", name: "测试工作区" } })
    mockFetch.mockResolvedValueOnce(mockJsonResponse({ code: 0, data: [] }))

    await request("/documents")

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          "X-Workspace-Id": "ws-abc"
        })
      })
    )
  })

  it("应注入 X-Org-Id header（当 store 中存在时）", async () => {
    useOrgStore.setState({ currentOrgId: "org-xyz" })
    mockFetch.mockResolvedValueOnce(mockJsonResponse({ code: 0, data: [] }))

    await request("/documents")

    expect(mockFetch).toHaveBeenCalledWith(
      expect.any(String),
      expect.objectContaining({
        headers: expect.objectContaining({
          "X-Org-Id": "org-xyz"
        })
      })
    )
  })

  it("401 响应应触发 token 刷新并重试", async () => {
    useAuthStore.setState({
      accessToken: "expired-token",
      refreshToken: "valid-refresh-token",
      isAuthenticated: true
    })

    // 第一次请求返回 401
    mockFetch.mockResolvedValueOnce(mockJsonResponse(null, 401))
    // 刷新 token 请求成功
    mockFetch.mockResolvedValueOnce(
      mockJsonResponse({
        code: 0,
        data: { accessToken: "new-access-token", refreshToken: "new-refresh-token" }
      })
    )
    // 重试请求成功
    mockFetch.mockResolvedValueOnce(mockJsonResponse({ code: 0, data: { id: 1 } }))

    const result = await request("/users/1")

    expect(result).toEqual({ id: 1 })
    // 共 3 次 fetch：原始请求 + 刷新 + 重试
    expect(mockFetch).toHaveBeenCalledTimes(3)
    // 重试请求应携带新 token
    const retryHeaders = mockFetch.mock.calls[2][1].headers
    expect(retryHeaders.Authorization).toBe("Bearer new-access-token")
  })

  it("401 且无 refreshToken 时应抛出 ApiError 并清除认证", async () => {
    useAuthStore.setState({ accessToken: "expired-token", refreshToken: null })
    mockFetch.mockResolvedValueOnce(mockJsonResponse(null, 401))

    await expect(request("/users/1")).rejects.toThrow(ApiError)
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it("业务错误码非 0 时应抛出 ApiError", async () => {
    mockFetch.mockResolvedValueOnce(mockJsonResponse({ code: 1001, message: "参数错误" }))

    await expect(request("/users")).rejects.toThrow("参数错误")
  })
})
