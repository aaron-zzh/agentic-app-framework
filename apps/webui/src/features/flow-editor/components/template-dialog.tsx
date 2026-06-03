/**
 * 模板选择对话框——从模板创建新流程
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { useCreateFromTemplate, useFlowTemplates } from "../hooks/use-flow-query"
import { builtinTemplates } from "../lib/templates"
import type { FlowMode, FlowTemplate } from "../types"

interface TemplateDialogProps {
  mode: FlowMode
  onSelect: (definition: FlowTemplate["definition"]) => void
}

export function TemplateDialog({ mode, onSelect }: TemplateDialogProps) {
  const [open, setOpen] = useState(false)
  const { data: remoteTemplates } = useFlowTemplates(mode)
  useCreateFromTemplate()

  // 合并内置模板和远程模板
  const templates = [...builtinTemplates.filter((t) => t.mode === mode), ...(remoteTemplates ?? [])]

  const handleSelect = (template: FlowTemplate) => {
    onSelect(template.definition)
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger
        render={
          <Button variant="outline" size="sm">
            从模板创建
          </Button>
        }
      />
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>选择流程模板</DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          {templates.length === 0 && (
            <p className="py-4 text-center text-muted-foreground text-sm">暂无可用模板</p>
          )}
          {templates.map((tpl) => (
            <button
              key={tpl.id}
              type="button"
              className="w-full rounded-lg border p-3 text-left transition-colors hover:bg-accent"
              onClick={() => handleSelect(tpl)}
            >
              <p className="font-medium text-sm">{tpl.name}</p>
              <p className="mt-0.5 text-muted-foreground text-xs">{tpl.description}</p>
            </button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
