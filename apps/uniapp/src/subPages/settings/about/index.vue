<script setup lang="ts">
definePage({
  name: 'settings-about',
  style: { navigationBarTitleText: '关于' },
})

const version = '0.1.0'

function openPrivacy() {
  uni.showToast({ title: '隐私政策（待完善）', icon: 'none' })
}

function openAgreement() {
  uni.showToast({ title: '用户协议（待完善）', icon: 'none' })
}

function openFeedback() {
  uni.showToast({ title: '意见反馈（待完善）', icon: 'none' })
}

function checkUpdate() {
  uni.showToast({ title: '已是最新版本', icon: 'success' })
}
</script>

<template>
  <scroll-view scroll-y class="h-full bg-gray-50">
    <!-- 应用信息 -->
    <view class="mx-4 mt-6 flex flex-col items-center gap-2 rounded-3 bg-white py-8">
      <view class="h-20 w-20 flex items-center justify-center rounded-4 bg-[#8e44ad]">
        <wd-icon name="home" size="40px" color="#fff" />
      </view>
      <text class="mt-2 text-lg text-gray-800 font-bold">校园互帮</text>
      <text class="text-sm text-gray-400">版本 {{ version }}</text>
      <text class="mt-1 text-xs text-gray-400">基于 AAF 框架构建</text>
    </view>

    <!-- 功能入口 -->
    <view class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell title="检查更新" is-link @click="checkUpdate">
          <template #right-icon>
            <wd-tag type="success" size="small">
              最新
            </wd-tag>
          </template>
        </wd-cell>
        <wd-cell title="意见反馈" icon="chat" is-link @click="openFeedback" />
      </wd-cell-group>
    </view>

    <!-- 法律条款 -->
    <view class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell title="用户服务协议" is-link @click="openAgreement" />
        <wd-cell title="隐私政策" is-link @click="openPrivacy" />
      </wd-cell-group>
    </view>

    <!-- 版权信息 -->
    <view class="mt-8 flex flex-col items-center gap-1 pb-8">
      <text class="text-xs text-gray-400">© 2026 学记 · AAF Framework</text>
      <text class="text-xs text-gray-400">Powered by wot-design-uni v2</text>
    </view>
  </scroll-view>
</template>
