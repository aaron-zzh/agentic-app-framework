/**
 * 文案面板底部参数栏：模型 / 模板 / 长度 / 翻译 / 角色选择
 * 模型列表从后端动态查询，并在未选时默认选中第一个
 * @author AaronZZH & Kiro
 */

"use client"

import { useQuery } from "@tanstack/react-query"
import { useEffect } from "react"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { listTextModels } from "@/lib/api/rest/ai/ai-model"
import { RoleSelector } from "../generation/RoleSelector"
import { useAigcStore } from "../store"
import { TEMPLATES, TRANSLATE_OPTIONS } from "./constants"

export function CopywritingParamsBar() {
  const template = useAigcStore((s) => s.copywritingTemplate)
  const setTemplate = useAigcStore((s) => s.setCopywritingTemplate)
  const translateTo = useAigcStore((s) => s.copywritingTranslateTo)
  const setTranslateTo = useAigcStore((s) => s.setCopywritingTranslateTo)
  const length = useAigcStore((s) => s.copywritingLength)
  const setLength = useAigcStore((s) => s.setCopywritingLength)
  const model = useAigcStore((s) => s.copywritingModel)
  const setModel = useAigcStore((s) => s.setCopywritingModel)
  const agentRole = useAigcStore((s) => s.agentRole)
  const setAgentRole = useAigcStore((s) => s.setAgentRole)

  const { data: textModels = [] } = useQuery({
    queryKey: ["ai", "models", "text"],
    queryFn: listTextModels,
    staleTime: 5 * 60 * 1000
  })

  useEffect(() => {
    if (!model && textModels.length > 0) setModel(textModels[0].modelId)
  }, [model, textModels, setModel])

  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        <Select value={model} onValueChange={(v) => setModel(v ?? "")}>
          <SelectTrigger className="h-8 w-[160px] text-xs">
            <span className="shrink-0 text-muted-foreground">模型</span>
            <span className="truncate">
              {textModels.find((m) => m.modelId === model)?.displayName ?? model}
            </span>
          </SelectTrigger>
          <SelectContent>
            {textModels.map((m) => (
              <SelectItem key={m.modelId} value={m.modelId}>
                {m.displayName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={template} onValueChange={(v) => setTemplate(v ?? "")}>
          <SelectTrigger className="h-8 w-[130px] text-xs">
            <span className="shrink-0 text-muted-foreground">模板</span>
            <span className="truncate">
              {TEMPLATES.find((t) => t.value === template)?.label ?? "无模板"}
            </span>
          </SelectTrigger>
          <SelectContent>
            {TEMPLATES.map((t) => (
              <SelectItem key={t.value} value={t.value}>
                {t.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

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

        <Select value={translateTo} onValueChange={(v) => setTranslateTo(v ?? "")}>
          <SelectTrigger className="h-8 w-[110px] text-xs">
            <span className="shrink-0 text-muted-foreground">翻译</span>
            <span className="truncate">
              {TRANSLATE_OPTIONS.find((o) => o.value === translateTo)?.label ?? "不翻译"}
            </span>
          </SelectTrigger>
          <SelectContent>
            {TRANSLATE_OPTIONS.map((o) => (
              <SelectItem key={o.value} value={o.value}>
                {o.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <RoleSelector value={agentRole} onChange={setAgentRole} />
    </>
  )
}
