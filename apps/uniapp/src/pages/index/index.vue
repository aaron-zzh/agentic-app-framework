<script setup lang="ts">
definePage({
  name: 'index',
  layout: 'tabbar',
  style: { navigationBarTitleText: '首页' },
})

// 公告文字（后续从接口获取）
const notice = ref('欢迎使用，如有问题请联系客服')

// 服务宫格（后续从接口获取）
const serviceList = ref([
  { name: '帮帮忙', icon: 'chat', color: '#4CAF50' },
  { name: '帮我买', icon: 'cart', color: '#FF9800' },
  { name: '代拿快递', icon: 'gift', color: '#2196F3' },
  { name: '打印服务', icon: 'file-text', color: '#9C27B0' },
  { name: '帮打饭', icon: 'star', color: '#F44336' },
  { name: '代清洁', icon: 'setting', color: '#00BCD4' },
  { name: '代搬运', icon: 'location', color: '#FF5722' },
  { name: '更多服务', icon: 'add', color: '#607D8B' },
])

function onServiceTap(item: { name: string }) {
  uni.showToast({ title: `${item.name}（待开发）`, icon: 'none' })
}
</script>

<template>
  <scroll-view scroll-y class="h-full">
    <!-- Banner 占位 -->
    <view class="mx-4 mt-4 h-40 flex items-center justify-center rounded-2 bg-gray-100">
      <text class="text-gray-400">Banner（待接入）</text>
    </view>

    <!-- 公告栏 -->
    <wd-notice-bar
      class="mx-4 mt-3 rounded-2"
      prefix="volume"
      :text="notice"
    />

    <!-- 服务宫格 -->
    <view class="mx-4 mt-4 rounded-2 bg-white p-4">
      <view class="grid grid-cols-4 gap-4">
        <view
          v-for="item in serviceList"
          :key="item.name"
          class="flex flex-col items-center gap-1"
          @tap="onServiceTap(item)"
        >
          <view
            class="h-12 w-12 flex items-center justify-center rounded-full"
            :style="{ background: item.color }"
          >
            <wd-icon :name="item.icon" size="24px" color="#fff" />
          </view>
          <text class="text-xs text-gray-600">{{ item.name }}</text>
        </view>
      </view>
    </view>
  </scroll-view>
</template>
