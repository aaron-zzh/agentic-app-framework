/**
 * ActionButton——通用操作按钮（类似 Odoo header action）。
 * 点击后根据 config.type 触发弹窗/直接调用/跳转。
 */

"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { ActionDialog } from "@/components/common/ActionDialog"
import { type ActionConfig, type ActionResult, useAction } from "@/lib/hooks/use-action"

interface ActionButtonProps {
  config: ActionConfig
  variant?: "default" | "outline" | "ghost" | "destructive"
  size?: "default" | "sm" | "lg" | "icon"
  onSuccess?: (result: ActionResult) => void
  className?: string
}

export function ActionButton({ config, variant = "default", size = "default", onSuccess, className }: ActionButtonProps) {
  const [dialogOpen, setDialogOpen] = useState(false)
  const { execute, loading } = useAction(config)

  async function handleClick() {
    if (config.type === "dialog") {
      setDialogOpen(true)
      return
    }

    if (config.type === "redirect") {
      window.location.href = config.api
      return
    }

    // api_call：直接调用（可选确认）
    if (config.confirm && !window.confirm(config.confirm)) return
    const res = await execute()
    if (res.success) onSuccess?.(res)
  }

  return (
    <>
      <Button
        variant={variant}
        size={size}
        onClick={handleClick}
        disabled={loading}
        className={className}
      >
        {loading ? "处理中..." : config.label}
      </Button>

      {config.type === "dialog" && (
        <ActionDialog
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          config={config}
          onSuccess={onSuccess}
        />
      )}
    </>
  )
}
