<script setup lang="ts">
definePage({
  name: 'profile',
  layout: 'tabbar',
  style: { navigationBarTitleText: '我的', navigationBarBackgroundColor: '#8e44ad', navigationBarTextStyle: 'white' },
})

const userStore = useUserStore()
const router = useRouter()

const menuItems = [
  { name: '我的订单', icon: 'list', path: '' },
  { name: '我的地址', icon: 'location', path: '' },
  { name: '消息通知', icon: 'chat', path: 'message-list' },
  { name: '用户指南', icon: 'info', path: '' },
]
</script>

<template>
  <scroll-view scroll-y class="h-full bg-gray-50">
    <!-- 顶部渐变背景 + 用户信息 -->
    <view class="px-4 pb-8 pt-12" style="background: linear-gradient(180deg, #8e44ad, #3498db)">
      <view class="flex items-center gap-3" @tap="userStore.isLoggedIn ? undefined : router.push({ name: 'login' })">
        <wd-icon name="user" size="56px" color="#fff" class="rounded-full bg-white/20 p-2" />
        <view class="flex-1">
          <text class="block text-base text-white font-medium">
            {{ userStore.userInfo?.nickname ?? '点击登录' }}
          </text>
          <text class="text-xs text-white/70">
            {{ userStore.isAdmin ? '管理员' : userStore.isLoggedIn ? '普通用户' : '未登录' }}
          </text>
        </view>
        <wd-icon name="arrow-right" color="#fff" />
      </view>
    </view>

    <!-- 管理员入口 -->
    <view v-if="userStore.isAdmin" class="mx-4 rounded-3 bg-white shadow-sm -mt-4">
      <wd-cell
        title="管理后台"
        label="数据看板、用户管理、内容审核"
        icon="setting"
        is-link
        @click="router.push({ name: 'admin-dashboard' })"
      />
    </view>

    <!-- 功能宫格 -->
    <view class="mx-4 mt-4 rounded-3 bg-white p-4">
      <view class="grid grid-cols-4 gap-4">
        <view
          v-for="item in menuItems"
          :key="item.name"
          class="flex flex-col items-center gap-1"
          @tap="item.path ? router.push(item.path) : undefined"
        >
          <view class="h-12 w-12 flex items-center justify-center rounded-full bg-[#f0e6ff]">
            <wd-icon :name="item.icon" size="24px" color="#8e44ad" />
          </view>
          <text class="text-xs text-gray-600">{{ item.name }}</text>
        </view>
      </view>
    </view>

    <!-- 其他设置 -->
    <view class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell title="设置" icon="setting" is-link @click="router.push({ name: 'settings' })" />
        <wd-cell title="联系客服" icon="chat" is-link />
        <wd-cell v-if="userStore.isLoggedIn" title="退出登录" icon="close-circle" @click="userStore.logout()" />
      </wd-cell-group>
    </view>
  </scroll-view>
</template>
