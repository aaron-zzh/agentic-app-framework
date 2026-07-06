/**
 * 二维码矩阵渲染工具。
 *
 * 使用 qrcode 库仅生成矩阵数据（QRCode.create），不使用其自带的 toCanvas/toDataURL，
 * 以便自行控制渲染风格（方块/圆点/圆角）与颜色，满足本地自定义生成需求。
 *
 * @author AaronZZH & Kiro
 */

import QRCode from "qrcode"

/** 二维码渲染风格 */
export type QrCodeStyle = "square" | "dot" | "rounded"

/** 二维码容错级别，级别越高越能容忍污损/遮挡，但矩阵密度更高 */
export type QrCodeErrorLevel = "L" | "M" | "Q" | "H"

export interface QrCodeRenderOptions {
  /** 二维码内容（URL 或文本），非空 */
  text: string
  /** 输出画布边长（像素），二维码始终为正方形 */
  size: number
  /** 前景色（模块颜色），CSS 颜色值 */
  foregroundColor?: string
  /** 背景色，CSS 颜色值 */
  backgroundColor?: string
  /** 渲染风格 */
  style?: QrCodeStyle
  /** 容错级别 */
  errorCorrectionLevel?: QrCodeErrorLevel
  /** 外边距占单元格数（同 qrcode 库 margin 语义） */
  margin?: number
}

const DEFAULT_OPTIONS = {
  foregroundColor: "#000000",
  backgroundColor: "#ffffff",
  style: "square" as QrCodeStyle,
  errorCorrectionLevel: "M" as QrCodeErrorLevel,
  margin: 2
}

/** 二维码内容最大长度限制，避免超长文本导致矩阵过大生成失败 */
export const QR_CODE_MAX_TEXT_LENGTH = 2000

/**
 * 生成二维码矩阵数据。
 *
 * @throws 当 text 为空或超长、或底层库生成失败时抛出错误
 */
export function createQrCodeMatrix(
  text: string,
  errorCorrectionLevel: QrCodeErrorLevel = DEFAULT_OPTIONS.errorCorrectionLevel
) {
  if (!text.trim()) {
    throw new Error("二维码内容不能为空")
  }
  if (text.length > QR_CODE_MAX_TEXT_LENGTH) {
    throw new Error(`二维码内容长度不能超过 ${QR_CODE_MAX_TEXT_LENGTH} 字符`)
  }
  return QRCode.create(text, { errorCorrectionLevel })
}

/**
 * 将二维码矩阵按指定风格绘制到 Canvas。
 *
 * 坐标系：整个矩阵（含 margin）等比缩放填满 size×size 画布。
 */
export function renderQrCodeToCanvas(
  canvas: HTMLCanvasElement,
  options: QrCodeRenderOptions
): void {
  const {
    text,
    size,
    foregroundColor = DEFAULT_OPTIONS.foregroundColor,
    backgroundColor = DEFAULT_OPTIONS.backgroundColor,
    style = DEFAULT_OPTIONS.style,
    errorCorrectionLevel = DEFAULT_OPTIONS.errorCorrectionLevel,
    margin = DEFAULT_OPTIONS.margin
  } = options

  const qr = createQrCodeMatrix(text, errorCorrectionLevel)
  const moduleCount = qr.modules.size
  const data = qr.modules.data // Uint8Array，1 表示深色模块

  const totalUnits = moduleCount + margin * 2
  const unitSize = size / totalUnits

  canvas.width = size
  canvas.height = size
  const ctx = canvas.getContext("2d")
  if (!ctx) {
    throw new Error("当前环境不支持 Canvas 2D 上下文")
  }

  // 背景
  ctx.fillStyle = backgroundColor
  ctx.fillRect(0, 0, size, size)

  // 模块
  ctx.fillStyle = foregroundColor
  for (let row = 0; row < moduleCount; row++) {
    for (let col = 0; col < moduleCount; col++) {
      const isDark = data[row * moduleCount + col] === 1
      if (!isDark) continue
      const x = (col + margin) * unitSize
      const y = (row + margin) * unitSize
      drawModule(ctx, x, y, unitSize, style)
    }
  }
}

function drawModule(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  unitSize: number,
  style: QrCodeStyle
): void {
  switch (style) {
    case "dot": {
      const radius = (unitSize / 2) * 0.85
      const cx = x + unitSize / 2
      const cy = y + unitSize / 2
      ctx.beginPath()
      ctx.arc(cx, cy, radius, 0, Math.PI * 2)
      ctx.fill()
      return
    }
    case "rounded": {
      const inset = unitSize * 0.08
      const radius = unitSize * 0.3
      ctx.beginPath()
      ctx.roundRect(x + inset, y + inset, unitSize - inset * 2, unitSize - inset * 2, radius)
      ctx.fill()
      return
    }
    default:
      ctx.fillRect(x, y, unitSize, unitSize)
  }
}

/** 生成 PNG 格式的 Blob，用于本地下载 */
export function qrCodeCanvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob)
      else reject(new Error("生成图片失败"))
    }, "image/png")
  })
}

/**
 * 将二维码矩阵渲染为 SVG 字符串，用于矢量下载。
 */
export function renderQrCodeToSvg(options: QrCodeRenderOptions): string {
  const {
    text,
    size,
    foregroundColor = DEFAULT_OPTIONS.foregroundColor,
    backgroundColor = DEFAULT_OPTIONS.backgroundColor,
    style = DEFAULT_OPTIONS.style,
    errorCorrectionLevel = DEFAULT_OPTIONS.errorCorrectionLevel,
    margin = DEFAULT_OPTIONS.margin
  } = options

  const qr = createQrCodeMatrix(text, errorCorrectionLevel)
  const moduleCount = qr.modules.size
  const data = qr.modules.data

  const totalUnits = moduleCount + margin * 2
  const unitSize = size / totalUnits

  const shapes: string[] = []
  for (let row = 0; row < moduleCount; row++) {
    for (let col = 0; col < moduleCount; col++) {
      const isDark = data[row * moduleCount + col] === 1
      if (!isDark) continue
      const x = (col + margin) * unitSize
      const y = (row + margin) * unitSize
      shapes.push(svgModule(x, y, unitSize, style))
    }
  }

  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">` +
    `<rect width="${size}" height="${size}" fill="${backgroundColor}"/>` +
    `<g fill="${foregroundColor}">${shapes.join("")}</g>` +
    `</svg>`
  )
}

function svgModule(x: number, y: number, unitSize: number, style: QrCodeStyle): string {
  switch (style) {
    case "dot": {
      const radius = (unitSize / 2) * 0.85
      const cx = x + unitSize / 2
      const cy = y + unitSize / 2
      return `<circle cx="${cx}" cy="${cy}" r="${radius}"/>`
    }
    case "rounded": {
      const inset = unitSize * 0.08
      const radius = unitSize * 0.3
      const w = unitSize - inset * 2
      return `<rect x="${x + inset}" y="${y + inset}" width="${w}" height="${w}" rx="${radius}" ry="${radius}"/>`
    }
    default:
      return `<rect x="${x}" y="${y}" width="${unitSize}" height="${unitSize}"/>`
  }
}
