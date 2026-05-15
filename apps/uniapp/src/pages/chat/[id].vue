<script setup lang="ts">
import type { ChatMessage } from '@/components/chat/ChatBubble.vue'
import { resetBuffer, streamPost } from '@/request/stream'

definePage({
  name: 'chat-detail',
  meta: { requiresAuth: true },
  style: { navigationBarTitleText: 'AI 对话' },
})

const route = useRoute()
const chatId = computed(() => route.params.id as string)
const userStore = useUserStore()
const toast = useGlobalToast()

const messageList = ref<ChatMessage[]>([])
const inputText = ref('')
const isStreaming = ref(false)
const pagingRef = ref()
const showNewMsgTip = ref(false)

// 生成消息 ID
let msgIdCounter = 0
function genId() {
  return `msg_${Date.now()}_${++msgIdCounter}`
}

/** 加载历史消息（z-paging query 回调） */
async function onQuery(_pageNo: number, _pageSize: number) {
  try {
    // TODO: 调用接口获取历史消息
    // const { data } = await chatApi.getMessages({ chatId: chatId.value, pageNo, pageSize })
    // pagingRef.value?.completeByTotal(data.list, data.total)
    pagingRef.value?.complete([]) // 暂无历史消息
  }
  catch {
    pagingRef.value?.complete(false)
  }
}

/** 发送消息 */
async function onSend(text: string = inputText.value, images: Array<{ url?: string }> = []) {
  const trimmed = text.trim()
  const imageUrls = images.map(img => img.url).filter(Boolean) as string[]
  if (!trimmed && !imageUrls.length)
    return
  if (isStreaming.value)
    return

  inputText.value = ''

  // 追加用户消息
  const userMsg: ChatMessage = {
    id: genId(),
    type: 'user',
    content: trimmed,
    images: imageUrls.length ? imageUrls : undefined,
    createTime: Date.now(),
  }
  pagingRef.value?.addChatRecordData(userMsg)

  // 追加 AI 占位消息
  const aiMsg: ChatMessage = { id: genId(), type: 'assistant', content: '', createTime: Date.now(), streaming: true }
  pagingRef.value?.addChatRecordData(aiMsg)

  isStreaming.value = true
  resetBuffer()

  const headers = { Authorization: `Bearer ${userStore.token}` }
  const body = { chatId: chatId.value, message: trimmed, images: imageUrls.length ? imageUrls : undefined }
  const apiUrl = `${import.meta.env.VITE_API_BASE_URL}/api/ai/chat/stream`

  // #ifdef MP-WEIXIN
  streamPost(apiUrl, body, headers, {
    onData: (chunk) => {
      messageList.value[0].content += chunk
    },
    onComplete: () => {
      messageList.value[0].streaming = false
      isStreaming.value = false
      showNewMsgTip.value = true
    },
    onError: () => {
      messageList.value[0].content = '请求失败，请重试'
      messageList.value[0].streaming = false
      isStreaming.value = false
    },
  })
  // #endif

  // #ifdef H5
  const { streamPostH5 } = await import('@/request/stream_h5')
  const ctrl = new AbortController()
  streamPostH5(apiUrl, body, headers, {
    onData: (chunk) => {
      messageList.value[0].content += chunk
    },
    onComplete: () => {
      messageList.value[0].streaming = false
      isStreaming.value = false
      showNewMsgTip.value = true
    },
    onError: () => {
      messageList.value[0].content = '请求失败，请重试'
      messageList.value[0].streaming = false
      isStreaming.value = false
    },
  }, ctrl)
  // #endif
}

/** 停止流式输出 */
function onStop() {
  isStreaming.value = false
  if (messageList.value[0]?.streaming) {
    messageList.value[0].streaming = false
  }
  toast.warning({ msg: '已停止' })
}

/** 点击"有新消息"提示 → 滚动到底部 */
function onNewMsgTipClick(event: (v: boolean) => void) {
  event(false) // 禁用默认行为
  pagingRef.value?.scrollToBottom()
  showNewMsgTip.value = false
}

/** 用户向上滚动查看历史时隐藏提示 */
function onScrollToUpper() {
  showNewMsgTip.value = false
}
</script>

<template>
  <view class="h-screen flex flex-col bg-gray-50">
    <!-- 消息列表（z-paging 聊天记录模式） -->
    <z-paging
      ref="pagingRef"
      v-model="messageList"
      use-chat-record-mode
      use-virtual-list
      cell-height-mode="dynamic"
      :default-page-size="20"
      :auto-clean-list-when-reload="false"
      :auto-show-back-to-top="showNewMsgTip"
      safe-area-inset-bottom
      bottom-bg-color="#f8f8f8"
      class="flex-1"
      @query="onQuery"
      @scrolltoupper="onScrollToUpper"
      @back-to-top-click="onNewMsgTipClick"
    >
      <template #cell="{ item }">
        <view style="transform: scaleY(-1)">
          <chat-bubble :message="item" />
        </view>
      </template>
      <template #backToTop>
        <view class="flex items-center gap-1 rounded-full bg-[#8e44ad] px-3 py-1 shadow-md">
          <wd-icon name="arrow-down" size="14px" color="#fff" />
          <text class="text-xs text-white">有新消息</text>
        </view>
      </template>
    </z-paging>

    <!-- 输入框 -->
    <message-input
      v-model="inputText"
      :streaming="isStreaming"
      @send="onSend"
      @stop="onStop"
    />
  </view>
</template>
