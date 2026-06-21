/**
 * 通知服务——封装 sonner toast，提供语义化 API
 * @author AaronZZH & Kiro
 *
 * @example
 * ```ts
 * import { notify } from "@/lib/notification"
 *
 * notify.success("保存成功")
 * notify.error("保存失败", { description: "网络错误" })
 * notify.success("已删除", { action: { label: "撤销", onClick: restore } })
 * notify.loading("正在保存...", { id: "save" })
 * notify.dismiss("save")
 * ```
 */

export { toast } from "sonner"

export interface NotifyOptions {
  description?: string
  /** 持续时间（ms），0 = 不自动关闭 */
  duration?: number
  action?: { label: string; onClick: () => void }
  id?: string | number
}

import { createElement } from "react"
import { toast } from "sonner"
import { LottieIcon } from "@/components/animate/LottieIcon"

// 构建统一样式的 custom toast 节点
function makeLottieToast(
  lottieName: string,
  msg: string,
  opts: ReturnType<typeof toOpts> | undefined,
  loop = false,
  titleClass = ""
) {
  return (id: string | number) =>
    createElement(
      "div",
      { className: "flex w-72 items-center gap-3 rounded-lg bg-background px-3" },
      createElement(LottieIcon, { name: lottieName, width: 48, height: 48, loop }),
      createElement(
        "div",
        { className: "flex-1 min-w-0" },
        createElement("p", { className: `font-medium text-sm ${titleClass}` }, msg),
        opts?.description
          ? createElement(
              "p",
              { className: "text-muted-foreground text-xs truncate" },
              opts.description
            )
          : null
      ),
      createElement(
        "button",
        {
          type: "button",
          className: "text-muted-foreground hover:text-foreground text-lg leading-none",
          onClick: () => toast.dismiss(id)
        },
        "×"
      )
    )
}

function toOpts(opts?: NotifyOptions) {
  if (!opts) return undefined
  return {
    description: opts.description,
    duration: opts.duration === 0 ? Number.POSITIVE_INFINITY : opts.duration,
    action: opts.action ? { label: opts.action.label, onClick: opts.action.onClick } : undefined,
    id: opts.id,
    // 有 action 按钮时隐藏关闭按钮，避免遮挡
    closeButton: !opts.action
  }
}

export const notify = {
  success: (msg: string, opts?: NotifyOptions) =>
    toast.custom(makeLottieToast("success", msg, toOpts(opts)), toOpts(opts)),
  error: (msg: string, opts?: NotifyOptions) =>
    toast.custom(makeLottieToast("error", msg, toOpts(opts)), toOpts(opts)),
  warning: (msg: string, opts?: NotifyOptions) =>
    toast.custom(makeLottieToast("warning", msg, toOpts(opts), true), toOpts(opts)),
  info: (msg: string, opts?: NotifyOptions) => toast.info(msg, toOpts(opts)),
  loading: (msg: string, opts?: NotifyOptions) => toast.loading(msg, toOpts(opts)),
  promise: <T>(
    promise: Promise<T> | (() => Promise<T>),
    messages: { loading: string; success: string; error: string }
  ) => toast.promise(promise, messages),
  dismiss: (id?: string | number) => toast.dismiss(id)
}
