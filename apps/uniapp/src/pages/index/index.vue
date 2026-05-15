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
  { name: '帮帮忙', icon: 'chat' },
  { name: '帮我买', icon: 'cart' },
  { name: '代拿快递', icon: 'gift' },
  { name: '打印服务', icon: 'file-text' },
  { name: '帮打饭', icon: 'star' },
  { name: '代清洁', icon: 'setting' },
  { name: '代搬运', icon: 'location' },
  { name: '更多服务', icon: 'add' },
])

function onServiceTap(item: { name: string }) {
  uni.showToast({ title: `${item.name}（待开发）`, icon: 'none' })
}
</script>

<template>
  <scroll-view scroll-y class="h-full">
    <!-- Banner（wd-swiper 占位，后续接入真实数据） -->
    <wd-swiper class="mx-4 mt-4" height="160px" :list="[]" indicator-type="dots" />

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
          <view class="h-12 w-12 flex items-center justify-center rounded-full bg-[#8e44ad]">
            <wd-icon :name="item.icon" size="24px" color="#fff" />
          </view>
          <text class="text-xs text-gray-600">{{ item.name }}</text>
        </view>
      </view>
    </view>
  </scroll-view>
</template>
