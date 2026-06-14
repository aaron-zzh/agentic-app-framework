/**
 * PricingSection — 定价卡片（方案名 + 价格 + 功能列表 + CTA），highlighted 推荐
 * @author AaronZZH & Kiro
 */

"use client"

import { motion } from "framer-motion"
import { Check, X } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils/cn"

import type { SectionComponentProps } from "../types"

interface PricingFeature {
  text: string
  included: boolean
}

interface PricingPlan {
  name: string
  price: string
  period?: string
  description?: string
  features: (PricingFeature | string)[]
  cta?: { label: string; href: string }
  highlighted?: boolean
}

interface PricingProps {
  title?: string
  subtitle?: string
  plans?: PricingPlan[]
}

/** 定价方案 Section */
export function PricingSection({ data }: SectionComponentProps) {
  const { title, subtitle, plans = [] } = data as PricingProps

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

        {/* 定价卡片 */}
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {(plans as PricingPlan[]).map((plan, i) => (
            <motion.div
              key={plan.name}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.1, duration: 0.4 }}
              className={cn(
                "relative flex flex-col rounded-xl border p-6",
                plan.highlighted && "border-primary shadow-lg"
              )}
            >
              {plan.highlighted && (
                <Badge className="absolute -top-3 left-1/2 -translate-x-1/2">推荐</Badge>
              )}

              <h3 className="font-semibold text-lg">{plan.name}</h3>
              {plan.description && (
                <p className="mt-1 text-muted-foreground text-sm">{plan.description}</p>
              )}

              <div className="mt-4 flex items-baseline gap-1">
                <span className="font-bold text-3xl">{plan.price}</span>
                {plan.period && (
                  <span className="text-muted-foreground text-sm">/{plan.period}</span>
                )}
              </div>

              {/* 功能列表 */}
              <ul className="mt-6 flex flex-1 flex-col gap-2">
                {plan.features.map((feat, i) => {
                  const text = typeof feat === "string" ? feat : feat.text
                  const included = typeof feat === "string" ? true : feat.included
                  return (
                    <li key={i} className="flex items-center gap-2 text-sm">
                      {included ? (
                        <Check className="size-4 text-primary" />
                      ) : (
                        <X className="size-4 text-muted-foreground/50" />
                      )}
                      <span className={cn(!included && "text-muted-foreground/60")}>{text}</span>
                    </li>
                  )
                })}
              </ul>

              {/* CTA */}
              {plan.cta && (
                <Button
                  variant={plan.highlighted ? "default" : "outline"}
                  className="mt-6 w-full"
                  asChild
                >
                  <a href={plan.cta.href}>{plan.cta.label}</a>
                </Button>
              )}
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
