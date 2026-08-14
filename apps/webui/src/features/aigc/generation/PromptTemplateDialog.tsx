/**
 * 提示词资产选择器：按系统、我的、当前工作区公开三个视图筛选并安全应用模板。
 * @author AaronZZH & Kiro
 */

"use client"

import { Search, Sparkles } from "lucide-react"
import { useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  type PromptTemplateAssetVO,
  useMyPromptTemplates,
  usePromptTemplate,
  usePublicPromptTemplates,
  useSystemPromptTemplates
} from "@/lib/api/rest/ai"
import { cn } from "@/lib/utils/cn"

type TemplateSource = "SYSTEM" | "MINE" | "WORKSPACE"

interface PromptTemplateDialogProps {
  type: string
  onSelect: (prompt: string) => void
  hasReferenceImages?: boolean
  /** 使用场景：GENERATION（默认，单次生成）| PROJECT（项目级） */
  scope?: "GENERATION" | "PROJECT"
  /** 触发按钮自定义样式，未传则使用默认样式 */
  triggerClassName?: string
}

function isTemplateSource(value: string): value is TemplateSource {
  return value === "SYSTEM" || value === "MINE" || value === "WORKSPACE"
}

/** 以技能选择器同款 Popover 展示并应用提示词资产。 */
export function PromptTemplateDialog({
  type,
  onSelect,
  hasReferenceImages,
  scope = "GENERATION",
  triggerClassName
}: PromptTemplateDialogProps) {
  const [open, setOpen] = useState(false)
  const [source, setSource] = useState<TemplateSource>("SYSTEM")
  const [activeCategory, setActiveCategory] = useState("全部")
  const [search, setSearch] = useState("")
  const [selectedTemplate, setSelectedTemplate] = useState<PromptTemplateAssetVO | null>(null)
  const [variableValues, setVariableValues] = useState<Record<string, string>>({})
  const systemQuery = useSystemPromptTemplates(
    { type, scope, page: 0, size: 100 },
    open && source === "SYSTEM"
  )
  const publicQuery = usePublicPromptTemplates(
    { type, scope, page: 0, size: 100 },
    open && source === "WORKSPACE"
  )
  const mineQuery = useMyPromptTemplates(
    { type, scope, pageNo: 1, pageSize: 100 },
    open && source === "MINE"
  )
  const useTemplate = usePromptTemplate()

  const sourceTemplates = useMemo(() => {
    if (source === "SYSTEM") return systemQuery.data?.list ?? []
    if (source === "WORKSPACE") return publicQuery.data?.list ?? []
    return mineQuery.data?.list ?? []
  }, [mineQuery.data?.list, publicQuery.data?.list, source, systemQuery.data?.list])
  const categories = useMemo(
    () => [
      "全部",
      ...Array.from(
        new Set(
          sourceTemplates
            .map((template) => template.category)
            .filter((category): category is string => Boolean(category))
        )
      )
    ],
    [sourceTemplates]
  )
  const filteredTemplates = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    return sourceTemplates.filter((template) => {
      if (activeCategory !== "全部" && template.category !== activeCategory) return false
      if (!normalizedSearch) return true
      return `${template.name} ${template.prompt} ${template.category ?? ""}`
        .toLowerCase()
        .includes(normalizedSearch)
    })
  }, [activeCategory, search, sourceTemplates])
  const isLoading =
    source === "SYSTEM"
      ? systemQuery.isLoading
      : source === "WORKSPACE"
        ? publicQuery.isLoading
        : mineQuery.isLoading
  const allVariablesReady =
    selectedTemplate?.variables.every((variable) => Boolean(variableValues[variable]?.trim())) ??
    false

  function resetSelection() {
    setSelectedTemplate(null)
    setVariableValues({})
  }

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)
    if (nextOpen) {
      setSource("SYSTEM")
      setActiveCategory(hasReferenceImages ? "图像编辑" : "全部")
      setSearch("")
      resetSelection()
    }
  }

  function handleSourceChange(value: string) {
    if (!isTemplateSource(value)) return
    setSource(value)
    setActiveCategory("全部")
    resetSelection()
  }

  function applyTemplate(template: PromptTemplateAssetVO, variables: Record<string, string>) {
    useTemplate.mutate(
      { id: template.id, variables },
      {
        onSuccess: (result) => {
          onSelect(result.prompt)
          setOpen(false)
          resetSelection()
        }
      }
    )
  }

  function handleSelect(template: PromptTemplateAssetVO) {
    if (template.variables.length === 0) {
      applyTemplate(template, {})
      return
    }
    setSelectedTemplate(template)
    setVariableValues(Object.fromEntries(template.variables.map((variable) => [variable, ""])))
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger
        render={
          <button
            type="button"
            className={
              triggerClassName ??
              "inline-flex h-7 items-center gap-1 rounded-md px-2 text-muted-foreground text-xs hover:bg-accent hover:text-foreground"
            }
          />
        }
      >
        <Sparkles className="size-3" />
        提示词库
      </PopoverTrigger>
      <PopoverContent align="end" sideOffset={6} className="w-[30rem] max-w-[calc(100vw-2rem)] p-0">
        <div className="flex items-center justify-between border-foreground/6 border-b px-4 py-3">
          <span className="font-semibold text-sm">
            {selectedTemplate ? `填写变量 · ${selectedTemplate.name}` : "选择提示词"}
          </span>
          <Badge variant="secondary">
            {selectedTemplate
              ? `${selectedTemplate.variables.length} 个变量`
              : `${filteredTemplates.length} 个`}
          </Badge>
        </div>

        {selectedTemplate ? (
          <div className="flex flex-col gap-4 p-4">
            <p className="text-muted-foreground text-xs">
              变量由服务端校验并编译，填写完成后再应用提示词。
            </p>
            <div className="flex max-h-64 flex-col gap-3 overflow-y-auto">
              {selectedTemplate.variables.map((variable) => {
                const inputId = `prompt-template-${selectedTemplate.id}-${variable}`
                return (
                  <div key={variable} className="flex flex-col gap-1.5">
                    <Label htmlFor={inputId}>{variable}</Label>
                    <Input
                      id={inputId}
                      value={variableValues[variable] ?? ""}
                      onChange={(event) =>
                        setVariableValues((current) => ({
                          ...current,
                          [variable]: event.target.value
                        }))
                      }
                      placeholder={`输入 ${variable}`}
                    />
                  </div>
                )
              })}
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={resetSelection}>
                返回
              </Button>
              <Button
                disabled={!allVariablesReady || useTemplate.isPending}
                onClick={() => applyTemplate(selectedTemplate, variableValues)}
              >
                {useTemplate.isPending ? "应用中…" : "应用提示词"}
              </Button>
            </div>
          </div>
        ) : (
          <>
            <div className="flex flex-col gap-2 border-foreground/6 border-b px-3 py-2">
              <div className="flex flex-wrap items-center gap-2">
                <Tabs value={source} onValueChange={handleSourceChange}>
                  <TabsList>
                    <TabsTrigger value="SYSTEM">系统</TabsTrigger>
                    <TabsTrigger value="MINE">我的</TabsTrigger>
                    <TabsTrigger value="WORKSPACE">当前工作区公开</TabsTrigger>
                  </TabsList>
                </Tabs>
                <div className="relative ml-auto w-44">
                  <Search className="absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder="搜索提示词..."
                    className="h-8 pl-8 text-xs"
                  />
                </div>
              </div>
              <div className="flex gap-1 overflow-x-auto">
                {categories.map((category) => (
                  <button
                    key={category}
                    type="button"
                    onClick={() => setActiveCategory(category)}
                    className={cn(
                      "shrink-0 rounded-full px-2.5 py-1 text-xs transition-colors",
                      activeCategory === category
                        ? "bg-foreground/10 text-foreground"
                        : "text-muted-foreground hover:bg-foreground/6 hover:text-foreground"
                    )}
                  >
                    {category}
                  </button>
                ))}
              </div>
            </div>

            <ScrollArea className="h-[300px]">
              <div className="flex flex-col gap-1.5 p-3">
                {isLoading ? (
                  Array.from({ length: 3 }).map((_, index) => (
                    <Skeleton key={`prompt-skeleton-${index}`} className="h-16 w-full rounded-xl" />
                  ))
                ) : filteredTemplates.length === 0 ? (
                  <p className="py-8 text-center text-muted-foreground text-sm">
                    {search.trim() ? "没有匹配的提示词" : "暂无可用提示词"}
                  </p>
                ) : (
                  filteredTemplates.map((template) => (
                    <button
                      key={template.id}
                      type="button"
                      disabled={useTemplate.isPending}
                      onClick={() => handleSelect(template)}
                      className="flex w-full items-start gap-3 rounded-xl border border-foreground/6 bg-foreground/2 px-3 py-2.5 text-left transition-colors hover:bg-foreground/5 disabled:opacity-50"
                    >
                      <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-foreground/8 font-semibold text-foreground/60 text-sm">
                        {template.name.slice(0, 1)}
                      </div>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5">
                          <span className="truncate font-medium text-sm">{template.name}</span>
                          {template.category ? (
                            <Badge variant="secondary" className="h-4 px-1.5 text-[10px]">
                              {template.category}
                            </Badge>
                          ) : null}
                          {template.variables.length > 0 ? (
                            <Badge variant="outline" className="h-4 px-1.5 text-[10px]">
                              {template.variables.length} 个变量
                            </Badge>
                          ) : null}
                        </div>
                        <p className="mt-0.5 line-clamp-2 text-muted-foreground text-xs">
                          {template.prompt}
                        </p>
                      </div>
                    </button>
                  ))
                )}
              </div>
            </ScrollArea>
          </>
        )}
      </PopoverContent>
    </Popover>
  )
}
