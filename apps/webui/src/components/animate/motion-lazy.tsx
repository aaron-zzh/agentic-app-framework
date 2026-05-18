"use client"

import { LazyMotion } from "framer-motion"

export type MotionLazyProps = {
  children: React.ReactNode
}

const loadFeatures = () => import("./features").then((res) => res.default)

export function MotionLazy({ children }: MotionLazyProps) {
  return (
    <LazyMotion strict features={loadFeatures}>
      {children}
    </LazyMotion>
  )
}
