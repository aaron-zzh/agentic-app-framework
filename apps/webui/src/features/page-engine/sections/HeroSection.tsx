/**
 * HeroSection — 首屏主视觉（主标题 + 副标题 + CTA 按钮组 + backgroundType）
 * @author AaronZZH & Kiro
 */

"use client"

import { motion } from "framer-motion"
import dynamic from "next/dynamic"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"

import type { SectionComponentProps } from "../types"
import {
  type HeroBackgroundVariant,
  HeroPostprocessingBackground
} from "./HeroPostprocessingBackground"

const ParticlesR3F = dynamic(
  () => import("@/components/three/ParticlesR3F").then((m) => m.ParticlesR3F),
  { ssr: false }
)

interface CTAButton {
  label: string
  href: string
  variant?: "default" | "outline" | "secondary"
}

interface HeroProps {
  title?: string
  subtitle?: string
  buttons?: CTAButton[]
  backgroundType?: "gradient" | "image" | "plain" | "postprocessing" | "particles"
  backgroundVariant?: HeroBackgroundVariant
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
    backgroundVariant = "streams",
    backgroundImage,
    align = "center"
  } = data as HeroProps

  const bgClass = cn(
    "relative flex min-h-[calc(100svh-var(--layout-marketing-header-height))] w-full flex-col items-center justify-center overflow-hidden px-6 py-24 md:py-32",
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
          ? {
              backgroundImage: `url(${backgroundImage})`,
              backgroundSize: "cover",
              backgroundPosition: "center"
            }
          : undefined
      }
    >
      {/* 图片背景遮罩 */}
      {backgroundType === "image" && <div className="absolute inset-0 bg-background/70" />}
      {backgroundType === "postprocessing" && (
        <HeroPostprocessingBackground variant={backgroundVariant} />
      )}
      {backgroundType === "particles" && (
        <div className="absolute inset-0">
          <ParticlesR3F />
        </div>
      )}

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className={cn(
          "relative z-10 w-full",
          align === "center" && "text-center",
          (backgroundType === "postprocessing" || backgroundType === "particles") && "text-white"
        )}
      >
        <h1 className="font-bold text-4xl tracking-tight md:text-6xl">{title}</h1>
        {subtitle && (
          <p
            className={cn(
              "mt-4 text-lg text-muted-foreground md:text-xl",
              backgroundType === "postprocessing" && "text-white/72"
            )}
          >
            {subtitle}
          </p>
        )}

        {(buttons as CTAButton[]).length > 0 && (
          <div
            className={cn(
              "mt-8 flex flex-wrap gap-3",
              align === "center" && "justify-center",
              (backgroundType === "postprocessing" || backgroundType === "particles") && "dark"
            )}
          >
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
