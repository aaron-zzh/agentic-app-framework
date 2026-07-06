/**
 * 创作-工具箱
 * logo(已用) / OCR(真) / 用户画像(真) / 天气(真) / 会议记录(敬请期待) / 热点跟踪(敬请期待)
 *
 * @author AaronZZH & Kiro
 */

"use client"

import {
  ArrowUpRight,
  Cloud,
  FileText,
  Lock,
  Mic,
  PenSquare,
  QrCode,
  TrendingUp
} from "lucide-react"
import Link from "next/link"
import { GlassCard, NeonChip } from "@/components/studio"
import { cn } from "@/lib/utils/index"

interface Tool {
  key: string
  title: string
  desc: string
  icon: React.FC<{ className?: string }>
  href?: string
  soon?: boolean
  tone: "violet" | "cyan" | "emerald" | "amber" | "rose"
}

const TOOLS: Tool[] = [
  {
    key: "draw",
    title: "无限画布",
    desc: "自由绘图、流程图、头脑风暴",
    icon: PenSquare,
    href: "/studio/create/draw",
    tone: "emerald"
  },
  // {
  //   key: "logo",
  //   title: "Logo 生成",
  //   desc: "品牌 Logo / 头像 / Banner 快速生成",
  //   icon: ImageIcon,
  //   href: "/studio/create/image?preset=logo",
  //   tone: "violet"
  // },
  {
    key: "ocr",
    title: "图片文字提取",
    desc: "上传图片，AI 提取文字内容",
    icon: FileText,
    href: "/studio/create/tools/ocr",
    tone: "cyan"
  },
  {
    key: "weather",
    title: "实时天气",
    desc: "查询城市天气，内容创作参考",
    icon: Cloud,
    href: "/studio/create/tools/weather",
    tone: "amber"
  },
  {
    key: "qrcode",
    title: "二维码生成",
    desc: "本地生成二维码，支持自定义风格与贴图下载",
    icon: QrCode,
    href: "/studio/create/tools/qrcode",
    tone: "violet"
  },
  {
    key: "meeting",
    title: "会议记录",
    desc: "录音转文字 + 会议摘要生成",
    icon: Mic,
    href: "/studio/create/tools/meeting",
    tone: "rose"
  },
  {
    key: "trending",
    title: "热点跟踪",
    desc: "精选热榜 + 内容借势建议",
    icon: TrendingUp,
    href: "/studio/create/tools/hot",
    tone: "cyan"
  }
  // {
  //   key: "model3d",
  //   title: "3D 模型生成",
  //   desc: "文字描述一键生成 3D 模型",
  //   icon: Layers,
  //   href: "/studio/create/tools/3d",
  //   tone: "violet"
  // }
]

export default function StudioCreateToolsPage() {
  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6">
      <header className="space-y-2">
        <h1 className="font-semibold text-xl">工具箱</h1>
        <p className="text-muted-foreground text-sm">内置实用工具，提升内容创作效率</p>
      </header>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TOOLS.map((tool) => {
          const Icon = tool.icon
          const inner = (
            <GlassCard
              glow={tool.soon ? "none" : "accent"}
              interactive={!tool.soon}
              className={cn("h-full", tool.soon && "opacity-60")}
            >
              <div className="flex h-full flex-col gap-3 p-5">
                <div className="flex items-start justify-between gap-2">
                  <div
                    className={`flex size-10 items-center justify-center rounded-xl bg-foreground/[0.04] text-${tool.tone}-300`}
                  >
                    <Icon className="size-5" />
                  </div>
                  {tool.soon && (
                    <NeonChip tone="neutral" size="sm">
                      <Lock className="size-3" />
                      敬请期待
                    </NeonChip>
                  )}
                </div>
                <div className="space-y-1">
                  <p className="font-medium text-base">{tool.title}</p>
                  <p className="text-muted-foreground text-xs leading-5">{tool.desc}</p>
                </div>
                {!tool.soon && (
                  <div className="mt-auto flex items-center justify-between text-muted-foreground text-xs">
                    <span>立即使用</span>
                    <ArrowUpRight className="size-3.5" />
                  </div>
                )}
              </div>
            </GlassCard>
          )

          if (tool.href && !tool.soon) {
            return (
              <Link key={tool.key} href={tool.href} className="block focus-visible:outline-none">
                {inner}
              </Link>
            )
          }
          return <div key={tool.key}>{inner}</div>
        })}
      </div>
    </div>
  )
}
