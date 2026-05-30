/**
 * 认证 API 模块——登录/注册/Token 刷新/OAuth
 * @author AaronZZH & Kiro
 */

import { request } from "./client"

export interface LoginResult {
  accessToken: string
  refreshToken: string
  userId: string
}

export interface UserInfo {
  id: string
  email: string
  nickname: string
  avatar?: string
}

export const authApi = {
  login(email: string, password: string) {
    return request<LoginResult>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username: email, password })
    })
  },

  register(email: string, password: string, nickname?: string) {
    return request<void>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ email, password, nickname })
    })
  },

  registerByCode(email: string, code: string, nickname?: string) {
    return request<LoginResult>("/auth/register-by-code", {
      method: "POST",
      body: JSON.stringify({ email, code, nickname })
    })
  },

  sendCode(email: string, type: "register" | "reset" | "login") {
    return request<void>("/auth/send-code", {
      method: "POST",
      body: JSON.stringify({ email, type })
    })
  },

  verifyEmail(email: string, code: string) {
    return request<LoginResult>("/auth/verify-email", {
      method: "POST",
      body: JSON.stringify({ email, code })
    })
  },

  loginByCode(email: string, code: string) {
    return request<LoginResult>("/auth/login-by-code", {
      method: "POST",
      body: JSON.stringify({ email, code })
    })
  },

  resetPassword(email: string, code: string, newPassword: string) {
    return request<void>("/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ email, code, newPassword })
    })
  },

  refresh(refreshToken: string) {
    return request<LoginResult>("/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken })
    })
  },

  logout(accessToken: string, refreshToken: string) {
    return request<void>("/auth/logout", {
      method: "POST",
      body: JSON.stringify({ accessToken, refreshToken })
    })
  },

  me() {
    return request<UserInfo>("/auth/me")
  },

  getOAuthUrl(provider: string, state: string) {
    return request<string>(`/auth/oauth/${provider}/url?state=${encodeURIComponent(state)}`)
  },

  oauthCallback(provider: string, code: string) {
    return request<LoginResult>(`/auth/oauth/${provider}/callback`, {
      method: "POST",
      body: JSON.stringify({ code, deviceId: getDeviceId() })
    })
  }
}

/** 获取或生成设备 ID */
function getDeviceId(): string {
  if (typeof window === "undefined") return ""
  let id = localStorage.getItem("aaf-device-id")
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem("aaf-device-id", id)
  }
  return id
}
