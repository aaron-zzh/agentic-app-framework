<script setup lang="ts">
/**
 * 海报分享弹窗
 * 包含：转发好友 / 生成海报（lime-painter）/ 复制链接
 *
 * 用法：
 *   <poster-modal ref="modalRef" :share-info="shareInfo" />
 *   modalRef.value.open()
 */
import type { PosterShareInfo } from './PosterPreview.vue'

export interface ShareInfo extends PosterShareInfo {
  /** 分享标题 */
  shareTitle?: string
  /** 分享描述 */
  shareDesc?: string
  /** 分享链接（H5 复制链接用） */
  shareLink?: string
  /** 支持的分享方式 */
  methods?: Array<'forward' | 'poster' | 'link'>
}

const props = withDefaults(defineProps<{ shareInfo: ShareInfo }>(), {
  shareInfo: () => ({ type: 'share', methods: ['forward', 'poster'] }),
})

const visible = ref(false)
const posterVisible = ref(false)
const posterUrl = ref('')
const generating = ref(false)
const posterRef = ref()

const methods = computed(() => props.shareInfo.methods ?? ['forward', 'poster'])

function open() {
  visible.value = true
}
function close() {
  visible.value = false
}

/** 生成海报 */
async function onPoster() {
  close()
  posterVisible.value = true
  generating.value = true
  posterUrl.value = ''
  try {
    posterUrl.value = await posterRef.value?.generate()
  }
  finally {
    generating.value = false
  }
}

/** 保存到相册 */
function onSave() {
  if (!posterUrl.value)
    return
  uni.saveImageToPhotosAlbum({
    filePath: posterUrl.value,
    success: () => useGlobalToast().success({ msg: '已保存到相册' }),
    fail: () => useGlobalToast().error({ msg: '保存失败，请检查相册权限' }),
  })
}

/** 复制链接 */
function onCopyLink() {
  if (!props.shareInfo.shareLink)
    return
  uni.setClipboardData({
    data: props.shareInfo.shareLink,
    success: () => useGlobalToast().success({ msg: '链接已复制' }),
  })
  close()
}

defineExpose({ open, close })
</script>

<template>
  <!-- 分享方式选择弹窗 -->
  <wd-action-sheet
    v-model="visible"
    :actions="[]"
    title="分享"
    cancel-text="取消"
  >
    <view class="flex justify-around px-4 py-6">
      <!-- 转发好友 -->
      <button
        v-if="methods.includes('forward')"
        class="flex flex-col items-center gap-2 border-none bg-transparent"
        open-type="share"
        @tap="close"
      >
        <view class="h-14 w-14 flex items-center justify-center rounded-full bg-green-500">
          <wd-icon name="chat" size="28px" color="#fff" />
        </view>
        <text class="text-xs text-gray-600">微信好友</text>
      </button>

      <!-- 生成海报 -->
      <view
        v-if="methods.includes('poster')"
        class="flex flex-col items-center gap-2"
        @tap="onPoster"
      >
        <view class="h-14 w-14 flex items-center justify-center rounded-full bg-[#8e44ad]">
          <wd-icon name="picture" size="28px" color="#fff" />
        </view>
        <text class="text-xs text-gray-600">生成海报</text>
      </view>

      <!-- 复制链接 -->
      <view
        v-if="methods.includes('link')"
        class="flex flex-col items-center gap-2"
        @tap="onCopyLink"
      >
        <view class="h-14 w-14 flex items-center justify-center rounded-full bg-blue-500">
          <wd-icon name="link" size="28px" color="#fff" />
        </view>
        <text class="text-xs text-gray-600">复制链接</text>
      </view>
    </view>
  </wd-action-sheet>

  <!-- 海报预览弹窗 -->
  <wd-popup v-model="posterVisible" position="center" round closeable>
    <view class="flex flex-col items-center gap-4 p-6">
      <!-- 生成中 -->
      <view v-if="generating" class="flex flex-col items-center gap-2 py-8">
        <wd-loading />
        <text class="text-sm text-gray-400">海报生成中...</text>
      </view>

      <!-- 海报图片 -->
      <image
        v-if="posterUrl"
        :src="posterUrl"
        class="rounded-2"
        style="width: 300px; height: 480px"
        :show-menu-by-longpress="true"
        mode="aspectFit"
      />

      <!-- 保存按钮 -->
      <wd-button v-if="posterUrl" block @click="onSave">
        保存到相册
      </wd-button>

      <!-- 隐藏的海报生成器 -->
      <poster-preview ref="posterRef" :info="shareInfo" />
    </view>
  </wd-popup>
</template>
