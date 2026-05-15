<script setup lang="ts">
/**
 * 消息输入组件
 * 支持：文字输入、图片选择（相册/拍照）、发送/停止
 */
import type { PickedImage } from './ImagePicker.vue'

const props = defineProps<{
  modelValue: string
  /** 是否正在流式输出（显示停止按钮） */
  streaming?: boolean
  /** 是否禁用 */
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'send': [text: string, images: PickedImage[]]
  'stop': []
}>()

const text = computed({
  get: () => props.modelValue,
  set: val => emit('update:modelValue', val),
})

const pickerRef = ref()
const pickedImages = ref<PickedImage[]>([])
const showImagePicker = ref(false)

function onImagesChange(images: PickedImage[]) {
  pickedImages.value = images
  if (images.length > 0) {
    showImagePicker.value = true
  }
}

function onSend() {
  const t = text.value.trim()
  const imgs = pickedImages.value.filter(img => img.status === 'done')
  if (!t && !imgs.length)
    return

  emit('send', t, imgs)
  text.value = ''
  pickerRef.value?.clear()
  showImagePicker.value = false
}

function onChooseImage() {
  pickerRef.value?.choose()
}
</script>

<template>
  <view class="bg-white">
    <!-- 已选图片预览 -->
    <image-picker
      v-show="showImagePicker"
      ref="pickerRef"
      :max-count="9"
      @change="onImagesChange"
    />

    <!-- 输入区域 -->
    <view class="flex items-end gap-2 border-t border-gray-100 px-3 py-2">
      <!-- 图片选择按钮 -->
      <view
        class="h-9 w-9 flex shrink-0 items-center justify-center rounded-full bg-gray-100"
        @tap="onChooseImage"
      >
        <wd-icon name="picture" size="20px" color="#666" />
      </view>

      <!-- 文字输入框 -->
      <wd-textarea
        v-model="text"
        class="flex-1"
        placeholder="请输入消息..."
        :maxlength="500"
        autosize
        :auto-height="true"
        :disabled="disabled"
        @confirm="onSend"
      />

      <!-- 发送/停止按钮 -->
      <wd-button
        v-if="!streaming"
        size="small"
        :disabled="!text.trim() && !pickedImages.filter(i => i.status === 'done').length"
        @click="onSend"
      >
        发送
      </wd-button>
      <wd-button
        v-else
        size="small"
        type="error"
        @click="emit('stop')"
      >
        停止
      </wd-button>
    </view>
  </view>
</template>
