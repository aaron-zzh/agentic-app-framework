<script setup lang="ts">
/**
 * 可编辑海报组件（gesti Canvas 手势库）
 * 支持：拖拽 / 缩放 / 旋转元素
 * 用法：
 *   const editorRef = ref()
 *   await editorRef.value.addImage(url)
 *   const result = await editorRef.value.generate()
 */
import Gesti, { DragButton, GestiController, HorizonButton, ImageBox, RotateButton, TextBox, VerticalButton, XImage } from 'gesti'

const props = withDefaults(defineProps<{
  width?: number
  height?: number
}>(), {
  width: 600,
  height: 900,
})

const canvasId = 'poster-editor-canvas'
const gesti = new Gesti()
const controller = new GestiController()
const initialized = ref(false)

onMounted(async () => {
  await initCanvas()
})

async function initCanvas() {
  await gesti.create({
    canvasId,
    element: uni.createSelectorQuery().select(`#${canvasId}`),
    width: props.width,
    height: props.height,
  })
  gesti.useController(controller)
  // 安装操作按钮
  gesti.installPlugin([
    new DragButton(),
    new RotateButton(),
    new VerticalButton(),
    new HorizonButton(),
  ])
  initialized.value = true
}

/** 添加图片元素 */
async function addImage(url: string) {
  if (!initialized.value)
    return
  const xImage = await XImage.fromUrl(url)
  const box = new ImageBox(xImage)
  await controller.add(box)
  gesti.render()
}

/** 添加文字元素 */
async function addText(text: string, color = '#333', fontSize = 32) {
  if (!initialized.value)
    return
  const box = new TextBox(text, { color, fontSize })
  await controller.add(box)
  gesti.render()
}

/** 生成海报，返回临时图片路径 */
async function generate(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.canvasToTempFilePath({
      canvasId,
      success: res => resolve(res.tempFilePath),
      fail: (err) => {
        reject(new Error(err.errMsg))
      },
    })
  })
}

/** 保存到相册 */
async function save(): Promise<void> {
  const url = await generate()
  return new Promise((resolve, reject) => {
    uni.saveImageToPhotosAlbum({
      filePath: url,
      success: () => {
        useGlobalToast().success({ msg: '已保存到相册' })
        resolve()
      },
      fail: () => {
        useGlobalToast().error({ msg: '保存失败' })
        reject(new Error('保存失败'))
      },
    })
  })
}

defineExpose({ addImage, addText, generate, save })
</script>

<template>
  <view :style="{ width: `${width}rpx`, height: `${height}rpx` }">
    <canvas
      :id="canvasId"
      :canvas-id="canvasId"
      :style="{ width: `${width}rpx`, height: `${height}rpx` }"
      @touchstart="gesti.onTouchStart($event)"
      @touchmove="gesti.onTouchMove($event)"
      @touchend="gesti.onTouchEnd($event)"
    />
  </view>
</template>
