<script setup lang="ts">
/**
 * 固定模板海报生成组件（lime-painter 声明式 JSON）
 *
 * 支持海报类型：
 * - `share`：通用分享海报（头像 + 标题 + 二维码）
 * - `profile`：用户名片（头像 + 昵称 + 二维码）
 * - `custom`：自定义（直接传 views JSON）
 *
 * 用法：
 *   const ref = useTemplateRef('posterRef')
 *   const url = await ref.generate()
 */

export type PosterType = 'share' | 'profile' | 'custom'

export interface PosterShareInfo {
  /** 海报类型 */
  type: PosterType
  /** 海报宽度（px），默认设备宽度 * 0.9 */
  width?: number
  /** 背景图 URL */
  bgImage?: string
  /** 头像 URL */
  avatar?: string
  /** 昵称 */
  nickname?: string
  /** 标题 */
  title?: string
  /** 副标题 / 描述 */
  desc?: string
  /** 二维码内容（URL 或文字） */
  qrcode?: string
  /** 自定义 views（type=custom 时使用） */
  views?: unknown[]
}

const props = defineProps<{ info: PosterShareInfo }>()

const painterRef = ref()
const resultUrl = ref('')

/** 根据类型构建 lime-painter JSON */
function buildViews(info: PosterShareInfo): unknown[] {
  const w = info.width ?? Math.round(uni.getSystemInfoSync().windowWidth * 0.9)

  if (info.type === 'custom')
    return info.views ?? []

  const views: unknown[] = []

  // 背景图
  if (info.bgImage) {
    views.push({
      type: 'image',
      src: info.bgImage,
      css: { position: 'fixed', top: '0', left: '0', width: w, zIndex: -1 },
    })
  }

  // 头像
  if (info.avatar) {
    views.push({
      type: 'image',
      src: info.avatar,
      css: { position: 'fixed', top: w * 0.06, left: w * 0.04, width: w * 0.14, height: w * 0.14, borderRadius: w * 0.07 },
    })
  }

  // 昵称
  if (info.nickname) {
    views.push({
      type: 'text',
      text: info.nickname,
      css: { position: 'fixed', top: w * 0.08, left: w * 0.22, color: '#333', fontSize: 16 },
    })
  }

  // 标题
  if (info.title) {
    views.push({
      type: 'text',
      text: info.title,
      css: { position: 'fixed', top: w * 0.25, left: w * 0.04, color: '#333', fontSize: 18, fontWeight: 'bold', maxWidth: w * 0.92 },
    })
  }

  // 描述
  if (info.desc) {
    views.push({
      type: 'text',
      text: info.desc,
      css: { position: 'fixed', top: w * 0.35, left: w * 0.04, color: '#666', fontSize: 14, maxWidth: w * 0.92 },
    })
  }

  // 二维码（小程序码用 image，H5 用 qrcode）
  if (info.qrcode) {
    // #ifdef MP-WEIXIN
    // 小程序码由后端生成 base64，此处用 image 类型
    views.push({
      type: 'image',
      src: info.qrcode,
      css: { position: 'fixed', right: w * 0.04, bottom: w * 0.06, width: w * 0.2, height: w * 0.2 },
    })
    // #endif
    // #ifndef MP-WEIXIN
    views.push({
      type: 'qrcode',
      text: info.qrcode,
      css: { position: 'fixed', right: w * 0.04, bottom: w * 0.06, width: w * 0.2, height: w * 0.2 },
    })
    // #endif
  }

  return views
}

/** 生成海报，返回临时图片路径 */
async function generate(): Promise<string> {
  resultUrl.value = ''
  const w = props.info.width ?? Math.round(uni.getSystemInfoSync().windowWidth * 0.9)
  await painterRef.value?.render({
    css: { width: w, height: Math.round(w * 1.6), background: '#fff' },
    views: buildViews(props.info),
  })
  return new Promise((resolve) => {
    const stop = watch(resultUrl, (url) => {
      if (url) {
        stop()
        resolve(url)
      }
    })
  })
}

/** 保存到相册 */
async function save(): Promise<void> {
  const url = resultUrl.value || await generate()
  return new Promise((resolve, reject) => {
    uni.saveImageToPhotosAlbum({
      filePath: url,
      success: () => {
        useGlobalToast().success({ msg: '已保存到相册' })
        resolve()
      },
      fail: () => {
        useGlobalToast().error({ msg: '保存失败，请检查相册权限' })
        reject(new Error('保存失败'))
      },
    })
  })
}

defineExpose({ generate, save, resultUrl })
</script>

<template>
  <l-painter
    ref="painterRef"
    :hidden="true"
    is-canvas-to-temp-file-path
    path-type="url"
    @success="(url: string) => resultUrl = url"
  />
</template>
