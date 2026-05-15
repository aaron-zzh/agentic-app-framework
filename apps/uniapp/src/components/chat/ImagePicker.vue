<script setup lang="ts">
/**
 * 图片选择预览组件
 * 支持：相册/拍照选择、多图预览、删除、上传
 */
export interface PickedImage {
  /** 本地临时路径 */
  path: string
  /** 上传后的 URL（上传完成后填充） */
  url?: string
  /** 上传状态 */
  status: 'pending' | 'uploading' | 'done' | 'error'
}

const props = withDefaults(defineProps<{
  /** 最多选择数量 */
  maxCount?: number
  /** 是否禁用 */
  disabled?: boolean
}>(), {
  maxCount: 9,
  disabled: false,
})

const emit = defineEmits<{
  change: [images: PickedImage[]]
}>()

const images = ref<PickedImage[]>([])
const { upload } = useUploader({ mode: 'server' })

/** 选择图片 */
function choose(sourceType: Array<'album' | 'camera'> = ['album', 'camera']) {
  if (props.disabled)
    return
  const remaining = props.maxCount - images.value.length
  if (remaining <= 0) {
    useGlobalToast().warning({ msg: `最多选择 ${props.maxCount} 张` })
    return
  }
  uni.chooseImage({
    count: remaining,
    sizeType: ['compressed'],
    sourceType,
    success: async (res) => {
      const newImages: PickedImage[] = res.tempFilePaths.map(path => ({
        path,
        status: 'pending',
      }))
      images.value.push(...newImages)
      emit('change', images.value)

      // 自动上传
      for (const img of newImages) {
        img.status = 'uploading'
        try {
          img.url = await upload({ path: img.path, name: `chat-${Date.now()}.jpg`, type: 'image' })
          img.status = 'done'
        }
        catch {
          img.status = 'error'
        }
        emit('change', images.value)
      }
    },
  })
}

/** 预览图片 */
function preview(index: number) {
  uni.previewImage({
    current: index,
    urls: images.value.map(img => img.url ?? img.path),
  })
}

/** 删除图片 */
function remove(index: number) {
  images.value.splice(index, 1)
  emit('change', images.value)
}

/** 清空 */
function clear() {
  images.value = []
  emit('change', images.value)
}

defineExpose({ choose, clear, images })
</script>

<template>
  <view v-if="images.length" class="flex flex-wrap gap-2 p-2">
    <view
      v-for="(img, index) in images"
      :key="img.path"
      class="relative h-20 w-20"
    >
      <image
        :src="img.path"
        class="h-20 w-20 rounded-2"
        mode="aspectFill"
        @tap="preview(index)"
      />
      <!-- 上传中遮罩 -->
      <view
        v-if="img.status === 'uploading'"
        class="absolute inset-0 flex items-center justify-center rounded-2 bg-black/40"
      >
        <wd-loading color="#fff" size="20px" />
      </view>
      <!-- 删除按钮 -->
      <view
        class="absolute h-5 w-5 flex items-center justify-center rounded-full bg-gray-600 -right-1 -top-1"
        @tap.stop="remove(index)"
      >
        <wd-icon name="close" size="12px" color="#fff" />
      </view>
    </view>
  </view>
</template>
