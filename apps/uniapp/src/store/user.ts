/**
 * 用户状态与认证
 * 持久化到 uni.storage（通过 src/store/persist.ts 插件）
 */
import { defineStore } from 'pinia'

export interface UserInfo {
  id: number
  nickname: string
  avatar: string
  mobile: string
}

/** 用户角色 */
export type UserRole = 'user' | 'admin'

interface UserState {
  /** 访问令牌 */
  token: string
  /** 用户信息 */
  userInfo: UserInfo | null
  /** 角色 */
  role: UserRole
  /** 权限列表 */
  permissions: string[]
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: uni.getStorageSync('token') ?? '',
    userInfo: null,
    role: 'user',
    permissions: [],
  }),

  getters: {
    /** 是否已登录 */
    isLoggedIn: state => !!state.token,
    /** 是否管理员 */
    isAdmin: state => state.role === 'admin',
  },

  actions: {
    /**
     * 登录成功后保存 token 和用户信息
     * @param token - 访问令牌
     * @param userInfo - 用户信息
     * @param role - 用户角色
     */
    login(token: string, userInfo: UserInfo, role: UserRole = 'user'): void {
      this.token = token
      this.userInfo = userInfo
      this.role = role
      uni.setStorageSync('token', token)
    },

    /** 登出，清除所有状态 */
    logout(): void {
      this.token = ''
      this.userInfo = null
      this.role = 'user'
      this.permissions = []
      uni.removeStorageSync('token')
    },

    /** 更新用户信息 */
    setUserInfo(info: Partial<UserInfo>): void {
      if (this.userInfo) {
        this.userInfo = { ...this.userInfo, ...info }
      }
    },

    /** 设置权限列表 */
    setPermissions(permissions: string[]): void {
      this.permissions = permissions
    },
  },
})
