<script setup lang="ts">
definePage({
  name: 'settings',
  style: { navigationBarTitleText: '设置' },
})

const router = useRouter()
const userStore = useUserStore()
const { confirm } = useGlobalDialog()

const cacheSize = ref('')
onMounted(() => {
  const info = uni.getStorageInfoSync()
  cacheSize.value = `${info.currentSize} KB`
})

function onLogout() {
  confirm({
    title: '退出登录',
    msg: '确定要退出登录吗？',
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    success() {
      userStore.logout()
      router.replaceAll({ name: 'login' })
    },
  })
}

function onLogoff() {
  confirm({
    title: '注销账号',
    msg: '注销后账号及所有数据将永久删除，且无法恢复，确定继续吗？',
    confirmButtonText: '确认注销',
    cancelButtonText: '取消',
    success() {
      // TODO: 调用注销接口
      userStore.logout()
      router.replaceAll({ name: 'login' })
    },
  })
}

function onClearCache() {
  confirm({
    title: '清除缓存',
    msg: '确定要清除本地缓存吗？',
    success() {
      uni.clearStorageSync()
      cacheSize.value = '0 KB'
      useGlobalToast().success({ msg: '缓存已清除' })
    },
  })
}
</script>

<template>
  <scroll-view scroll-y class="h-full bg-gray-50">
    <view class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell title="通知设置" icon="bell" is-link />
        <wd-cell title="清除缓存" icon="delete" is-link :value="cacheSize" @click="onClearCache" />
        <wd-cell
          title="关于"
          icon="info-circle"
          is-link
          @click="router.push({ name: 'settings-about' })"
        />
      </wd-cell-group>
    </view>

    <view class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell
          v-if="userStore.isLoggedIn"
          title="退出登录"
          title-class="text-red-500"
          @click="onLogout"
        />
        <!-- #ifdef APP-PLUS -->
        <wd-cell
          v-if="userStore.isLoggedIn"
          title="注销账号"
          label="注销后账号数据将永久删除"
          title-class="text-red-500"
          @click="onLogoff"
        />
        <!-- #endif -->
      </wd-cell-group>
    </view>
  </scroll-view>
</template>
