/**
 * StatsSection — 数据统计（数字动画，Intersection Observer 触发）
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useRef, useState } from "react"
import { motion, useInView } from "framer-motion"

import type { SectionComponentProps } from "../types"

interface StatItem {
  value: number
  label: string
  prefix?: string
  suffix?: string
}

interface StatsProps {
  title?: string
  items?: StatItem[]
}

/** 数字动画计数器 */
function AnimatedNumber({ value, prefix, suffix }: { value: number; prefix?: string; suffix?: string }) {
  const ref = useRef<HTMLSpanElement>(null)
  const inView = useInView(ref, { once: true })
  const [display, setDisplay] = useState(0)

  useEffect(() => {
    if (!inView) return
    let start = 0
    const duration = 1500
    const step = Math.ceil(value / (duration / 16))
    const timer = setInterval(() => {
      start += step
      if (start >= value) {
        setDisplay(value)
        clearInterval(timer)
      } else {
        setDisplay(start)
      }
    }, 16)
    return () => clearInterval(timer)
  }, [inView, value])

  return (
    <span ref={ref} className="font-bold text-4xl">
      {prefix}
      {display.toLocaleString()}
      {suffix}
    </span>
  )
}

/** 数据统计 Section */
export function StatsSection({ data }: SectionComponentProps) {
  const { title, items = [] } = data as StatsProps

  return (
    <section className="w-full bg-muted/30 px-6 py-16 md:py-24">
      <div className="mx-auto max-w-7xl">
        {title && <h2 className="mb-12 text-center font-bold text-3xl">{title}</h2>}

        <div className="grid grid-cols-2 gap-8 md:grid-cols-4">
          {(items as StatItem[]).map((item, i) => (
            <motion.div
              key={item.label}
              initial={{ opacity: 0, y: 16 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
              transition={{ delay: i * 0.1 }}
              className="flex flex-col items-center text-center"
            >
              <AnimatedNumber value={item.value} prefix={item.prefix} suffix={item.suffix} />
              <span className="mt-2 text-muted-foreground text-sm">{item.label}</span>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  )
}
