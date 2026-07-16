/**
 * 认证 API 模块——登录/注册/Token 刷新/OAuth
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

export interface LoginResult {
  accessToken: string
  refreshToken: string
  userId: string
  /** 是否为新用户（首次通过手机号登录即注册） */
  isNewUser?: boolean
}

export interface UserInfo {
  id: string
  username: string
  email: string
  nickname: string
  avatar?: string
  roles?: string[]
}

export const authApi = {
  login(account: string, password: string, captchaVerifyParam?: string) {
    return backendApi.post<LoginResult>(
      "/auth/login",
      { username: account, password },
      captchaVerifyParam ? { headers: { "captcha-verify-param": captchaVerifyParam } } : undefined
    )
  },

  register(
    email: string,
    password: string,
    nickname?: string,
    captchaVerifyParam?: string,
    referrerCode?: string
  ) {
    return backendApi.post<void>(
      "/auth/register",
      { email, password, nickname, referrerCode },
      captchaVerifyParam ? { headers: { "captcha-verify-param": captchaVerifyParam } } : undefined
    )
  },

  registerByEmail(email: string, code: string, nickname?: string, referrerCode?: string) {
    return backendApi.post<LoginResult>("/auth/register-by-email", {
      email,
      code,
      nickname,
      referrerCode
    })
  },

  sendEmailCode(email: string, type: "register" | "reset" | "login", captchaVerifyParam?: string) {
    return backendApi.post<void>(
      "/auth/send-email-code",
      { email, type },
      captchaVerifyParam ? { headers: { "captcha-verify-param": captchaVerifyParam } } : undefined
    )
  },

  sendSmsCode(
    phone: string,
    type: "register" | "login" | "reset" | "bind",
    captchaVerifyParam?: string
  ) {
    return backendApi.post<void>(
      "/auth/send-sms-code",
      { phone, type },
      captchaVerifyParam ? { headers: { "captcha-verify-param": captchaVerifyParam } } : undefined
    )
  },

  loginByPhone(phone: string, code: string, referrerCode?: string) {
    return backendApi.post<LoginResult>("/auth/login-by-phone", { phone, code, referrerCode })
  },

  verifyEmail(email: string, code: string, referrerCode?: string) {
    return backendApi.post<LoginResult>("/auth/verify-email", { email, code, referrerCode })
  },

  loginByEmail(email: string, code: string) {
    return backendApi.post<LoginResult>("/auth/login-by-email", { email, code })
  },

  resetPassword(email: string, code: string, newPassword: string) {
    return backendApi.post<void>("/auth/reset-password", { email, code, newPassword })
  },

  resetPasswordByPhone(phone: string, code: string, newPassword: string) {
    return backendApi.post<void>("/auth/reset-password-by-phone", { phone, code, newPassword })
  },

  refresh(refreshToken: string) {
    return backendApi.post<LoginResult>("/auth/refresh", { refreshToken })
  },

  logout(accessToken: string, refreshToken: string) {
    return backendApi.post<void>("/auth/logout", { accessToken, refreshToken })
  },

  me() {
    return backendApi.get<{ user: UserInfo; roles: string[] }>("/auth/me")
  },

  getOAuthUrl(provider: string, state: string) {
    return backendApi.get<string>(`/auth/oauth/${provider}/url?state=${encodeURIComponent(state)}`)
  },

  oauthCallback(provider: string, code: string, referrerCode?: string) {
    return backendApi.post<LoginResult>(`/auth/oauth/${provider}/callback`, {
      code,
      deviceId: getDeviceId(),
      referrerCode
    })
  }
}

/**
 * 获取或生成设备 ID
 * 注意：存储在 localStorage，清除浏览器数据后会生成新 ID。
 * 仅用于辅助识别设备（如登录日志），后端不应依赖此 ID 做安全决策。
 */
function getDeviceId(): string {
  if (typeof window === "undefined") return ""
  let id = localStorage.getItem("aaf-device-id")
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem("aaf-device-id", id)
  }
  return id
}
