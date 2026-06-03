/**
 * client.ts 单元测试——验证 request() 的认证头注入和 401 刷新重试
 * @author AaronZZH & Kiro
 */

import axios from "axios"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { useAuthStore } from "@/lib/store/auth-store"
import { useOrgStore } from "@/lib/store/org-store"
import { useUIStore } from "@/lib/store/ui-store"
import {
  installMockBackendClient,
  mockBackendRequest,
  mockBackendResponse,
  resetMockBackendClient
} from "@/test/mock-backend-client"
import { ApiError, request } from "./crud"

describe("request()", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    installMockBackendClient()
    resetMockBackendClient()
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
    useAuthStore.getState().setTokens("test-token-123", "refresh-token")
    mockBackendResponse({ code: 0, data: { id: 1 } })

    await request("/users/1")

    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer test-token-123"
        }),
        url: "/users/1"
      })
    )
  })

  it("无 accessToken 时不注入 Authorization header", async () => {
    mockBackendResponse({ code: 0, data: null })

    await request("/public/info")

    const callHeaders = mockBackendRequest.mock.calls[0][0].headers
    expect(callHeaders.Authorization).toBeUndefined()
  })

  it("应注入 X-Workspace-Id header（当 store 中存在时）", async () => {
    useUIStore.getState().setCurrentWorkspace({ id: "ws-abc", name: "测试工作区" })
    mockBackendResponse({ code: 0, data: [] })

    await request("/documents")

    expect(mockBackendRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        headers: expect.objectContaining({
          "X-Workspace-Id": "ws-abc"
        })
      })
    )
  })

  it("应注入 X-Org-Id header（当 store 中存在时）", async () => {
    useOrgStore.getState().setCurrentOrgId("org-xyz")
    mockBackendResponse({ code: 0, data: [] })

    await request("/documents")

    expect(mockBackendRequest).toHaveBeenCalledWith(
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

    mockBackendResponse(null, 401)
    vi.spyOn(axios, "post").mockResolvedValueOnce({
      data: {
        code: 0,
        data: { accessToken: "new-access-token", refreshToken: "new-refresh-token" }
      }
    })
    mockBackendResponse({ code: 0, data: { id: 1 } })

    const result = await request("/users/1")

    expect(result).toEqual({ id: 1 })
    expect(mockBackendRequest).toHaveBeenCalledTimes(2)
    // 重试请求应携带新 token
    const retryHeaders = mockBackendRequest.mock.calls[1][0].headers
    expect(retryHeaders.Authorization).toBe("Bearer new-access-token")
  })

  it("401 且无 refreshToken 时应抛出 ApiError 并清除认证", async () => {
    useAuthStore.getState().setTokens("expired-token", "refresh-token")
    useAuthStore.setState({ refreshToken: null })
    mockBackendResponse(null, 401)

    await expect(request("/users/1")).rejects.toThrow(ApiError)
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it("业务错误码非 0 时应抛出 ApiError", async () => {
    mockBackendResponse({ code: 1001, message: "参数错误" })

    await expect(request("/users")).rejects.toThrow("参数错误")
  })
})
