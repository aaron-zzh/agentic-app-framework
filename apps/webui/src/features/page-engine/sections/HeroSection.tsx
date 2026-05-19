/**
 * HeroSection — 首屏主视觉（主标题 + 副标题 + CTA 按钮组 + backgroundType）
 * @author AaronZZH & Kiro
 */

"use client"

import { motion } from "framer-motion"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"

import type { SectionComponentProps } from "../types"

interface CTAButton {
  label: string
  href: string
  variant?: "default" | "outline" | "secondary"
}

interface HeroProps {
  title?: string
  subtitle?: string
  buttons?: CTAButton[]
  backgroundType?: "gradient" | "image" | "plain"
  backgroundImage?: string
  align?: "center" | "left"
}

/** 首屏 Hero Section */
export function HeroSection({ data }: SectionComponentProps) {
  const {
    title = "构建下一代 AI 应用",
    subtitle,
    buttons = [],
    backgroundType = "gradient",
    backgroundImage,
    align = "center",
  } = data as HeroProps

  const bgClass = cn(
    "relative flex w-full flex-col items-center justify-center px-6 py-24 md:py-32",
    backgroundType === "gradient" &&
      "bg-gradient-to-br from-primary/10 via-background to-secondary/10",
    backgroundType === "plain" && "bg-background",
    align === "left" && "items-start"
  )

  return (
    <section
      className={bgClass}
      style={
        backgroundType === "image" && backgroundImage
          ? { backgroundImage: `url(${backgroundImage})`, backgroundSize: "cover", backgroundPosition: "center" }
          : undefined
      }
    >
      {/* 图片背景遮罩 */}
      {backgroundType === "image" && (
        <div className="absolute inset-0 bg-background/70" />
      )}

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className={cn("relative z-10 max-w-3xl", align === "center" && "text-center")}
      >
        <h1 className="font-bold text-4xl tracking-tight md:text-6xl">{title}</h1>
        {subtitle && (
          <p className="mt-4 text-lg text-muted-foreground md:text-xl">{subtitle}</p>
        )}

        {(buttons as CTAButton[]).length > 0 && (
          <div className={cn("mt-8 flex flex-wrap gap-3", align === "center" && "justify-center")}>
            {(buttons as CTAButton[]).map((btn) => (
              <Button key={btn.href} variant={btn.variant ?? "default"} size="lg" asChild>
                <a href={btn.href}>{btn.label}</a>
              </Button>
            ))}
          </div>
        )}
      </motion.div>
    </section>
  )
}
