<script setup lang="ts">
definePage({
  name: 'chat',
  meta: { public: true },
  layout: 'tabbar',
  style: { navigationBarTitleText: 'AI 对话' },
})

const router = useRouter()

// 对话列表（后续从接口获取）
const chatList = ref([
  { id: '1', title: '新对话', lastMessage: '开始一段新的对话', updateTime: Date.now() },
])

function onNewChat() {
  // TODO: 调用接口创建新对话
  router.push({ name: 'chat-detail', query: { id: 'new' } })
}
</script>

<template>
  <view class="h-full bg-gray-50">
    <!-- 新建对话按钮 -->
    <view class="mx-4 mt-4">
      <wd-button block icon="add" @click="onNewChat">
        新建对话
      </wd-button>
    </view>

    <!-- 对话列表 -->
    <view class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell
          v-for="item in chatList"
          :key="item.id"
          :title="item.title"
          :label="item.lastMessage"
          is-link
          @click="router.push({ name: 'chat-detail', query: { id: item.id } })"
        />
      </wd-cell-group>
    </view>
  </view>
</template>
