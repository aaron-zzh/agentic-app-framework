/**
 * ExportDialog——对话导出弹窗
 * 支持 Markdown / JSON 格式导出，可选全部或选中消息
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useThread } from "@assistant-ui/react"
import { Download } from "lucide-react"
import { useCallback, useId, useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"

type ExportFormat = "markdown" | "json"

interface ExportDialogProps {
  /** 选中的消息 ID 列表（为空则导出全部） */
  selectedMessageIds?: string[]
}

/** 触发浏览器下载 */
function downloadFile(content: string, filename: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

export function ExportDialog({ selectedMessageIds = [] }: ExportDialogProps) {
  const { messages } = useThread()
  const formId = useId()
  const [format, setFormat] = useState<ExportFormat>("markdown")
  const [open, setOpen] = useState(false)

  const handleExport = useCallback(() => {
    const toExport =
      selectedMessageIds.length > 0
        ? messages.filter((m) => selectedMessageIds.includes(m.id))
        : messages

    if (toExport.length === 0) {
      toast.error("没有可导出的消息")
      return
    }

    const timestamp = new Date().toISOString().slice(0, 10)

    if (format === "markdown") {
      const md = toExport
        .map((msg) => {
          const role = msg.role === "user" ? "**用户**" : "**助手**"
          const text = msg.content
            .filter((c): c is { type: "text"; text: string } => c.type === "text")
            .map((c) => c.text)
            .join("\n")
          return `### ${role}\n\n${text}`
        })
        .join("\n\n---\n\n")

      downloadFile(md, `对话导出_${timestamp}.md`, "text/markdown;charset=utf-8")
    } else {
      const data = toExport.map((msg) => ({
        id: msg.id,
        role: msg.role,
        content: msg.content,
        createdAt: msg.createdAt
      }))
      downloadFile(
        JSON.stringify(data, null, 2),
        `对话导出_${timestamp}.json`,
        "application/json;charset=utf-8"
      )
    }

    toast.success("导出成功")
    setOpen(false)
  }, [messages, selectedMessageIds, format])

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button variant="outline" size="sm" />}>
        <Download className="size-4" />
        导出对话
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>导出对话</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* 导出范围 */}
          <div>
            <p className="mb-1 text-muted-foreground text-sm">
              导出范围：
              {selectedMessageIds.length > 0
                ? `选中 ${selectedMessageIds.length} 条消息`
                : `全部 ${messages.length} 条消息`}
            </p>
          </div>

          {/* 格式选择 */}
          <div className="space-y-2">
            <p className="font-medium text-sm">导出格式</p>
            <RadioGroup value={format} onValueChange={(v) => setFormat(v as ExportFormat)}>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="markdown" id={`${formId}-fmt-md`} />
                <Label htmlFor={`${formId}-fmt-md`}>Markdown (.md)</Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="json" id={`${formId}-fmt-json`} />
                <Label htmlFor={`${formId}-fmt-json`}>JSON (.json)</Label>
              </div>
              <div className="flex items-center gap-2 opacity-50">
                <RadioGroupItem value="pdf" id={`${formId}-fmt-pdf`} disabled />
                <Label htmlFor={`${formId}-fmt-pdf`}>
                  PDF (.pdf) <Badge variant="outline">Coming Soon</Badge>
                </Label>
              </div>
            </RadioGroup>
          </div>
        </div>

        <DialogFooter>
          <Button onClick={handleExport}>
            <Download className="size-4" />
            导出
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
