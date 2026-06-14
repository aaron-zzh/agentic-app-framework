"use client"

import type * as React from "react"

import { cn } from "@/lib/utils/index"

/**
 * 样式封装的原生 input。
 * 注意：项目表单体系基于 RHF + Zod，不使用 @base-ui/react 的表单体系（Field.Root 等）。
 * base-ui Input（Field.Control）需要 Field.Root context，单独使用时点击会触发内部
 * re-render 重置输入值，故此处直接用原生 <input>。
 */
function Input({ className, type, ...props }: React.ComponentProps<"input">) {
  return (
    <input
      type={type}
      data-slot="input"
      className={cn(
        "h-10 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base outline-none transition-colors file:inline-flex file:h-6 file:border-0 file:bg-transparent file:font-medium file:text-foreground file:text-sm placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:aria-invalid:border-destructive/50 dark:aria-invalid:ring-destructive/40 dark:disabled:bg-input/80",
        className
      )}
      {...props}
    />
  )
}

export { Input }
