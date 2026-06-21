/**
 * ApplyPresetDialog——应用预设布局弹窗
 * @author AaronZZH & Kiro
 */

"use client"

import { LayoutTemplate } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import type { DashboardPresetVO, DashboardWidgetVO } from "@/lib/api/rest/dashboard/dashboard"
import { usePresets } from "@/lib/queries/use-dashboard"
import { useAuthStore } from "@/lib/store/auth-store"

interface ApplyPresetDialogProps {
  onApply: (widgets: DashboardWidgetVO[], refreshInterval?: number) => void
}

export function ApplyPresetDialog({ onApply }: ApplyPresetDialogProps) {
  const { data: remotePresets } = usePresets()
  const userRoles = useAuthStore((s) => s.user?.roles)
  const isAdmin = userRoles?.some((r) => ["admin", "super_admin", "org_admin"].includes(r)) ?? false

  // 仅使用后端预设；接口失败/未就绪时列表为空，弹窗显示空状态
  const presets: DashboardPresetVO[] = remotePresets ?? []

  const visiblePresets = isAdmin ? presets : presets.filter((p) => !p.adminOnly)

  const [selected, setSelected] = useState(visiblePresets[0]?.presetKey ?? "")
  const [open, setOpen] = useState(false)

  // remotePresets 异步加载完后，若 selected 仍为空则自动选中第一项
  const effectiveSelected = selected || visiblePresets[0]?.presetKey || ""

  function handleApply() {
    const preset = visiblePresets.find((p) => p.presetKey === effectiveSelected)
    if (!preset) return
    onApply(preset.widgets, preset.refreshInterval)
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        render={
          <Button variant="outline" size="sm">
            <LayoutTemplate className="mr-1 h-4 w-4" />
            应用预设
          </Button>
        }
      />
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>应用预设布局</DialogTitle>
          <DialogDescription>选择一套预设，当前布局将被替换并自动保存</DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          {visiblePresets.length === 0 ? (
            <p className="py-6 text-center text-muted-foreground text-sm">暂无可用预设</p>
          ) : (
            visiblePresets.map((preset) => (
              <button
                key={preset.presetKey}
                type="button"
                onClick={() => setSelected(preset.presetKey)}
                className="w-full rounded-lg border p-3 text-left transition-colors hover:bg-accent data-[selected=true]:border-primary data-[selected=true]:bg-primary/5"
                data-selected={effectiveSelected === preset.presetKey}
              >
                <p className="font-medium text-sm">{preset.name}</p>
                <p className="text-muted-foreground text-xs">{preset.description}</p>
              </button>
            ))
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            取消
          </Button>
          <Button onClick={handleApply} disabled={visiblePresets.length === 0}>
            应用
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
