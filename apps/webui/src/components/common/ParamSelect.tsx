/**
 * ParamSelect——通用参数下拉选择器
 * 标签 + 选项数组 + 可选显示名映射，适用于图像/视频/文本/配音等参数栏
 *
 * @example
 * <ParamSelect label="画质" value={quality} options={["auto","high"]} onChange={setQuality}
 *   labelMap={{ auto: "自动", high: "高" }} />
 */
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"

interface ParamSelectProps {
  label: string
  value: string
  options: string[]
  onChange: (value: string) => void
  /** 选项显示名映射，未命中则直接显示原值 */
  labelMap?: Record<string, string>
  className?: string
}

export function ParamSelect({
  label,
  value,
  options,
  onChange,
  labelMap,
  className = "h-8 w-[120px] text-xs"
}: ParamSelectProps) {
  return (
    <Select value={value} onValueChange={(v) => v != null && onChange(v)}>
      <SelectTrigger className={className}>
        <span className="shrink-0 text-muted-foreground">{label}</span>
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {options.map((o) => (
          <SelectItem key={o} value={o}>
            {labelMap?.[o] ?? o}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
