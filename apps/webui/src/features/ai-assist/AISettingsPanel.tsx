/**
 * AI 辅助设置面板
 * 用户可全局开关 AI 辅助功能，并配置建议灵敏度
 * @author AaronZZH & Kiro
 */
"use client"

import { Bot } from "lucide-react"

import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { type AISensitivity, useAISettingsStore } from "@/lib/store/ai-settings-store"

const sensitivityOptions: { value: AISensitivity; label: string; description: string }[] = [
  { value: "low", label: "低", description: "仅在明确需要时提供建议" },
  { value: "medium", label: "中", description: "适度提供建议（推荐）" },
  { value: "high", label: "高", description: "积极主动提供建议" },
]

/**
 * AI 辅助设置面板，嵌入设置页面使用
 *
 * @example
 * <AISettingsPanel />
 */
export function AISettingsPanel() {
  const { enabled, sensitivity, setEnabled, setSensitivity } = useAISettingsStore()

  return (
    <div className="space-y-6">
      {/* 标题 */}
      <div className="flex items-center gap-2">
        <Bot className="size-5 text-muted-foreground" />
        <h3 className="font-medium text-lg">AI 辅助</h3>
      </div>

      {/* 全局开关 */}
      <div className="flex items-center justify-between rounded-lg border border-border p-4">
        <div className="space-y-0.5">
          <Label className="font-medium text-sm">启用 AI 辅助</Label>
          <p className="text-muted-foreground text-xs">
            开启后 AI 将提供字段补全、操作建议和错误修复
          </p>
        </div>
        <Switch checked={enabled} onCheckedChange={setEnabled} />
      </div>

      {/* 灵敏度配置 */}
      {enabled && (
        <div className="space-y-3">
          <Label className="font-medium text-sm">建议灵敏度</Label>
          <RadioGroup
            value={sensitivity}
            onValueChange={(v) => setSensitivity(v as AISensitivity)}
          >
            {sensitivityOptions.map((opt) => (
              <label
                key={opt.value}
                htmlFor={`sensitivity-${opt.value}`}
                className="flex cursor-pointer items-center gap-3 rounded-lg border border-border p-3 transition-colors has-data-checked:border-primary has-data-checked:bg-primary/5"
              >
                <RadioGroupItem value={opt.value} id={`sensitivity-${opt.value}`} />
                <div className="space-y-0.5">
                  <span className="font-medium text-sm">{opt.label}</span>
                  <p className="text-muted-foreground text-xs">{opt.description}</p>
                </div>
              </label>
            ))}
          </RadioGroup>
        </div>
      )}
    </div>
  )
}
