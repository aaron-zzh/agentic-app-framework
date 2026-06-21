/**
 * 动画组件示例页——展示 framer-motion variants 和 AnimateBorder
 */

"use client"

import { AnimatePresence, m } from "framer-motion"
import { useState } from "react"
import {
  AnimateBorder,
  MotionContainer,
  MotionViewport,
  RoseCurveLoader,
  transitionTap,
  varBounce,
  varFade,
  varFlip,
  varHover,
  varRotate,
  varScale,
  varSlide,
  varTap,
  varZoom
} from "@/components/animate"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"

const VARIANTS = ["Fade", "Scale", "Slide", "Rotate", "Flip", "Bounce", "Zoom"] as const

export default function AnimateDemoPage() {
  const [tab, setTab] = useState<(typeof VARIANTS)[number]>("Fade")
  const [key, setKey] = useState(0)

  return (
    <div className="mx-auto max-w-4xl space-y-12 p-8">
      <h1 className="font-bold text-2xl">Animate 组件示例</h1>

      {/* Tabs */}
      <section className="space-y-4">
        <h2 className="font-semibold text-lg">Variants（进入/退出动画）</h2>
        <div className="flex flex-wrap gap-2">
          {VARIANTS.map((v) => (
            <button
              key={v}
              type="button"
              onClick={() => {
                setTab(v)
                setKey((k) => k + 1)
              }}
              className={`rounded-md px-3 py-1.5 text-sm ${tab === v ? "bg-primary text-primary-foreground" : "bg-muted"}`}
            >
              {v}
            </button>
          ))}
        </div>

        <AnimatePresence mode="wait">
          <MotionContainer key={key} className="grid grid-cols-3 gap-4">
            {getVariantItems(tab).map((item) => (
              <m.div
                key={item.label}
                variants={item.variant}
                className="flex h-20 items-center justify-center rounded-lg bg-primary/10 font-medium text-primary text-sm"
              >
                {item.label}
              </m.div>
            ))}
          </MotionContainer>
        </AnimatePresence>
      </section>

      {/* In Viewport */}
      <section className="space-y-4">
        <h2 className="font-semibold text-lg">MotionViewport（滚动进入视口触发）</h2>
        <p className="text-muted-foreground text-sm">向下滚动查看效果</p>
        <div className="h-40" />
        <MotionViewport className="grid grid-cols-4 gap-4">
          {["fadeIn 1", "fadeIn 2", "fadeIn 3", "fadeIn 4"].map((text) => (
            <m.div
              key={text}
              variants={varFade("inUp")}
              className="flex h-24 items-center justify-center rounded-lg bg-accent font-medium text-sm"
            >
              {text}
            </m.div>
          ))}
        </MotionViewport>
      </section>

      {/* Tap & Hover */}
      <section className="space-y-4">
        <h2 className="font-semibold text-lg">Tap & Hover</h2>
        <div className="flex gap-4">
          <m.button
            type="button"
            whileHover={varHover(1.08)}
            whileTap={varTap(0.92)}
            transition={transitionTap()}
            className="rounded-lg bg-primary px-6 py-3 font-medium text-primary-foreground"
          >
            Hover & Tap me
          </m.button>
          <m.div
            whileHover={varHover(1.15)}
            whileTap={varTap(0.85)}
            transition={transitionTap()}
            className="flex size-16 cursor-pointer items-center justify-center rounded-full bg-accent font-bold"
          >
            🎯
          </m.div>
        </div>
      </section>

      {/* Rose Curve Loader */}
      <section className="space-y-6">
        <div>
          <h2 className="font-semibold text-lg">RoseCurveLoader（玫瑰曲线加载）</h2>
          <p className="mt-1 text-muted-foreground text-sm">
            基于 r = a·cos(5t) 极坐标方程，五瓣玫瑰线 + 粒子尾迹 + 呼吸缩放 + 慢速旋转。纯
            SVG，无依赖。
          </p>
        </div>

        {/* 纯色模式 */}
        <div className="space-y-2">
          <p className="font-medium text-sm">纯色</p>
          <div className="flex flex-wrap items-end gap-8">
            <div className="flex flex-col items-center gap-2">
              <RoseCurveLoader size={40} />
              <span className="text-muted-foreground text-xs">40px</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <RoseCurveLoader size={80} />
              <span className="text-muted-foreground text-xs">80px 默认</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <RoseCurveLoader size={80} className="text-primary" />
              <span className="text-muted-foreground text-xs">text-primary</span>
            </div>
          </div>
        </div>

        {/* 渐变光效模式 */}
        <div className="space-y-2">
          <p className="font-medium text-sm">渐变光效</p>
          <div className="flex flex-wrap items-end gap-6">
            {/* 默认紫→青 */}
            <div className="flex flex-col items-center gap-2 rounded-xl bg-slate-950 p-5">
              <RoseCurveLoader size={80} gradient />
              <span className="text-slate-400 text-xs">默认（紫→青）</span>
            </div>
            {/* 默认 + glow */}
            <div className="flex flex-col items-center gap-2 rounded-xl bg-slate-950 p-5">
              <RoseCurveLoader size={80} gradient glow />
              <span className="text-slate-400 text-xs">渐变 + glow</span>
            </div>
            {/* 自定义金→橙 */}
            <div className="flex flex-col items-center gap-2 rounded-xl bg-slate-950 p-5">
              <RoseCurveLoader size={80} gradient={{ from: "#fbbf24", to: "#f43f5e" }} glow />
              <span className="text-slate-400 text-xs">金→玫红 + glow</span>
            </div>
            {/* 绿→蓝 */}
            <div className="flex flex-col items-center gap-2 rounded-xl bg-slate-950 p-5">
              <RoseCurveLoader size={80} gradient={{ from: "#34d399", to: "#6366f1" }} glow />
              <span className="text-slate-400 text-xs">翠绿→靛蓝 + glow</span>
            </div>
            {/* 大尺寸 */}
            <div className="flex flex-col items-center gap-2 rounded-xl bg-slate-950 p-5">
              <RoseCurveLoader size={120} gradient glow />
              <span className="text-slate-400 text-xs">120px + glow</span>
            </div>
          </div>
        </div>
      </section>

      {/* AnimateBorder */}
      <section className="space-y-4">
        <h2 className="font-semibold text-lg">AnimateBorder（旋转光圈）</h2>
        <div className="flex items-center gap-8">
          {/* 头像 */}
          <div className="text-center">
            <AnimateBorder rounded="full" borderWidth={2} size={48} glowSize={60} duration={8}>
              <Avatar className="size-full">
                <AvatarImage src="/assets/avatar/avatar.png" alt="Demo" />
                <AvatarFallback>U</AvatarFallback>
              </Avatar>
            </AnimateBorder>
            <p className="mt-2 text-muted-foreground text-xs">Avatar</p>
          </div>

          {/* 圆角矩形 */}
          <div className="text-center">
            <AnimateBorder
              rounded="xl"
              borderWidth={2}
              primaryColor="#f97316"
              secondaryColor="#8b5cf6"
              duration={6}
            >
              <div className="flex h-16 w-32 items-center justify-center rounded-xl bg-background font-medium text-sm">
                Card
              </div>
            </AnimateBorder>
            <p className="mt-2 text-muted-foreground text-xs">Rounded XL</p>
          </div>

          {/* 大尺寸 */}
          <div className="text-center">
            <AnimateBorder
              rounded="full"
              borderWidth={3}
              size={80}
              glowSize={80}
              primaryColor="#ec4899"
              secondaryColor="#14b8a6"
            >
              <div className="flex size-full items-center justify-center rounded-full bg-background font-bold text-lg">
                80px
              </div>
            </AnimateBorder>
            <p className="mt-2 text-muted-foreground text-xs">Large</p>
          </div>
        </div>
      </section>
    </div>
  )
}

