<script setup lang="ts">
import { getMessageList } from '@/api/message'
import type { MessageItem } from '@/api/message'

definePage({
  name: 'message-list',
  style: { navigationBarTitleText: '消息通知' },
})

const router = useRouter()

const {
  data: messageList,
  loading,
  isLastPage,
  total,
  loadMore,
  refresh,
} = usePagination(
  (page, pageSize) => getMessageList(page, pageSize),
  {
    initialPage: 1,
    initialPageSize: 10,
    // 告诉 alova 如何从响应中取列表和总数
    data: res => res.data,
    total: res => res.total,
    // 追加模式：loadMore 时追加到列表末尾
    append: true,
  },
)

function onItemTap(item: MessageItem) {
  router.push({
    name: 'message-detail',
    query: { id: String(item.id), title: item.title },
  })
}
</script>

<template>
  <view class="h-full bg-gray-50">
    <!-- 列表 -->
    <scroll-view
      scroll-y
      class="h-full"
      refresher-enabled
      :refresher-triggered="loading && messageList?.length === 0"
      @refresherrefresh="refresh"
      @scrolltolower="!isLastPage && loadMore()"
    >
      <wd-loading v-if="loading && !messageList?.length" class="flex justify-center py-8" />

      <wd-empty v-else-if="!messageList?.length" description="暂无消息" />

      <view v-else>
        <view class="mx-4 mt-3 rounded-3 bg-white">
          <wd-cell-group>
            <wd-cell
              v-for="item in messageList"
              :key="item.id"
              :title="item.title"
              :label="item.content"
              is-link
              @click="onItemTap(item)"
            >
              <template #icon>
                <view class="relative mr-3">
                  <wd-icon name="bell" size="20px" :color="item.read ? '#ccc' : '#8e44ad'" />
                  <view v-if="!item.read" class="absolute h-2 w-2 rounded-full bg-red-500 -right-1 -top-1" />
                </view>
              </template>
              <template #right-icon>
                <text class="text-xs text-gray-400">{{ item.createdAt.slice(0, 10) }}</text>
              </template>
            </wd-cell>
          </wd-cell-group>
        </view>

        <!-- 底部状态 -->
        <view class="py-4 text-center text-xs text-gray-400">
          <wd-loading v-if="loading" size="16px" />
          <text v-else-if="isLastPage">共 {{ total }} 条，已全部加载</text>
          <text v-else @tap="loadMore">上拉加载更多</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>
