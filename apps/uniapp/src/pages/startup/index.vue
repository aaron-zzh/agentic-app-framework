<script setup lang="ts">
definePage({
  name: 'startup',
  type: 'home',
  style: { navigationBarTitleText: '', navigationStyle: 'custom' },
})

const router = useRouter()

onMounted(async () => {
  const userStore = useUserStore()

  // 至少展示 500ms，避免闪屏
  const [result] = await Promise.allSettled([
    checkLoginStatus(userStore),
    new Promise(resolve => setTimeout(resolve, 500)),
  ])

  const isValid = result.status === 'fulfilled' && result.value === true
  router.replaceAll({ name: isValid ? 'index' : 'login' })
})

/**
 * 验证登录态：
 * 1. 本地无 token → 直接返回 false
 * 2. 本地有 token → 调接口验证是否仍有效
 */
async function checkLoginStatus(userStore: ReturnType<typeof useUserStore>): Promise<boolean> {
  if (!userStore.isLoggedIn)
    return false

  try {
    // TODO: 调用 /api/user/profile 验证 token 有效性
    // const info = await userApi.getProfile()
    // userStore.setUserInfo(info)
    return true
  }
  catch {
    // token 失效，清除本地状态
    userStore.logout()
    return false
  }
}
</script>

<template>
  <view
    class="h-screen flex flex-col items-center justify-center"
    style="background: linear-gradient(180deg, #8e44ad, #3498db)"
  >
    <view class="h-24 w-24 flex items-center justify-center rounded-4 bg-white/20">
      <wd-icon name="home" size="48px" color="#fff" />
    </view>
    <text class="mt-4 text-2xl text-white font-bold">校园互帮</text>
    <text class="mt-2 text-sm text-white/70">帮你解决生活琐事</text>
    <wd-loading class="mt-8" color="#fff" />
  </view>
</template>
