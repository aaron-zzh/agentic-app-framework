<script setup lang="ts">
definePage({
  name: 'profile',
  layout: 'tabbar',
  style: { navigationBarTitleText: '我的' },
})

const userStore = useUserStore()
</script>

<template>
  <view class="p-4">
    <text>{{ userStore.isLoggedIn ? userStore.userInfo?.nickname : '未登录' }}</text>
  </view>
</template>
