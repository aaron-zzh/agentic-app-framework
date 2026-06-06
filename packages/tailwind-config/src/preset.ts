/**
 * AAF 共享 Tailwind CSS 预设
 * 定义跨应用共享的设计 token（颜色、字体、间距、圆角）
 *
 * Tailwind v4 CSS-first 模式下，通过 @import 引入共享 CSS 变量。
 * 本文件导出 token 常量供 JS 侧引用（如动态样式计算）。
 *
 * @author AaronZZH & Kiro
 */

/** 布局 token */
export const layout = {
  sidebarWidth: "240px",
  sidebarCollapsedWidth: "64px",
  headerHeight: "48px",
  contentPadding: "16px",
  marketingHeaderHeight: "64px",
  marketingMaxWidth: "1200px",
  authCardWidth: "420px",
  canvasToolbarHeight: "48px"
} as const

/** 字体栈 */
export const fontFamily = {
  sans: [
    "var(--font-geist-sans)",
    "var(--font-noto-sans-sc)",
    "PingFang SC",
    "Microsoft YaHei",
    "Hiragino Sans GB",
    "Noto Sans CJK SC",
    "system-ui",
    "sans-serif"
  ],
  mono: [
    "var(--font-geist-mono)",
    "ui-monospace",
    "SFMono-Regular",
    "Menlo",
    "Monaco",
    "Consolas",
    "Courier New",
    "monospace"
  ]
} as const

/** 圆角 token */
export const radius = {
  sm: "calc(var(--radius) - 4px)",
  md: "calc(var(--radius) - 2px)",
  lg: "var(--radius)",
  xl: "calc(var(--radius) + 4px)"
} as const
