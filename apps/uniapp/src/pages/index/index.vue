<script setup lang="ts">
definePage({
  name: 'index',
  meta: { public: true },
  layout: 'tabbar',
  style: { navigationBarTitleText: '', navigationStyle: 'custom' },
})

const notice = ref('欢迎使用，如有问题请联系客服')

const serviceList = ref([
  { name: '帮帮忙', icon: 'star' },
  { name: '帮我买', icon: 'star' },
  { name: '代拿快递', icon: 'star' },
  { name: '打印服务', icon: 'star' },
  { name: '帮打饭', icon: 'star' },
  { name: '代清洁', icon: 'star' },
  { name: '代搬运', icon: 'star' },
  { name: '更多服务', icon: 'star' },
])

function onServiceTap(item: { name: string }) {
  uni.showToast({ title: `${item.name}（待开发）`, icon: 'none' })
}
</script>

<template>
  <scroll-view
    scroll-y
    class="bg-white"
  >
    <!-- 背景图区域：固定定位在顶部，自然显示渐变，不裁剪 -->
    <image
      src="/static/bg/home-bg.png"
      style="position: fixed; top: 0; left: 0; width: 100%; height: 200px; z-index: 0;"
      mode="widthFix"
    />

    <!-- 内容区：200px 后开始，z-index 高于背景图 -->
    <view style="position: relative; z-index: 1; margin-top: 200px;">
      <!-- 公告栏 -->
      <wd-notice-bar
        class="mx-4 rounded-2"
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
    </view>
  </scroll-view>
</template>
