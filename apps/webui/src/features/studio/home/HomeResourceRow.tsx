/**
 * Studio 首页"我的资源"单行：图标 + 名称 + 数字，整行是跳转链接，悬停背景变亮并显示箭头。
 *
 * 数字为 undefined 且不在加载中时显示"—"（该行接口失败）；alert 为真时数字标红。
 * @author AaronZZH & Kiro
 */

import { ArrowUpRight } from "lucide-react"
import Link from "next/link"
import type * as React from "react"
import { cn } from "@/lib/utils/cn"

export type HomeResourceTone = "violet" | "cyan" | "emerald" | "amber" | "rose"

/** 图标底色与图标色 */
const TONE_CLASSES: Record<HomeResourceTone, string> = {
  violet: "bg-violet-400/10 text-violet-300",
  cyan: "bg-cyan-400/10 text-cyan-300",
  emerald: "bg-emerald-400/10 text-emerald-300",
  amber: "bg-amber-400/10 text-amber-300",
  rose: "bg-rose-400/10 text-rose-300"
}

interface HomeResourceRowProps {
  href: string
  label: string
  value: number | undefined
  loading: boolean
  icon: React.ReactNode
  tone: HomeResourceTone
  /** 数字是否需要警示（标红） */
  alert?: boolean
  /** 完整含义，悬停提示（名称缩成两字时使用） */
  hint?: string
}

export function HomeResourceRow({
  href,
  label,
  value,
  loading,
  icon,
  tone,
  alert = false,
  hint
}: HomeResourceRowProps) {
  return (
    <Link
      href={href}
      title={hint}
      className="group flex items-center gap-2 rounded-lg border border-transparent px-2 py-1.5 outline-none transition-colors hover:border-foreground/15 hover:bg-background/20 hover:backdrop-blur-sm focus-visible:border-foreground/15 focus-visible:bg-background/20 focus-visible:ring-2 focus-visible:ring-primary"
    >
      <span
        className={cn(
          "flex size-8 shrink-0 items-center justify-center rounded-lg [&_svg]:size-4",
          TONE_CLASSES[tone]
        )}
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1 truncate text-foreground/85 text-sm">{label}</span>
      {loading ? (
        <span className="h-5 w-8 animate-pulse rounded bg-foreground/10" />
      ) : (
        <span
          className={cn(
            "font-semibold text-lg tabular-nums leading-none",
            alert && value !== undefined && value > 0 ? "text-rose-400" : "text-foreground"
          )}
        >
          {value === undefined ? "—" : value.toLocaleString()}
        </span>
      )}
      <ArrowUpRight className="size-3.5 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100 group-focus-visible:opacity-100" />
    </Link>
  )
}
