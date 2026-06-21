/**
 * 仪表盘卡片视觉 token——color → 渐变背景、图标背景、字色、sparkline 颜色、halftone 点阵装饰。
 *
 * <p>设计参考 Minimal UI Kit。色板覆盖现有 presets.ts 中已使用的色名（yellow / purple / blue / green /
 * orange / red / pink），并补充截图风格的薄荷 / 薰衣草 / 沙色 / 珊瑚 / 天蓝。
 *
 * @author AaronZZH &amp; Kiro
 */

export type CardColor =
  | "mint"
  | "lavender"
  | "sand"
  | "coral"
  | "sky"
  | "rose"
  | "blue"
  | "green"
  | "yellow"
  | "purple"
  | "orange"
  | "red"
  | "pink"

export interface CardStyle {
  /** 卡片渐变背景 tailwind 类 */
  gradient: string
  /** 主文字色 tailwind 类 */
  fg: string
  /** 图标圆角背景色 tailwind 类（柔和高亮） */
  iconBg: string
  /** 图标本身颜色 tailwind 类 */
  iconFg: string
  /** sparkline 描边色（CSS 色值，SVG 用） */
  sparklineColor: string
  /** 趋势上升 / 下降的色——保留 fg 同色避免太花 */
  trendColor: string
}

/** 兜底色——配置项 color 缺失或未知时使用。 */
export const DEFAULT_CARD_COLOR: CardColor = "mint"

export const CARD_COLOR_STYLES: Record<CardColor, CardStyle> = {
  mint: {
    gradient: "bg-gradient-to-br from-emerald-50 to-emerald-100/80",
    fg: "text-emerald-900",
    iconBg: "bg-emerald-200/60",
    iconFg: "text-emerald-700",
    sparklineColor: "rgb(5 150 105)",
    trendColor: "text-emerald-700"
  },
  lavender: {
    gradient: "bg-gradient-to-br from-violet-50 to-violet-100/80",
    fg: "text-violet-900",
    iconBg: "bg-violet-200/60",
    iconFg: "text-violet-700",
    sparklineColor: "rgb(124 58 237)",
    trendColor: "text-violet-700"
  },
  sand: {
    gradient: "bg-gradient-to-br from-amber-50 to-amber-100/80",
    fg: "text-amber-900",
    iconBg: "bg-amber-200/60",
    iconFg: "text-amber-700",
    sparklineColor: "rgb(217 119 6)",
    trendColor: "text-amber-700"
  },
  coral: {
    gradient: "bg-gradient-to-br from-orange-50 to-orange-100/80",
    fg: "text-orange-900",
    iconBg: "bg-orange-200/60",
    iconFg: "text-orange-700",
    sparklineColor: "rgb(234 88 12)",
    trendColor: "text-orange-700"
  },
  sky: {
    gradient: "bg-gradient-to-br from-sky-50 to-sky-100/80",
    fg: "text-sky-900",
    iconBg: "bg-sky-200/60",
    iconFg: "text-sky-700",
    sparklineColor: "rgb(2 132 199)",
    trendColor: "text-sky-700"
  },
  rose: {
    gradient: "bg-gradient-to-br from-rose-50 to-rose-100/80",
    fg: "text-rose-900",
    iconBg: "bg-rose-200/60",
    iconFg: "text-rose-700",
    sparklineColor: "rgb(225 29 72)",
    trendColor: "text-rose-700"
  },
  // ── 现有 preset 已用的色名 alias ──
  blue: {
    gradient: "bg-gradient-to-br from-blue-50 to-blue-100/80",
    fg: "text-blue-900",
    iconBg: "bg-blue-200/60",
    iconFg: "text-blue-700",
    sparklineColor: "rgb(37 99 235)",
    trendColor: "text-blue-700"
  },
  green: {
    gradient: "bg-gradient-to-br from-green-50 to-green-100/80",
    fg: "text-green-900",
    iconBg: "bg-green-200/60",
    iconFg: "text-green-700",
    sparklineColor: "rgb(22 163 74)",
    trendColor: "text-green-700"
  },
  yellow: {
    gradient: "bg-gradient-to-br from-yellow-50 to-yellow-100/80",
    fg: "text-yellow-900",
    iconBg: "bg-yellow-200/60",
    iconFg: "text-yellow-700",
    sparklineColor: "rgb(202 138 4)",
    trendColor: "text-yellow-700"
  },
  purple: {
    gradient: "bg-gradient-to-br from-purple-50 to-purple-100/80",
    fg: "text-purple-900",
    iconBg: "bg-purple-200/60",
    iconFg: "text-purple-700",
    sparklineColor: "rgb(147 51 234)",
    trendColor: "text-purple-700"
  },
  orange: {
    gradient: "bg-gradient-to-br from-orange-50 to-orange-100/80",
    fg: "text-orange-900",
    iconBg: "bg-orange-200/60",
    iconFg: "text-orange-700",
    sparklineColor: "rgb(234 88 12)",
    trendColor: "text-orange-700"
  },
  red: {
    gradient: "bg-gradient-to-br from-red-50 to-red-100/80",
    fg: "text-red-900",
    iconBg: "bg-red-200/60",
    iconFg: "text-red-700",
    sparklineColor: "rgb(220 38 38)",
    trendColor: "text-red-700"
  },
  pink: {
    gradient: "bg-gradient-to-br from-pink-50 to-pink-100/80",
    fg: "text-pink-900",
    iconBg: "bg-pink-200/60",
    iconFg: "text-pink-700",
    sparklineColor: "rgb(219 39 119)",
    trendColor: "text-pink-700"
  }
}

export function resolveCardStyle(color?: string): CardStyle {
  return CARD_COLOR_STYLES[color as CardColor] ?? CARD_COLOR_STYLES[DEFAULT_CARD_COLOR]
}

/**
 * 大数字紧凑格式化：1_234_567 → 1.23m / 12_345 → 12.3k / 999 → 999。
 * 用于 counter / kpi 类展示，避免长数字撑爆卡片。
 */
export function formatCompactNumber(value: number | undefined | null): string {
  if (value === undefined || value === null || Number.isNaN(value)) return "—"
  const abs = Math.abs(value)
  if (abs >= 1_000_000) return `${(value / 1_000_000).toFixed(2)}m`
  if (abs >= 1_000) return `${(value / 1_000).toFixed(1)}k`
  return String(value)
}
