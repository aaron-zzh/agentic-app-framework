/**
 * FieldCascader——级联选择（省→市→区模式）
 * @author AaronZZH & Kiro
 */

"use client"

import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import type { CascaderLevel } from "@/lib/hooks/use-field-cascader"
import { useFieldCascader } from "@/lib/hooks/use-field-cascader"

interface FieldCascaderProps {
  name: string
  label?: string
  levels: CascaderLevel[]
  value?: string[] // 每级选中的 id
  onChange?: (values: string[]) => void
  disabled?: boolean
  error?: string
}

export function FieldCascader({
  name,
  label,
  levels,
  value = [],
  onChange,
  disabled,
  error
}: FieldCascaderProps) {
  const { options, loading } = useFieldCascader(levels, value)

  const handleChange = (levelIdx: number, id: string) => {
    // 选中某级后，清空后续所有级
    const next = [...value]
    next[levelIdx] = id
    for (let i = levelIdx + 1; i < levels.length; i++) next[i] = ""
    onChange?.(next)
  }

  return (
    <div className="flex flex-col gap-1.5">
      {label && <Label htmlFor={name}>{label}</Label>}
      <div className="flex gap-2">
        {levels.map((level, i) => (
          <Select
            key={level.relationTo}
            value={value[i] ?? ""}
            onValueChange={(id) => handleChange(i, id ?? "")}
            disabled={disabled || (i > 0 && !value[i - 1]) || loading[i]}
          >
            <SelectTrigger className="flex-1">
              <SelectValue placeholder={loading[i] ? "加载中…" : level.label} />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                {options[i]?.map((opt) => (
                  <SelectItem key={opt.id} value={opt.id}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectGroup>
            </SelectContent>
          </Select>
        ))}
      </div>
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
