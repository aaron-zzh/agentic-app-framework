<script setup lang="ts">
definePage({
  name: 'admin-notice',
  meta: { requiresAuth: true, requiresAdmin: true },
  style: { navigationBarTitleText: '公告管理' },
})

const router = useRouter()

interface Notice {
  id: number
  title: string
  content: string
  status: 0 | 1
  createTime: number
}

const { list, loading, loadMore, isLastPage, refresh } = usePage<Notice>('/admin/notice/page')

onReachBottom(() => loadMore())
onPullDownRefresh(() => {
  refresh()
  uni.stopPullDownRefresh()
})

function onEdit(item?: Notice) {
  router.push({ name: 'admin-notice-edit', params: item ? { id: item.id } : {} })
}

async function onToggleStatus(item: Notice) {
  await alovaInstance.Put(`/admin/notice/${item.id}/status`, { status: item.status === 1 ? 0 : 1 })
  refresh()
}
</script>

<template>
  <view class="h-full bg-gray-50">
    <!-- 新建按钮 -->
    <view class="mx-4 mt-4">
      <wd-button block icon="add" @click="onEdit()">
        发布公告
      </wd-button>
    </view>

    <wd-loading v-if="loading && !list.length" class="flex justify-center py-8" />
    <wd-empty v-else-if="!list.length" description="暂无公告" />

    <view v-else class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell
          v-for="item in list"
          :key="item.id"
          :title="item.title"
          :label="new Date(item.createTime).toLocaleDateString()"
          is-link
          @click="onEdit(item)"
        >
          <template #right-icon>
            <wd-switch
              :model-value="item.status === 1"
              size="small"
              @click.stop="onToggleStatus(item)"
            />
          </template>
        </wd-cell>
      </wd-cell-group>
    </view>

    <wd-loadmore v-if="list.length" :state="isLastPage ? 'finished' : 'loading'" />
  </view>
</template>
