/**
 * 图片合成工具：将二维码图层贴合到底图指定位置并导出。
 *
 * 坐标换算思路参考图片编辑器的框选换算方式（预览容器展示尺寸 → 图片原始像素尺寸），
 * 但用途是图层拖拽定位合成，而非框选裁剪。全程本地 Canvas 处理，不上传服务器。
 *
 * @author AaronZZH & Kiro
 */

/** 二维码图层在预览容器中的位置与尺寸（CSS 像素，相对预览容器左上角），始终正方形 */
export interface QrLayerRect {
  x: number
  y: number
  /** 边长，宽高相同 */
  size: number
}

/** 预览容器的展示尺寸（CSS 像素），用于将图层坐标换算为底图原始像素坐标 */
export interface PreviewViewport {
  width: number
  height: number
}

/**
 * 将预览容器坐标系下的图层位置换算为底图原始像素坐标系。
 */
export function toImagePixelRect(
  layer: QrLayerRect,
  viewport: PreviewViewport,
  imageWidth: number,
  imageHeight: number
): { x: number; y: number; size: number } {
  const scaleX = imageWidth / viewport.width
  const scaleY = imageHeight / viewport.height
  // 二维码保持正方形，缩放比例取宽高换算的较小值，避免因容器非等比缩放导致变形
  const scale = Math.min(scaleX, scaleY)
  return {
    x: layer.x * scaleX,
    y: layer.y * scaleY,
    size: layer.size * scale
  }
}

/**
 * 限制图层矩形完全落在底图范围内（clamp 位置，防止拖拽超出边界）。
 */
export function clampLayerRect(
  layer: QrLayerRect,
  viewport: PreviewViewport,
  minSize: number,
  maxSize: number
): QrLayerRect {
  const size = Math.min(Math.max(layer.size, minSize), maxSize)
  const maxX = Math.max(0, viewport.width - size)
  const maxY = Math.max(0, viewport.height - size)
  return {
    size,
    x: Math.min(Math.max(layer.x, 0), maxX),
    y: Math.min(Math.max(layer.y, 0), maxY)
  }
}

/**
 * 将二维码图层合成到底图上，返回合成后的 Canvas。
 *
 * @param baseImage 底图（本地文件读取的 ImageBitmap，或已加载的 HTMLImageElement）
 * @param qrImage 二维码图层图像（Canvas 或 ImageBitmap）
 * @param layer 图层在预览容器坐标系下的位置尺寸
 * @param viewport 预览容器展示尺寸
 */
export function compositeQrOntoImage(
  baseImage: ImageBitmap | HTMLImageElement,
  qrImage: HTMLCanvasElement | ImageBitmap,
  layer: QrLayerRect,
  viewport: PreviewViewport
): HTMLCanvasElement {
  const imageWidth = "naturalWidth" in baseImage ? baseImage.naturalWidth : baseImage.width
  const imageHeight = "naturalHeight" in baseImage ? baseImage.naturalHeight : baseImage.height

  const canvas = document.createElement("canvas")
  canvas.width = imageWidth
  canvas.height = imageHeight
  const ctx = canvas.getContext("2d")
  if (!ctx) {
    throw new Error("当前环境不支持 Canvas 2D 上下文")
  }

  ctx.drawImage(baseImage, 0, 0, imageWidth, imageHeight)

  const pixelRect = toImagePixelRect(layer, viewport, imageWidth, imageHeight)
  ctx.drawImage(qrImage, pixelRect.x, pixelRect.y, pixelRect.size, pixelRect.size)

  return canvas
}

/** 生成 PNG 格式的 Blob，用于本地下载合成结果 */
export function compositeCanvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error("生成图片失败"))
    }, "image/png")
  })
}
