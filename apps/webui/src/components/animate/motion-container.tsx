"use client"

import type { HTMLMotionProps } from "framer-motion"

import { m } from "framer-motion"

import { cn } from "@/lib/utils/cn"
import { varContainer } from "./variants"

export type MotionContainerProps = HTMLMotionProps<"div"> & {
  action?: boolean
  animate?: boolean
}

export function MotionContainer({
  animate,
  children,
  className,
  action = false,
  ...other
}: MotionContainerProps) {
  return (
    <m.div
      variants={varContainer()}
      initial={action ? false : "initial"}
      animate={action ? (animate ? "animate" : "exit") : "animate"}
      exit={action ? undefined : "exit"}
      className={cn(className)}
      {...other}
    >
      {children}
    </m.div>
  )
}
