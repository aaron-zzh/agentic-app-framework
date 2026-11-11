/**
 * Studio 首页励志短句：跟在页头日期时间之后，每 10 分钟切换一条。
 *
 * 句子按当前时间所在的 10 分钟时段取模选取，刷新页面或多个窗口看到的是同一条，无需额外定时器。
 * 当前仅前端内置文案，后续如需运营配置再接后端。
 * @author AaronZZH & Kiro
 */

"use client"

import { cn } from "@/lib/utils/cn"

/** 切换间隔 */
const MOTTO_INTERVAL_MS = 10 * 60 * 1000

/** 内置励志短句 */
const MOTTOS: readonly string[] = [
  "行动是治愈焦虑的良药",
  "今天的努力，是明天的底气",
  "慢慢来，比较快",
  "把小事做好，就是不简单",
  "专注当下，答案自会出现",
  "每一次尝试，都算数",
  "想法很好，动手更好",
  "保持好奇，持续创造",
  "进步一点点，就是赢",
  "灵感偏爱勤奋的人",
  "先完成，再完美",
  "你比想象中更有力量",
  "日拱一卒，功不唐捐",
  "难走的路，从不拥挤",
  "让作品替你说话",
  "热爱可抵岁月漫长",
  "星光不问赶路人",
  "心有所向，日复一日",
  "种一棵树，最好是现在",
  "所有坚持，终将闪光"
]

interface HomeMottoProps {
  /** 当前时间，由页头时钟传入 */
  now: Date
  className?: string
}

export function HomeMotto({ now, className }: HomeMottoProps) {
  const index = Math.floor(now.getTime() / MOTTO_INTERVAL_MS) % MOTTOS.length
  const motto = MOTTOS[index]
  return (
    <p title={motto} className={cn("min-w-0 truncate text-muted-foreground/60 text-xs", className)}>
      {motto}
    </p>
  )
}
