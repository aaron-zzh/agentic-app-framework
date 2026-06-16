/**
 * CTASection — 行动号召横幅
 * @author AaronZZH & Kiro
 */

"use client"

import { motion } from "framer-motion"
import Link from "next/link"

import { Button } from "@/components/ui/button"

import type { SectionComponentProps } from "../types"

interface CTAButton {
  label: string
  href: string
  variant?: "default" | "outline" | "secondary"
}

interface CTAProps {
  title?: string
  description?: string
  buttons?: CTAButton[]
}

/** 行动号召横幅 Section */
export function CTASection({ data }: SectionComponentProps) {
  const { title, description, buttons = [] } = data as CTAProps

  return (
    <section className="w-full px-6 py-16 md:py-24">
      <motion.div
        initial={{ opacity: 0, y: 16 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        className="mx-auto max-w-4xl rounded-2xl bg-primary/5 px-8 py-12 text-center md:px-16"
      >
        {title && <h2 className="font-bold text-3xl">{title}</h2>}
        {description && <p className="mt-3 text-muted-foreground">{description}</p>}

        {(buttons as CTAButton[]).length > 0 && (
          <div className="mt-8 flex flex-wrap justify-center gap-3">
            {(buttons as CTAButton[]).map((btn) => (
              <Button
                key={btn.href}
                variant={btn.variant ?? "default"}
                size="lg"
                nativeButton={false}
                render={<Link href={btn.href} />}
              >
                {btn.label}
              </Button>
            ))}
          </div>
        )}
      </motion.div>
    </section>
  )
}
