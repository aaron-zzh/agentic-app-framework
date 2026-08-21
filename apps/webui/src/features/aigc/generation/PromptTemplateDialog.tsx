/**
 * 提示词资产选择器：按我的与公共两个视图筛选并安全应用模板。
 * @author AaronZZH & Kiro
 */

"use client"

import { useBoolean } from "@aaf/hooks"
import { Plus, Search, Sparkles } from "lucide-react"
import { useId, useMemo, useState } from "react"
import { useDebounce } from "use-debounce"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { CoverImageUpload } from "@/features/aigc/generation/CoverImageUpload"
import { CoverThumbnail } from "@/features/aigc/generation/CoverThumbnail"
import { useLoadMoreOnVisible } from "@/features/studio/assets/useLoadMoreOnVisible"
import {
  type PromptTemplateAssetVO,
  useCreatePromptTemplate,
  useInfiniteMyPromptTemplates,
  useInfinitePublicPromptTemplates,
  usePromptTemplate,
  usePromptTemplateMeta
} from "@/lib/api/rest/ai"
import { cn } from "@/lib/utils/cn"

type TemplateSource = "MINE" | "PUBLIC"

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
  return value === "MINE" || value === "PUBLIC"
}

/** 以技能选择器同款 Popover 展示并应用提示词资产。 */
export function PromptTemplateDialog({
  type,
  onSelect,
  scope = "GENERATION",
  triggerClassName
}: PromptTemplateDialogProps) {
  const uid = useId()
  const quickCreate = useBoolean()
  const [open, setOpen] = useState(false)
  const [source, setSource] = useState<TemplateSource>("MINE")
  const [activeCategory, setActiveCategory] = useState("全部")
  const [search, setSearch] = useState("")
  const [debouncedSearch] = useDebounce(search.trim(), 300)
  const [selectedTemplate, setSelectedTemplate] = useState<PromptTemplateAssetVO | null>(null)
  const [variableValues, setVariableValues] = useState<Record<string, string>>({})
  const [createName, setCreateName] = useState("")
  const [createCategory, setCreateCategory] = useState("DEFAULT")
  const [createCoverUrl, setCreateCoverUrl] = useState<string | null>(null)
  const [createPrompt, setCreatePrompt] = useState("")
  const queryParams = { type, scope, search: debouncedSearch || undefined }
  const publicQuery = useInfinitePublicPromptTemplates(queryParams, open && source === "PUBLIC")
  const mineQuery = useInfiniteMyPromptTemplates(queryParams, open && source === "MINE")
  const useTemplate = usePromptTemplate()
  const createTemplate = useCreatePromptTemplate()
  const metaQuery = usePromptTemplateMeta(open)
  const activeQuery = source === "PUBLIC" ? publicQuery : mineQuery
  const canCreate = source === "MINE" && Boolean(metaQuery.data?.operations.includes("create"))

  const sourceTemplates = useMemo(() => {
    const byId = new Map<number, PromptTemplateAssetVO>()
    activeQuery.data?.pages.forEach((page) => {
      page.list.forEach((template) => {
        byId.set(template.id, template)
      })
    })
    return [...byId.values()]
  }, [activeQuery.data?.pages])
  const categories = useMemo(
    () => [
      "全部",
      ...Array.from(new Set(sourceTemplates.flatMap((template) => template.categories)))
    ],
    [sourceTemplates]
  )
  const filteredTemplates = useMemo(
    () =>
      activeCategory === "全部"
        ? sourceTemplates
        : sourceTemplates.filter((template) => template.categories.includes(activeCategory)),
    [activeCategory, sourceTemplates]
  )
  const loadMoreRef = useLoadMoreOnVisible({
    hasNextPage: Boolean(activeQuery.hasNextPage),
    isFetchingNextPage: activeQuery.isFetchingNextPage,
    fetchNextPage: activeQuery.fetchNextPage
  })
  const isLoading = activeQuery.isLoading
  const allVariablesReady =
    selectedTemplate?.variables.every((variable) => Boolean(variableValues[variable]?.trim())) ??
    false

  function resetSelection() {
    setSelectedTemplate(null)
    setVariableValues({})
  }

  function resetQuickCreate() {
    quickCreate.onFalse()
    setCreateName("")
    setCreateCategory("DEFAULT")
    setCreateCoverUrl(null)
    setCreatePrompt("")
  }

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen)
    if (nextOpen) {
      setSource("MINE")
      setActiveCategory("全部")
      setSearch("")
      resetSelection()
      resetQuickCreate()
    }
  }

  function handleSourceChange(value: string) {
    if (!isTemplateSource(value)) return
    setSource(value)
    setActiveCategory("全部")
    resetSelection()
    resetQuickCreate()
  }

  function handleCreate() {
    const name = createName.trim()
    const prompt = createPrompt.trim()
    if (!name || !prompt) return
    createTemplate.mutate(
      {
        name,
        categories: [createCategory.trim() || "DEFAULT"],
        coverUrl: createCoverUrl,
        prompt,
        type,
        scope,
        isPublic: false,
        variables: []
      },
      {
        onSuccess: (created) => {
          onSelect(created.prompt)
          setOpen(false)
          resetQuickCreate()
        }
      }
    )
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
        <div className="flex items-center justify-between gap-3 border-foreground/6 border-b px-4 py-3">
          <span className="min-w-0 truncate font-semibold text-sm">
            {quickCreate.value
              ? "快速创建提示词"
              : selectedTemplate
                ? `填写变量 · ${selectedTemplate.name}`
                : "选择提示词"}
          </span>
          <div className="flex shrink-0 items-center gap-2">
            {!quickCreate.value && !selectedTemplate && canCreate ? (
              <Button type="button" size="sm" variant="outline" onClick={quickCreate.onTrue}>
                <Plus className="size-3.5" />
                新建
              </Button>
            ) : null}
            <Badge variant="secondary">
              {quickCreate.value
                ? "保存到我的"
                : selectedTemplate
                  ? `${selectedTemplate.variables.length} 个变量`
                  : `${filteredTemplates.length} 个`}
            </Badge>
          </div>
        </div>

        {quickCreate.value ? (
          <div className="flex flex-col gap-3 p-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-quick-name`}>名称</Label>
              <Input
                id={`${uid}-quick-name`}
                value={createName}
                onChange={(event) => setCreateName(event.target.value)}
                placeholder="提示词名称"
                autoFocus
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-quick-category`}>分类</Label>
              <Input
                id={`${uid}-quick-category`}
                value={createCategory}
                onChange={(event) => setCreateCategory(event.target.value)}
                placeholder="例如：图像生成"
              />
            </div>
            <CoverImageUpload
              id={`${uid}-quick-cover`}
              value={createCoverUrl}
              onChange={setCreateCoverUrl}
              disabled={createTemplate.isPending}
            />
            <div className="flex flex-col gap-1.5">
              <Label htmlFor={`${uid}-quick-prompt`}>提示词内容</Label>
              <Textarea
                id={`${uid}-quick-prompt`}
                value={createPrompt}
                onChange={(event) => setCreatePrompt(event.target.value)}
                placeholder="输入提示词…"
                className="min-h-32"
              />
            </div>
            <div className="flex justify-end gap-2 pt-1">
              <Button type="button" variant="outline" onClick={resetQuickCreate}>
                返回
              </Button>
              <Button
                type="button"
                disabled={createTemplate.isPending || !createName.trim() || !createPrompt.trim()}
                onClick={handleCreate}
              >
                {createTemplate.isPending ? "创建中…" : "创建并应用"}
              </Button>
            </div>
          </div>
        ) : selectedTemplate ? (
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
                    <TabsTrigger value="MINE">我的</TabsTrigger>
                    <TabsTrigger value="PUBLIC">公共</TabsTrigger>
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
                      <CoverThumbnail
                        src={template.coverUrl}
                        alt={`${template.name}封面`}
                        fallback={
                          <span className="font-semibold text-sm">{template.name.slice(0, 1)}</span>
                        }
                        className="mt-0.5 size-9"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5">
                          <span className="truncate font-medium text-sm">{template.name}</span>
                          {source === "PUBLIC" ? (
                            <Badge variant="outline" className="h-4 px-1.5 text-[10px]">
                              {template.visibility === "SYSTEM" ? "内置" : "工作区"}
                            </Badge>
                          ) : null}
                          {template.categories.length > 0 ? (
                            <Badge variant="secondary" className="h-4 px-1.5 text-[10px]">
                              {template.categories.join("、")}
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
                <div
                  ref={loadMoreRef}
                  className="flex h-8 items-center justify-center text-muted-foreground text-xs"
                >
                  {activeQuery.isFetchingNextPage
                    ? "正在加载更多…"
                    : activeQuery.hasNextPage
                      ? "继续向下滚动加载"
                      : filteredTemplates.length > 0
                        ? "已加载全部提示词"
                        : null}
                </div>
              </div>
            </ScrollArea>
          </>
        )}
      </PopoverContent>
    </Popover>
  )
}
