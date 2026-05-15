<script setup lang="ts">
/**
 * 流式文字渲染组件
 * 接收 text prop，每次更新时平滑追加显示
 */
defineProps<{
  text: string
  streaming?: boolean
}>()
</script>

<template>
  <view>
    <mp-html v-if="text" :content="text" />
    <text v-if="streaming" class="animate-pulse text-gray-400">▋</text>
  </view>
</template>
