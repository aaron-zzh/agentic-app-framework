/**
 * 认证 API facade 单元测试——验证 backendApi 请求构造
 */

import { beforeEach, describe, expect, it, vi } from "vitest"

const backendApiMock = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  patch: vi.fn(),
  post: vi.fn(),
  put: vi.fn()
}))

vi.mock("../backend-client", () => ({ backendApi: backendApiMock }))

import { authApi } from "./auth"

describe("authApi", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("登录应传递凭据与验证码头", async () => {
    await authApi.login("testuser", "pass123", "captcha")
    expect(backendApiMock.post).toHaveBeenCalledWith(
      "/auth/login",
      { username: "testuser", password: "pass123" },
      { headers: { "captcha-verify-param": "captcha" } }
    )
  })

  it("注册应传递推荐码与验证码头", async () => {
    await authApi.register("a@b.com", "password123", "昵称", "captcha", "invite")
    expect(backendApiMock.post).toHaveBeenCalledWith(
      "/auth/register",
      { email: "a@b.com", password: "password123", nickname: "昵称", referrerCode: "invite" },
      { headers: { "captcha-verify-param": "captcha" } }
    )
  })

  it("邮箱注册、登录和验证应保持各自端点", async () => {
    await authApi.registerByEmail("a@b.com", "123456", "昵称", "invite")
    await authApi.loginByEmail("a@b.com", "123456")
    await authApi.verifyEmail("a@b.com", "123456", "invite")
    expect(backendApiMock.post).toHaveBeenNthCalledWith(1, "/auth/register-by-email", {
      email: "a@b.com",
      code: "123456",
      nickname: "昵称",
      referrerCode: "invite"
    })
    expect(backendApiMock.post).toHaveBeenNthCalledWith(2, "/auth/login-by-email", {
      email: "a@b.com",
      code: "123456"
    })
    expect(backendApiMock.post).toHaveBeenNthCalledWith(3, "/auth/verify-email", {
      email: "a@b.com",
      code: "123456",
      referrerCode: "invite"
    })
  })

  it("短信验证码应保留验证码头，手机号登录应保留推荐码", async () => {
    await authApi.sendSmsCode("13800138000", "login", "captcha")
    await authApi.loginByPhone("13800138000", "123456", "invite")
    expect(backendApiMock.post).toHaveBeenNthCalledWith(
      1,
      "/auth/send-sms-code",
      { phone: "13800138000", type: "login" },
      { headers: { "captcha-verify-param": "captcha" } }
    )
    expect(backendApiMock.post).toHaveBeenNthCalledWith(2, "/auth/login-by-phone", {
      phone: "13800138000",
      code: "123456",
      referrerCode: "invite"
    })
  })

  it("邮箱验证码应保留验证码头", async () => {
    await authApi.sendEmailCode("a@b.com", "reset", "captcha")
    expect(backendApiMock.post).toHaveBeenCalledWith(
      "/auth/send-email-code",
      { email: "a@b.com", type: "reset" },
      { headers: { "captcha-verify-param": "captcha" } }
    )
  })

  it("密码重置端点应保持邮箱和手机号负载", async () => {
    await authApi.resetPassword("a@b.com", "123456", "password123")
    await authApi.resetPasswordByPhone("13800138000", "123456", "password123")
    expect(backendApiMock.post).toHaveBeenNthCalledWith(1, "/auth/reset-password", {
      email: "a@b.com",
      code: "123456",
      newPassword: "password123"
    })
    expect(backendApiMock.post).toHaveBeenNthCalledWith(2, "/auth/reset-password-by-phone", {
      phone: "13800138000",
      code: "123456",
      newPassword: "password123"
    })
  })

  it("刷新和登出应传递 token", async () => {
    await authApi.refresh("old-refresh")
    await authApi.logout("access-t", "refresh-t")
    expect(backendApiMock.post).toHaveBeenNthCalledWith(1, "/auth/refresh", {
      refreshToken: "old-refresh"
    })
    expect(backendApiMock.post).toHaveBeenNthCalledWith(2, "/auth/logout", {
      accessToken: "access-t",
      refreshToken: "refresh-t"
    })
  })

  it("当前用户和 OAuth 地址应使用 GET", async () => {
    await authApi.me()
    await authApi.getOAuthUrl("github", "random-state")
    expect(backendApiMock.get).toHaveBeenNthCalledWith(1, "/auth/me")
    expect(backendApiMock.get).toHaveBeenNthCalledWith(
      2,
      "/auth/oauth/github/url?state=random-state"
    )
  })

  it("OAuth 回调应提交 code、设备 ID 和推荐码", async () => {
    await authApi.oauthCallback("github", "code", "invite")
    expect(backendApiMock.post).toHaveBeenCalledWith(
      "/auth/oauth/github/callback",
      expect.objectContaining({
        code: "code",
        referrerCode: "invite",
        deviceId: expect.any(String)
      })
    )
  })
})
