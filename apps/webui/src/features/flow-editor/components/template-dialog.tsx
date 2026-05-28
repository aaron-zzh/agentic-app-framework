/**
 * 模板选择对话框——从模板创建新流程
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import type { FlowTemplate, FlowMode } from "../types"
import { builtinTemplates } from "../lib/templates"
import { useFlowTemplates, useCreateFromTemplate } from "../hooks/use-flow-query"

interface TemplateDialogProps {
  mode: FlowMode
  onSelect: (definition: FlowTemplate["definition"]) => void
}

export function TemplateDialog({ mode, onSelect }: TemplateDialogProps) {
  const [open, setOpen] = useState(false)
  const { data: remoteTemplates } = useFlowTemplates(mode)
  const createFromTemplate = useCreateFromTemplate()

  // 合并内置模板和远程模板
  const templates = [
    ...builtinTemplates.filter((t) => t.mode === mode),
    ...(remoteTemplates ?? [])
  ]

  const handleSelect = (template: FlowTemplate) => {
    onSelect(template.definition)
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          从模板创建
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>选择流程模板</DialogTitle>
        </DialogHeader>
        <div className="space-y-2">
          {templates.length === 0 && (
            <p className="text-muted-foreground py-4 text-center text-sm">暂无可用模板</p>
          )}
          {templates.map((tpl) => (
            <button
              key={tpl.id}
              type="button"
              className="hover:bg-accent w-full rounded-lg border p-3 text-left transition-colors"
              onClick={() => handleSelect(tpl)}
            >
              <p className="text-sm font-medium">{tpl.name}</p>
              <p className="text-muted-foreground mt-0.5 text-xs">{tpl.description}</p>
            </button>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}
