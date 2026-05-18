"use client"

import type { HTMLMotionProps, ViewportOptions } from "framer-motion"

import { m } from "framer-motion"

import { cn } from "@/lib/utils/cn"
import { varContainer } from "./variants"

export type MotionViewportProps = HTMLMotionProps<"div"> & {
  disableAnimate?: boolean
  viewport?: ViewportOptions
}

export function MotionViewport({
  children,
  viewport,
  className,
  disableAnimate,
  ...other
}: MotionViewportProps) {
  return (
    <m.div
      initial="initial"
      whileInView="animate"
      variants={varContainer()}
      viewport={{ once: true, amount: 0.3, ...viewport }}
      className={cn(className)}
      {...other}
    >
      {children}
    </m.div>
  )
}
