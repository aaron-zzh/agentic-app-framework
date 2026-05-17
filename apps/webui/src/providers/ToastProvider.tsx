"use client"

import { AlertCircle, AlertTriangle, CheckCircle2, Info, Loader2 } from "lucide-react"
import { Toaster } from "sonner"

/**
 * ToastProvider——全局 Toast 容器
 * @author AaronZZH & Kiro
 *
 * 参考 next-ts Snackbar 设计：
 * - unstyled + Tailwind classNames 完全自控样式
 * - toast 背景统一（bg-background），只有 icon 区域继承类型颜色
 * - 自定义 lucide 图标，与项目图标体系一致
 * - 支持 position 覆盖（每次调用 toast(msg, { position }) 即可）
 *
 * 业务代码通过 notify.success/error/... 触发，见 @/lib/notification
 */
export function ToastProvider() {
  return (
    <Toaster
      expand
      closeButton
      gap={8}
      offset={16}
      visibleToasts={4}
      position="top-right"
      icons={{
        success: <CheckCircle2 className="toast-icon-svg size-5" />,
        error: <AlertCircle className="toast-icon-svg size-5" />,
        warning: <AlertTriangle className="toast-icon-svg size-5" />,
        info: <Info className="toast-icon-svg size-5" />,
        loading: <Loader2 className="toast-icon-svg size-5 animate-spin" />
      }}
      toastOptions={{
        unstyled: true,
        classNames: {
          toast: [
            "flex w-[300px] items-center gap-3 rounded-xl border bg-background px-2 py-2",
            "shadow-lg text-sm text-foreground relative"
          ].join(" "),
          // icon 容器：48×48，圆角，背景色由类型类控制
          icon: [
            "toast-icon shrink-0 size-12 flex items-center justify-center",
            "rounded-[10px] bg-current/8 self-start"
          ].join(" "),
          content: "flex flex-col gap-0.5 flex-1 min-w-0",
          title: "font-medium leading-snug text-foreground",
          description: "text-muted-foreground text-xs leading-snug",
          closeButton: [
            "absolute top-1.5 right-1.5 size-5 rounded-full flex items-center justify-center",
            "border border-border/60 text-muted-foreground",
            "hover:bg-accent hover:border-border transition-colors cursor-pointer bg-transparent"
          ].join(" "),
          actionButton: [
            "rounded-md px-2.5 py-1 text-xs font-semibold",
            "border border-current/20 hover:bg-current/8 hover:border-current/30",
            "transition-colors cursor-pointer bg-transparent"
          ].join(" "),
          cancelButton: [
            "rounded-md px-2.5 py-1 text-xs",
            "border border-border hover:bg-accent",
            "transition-colors cursor-pointer bg-transparent"
          ].join(" "),
          // 类型颜色只作用于 icon 区域（通过 CSS 变量 currentColor 传递）
          success: "toast-success",
          error: "toast-error",
          warning: "toast-warning",
          info: "toast-info"
        }
      }}
    />
  )
}
