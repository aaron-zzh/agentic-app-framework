<script setup lang="ts">
definePage({
  name: 'chat-detail',
  meta: { requiresAuth: true },
  style: { navigationBarTitleText: 'AI 对话' },
})

const route = useRoute()
const chatId = computed(() => route.params.id as string)
</script>

<template>
  <view class="p-4">
    <text>对话详情 {{ chatId }}（待开发）</text>
  </view>
</template>
