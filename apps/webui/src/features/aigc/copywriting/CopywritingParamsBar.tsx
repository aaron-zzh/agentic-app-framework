/**
 * 文案面板底部参数栏：模型 / 长度 / 翻译选择
 * 模型与输出语言均从后端受控数据源动态查询
 * @author AaronZZH & Kiro
 */

"use client"

import { ModelSelector } from "@/components/common/ModelSelector"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { DictType } from "@/lib/constants/dict-type"
import { useDict } from "@/lib/hooks/use-dict"
import { useModelSelector } from "@/lib/hooks/use-model-selector"
import { useAigcStore } from "../store"

const NO_SELECTION_VALUE = "__none__"

export function CopywritingParamsBar() {
  const translateTo = useAigcStore((s) => s.copywritingTranslateTo)
  const setTranslateTo = useAigcStore((s) => s.setCopywritingTranslateTo)
  const length = useAigcStore((s) => s.copywritingLength)
  const setLength = useAigcStore((s) => s.setCopywritingLength)
  const model = useAigcStore((s) => s.copywritingModel)
  const setModel = useAigcStore((s) => s.setCopywritingModel)
  const { options: localeOptions, getLabel: getLocaleLabel } = useDict(
    DictType.Ai.COPYWRITING_OUTPUT_LOCALE
  )
  const { options, modelId, setModelId } = useModelSelector("CHAT", {
    value: model,
    onChange: (id) => setModel(id)
  })

  return (
    <div className="flex flex-wrap items-center gap-2">
      <ModelSelector variant="select" options={options} value={modelId} onChange={setModelId} />

      <Select value={length} onValueChange={(v) => setLength(v as "short" | "medium" | "long")}>
        <SelectTrigger className="h-8 w-[110px] text-xs">
          <span className="shrink-0 text-muted-foreground">长度</span>
          <span className="truncate">
            {{ short: "短篇", medium: "中篇", long: "长篇" }[length] ?? length}
          </span>
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="short">短篇（≤200字）</SelectItem>
          <SelectItem value="medium">中篇（200-500字）</SelectItem>
          <SelectItem value="long">长篇（500+字）</SelectItem>
        </SelectContent>
      </Select>

      <Select
        value={translateTo ?? NO_SELECTION_VALUE}
        onValueChange={(value) => setTranslateTo(value === NO_SELECTION_VALUE ? null : value)}
      >
        <SelectTrigger className="h-8 w-[110px] text-xs">
          <span className="shrink-0 text-muted-foreground">翻译</span>
          <span className="truncate">
            {translateTo === null ? "不翻译" : getLocaleLabel(translateTo)}
          </span>
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={NO_SELECTION_VALUE}>不翻译</SelectItem>
          {localeOptions.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
