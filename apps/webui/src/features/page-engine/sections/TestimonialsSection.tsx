/**
 * TestimonialsSection — 用户评价卡片
 * @author AaronZZH & Kiro
 */

"use client"

import { motion } from "framer-motion"
import { Quote } from "lucide-react"

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"

import type { SectionComponentProps } from "../types"

interface TestimonialItem {
  quote: string
  author: string
  avatar?: string
  company?: string
  role?: string
}

interface TestimonialsProps {
  title?: string
  subtitle?: string
  items?: TestimonialItem[]
}

/** 用户评价 Section */
export function TestimonialsSection({ data }: SectionComponentProps) {
  const { title, subtitle, items = [] } = data as TestimonialsProps

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

        {/* 评价卡片网格 */}
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {(items as TestimonialItem[]).map((item, i) => (
            <motion.div
              key={`${item.author}-${i}`}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.08, duration: 0.4 }}
              className="flex flex-col rounded-xl border bg-card p-6"
            >
              <Quote className="mb-3 size-5 text-primary/40" />
              <p className="flex-1 text-sm leading-relaxed">{item.quote}</p>

              <div className="mt-4 flex items-center gap-3 border-t pt-4">
                <Avatar className="size-9">
                  <AvatarImage src={item.avatar} alt={item.author} />
                  <AvatarFallback>{item.author[0]}</AvatarFallback>
                </Avatar>
                <div>
                  <p className="font-medium text-sm">{item.author}</p>
                  {(item.role || item.company) && (
                    <p className="text-muted-foreground text-xs">
                      {[item.role, item.company].filter(Boolean).join(" · ")}
                    </p>
                  )}
                </div>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
