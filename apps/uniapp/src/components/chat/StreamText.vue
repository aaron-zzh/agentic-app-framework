<script setup lang="ts">
import { marked } from 'marked'

defineProps<{
  text: string
  streaming?: boolean
}>()

function renderMarkdown(content: string): string {
  if (!content)
    return ''
  return marked.parse(content, { async: false }) as string
}
</script>

<template>
  <view>
    <mp-html v-if="text" :content="renderMarkdown(text)" />
    <text v-if="streaming" class="animate-pulse text-gray-400">▋</text>
  </view>
</template>
