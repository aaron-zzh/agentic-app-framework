/**
 * 新建开发文档弹窗（路径必须以 docs/ 开头）
 * @author AaronZZH & Kiro
 */
"use client"

import { useId, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { useCreateAutodevDoc } from "@/lib/queries/use-autodev-documents"

const DOC_TYPES = [
  { value: "spec", label: "规格" },
  { value: "design", label: "设计" },
  { value: "task", label: "任务" },
  { value: "guide", label: "指南" },
  { value: "reference", label: "参考" },
  { value: "explanation", label: "说明" },
]

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function AutodevDocCreateDialog({ open, onOpenChange }: Props) {
  const uid = useId()
  const [title, setTitle] = useState("")
  const [filePath, setFilePath] = useState("")
  const [docType, setDocType] = useState("spec")
  const [content, setContent] = useState("")
  const { mutate: create, isPending } = useCreateAutodevDoc()

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!filePath.startsWith("docs/")) {
      toast.error("文件路径必须以 docs/ 开头")
      return
    }
    create(
      { title, filePath, docType, content: content || undefined },
      {
        onSuccess: () => {
          toast.success("文档创建成功")
          onOpenChange(false)
          setTitle(""); setFilePath(""); setDocType("spec"); setContent("")
        },
        onError: () => toast.error("创建失败"),
      }
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader><DialogTitle>新建开发文档</DialogTitle></DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor={`${uid}-title`}>标题</Label>
            <Input id={`${uid}-title`} value={title} onChange={(e) => setTitle(e.target.value)} placeholder="文档标题" required />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`${uid}-path`}>文件路径</Label>
            <Input id={`${uid}-path`} value={filePath} onChange={(e) => setFilePath(e.target.value)} placeholder="docs/design/xxx.md" required />
          </div>
          <div className="space-y-2">
            <Label htmlFor={`${uid}-type`}>文档类型</Label>
            <Select value={docType} onValueChange={(v) => setDocType(v ?? "spec")}>
              <SelectTrigger id={`${uid}-type`}><SelectValue /></SelectTrigger>
              <SelectContent>{DOC_TYPES.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}</SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor={`${uid}-content`}>初始内容（可选）</Label>
            <Textarea id={`${uid}-content`} value={content} onChange={(e) => setContent(e.target.value)} placeholder="Markdown 内容..." rows={4} />
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
            <Button type="submit" disabled={isPending}>{isPending ? "创建中..." : "创建"}</Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
