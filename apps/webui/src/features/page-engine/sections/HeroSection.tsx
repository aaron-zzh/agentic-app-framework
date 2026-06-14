/**
 * HeroSection — 首屏主视觉（主标题 + 副标题 + CTA 按钮组 + backgroundType）
 * @author AaronZZH & Kiro
 */

"use client"

import React from "react"
import { motion } from "framer-motion"
import dynamic from "next/dynamic"

import { Button } from "@/components/ui/button"

const GithubIcon = () => (
  <svg viewBox="0 0 24 24" className="size-4" fill="currentColor" aria-hidden="true">
    <path d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.531 1.032 1.531 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0 1 12 6.844a9.59 9.59 0 0 1 2.504.337c1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.02 10.02 0 0 0 22 12.017C22 6.484 17.522 2 12 2z" />
  </svg>
)
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

const ICON_MAP: Record<string, React.ReactNode> = {
  github: <GithubIcon />,
}

interface CTAButton {
  label: string
  href: string
  variant?: "default" | "outline" | "secondary" | "ghost"
  icon?: string
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
          <div className="absolute top-0 right-0 w-[20%] h-full" />
          <div className="absolute bottom-0 left-0 w-full h-[20%]" />
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
                <a href={btn.href} className="flex items-center gap-2">
                  {btn.icon && ICON_MAP[btn.icon]}
                  {btn.label}
                </a>
              </Button>
            ))}
          </div>
        )}
      </motion.div>
    </section>
  )
}
