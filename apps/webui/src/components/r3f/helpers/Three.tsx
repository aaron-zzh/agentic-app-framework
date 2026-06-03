"use client"

import { t } from "@/lib/tunnel"

export const ToThree = ({ children }: { children: React.ReactNode }) => {
  return <t.In>{children}</t.In>
}
