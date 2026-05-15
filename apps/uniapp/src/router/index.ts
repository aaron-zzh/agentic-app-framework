/// <reference types="@uni-helper/vite-plugin-uni-pages/client" />
import { pages, subPackages } from 'virtual:uni-pages'

/** 管理端分包路径前缀 */
const ADMIN_PREFIX = '/subPages/admin/'

/** 不需要登录的白名单页面 */
const WHITE_LIST = [
  '/pages/startup/index',
  '/pages/login/index',
]

// ===== @wot-ui/router 路由实例 =====

function generateRoutes() {
  const routes = pages.map(page => ({ ...page, path: `/${page.path}` }))
  subPackages?.forEach((pkg) => {
    pkg.pages.forEach((page: { path: string }) => {
      routes.push({ ...page, path: `/${pkg.root}/${page.path}` })
    })
  })
  return routes
}

const router = createRouter({ routes: generateRoutes() })

router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  // 白名单直接放行
  if (WHITE_LIST.includes(to.path)) {
    return next()
  }

  // 未登录跳登录页
  if (!userStore.isLoggedIn) {
    return next({ name: 'login' })
  }

  // 管理端路由校验管理员角色
  if (to.path.startsWith(ADMIN_PREFIX) && !userStore.isAdmin) {
    return next({ name: 'index' })
  }

  next()
})

export default router

// ===== uni.addInterceptor 原生导航拦截 =====
// 覆盖 @wot-ui/router 未处理的 uni.navigateTo 等原生跳转

function checkAccess(url: string): boolean {
  const path = `/${url.split('?')[0].replace(/^\//, '')}`
  const userStore = useUserStore()

  if (WHITE_LIST.includes(path))
    return true

  if (!userStore.isLoggedIn) {
    uni.redirectTo({ url: '/pages/login/index' })
    return false
  }

  if (path.startsWith(ADMIN_PREFIX) && !userStore.isAdmin) {
    uni.switchTab({ url: '/pages/index/index' })
    return false
  }

  return true
}

;(['navigateTo', 'redirectTo', 'reLaunch'] as const).forEach((method) => {
  uni.addInterceptor(method, {
    invoke(args: { url: string }) {
      return checkAccess(args.url)
    },
  })
})
