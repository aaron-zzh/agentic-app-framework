/**
 * Studio 首页大屏模式的浮层尺寸，以及据此推出的中央画布可视区域。
 *
 * 浮层布局（HomeCockpitLayout）与画布（canvas/HomeCanvas）共用这里的常量，
 * 保证画布的适应视图内边距、画布内的标记与按钮都落在浮层之外的可视区。
 *
 * @example
 * const insets = { top: HOME_HEADER_HEIGHT, bottom: 216, left: true, right: true }
 * const padding = canvasFitPadding(insets)
 * const style = canvasVisibleArea(insets)
 * @author AaronZZH & Kiro
 */

import type { FitViewOptions } from "@xyflow/react"
import type { CSSProperties } from "react"

/** 页头浮层高度（px） */
export const HOME_HEADER_HEIGHT = 48
/** 浮层与容器边缘、浮层之间的间距（px） */
export const HOME_PANEL_GAP = 16
/** 左栏宽度：12%，最小 160px，最大 190px */
export const HOME_LEFT_PANEL_WIDTH = "clamp(160px, 12%, 190px)"
/** 右栏宽度：22%，最小 260px，最大 340px */
export const HOME_RIGHT_PANEL_WIDTH = "clamp(260px, 22%, 340px)"
/** 底部创作区内容宽度，居中 */
export const HOME_CREATE_WIDTH = "60%"

/** 画布被浮层遮挡的范围 */
export interface CanvasViewportInsets {
  /** 顶部被遮挡的高度（px） */
  top: number
  /** 底部被遮挡的高度（px） */
  bottom: number
  /** 左栏浮层是否存在 */
  left: boolean
  /** 右栏浮层是否存在 */
  right: boolean
}

/** 窄屏堆叠布局：画布是独立区块，没有遮挡 */
export const NO_CANVAS_INSETS: CanvasViewportInsets = {
  top: 0,
  bottom: 0,
  left: false,
  right: false
}

export type CanvasFitPadding = NonNullable<FitViewOptions["padding"]>

/**
 * 适应视图时的分边内边距：有浮层的一侧按浮层宽度留白（左约 14%、右约 24%），
 * 上下按遮挡高度留白，图内容落在中间可视区。
 */
export function canvasFitPadding(insets: CanvasViewportInsets): CanvasFitPadding {
  return {
    top: `${insets.top + HOME_PANEL_GAP}px`,
    bottom: `${insets.bottom + HOME_PANEL_GAP}px`,
    left: insets.left ? "14%" : "4%",
    right: insets.right ? "24%" : "4%"
  }
}

/** 画布可视区域的绝对定位样式，用于放置画布内的"示例数据"标记、任务切换条与"适应画布"按钮 */
export function canvasVisibleArea(insets: CanvasViewportInsets): CSSProperties {
  const sideGap = HOME_PANEL_GAP * 2
  return {
    top: insets.top + 8,
    bottom: insets.bottom + 8,
    left: insets.left ? `calc(${HOME_LEFT_PANEL_WIDTH} + ${sideGap}px)` : 12,
    right: insets.right ? `calc(${HOME_RIGHT_PANEL_WIDTH} + ${sideGap}px)` : 12
  }
}