function getVariantItems(tab: (typeof VARIANTS)[number]) {
  switch (tab) {
    case "Fade":
      return [
        { label: "in", variant: varFade("in") },
        { label: "inUp", variant: varFade("inUp") },
        { label: "inDown", variant: varFade("inDown") },
        { label: "inLeft", variant: varFade("inLeft") },
        { label: "inRight", variant: varFade("inRight") }
      ]
    case "Scale":
      return [
        { label: "in", variant: varScale("in") },
        { label: "inX", variant: varScale("inX") },
        { label: "inY", variant: varScale("inY") }
      ]
    case "Slide":
      return [
        { label: "inUp", variant: varSlide("inUp") },
        { label: "inDown", variant: varSlide("inDown") },
        { label: "inLeft", variant: varSlide("inLeft") },
        { label: "inRight", variant: varSlide("inRight") }
      ]
    case "Rotate":
      return [
        { label: "in", variant: varRotate("in") },
        { label: "out", variant: varRotate("out") }
      ]
    case "Flip":
      return [
        { label: "inX", variant: varFlip("inX") },
        { label: "inY", variant: varFlip("inY") }
      ]
    case "Bounce":
      return [
        { label: "in", variant: varBounce("in") },
        { label: "inUp", variant: varBounce("inUp") },
        { label: "inDown", variant: varBounce("inDown") },
        { label: "inLeft", variant: varBounce("inLeft") },
        { label: "inRight", variant: varBounce("inRight") }
      ]
    case "Zoom":
      return [
        { label: "in", variant: varZoom("in") },
        { label: "inUp", variant: varZoom("inUp") },
        { label: "inDown", variant: varZoom("inDown") },
        { label: "inLeft", variant: varZoom("inLeft") },
        { label: "inRight", variant: varZoom("inRight") }
      ]
  }
}
