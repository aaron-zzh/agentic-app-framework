/**
 * 应用全局状态 + 启动序列
 */
import { defineStore } from 'pinia'
import platform from '@/platform'

interface AppState {
  /** 是否已初始化 */
  initialized: boolean
  /** 网络是否可用 */
  networkAvailable: boolean
}

export const useAppStore = defineStore('app', {
  state: (): AppState => ({
    initialized: false,
    networkAvailable: true,
  }),

  actions: {
    /**
     * 应用启动序列：检查网络 → 加载平台 → 初始化主题 → 检查登录态
     * 在 App.vue onLaunch 中调用
     */
    async init(): Promise<void> {
      // 1. 检查网络
      this.networkAvailable = await platform.checkNetwork()
      if (!this.networkAvailable) {
        uni.showToast({ title: '网络不可用，请检查网络设置', icon: 'none' })
        return
      }

      // 2. 加载平台（JS-SDK 初始化、小程序更新检查等）
      platform.load()

      // 3. 初始化主题
      const manualThemeStore = useManualThemeStore()
      manualThemeStore.initTheme()

      // 4. 检查登录态
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) {
        // 未登录由路由守卫处理跳转，此处仅记录状态
      }

      this.initialized = true
    },
  },
})
