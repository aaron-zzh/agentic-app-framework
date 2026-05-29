/**
 * SectionPropsPanel——Section 属性编辑面板
 * @author AaronZZH & Kiro
 *
 * 根据选中的 Section 类型动态渲染属性编辑表单。
 * 使用 JSON 文本编辑作为通用方案（v0.1 阶段），后续可按类型定制表单。
 */

"use client"

import { useCallback, useId, useState } from "react"
import { toast } from "sonner"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"

import type { SectionDef, SectionStyle } from "../page-engine/types"

interface SectionPropsPanelProps {
  /** 当前选中的 Section */
  section: SectionDef
  /** 属性变更回调 */
  onChange: (updated: SectionDef) => void
}

/** Section 属性编辑面板 */
export function SectionPropsPanel({ section, onChange }: SectionPropsPanelProps) {
  const formId = useId()
  const [propsJson, setPropsJson] = useState(() => JSON.stringify(section.props, null, 2))
  const [jsonError, setJsonError] = useState<string | null>(null)

  /** 应用 JSON 变更 */
  const applyProps = useCallback(() => {
    try {
      const parsed = JSON.parse(propsJson) as Record<string, unknown>
      setJsonError(null)
      onChange({ ...section, props: parsed })
      toast.success("属性已更新")
    } catch (e) {
      setJsonError((e as Error).message)
    }
  }, [propsJson, section, onChange])

  /** 更新样式字段 */
  const updateStyle = useCallback(
    (key: keyof SectionStyle, value: string | boolean) => {
      const newStyle = { ...section.style, [key]: value || undefined }
      onChange({ ...section, style: newStyle })
    },
    [section, onChange]
  )

  return (
    <div className="flex flex-col gap-4 p-4">
      <div>
        <h3 className="mb-1 font-semibold text-sm">区块类型</h3>
        <p className="text-muted-foreground text-xs">{section.type}</p>
      </div>

      {/* ID 编辑 */}
      <div>
        <Label htmlFor={`${formId}-section-id`} className="text-xs">
          ID（锚点）
        </Label>
        <Input
          id={`${formId}-section-id`}
          value={section.id}
          onChange={(e) => onChange({ ...section, id: e.target.value })}
          className="mt-1 h-8 text-xs"
        />
      </div>

      {/* 样式配置 */}
      <div className="space-y-3 border-t pt-3">
        <h4 className="font-medium text-xs">样式</h4>

        <div>
          <Label htmlFor={`${formId}-section-padding`} className="text-xs">
            内边距
          </Label>
          <Select
            value={section.style?.padding ?? ""}
            onValueChange={(v) => updateStyle("padding", v ?? "")}
          >
            <SelectTrigger id={`${formId}-section-padding`} className="mt-1 h-8 text-xs">
              <SelectValue placeholder="默认" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="sm">小 (sm)</SelectItem>
              <SelectItem value="md">中 (md)</SelectItem>
              <SelectItem value="lg">大 (lg)</SelectItem>
              <SelectItem value="xl">超大 (xl)</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div>
          <Label htmlFor={`${formId}-section-animation`} className="text-xs">
            动效
          </Label>
          <Select
            value={section.style?.animation ?? "none"}
            onValueChange={(v) => updateStyle("animation", v ?? "none")}
          >
            <SelectTrigger id={`${formId}-section-animation`} className="mt-1 h-8 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none">无</SelectItem>
              <SelectItem value="fadeIn">淡入</SelectItem>
              <SelectItem value="slideUp">上滑</SelectItem>
              <SelectItem value="slideLeft">左滑</SelectItem>
              <SelectItem value="scaleIn">缩放</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div>
          <Label htmlFor={`${formId}-section-bg`} className="text-xs">
            背景色
          </Label>
          <Input
            id={`${formId}-section-bg`}
            value={section.style?.backgroundColor ?? ""}
            onChange={(e) => updateStyle("backgroundColor", e.target.value)}
            placeholder="如 #f5f5f5"
            className="mt-1 h-8 text-xs"
          />
        </div>
      </div>

      {/* Props JSON 编辑 */}
      <div className="border-t pt-3">
        <Label htmlFor={`${formId}-section-props`} className="text-xs">
          属性（JSON）
        </Label>
        <Textarea
          id={`${formId}-section-props`}
          value={propsJson}
          onChange={(e) => {
            setPropsJson(e.target.value)
            setJsonError(null)
          }}
          className="mt-1 min-h-[200px] font-mono text-xs"
        />
        {jsonError && <p className="mt-1 text-destructive text-xs">{jsonError}</p>}
        <Button size="sm" onClick={applyProps} className="mt-2 w-full">
          应用变更
        </Button>
      </div>
    </div>
  )
}
