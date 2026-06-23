/**
 * /studio/create/tools/hot——热点跟踪（v0.1 静态推荐位）
 * v0.2 将接入实时热榜 API
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowUpRight, TrendingUp } from "lucide-react"
import Link from "next/link"
import { GlassCard, GlassCardBody, NeonChip, SectionHaze } from "@/components/studio"

const STATIC_HOTSPOTS = [
  {
    id: 1,
    title: "AI 绘画工具盘点：2026 最值得用的 10 款",
    index: 9823,
    platform: "小红书",
    category: "短视频",
    topic: "AI绘画工具盘点2026"
  },
  {
    id: 2,
    title: "品牌 Logo 设计趋势：极简风回归",
    index: 8741,
    platform: "微博",
    category: "品牌",
    topic: "Logo设计趋势极简风"
  },
  {
    id: 3,
    title: "短视频创作者如何用 AI 提升 10 倍产能",
    index: 7652,
    platform: "抖音",
    category: "短视频",
    topic: "AI提升短视频产能"
  },
  {
    id: 4,
    title: "大模型降价潮：GPT-5 发布后生态巨变",
    index: 6934,
    platform: "科技媒体",
    category: "科技",
    topic: "大模型降价潮"
  },
  {
    id: 5,
    title: "家庭 Vlog 爆款公式：这样拍流量涨 300%",
    index: 5821,
    platform: "小红书",
    category: "生活",
    topic: "家庭Vlog爆款公式"
  }
]

const CATEGORY_TONE: Record<string, "violet" | "cyan" | "amber" | "rose" | "emerald"> = {
  短视频: "violet",
  品牌: "cyan",
  科技: "amber",
  生活: "emerald"
}

export default function StudioToolsHotPage() {
  return (
    <div className="relative mx-auto max-w-6xl p-6">
      <SectionHaze variant="violet" />
      <div className="relative space-y-6">
        <div className="flex items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <TrendingUp className="size-5 text-cyan-400" />
            <h1 className="font-semibold text-xl">热点跟踪</h1>
          </div>
          <NeonChip tone="cyan" size="sm">
            v0.2 实时热点
          </NeonChip>
        </div>

        <p className="text-muted-foreground text-sm">
          以下为编辑精选热点（v0.1 静态），点击借势创作文案。v0.2 将接入实时热榜 API。
        </p>

        <div className="space-y-3">
          {STATIC_HOTSPOTS.map((item, idx) => (
            <Link
              key={item.id}
              href={`/studio/create/copy?topic=${encodeURIComponent(item.topic)}`}
              className="block focus-visible:outline-none"
            >
              <GlassCard interactive glow="none">
                <GlassCardBody className="flex items-center gap-4">
                  {/* 排名 */}
                  <span
                    className={`w-7 shrink-0 text-center font-bold text-lg tabular-nums ${idx < 3 ? "text-amber-400" : "text-muted-foreground/40"}`}
                  >
                    {idx + 1}
                  </span>

                  {/* 内容 */}
                  <div className="min-w-0 flex-1 space-y-1">
                    <p className="font-medium text-sm">{item.title}</p>
                    <div className="flex items-center gap-2">
                      <NeonChip tone={CATEGORY_TONE[item.category] ?? "violet"} size="sm">
                        {item.category}
                      </NeonChip>
                      <span className="text-muted-foreground text-xs">{item.platform}</span>
                      <span className="text-muted-foreground text-xs tabular-nums">
                        🔥 {item.index.toLocaleString()}
                      </span>
                    </div>
                  </div>

                  {/* 借势 CTA */}
                  <div className="flex shrink-0 items-center gap-1 text-muted-foreground text-xs">
                    <span>借势创作</span>
                    <ArrowUpRight className="size-3.5" />
                  </div>
                </GlassCardBody>
              </GlassCard>
            </Link>
          ))}
        </div>
      </div>
    </div>
  )
}
