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
import { dashboardPresets } from "./presets"

interface ApplyPresetDialogProps {
  onApply: (presetKey: string) => void
}

export function ApplyPresetDialog({ onApply }: ApplyPresetDialogProps) {
  const [selected, setSelected] = useState(dashboardPresets[0].key)
  const [open, setOpen] = useState(false)

  function handleApply() {
    onApply(selected)
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
          {dashboardPresets.map((preset) => (
            <button
              key={preset.key}
              type="button"
              onClick={() => setSelected(preset.key)}
              className="w-full rounded-lg border p-3 text-left transition-colors hover:bg-accent data-[selected=true]:border-primary data-[selected=true]:bg-primary/5"
              data-selected={selected === preset.key}
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
