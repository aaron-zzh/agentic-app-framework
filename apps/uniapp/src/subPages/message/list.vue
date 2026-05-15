<script setup lang="ts">
definePage({
  name: 'message-list',
  style: { navigationBarTitleText: '消息通知' },
})

const router = useRouter()

/** 消息类型 Tab */
const tabs = [
  { label: '通知', value: 'notify' },
  { label: '公告', value: 'announce' },
  { label: '活动', value: 'activity' },
]
const activeTab = ref(0)

export interface MessageItem {
  id: number
  logId?: number
  type: string
  title: string
  /** 消息摘要（列表展示） */
  summary?: string
  /** 富文本内容（详情展示） */
  content?: string
  /** 是否已读：0=未读，1=已读 */
  hasRead: 0 | 1
  createTime: number
  /** 关联业务类型（用于跳转） */
  datasourceType?: string
  /** 关联业务 ID */
  datasourceId?: number
}

const { data: messageList, loading, send: reload } = useRequest(
  () => alovaInstance.Get<{ list: MessageItem[], total: number }>('/message/my-page', {
    params: { type: tabs[activeTab.value].value, pageNo: 1, pageSize: 20 },
  }),
  { immediate: true },
)

function onTabChange(index: number) {
  activeTab.value = index
  reload()
}

function onItemTap(item: MessageItem) {
  router.push({
    name: 'message-detail',
    params: {
      id: item.id,
      logId: item.logId,
      hasRead: item.hasRead,
      title: item.title,
      datasourceType: item.datasourceType,
      datasourceId: item.datasourceId,
    },
  })
}

/** 未读数角标（供 tabbar 使用） */
const _unreadCount = computed(() =>
  messageList.value?.list.filter(m => m.hasRead === 0).length ?? 0,
)
</script>

<template>
  <view class="h-full bg-gray-50">
    <!-- Tab 切换 -->
    <wd-tabs v-model="activeTab" @change="onTabChange">
      <wd-tab v-for="tab in tabs" :key="tab.value" :title="tab.label" />
    </wd-tabs>

    <!-- 列表 -->
    <scroll-view scroll-y class="flex-1">
      <wd-loading v-if="loading" class="flex justify-center py-8" />

      <wd-empty v-else-if="!messageList?.list.length" description="暂无消息" />

      <view v-else class="mx-4 mt-3 rounded-3 bg-white">
        <wd-cell-group>
          <wd-cell
            v-for="item in messageList?.list"
            :key="item.id"
            :title="item.title"
            :label="item.summary"
            is-link
            @click="onItemTap(item)"
          >
            <template #icon>
              <!-- 未读红点 -->
              <view class="relative mr-3">
                <wd-icon name="bell" size="20px" :color="item.hasRead ? '#ccc' : '#8e44ad'" />
                <view
                  v-if="!item.hasRead"
                  class="absolute h-2 w-2 rounded-full bg-red-500 -right-1 -top-1"
                />
              </view>
            </template>
            <template #right-icon>
              <text class="text-xs text-gray-400">
                {{ new Date(item.createTime).toLocaleDateString() }}
              </text>
            </template>
          </wd-cell>
        </wd-cell-group>
      </view>
    </scroll-view>
  </view>
</template>
