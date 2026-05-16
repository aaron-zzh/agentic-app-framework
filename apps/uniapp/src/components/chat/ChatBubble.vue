<script setup lang="ts">
/**
 * AI 对话气泡组件
 * - 用户消息：右对齐，主题色背景
 * - AI 消息：左对齐，白色背景，mp-html 渲染（支持 Markdown）
 *
 * ## 扩展能力（待实现）
 *
 * ### 1. 内嵌交互组件（widget）
 * 后端 SSE 响应中可携带 widget 字段，前端根据类型渲染对应组件：
 * - `form`：内嵌表单，用户填写后作为下一条消息发送给 AI
 * - `select`：单选/多选，用户选择后继续对话
 * - `confirm`：确认操作，如"是否预约？"
 * - `card`：展示卡片（商品、课程、用户信息等）
 * 这是 AAF 语义组件 DSL 的移动端实现，后端下发配置，前端动态渲染。
 *
 * ### 2. 内联页面跳转链接
 * AI 回复文本中可标记内置页面路由，点击直接跳转：
 * - 格式：`[文字](route://页面名称?参数)` 或后端返回特殊标记
 * - 示例：AI 回复"您可以前往[个人中心](route://profile)修改信息"
 * - 实现：mp-html 的 `@linktap` 事件拦截，识别 `route://` 协议后调用 router.push()
 * - 支持的协议：
 *   - `route://页面名` → uni-app 内部路由跳转
 *   - `http(s)://` → 打开 webview 或外部浏览器
 *   - `tel://` → 拨打电话
 *
 * ### 3. 图片消息（#3623 多模态）
 * 用户可发送图片，AI 可返回图片，在气泡中渲染缩略图，点击预览。
 */
import { marked } from 'marked'
import ChatWidget from './ChatWidget.vue'

export interface ChatMessage {
  id: string
  type: 'user' | 'assistant'
  content: string
  createTime: number
  /** AI 消息是否还在流式输出中 */
  streaming?: boolean
  /** 图片消息（用户发送的图片或 AI 返回的图片） */
  images?: string[]
  /**
   * 内嵌交互组件
   * 后端在 SSE 响应中携带此字段，前端根据 type 渲染对应组件
   */
  widget?: {
    /** form: 表单 | select: 选择 | confirm: 确认 | card: 展示卡片 */
    type: 'form' | 'select' | 'confirm' | 'card'
    /** 组件配置（字段定义、选项、数据等） */
    schema: unknown
    /** 是否已提交/已操作 */
    submitted?: boolean
  }
}

const props = defineProps<{
  message: ChatMessage
  userAvatar?: string
}>()

const emit = defineEmits<{
  /** widget 提交，将结果作为下一条消息发送 */
  widgetSubmit: [text: string]
}>()

/** Markdown → HTML，供 mp-html 渲染 */
function renderMarkdown(content: string): string {
  if (!content)
    return ''
  return marked.parse(content, { async: false }) as string
}

/**
 * 拦截 mp-html 的链接点击事件（#3626）
 * 支持协议：
 * - route://页面名?参数  → uni-app 内部路由跳转
 * - http(s)://           → webview 或外部浏览器
 * - tel://               → 拨打电话
 */
function onLinkTap(e: { href: string }) {
  const { href } = e
  if (!href)
    return

  if (href.startsWith('route://')) {
    // 解析 route://页面名?key=value
    const withoutScheme = href.slice('route://'.length)
    const [pageName, queryStr] = withoutScheme.split('?')
    const query: Record<string, string> = {}
    if (queryStr) {
      queryStr.split('&').forEach((pair) => {
        const [k, v] = pair.split('=')
        if (k)
          query[decodeURIComponent(k)] = decodeURIComponent(v ?? '')
      })
    }
    uni.navigateTo({ url: `/${pageName}${queryStr ? `?${queryStr}` : ''}` })
  }
  else if (href.startsWith('tel://')) {
    uni.makePhoneCall({ phoneNumber: href.slice('tel://'.length) })
  }
  else if (href.startsWith('http://') || href.startsWith('https://')) {
    // #ifdef MP-WEIXIN
    uni.navigateTo({ url: `/pages/webview/index?url=${encodeURIComponent(href)}` })
    // #endif
    // #ifdef H5
    window.open(href, '_blank')
    // #endif
  }
}

function onWidgetSubmit(text: string) {
  if (props.message.widget)
    props.message.widget.submitted = true
  emit('widgetSubmit', text)
}

function previewImage(current: number, urls: string[]) {
  uni.previewImage({ current, urls })
}
</script>

<template>
  <view class="px-4 py-2">
    <!-- AI 消息（左对齐） -->
    <view v-if="message.type === 'assistant'" class="flex items-start gap-2">
      <view class="h-9 w-9 flex shrink-0 items-center justify-center rounded-full bg-[#8e44ad]">
        <wd-icon name="chat" size="18px" color="#fff" />
      </view>
      <view class="max-w-4/5">
        <view class="rounded-2 rounded-tl-none bg-white p-3 shadow-sm">
          <!-- 图片消息 -->
          <view v-if="message.images?.length" class="mb-2 flex flex-wrap gap-1">
            <image
              v-for="(img, i) in message.images"
              :key="i"
              :src="img"
              class="h-24 w-24 rounded-1"
              mode="aspectFill"
              @tap="previewImage(i, message.images!)"
            />
          </view>
          <mp-html
            v-if="message.content"
            :content="renderMarkdown(message.content)"
            @linktap="onLinkTap"
          />
          <!-- 流式输出中的光标 -->
          <text v-if="message.streaming" class="animate-pulse text-gray-400">▋</text>
        </view>
        <!-- 内嵌交互组件（未提交时显示） -->
        <chat-widget
          v-if="message.widget && !message.widget.submitted"
          :widget="message.widget"
          @submit="onWidgetSubmit"
        />
      </view>
    </view>

    <!-- 用户消息（右对齐） -->
    <view v-else class="flex items-start justify-end gap-2">
      <view class="max-w-4/5 rounded-2 rounded-tr-none p-3 text-white" style="background: #8e44ad">
        <!-- 图片消息 -->
        <view v-if="message.images?.length" class="mb-2 flex flex-wrap gap-1">
          <image
            v-for="(img, i) in message.images"
            :key="i"
            :src="img"
            class="h-24 w-24 rounded-1"
            mode="aspectFill"
            @tap="previewImage(i, message.images!)"
          />
        </view>
        <text v-if="message.content" class="text-sm leading-relaxed">{{ message.content }}</text>
      </view>
      <view class="h-9 w-9 flex shrink-0 items-center justify-center rounded-full bg-gray-200">
        <wd-icon name="user" size="18px" color="#8e44ad" />
      </view>
    </view>
  </view>
</template>
