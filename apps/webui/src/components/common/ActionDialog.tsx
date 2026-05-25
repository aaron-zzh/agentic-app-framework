/**
 * ActionDialog——通用动态表单弹窗。
 * 根据 ActionConfig.form 定义动态渲染表单字段，提交后调用 API。
 */

"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { type ActionConfig, type ActionResult, useAction } from "@/lib/hooks/use-action"

interface ActionDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  config: ActionConfig
  onSuccess?: (result: ActionResult) => void
}

export function ActionDialog({ open, onOpenChange, config, onSuccess }: ActionDialogProps) {
  const { execute, loading, result } = useAction(config)
  const [formValues, setFormValues] = useState<Record<string, unknown>>({})

  const fields = config.form ?? {}
  const visibleFields = Object.entries(fields).filter(([, f]) => f.type !== "hidden")

  function handleChange(name: string, value: unknown) {
    setFormValues((prev) => ({ ...prev, [name]: value }))
  }

  async function handleSubmit() {
    // 合并隐藏字段默认值
    const data: Record<string, unknown> = {}
    for (const [name, field] of Object.entries(fields)) {
      if (field.type === "hidden") {
        data[name] = field.defaultValue
      } else {
        data[name] = formValues[name] ?? field.defaultValue ?? ""
      }
    }

    const res = await execute(data)
    if (res.success) {
      onSuccess?.(res)
      if (config.onSuccess !== "showOnce") {
        onOpenChange(false)
      }
    }
  }

  // 生成 Key 后展示结果（showOnce 模式）
  if (config.onSuccess === "showOnce" && result?.success && result.data?.key) {
    return (
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Key 已生成</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <p className="text-sm text-muted-foreground">
              请立即复制保存，关闭后将无法再次查看。
            </p>
            <code className="block rounded bg-muted p-3 text-sm break-all">
              {result.data.key as string}
            </code>
          </div>
          <DialogFooter>
            <Button onClick={() => { onOpenChange(false) }}>关闭</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    )
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{config.label}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          {visibleFields.map(([name, field]) => (
            <div key={name} className="space-y-1.5">
              <Label htmlFor={name}>{field.label ?? name}</Label>
              {field.type === "select" ? (
                <select
                  id={name}
                  className="w-full rounded border px-3 py-2 text-sm"
                  value={(formValues[name] as string) ?? ""}
                  onChange={(e) => handleChange(name, e.target.value)}
                >
                  <option value="">请选择</option>
                  {field.options?.map((opt) => (
                    <option key={opt} value={opt}>{opt}</option>
                  ))}
                </select>
              ) : (
                <Input
                  id={name}
                  type={field.type === "number" ? "number" : "text"}
                  placeholder={field.placeholder}
                  value={(formValues[name] as string) ?? ""}
                  onChange={(e) => handleChange(name, e.target.value)}
                />
              )}
            </div>
          ))}
          {result?.error && (
            <p className="text-sm text-destructive">{result.error}</p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button onClick={handleSubmit} disabled={loading}>
            {loading ? "处理中..." : "确认"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
