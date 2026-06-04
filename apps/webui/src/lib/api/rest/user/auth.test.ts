/**
 * auth.ts API 单元测试——验证各认证接口的请求构造
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { authApi } from "./auth"

// mock request()
vi.mock("../entity/crud", () => ({
  request: vi.fn()
}))

import { request } from "../entity/crud"

const mockRequest = vi.mocked(request)

describe("authApi", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it("login 应发送 POST /auth/login", async () => {
    mockRequest.mockResolvedValueOnce({ accessToken: "t", refreshToken: "r", userId: "1" })

    await authApi.login("testuser", "pass123")

    expect(mockRequest).toHaveBeenCalledWith("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username: "testuser", password: "pass123" })
    })
  })

  it("login 支持邮箱作为登录账号", async () => {
    mockRequest.mockResolvedValueOnce({ accessToken: "t", refreshToken: "r", userId: "1" })

    await authApi.login("test@example.com", "pass123")

    expect(mockRequest).toHaveBeenCalledWith("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username: "test@example.com", password: "pass123" })
    })
  })

  it("register 应发送 POST /auth/register", async () => {
    mockRequest.mockResolvedValueOnce(undefined)

    await authApi.register("a@b.com", "pwd", "昵称")

    expect(mockRequest).toHaveBeenCalledWith("/auth/register", {
      method: "POST",
      body: JSON.stringify({ email: "a@b.com", password: "pwd", nickname: "昵称" })
    })
  })

  it("sendCode 应发送正确的 type 参数", async () => {
    mockRequest.mockResolvedValueOnce(undefined)

    await authApi.sendCode("a@b.com", "reset")

    expect(mockRequest).toHaveBeenCalledWith("/auth/send-code", {
      method: "POST",
      body: JSON.stringify({ email: "a@b.com", type: "reset" })
    })
  })

  it("refresh 应发送 refreshToken", async () => {
    mockRequest.mockResolvedValueOnce({ accessToken: "new", refreshToken: "new-r", userId: "1" })

    await authApi.refresh("old-refresh")

    expect(mockRequest).toHaveBeenCalledWith("/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken: "old-refresh" })
    })
  })

  it("me 应发送 GET /auth/me", async () => {
    mockRequest.mockResolvedValueOnce({ id: "1", email: "a@b.com", nickname: "test" })

    await authApi.me()

    expect(mockRequest).toHaveBeenCalledWith("/auth/me")
  })

  it("logout 应发送两个 token", async () => {
    mockRequest.mockResolvedValueOnce(undefined)

    await authApi.logout("access-t", "refresh-t")

    expect(mockRequest).toHaveBeenCalledWith("/auth/logout", {
      method: "POST",
      body: JSON.stringify({ accessToken: "access-t", refreshToken: "refresh-t" })
    })
  })

  it("resetPassword 应发送邮箱、验证码和新密码", async () => {
    mockRequest.mockResolvedValueOnce(undefined)

    await authApi.resetPassword("a@b.com", "123456", "newPwd!")

    expect(mockRequest).toHaveBeenCalledWith("/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ email: "a@b.com", code: "123456", newPassword: "newPwd!" })
    })
  })

  it("getOAuthUrl 应拼接 provider 和 state", async () => {
    mockRequest.mockResolvedValueOnce("https://oauth.example.com")

    await authApi.getOAuthUrl("github", "random-state")

    expect(mockRequest).toHaveBeenCalledWith("/auth/oauth/github/url?state=random-state")
  })
})
