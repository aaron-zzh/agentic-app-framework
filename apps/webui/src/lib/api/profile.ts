/**
 * 个人资料 API 模块——获取/更新个人信息、修改密码
 * @author AaronZZH & Kiro
 */

import { request } from "./client"

/** 个人资料 */
export interface ProfileVO {
  id: string
  email: string
  nickname: string
  avatar?: string
  phone?: string
  bio?: string
}

/** 更新个人资料请求 */
export interface ProfileUpdateReq {
  nickname?: string
  avatar?: string
  phone?: string
  bio?: string
}

/** 修改密码请求 */
export interface ChangePasswordReq {
  oldPassword: string
  newPassword: string
}

export const profileApi = {
  /** 获取当前用户个人资料 */
  get: () => request<ProfileVO>("/user/profile"),

  /** 更新个人资料 */
  update: (data: ProfileUpdateReq) =>
    request<ProfileVO>("/user/profile", { method: "PUT", body: JSON.stringify(data) }),

  /** 修改密码 */
  changePassword: (data: ChangePasswordReq) =>
    request<void>("/user/profile/password", { method: "PUT", body: JSON.stringify(data) })
}
