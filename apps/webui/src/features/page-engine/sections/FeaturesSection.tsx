/**
 * FeaturesSection — 功能卡片网格（图标 + 标题 + 描述），columns 配置(2/3/4)
 * @author AaronZZH & Kiro
 */

"use client"

import { motion } from "framer-motion"
import { icons } from "lucide-react"

import { cn } from "@/lib/utils/cn"

import type { SectionComponentProps } from "../types"

interface FeatureItem {
  icon?: string
  title: string
  description?: string
}

interface FeaturesProps {
  title?: string
  subtitle?: string
  items?: FeatureItem[]
  columns?: 2 | 3 | 4
}

/** 根据图标名获取 Lucide 图标组件 */
function LucideIcon({ name }: { name: string }) {
  const Icon = icons[name as keyof typeof icons]
  if (!Icon) return null
  return <Icon className="size-6 text-primary" />
}

/** 功能亮点网格 Section */
export function FeaturesSection({ data }: SectionComponentProps) {
  const { title, subtitle, items = [], columns = 3 } = data as FeaturesProps

  const gridCols = cn(
    "grid gap-6",
    columns === 2 && "grid-cols-1 md:grid-cols-2",
    columns === 3 && "grid-cols-1 md:grid-cols-2 lg:grid-cols-3",
    columns === 4 && "grid-cols-1 md:grid-cols-2 lg:grid-cols-4"
  )

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

        {/* 卡片网格 */}
        <div className={gridCols}>
          {(items as FeatureItem[]).map((item, i) => (
            <motion.div
              key={item.title}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.08, duration: 0.4 }}
              className="rounded-xl border bg-card p-6 transition-shadow hover:shadow-md"
            >
              {item.icon && <div className="mb-3"><LucideIcon name={item.icon} /></div>}
              <h3 className="font-semibold">{item.title}</h3>
              {item.description && (
                <p className="mt-2 text-muted-foreground text-sm">{item.description}</p>
              )}
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
