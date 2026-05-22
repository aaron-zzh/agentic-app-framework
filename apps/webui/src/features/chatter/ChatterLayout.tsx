/**
 * ChatterLayout——根据 layout prop 选择容器
 * panel：直接渲染（由父组件放入 ResizablePanel）
 * dialog：非模式对话框
 * drawer：右侧抽屉
 *
 * @author AaronZZH & Kiro
 */

"use client"

import type { ReactNode } from "react"
import { Dialog, DialogContent } from "@/components/ui/dialog"
import { Sheet, SheetContent } from "@/components/ui/sheet"
import type { ChatterLayout as LayoutType } from "./types"

interface ChatterLayoutProps {
  layout: LayoutType
  open?: boolean
  onOpenChange?: (open: boolean) => void
  children: ReactNode
}

/**
 * 布局容器选择器
 * panel 模式直接渲染 children，dialog/drawer 包裹对应容器
 */
export function ChatterLayout({ layout, open, onOpenChange, children }: ChatterLayoutProps) {
  if (layout === "dialog") {
    return (
      <Dialog open={open} onOpenChange={onOpenChange} modal={false}>
        <DialogContent
          className="flex h-[70vh] w-[400px] flex-col p-0 sm:max-w-[400px]"
          showCloseButton
        >
          {children}
        </DialogContent>
      </Dialog>
    )
  }

  if (layout === "drawer") {
    return (
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent side="right" className="flex w-[400px] flex-col p-0 sm:max-w-[400px]">
          {children}
        </SheetContent>
      </Sheet>
    )
  }

  // panel：直接渲染
  return <div className="flex h-full flex-col">{children}</div>
}
