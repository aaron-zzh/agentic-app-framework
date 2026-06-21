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
import { dashboardPresets } from "./presets"

interface ApplyPresetDialogProps {
  onApply: (widgets: DashboardWidgetVO[], refreshInterval?: number) => void
}

export function ApplyPresetDialog({ onApply }: ApplyPresetDialogProps) {
  const { data: remotePresets } = usePresets()
  const userRoles = useAuthStore((s) => s.user?.roles)
  const isAdmin = userRoles?.some((r) => ["admin", "super_admin", "org_admin"].includes(r)) ?? false

  // 优先用接口数据，接口失败降级到本地硬编码
  const presets: DashboardPresetVO[] = remotePresets
    ? remotePresets
    : dashboardPresets
        .filter((p) => !p.adminOnly || isAdmin)
        .map((p, i) => ({
          id: String(i),
          presetKey: p.key,
          name: p.name,
          description: p.description,
          adminOnly: p.adminOnly ?? false,
          refreshInterval: p.refreshInterval,
          widgets: p.widgets,
          sortOrder: i
        }))

  const visiblePresets = isAdmin ? presets : presets.filter((p) => !p.adminOnly)

  const [selected, setSelected] = useState(visiblePresets[0]?.presetKey ?? "")
  const [open, setOpen] = useState(false)

  function handleApply() {
    const preset = visiblePresets.find((p) => p.presetKey === selected)
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
          <DialogDescription>选择一套预设，当前布局将被替换（保存后生效）</DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          {visiblePresets.map((preset) => (
            <button
              key={preset.presetKey}
              type="button"
              onClick={() => setSelected(preset.presetKey)}
              className="w-full rounded-lg border p-3 text-left transition-colors hover:bg-accent data-[selected=true]:border-primary data-[selected=true]:bg-primary/5"
              data-selected={selected === preset.presetKey}
            >
              <p className="font-medium text-sm">{preset.name}</p>
              <p className="text-muted-foreground text-xs">{preset.description}</p>
            </button>
          ))}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)}>
            取消
          </Button>
          <Button onClick={handleApply}>应用</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
