<script setup lang="ts">
import type { MessageItem } from './list.vue'

definePage({
  name: 'message-detail',
  style: { navigationBarTitleText: '消息详情' },
})

const router = useRouter()
const route = useRoute()

// 从路由参数获取基本信息（避免等待接口才能显示标题）
const params = route.params as {
  id: string
  logId?: string
  hasRead?: string
  title?: string
  datasourceType?: string
  datasourceId?: string
}

const detail = ref<MessageItem | null>(null)

onLoad(async () => {
  const id = Number(params.id)

  if (id > 0) {
    // 有 id 则从接口加载完整内容
    try {
      const data = await alovaInstance.Get<MessageItem>(`/message/${id}`)
      detail.value = data
    }
    catch {
      // 接口失败时用路由参数兜底
      detail.value = {
        id,
        type: 'notify',
        title: params.title ?? '',
        hasRead: Number(params.hasRead ?? 1) as 0 | 1,
        createTime: Date.now(),
        datasourceType: params.datasourceType,
        datasourceId: params.datasourceId ? Number(params.datasourceId) : undefined,
      }
    }
  }

  // 标记已读
  if (params.hasRead === '0' && params.logId) {
    alovaInstance.Put(`/message/read/${params.logId}`).catch(() => {})
  }
})

/**
 * 跳转到关联业务页面
 * datasourceType 对应路由名称映射
 */
const DATASOURCE_ROUTE_MAP: Record<string, string> = {
  order: 'order-detail',
  course: 'course-detail',
  activity: 'activity-detail',
  // 后续按业务扩展
}

function onViewSource() {
  const type = detail.value?.datasourceType ?? params.datasourceType
  const id = detail.value?.datasourceId ?? Number(params.datasourceId)
  if (!type || !id)
    return

  const routeName = DATASOURCE_ROUTE_MAP[type]
  if (routeName) {
    router.push({ name: routeName, params: { id } })
  }
  else {
    useGlobalToast().warning({ msg: '暂不支持跳转此类型' })
  }
}

const hasSource = computed(() => {
  const type = detail.value?.datasourceType ?? params.datasourceType
  return !!type && !!DATASOURCE_ROUTE_MAP[type]
})
</script>

<template>
  <scroll-view scroll-y class="h-full bg-white px-4 py-4">
    <view v-if="detail || params.title">
      <!-- 标题 -->
      <text class="block text-xl text-gray-800 font-bold leading-relaxed">
        {{ detail?.title ?? params.title }}
      </text>

      <!-- 时间 -->
      <text class="mt-2 block text-sm text-gray-400">
        {{ detail?.createTime ? new Date(detail.createTime).toLocaleString() : '' }}
      </text>

      <wd-divider class="my-4" />

      <!-- 富文本内容 -->
      <mp-html v-if="detail?.content" :content="detail.content" />
      <text v-else class="text-gray-500">加载中...</text>

      <!-- 关联业务跳转按钮 -->
      <view v-if="hasSource" class="mt-6">
        <wd-button block type="primary" @click="onViewSource">
          查看详情
        </wd-button>
      </view>
    </view>

    <wd-empty v-else description="消息不存在" />
  </scroll-view>
</template>
