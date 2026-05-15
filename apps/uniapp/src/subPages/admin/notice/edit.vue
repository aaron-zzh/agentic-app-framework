<script setup lang="ts">
definePage({
  name: 'admin-notice-edit',
  meta: { requiresAuth: true, requiresAdmin: true },
  style: { navigationBarTitleText: '编辑公告' },
})

const route = useRoute()
const toast = useGlobalToast()
const id = computed(() => route.params.id as string | undefined)

const form = reactive({ title: '', content: '' })

onLoad(async () => {
  if (id.value) {
    const data = await alovaInstance.Get<{ title: string, content: string }>(`/admin/notice/${id.value}`)
    form.title = data.title
    form.content = data.content
  }
})

async function onSave() {
  if (!form.title.trim()) {
    toast.warning({ msg: '请输入标题' })
    return
  }
  if (!form.content.trim()) {
    toast.warning({ msg: '请输入内容' })
    return
  }

  if (id.value) {
    await alovaInstance.Put(`/admin/notice/${id.value}`, form)
  }
  else {
    await alovaInstance.Post('/admin/notice', form)
  }
  toast.success({ msg: '保存成功' })
  uni.navigateBack()
}
</script>

<template>
  <scroll-view scroll-y class="h-full bg-gray-50">
    <view class="mx-4 mt-4 rounded-3 bg-white p-4">
      <wd-input v-model="form.title" placeholder="公告标题" class="mb-3" />
      <wd-textarea v-model="form.content" placeholder="公告内容（支持 HTML 富文本）" :rows="10" />
    </view>
    <view class="mx-4 mt-4">
      <wd-button block @click="onSave">
        保存发布
      </wd-button>
    </view>
  </scroll-view>
</template>
