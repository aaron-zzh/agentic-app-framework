/**
 * ShowcaseSection — Tab 切换产品截图/演示
 * @author AaronZZH & Kiro
 */

"use client"

import { AnimatePresence, motion } from "framer-motion"
import Image from "next/image"
import { useState } from "react"

import { $url } from "@/lib/utils"

import { cn } from "@/lib/utils/cn"

import type { SectionComponentProps } from "../types"

interface ShowcaseTab {
  label: string
  image: string
  description?: string
}

interface ShowcaseProps {
  title?: string
  subtitle?: string
  tabs?: ShowcaseTab[]
}

/** 产品截图展示 Section */
export function ShowcaseSection({ data }: SectionComponentProps) {
  const { title, subtitle, tabs = [] } = data as ShowcaseProps
  const [active, setActive] = useState(0)
  const currentTab = (tabs as ShowcaseTab[])[active]

  return (
    <section className="w-full px-6 py-16 md:py-24">
      <div className="mx-auto max-w-7xl">
        {/* 标题区 */}
        {(title || subtitle) && (
          <div className="mb-12 text-center">
            {title && <h2 className="font-bold text-3xl">{title}</h2>}
            {subtitle && <p className="mt-3 text-muted-foreground">{subtitle}</p>}
          </div>
        )}

        {/* Tab 切换 */}
        {(tabs as ShowcaseTab[]).length > 0 && (
          <>
            <div className="mb-8 flex flex-wrap justify-center gap-2">
              {(tabs as ShowcaseTab[]).map((tab, i) => (
                <button
                  key={tab.label}
                  type="button"
                  onClick={() => setActive(i)}
                  className={cn(
                    "rounded-full px-4 py-2 text-sm transition-colors",
                    i === active
                      ? "bg-primary text-primary-foreground"
                      : "bg-muted text-muted-foreground hover:text-foreground"
                  )}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* 截图展示 */}
            <AnimatePresence mode="wait">
              {currentTab && (
                <motion.div
                  key={active}
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  transition={{ duration: 0.3 }}
                  className="flex flex-col items-center"
                >
                  <div className="relative aspect-video w-full max-w-4xl overflow-hidden rounded-xl border shadow-lg">
                    <Image
                      src={$url.cdn(currentTab.image)}
                      alt={currentTab.label}
                      fill
                      className="object-cover"
                      sizes="(max-width: 768px) 100vw, 896px"
                    />
                  </div>
                  {currentTab.description && (
                    <p className="mt-4 text-muted-foreground text-sm">{currentTab.description}</p>
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </>
        )}
      </div>
    </section>
  )
}
