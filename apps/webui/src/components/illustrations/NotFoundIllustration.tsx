/**
 * NotFoundIllustration——404 页面插画
 * @author AaronZZH & Kiro
 */

import { cn } from "@/lib/utils/cn"

interface Props {
  className?: string
}

export function NotFoundIllustration({ className }: Props) {
  return (
    <svg
      viewBox="0 0 480 360"
      xmlns="http://www.w3.org/2000/svg"
      className={cn("h-auto w-64 max-w-full", className)}
      aria-hidden="true"
    >
      {/* 背景圆 */}
      <ellipse cx="240" cy="300" rx="180" ry="30" className="fill-muted/50" />

      {/* 文件/页面 */}
      <rect
        x="160"
        y="80"
        width="160"
        height="200"
        rx="8"
        className="fill-background stroke-border"
        strokeWidth="2"
      />
      <rect x="180" y="110" width="80" height="8" rx="4" className="fill-muted" />
      <rect x="180" y="130" width="120" height="8" rx="4" className="fill-muted" />
      <rect x="180" y="150" width="100" height="8" rx="4" className="fill-muted" />
      <rect x="180" y="170" width="110" height="8" rx="4" className="fill-muted" />

      {/* 放大镜 */}
      <circle
        cx="300"
        cy="200"
        r="40"
        className="fill-none stroke-primary"
        strokeWidth="4"
        opacity="0.6"
      />
      <line
        x1="328"
        y1="228"
        x2="360"
        y2="260"
        className="stroke-primary"
        strokeWidth="6"
        strokeLinecap="round"
        opacity="0.6"
      />

      {/* 问号 */}
      <text
        x="288"
        y="210"
        className="fill-primary"
        fontSize="36"
        fontWeight="bold"
        textAnchor="middle"
        opacity="0.8"
      >
        ?
      </text>

      {/* 装饰点 */}
      <circle cx="120" cy="120" r="6" className="fill-primary/20" />
      <circle cx="380" cy="100" r="4" className="fill-primary/30" />
      <circle cx="100" cy="240" r="5" className="fill-primary/15" />
      <circle cx="400" cy="180" r="3" className="fill-primary/25" />
    </svg>
  )
}
