/// <reference types="@uni-helper/vite-plugin-uni-pages/client" />
import { pages, subPackages } from 'virtual:uni-pages'

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

router.beforeEach((to, from, next) => {
  console.log('🚀 beforeEach 守卫触发:', { to, from })

  const userStore = useUserStore()

  // 公开页直接放行
  if (to.meta?.public) {
    return next()
  }

  // 未登录跳登录页，携带 redirect 以便登录后回跳；replaceAll 避免继承 pushTab 类型
  if (!userStore.isLoggedIn) {
    return next({ name: 'login', navType: 'replaceAll', query: { redirect: to.path } })
  }

  // 细粒度权限校验（meta.permission 指定所需权限字符串）
  if (to.meta?.permission && !userStore.permissions.includes(to.meta.permission as string)) {
    return next({ name: 'index', navType: 'replaceAll' })
  }

  // 演示：对受保护页面的简单拦截
  if (to.name === 'demo-protected') {
    const { confirm: showConfirm } = useGlobalDialog()
    console.log('🛡️ 检测到访问受保护页面')

    return new Promise<void>((resolve, reject) => {
      showConfirm({
        title: '守卫拦截演示',
        msg: '这是一个受保护的页面，需要确认后才能访问',
        confirmButtonText: '允许访问',
        cancelButtonText: '取消',
        success() {
          console.log('✅ 用户确认访问，允许导航')
          next()
          resolve()
        },
        fail() {
          console.log('❌ 用户取消访问，阻止导航')
          next(false)
          reject(new Error('用户取消访问'))
        },
      })
    })
  }

  next()
})

router.afterEach((to, from) => {
  console.log('🎯 afterEach 钩子触发:', { to, from })

  // 演示：简单的页面切换记录
  if (to.path) {
    console.log(`📄 页面切换完成: ${to.path}`)
  }

  // 演示：针对 afterEach 演示页面的简单提示
  if (to.name === 'demo-aftereach') {
    const { show: showToast } = useGlobalToast()
    console.log('📊 进入 afterEach 演示页面')
    setTimeout(() => {
      showToast('afterEach 钩子已触发！')
    }, 500)
  }
})

export default router
