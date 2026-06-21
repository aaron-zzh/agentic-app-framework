/**
 * Brand——应用 Logo + 名称
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * <Brand />                    // 图标 + 文字
 * <Brand collapsed />          // 仅图标
 * <Brand size="lg" />          // 大号（营销页）
 * ```
 */

import Link from "next/link"
import { APP } from "@/lib/config"
import { $url } from "@/lib/utils"
import { cn } from "@/lib/utils/cn"

interface BrandProps {
  /** 仅显示图标（侧边栏折叠态） */
  collapsed?: boolean
  /** 尺寸 */
  size?: "sm" | "md" | "lg"
  /** 链接地址，默认 / */
  href?: string
  className?: string
}

const sizeMap = {
  sm: { icon: "size-6", text: "text-base" },
  md: { icon: "size-7", text: "text-lg" },
  lg: { icon: "size-8", text: "text-xl" }
}

export function Brand({ collapsed, size = "md", href = "/", className }: BrandProps) {
  const s = sizeMap[size]

  return (
    <Link href={href} className={cn("flex items-center gap-2", className)}>
      {/* biome-ignore lint/performance/noImgElement: logo 小图标无需 next/image 优化 */}
      <img src={$url.cdn("/logo/logo.png")} alt={APP.name} className={cn("shrink-0", s.icon)} />
      {!collapsed && <span className={cn("font-bold", s.text)}>{APP.name}</span>}
    </Link>
  )
}
