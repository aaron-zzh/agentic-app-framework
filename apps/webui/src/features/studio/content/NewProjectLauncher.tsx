/**
 * Content Studio 项目类型优先创建器。
 * @author AaronZZH & Kiro
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Bot, Check, ChevronRight, ImageIcon, Plus, Sparkles, Star, Upload, X } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useId } from "react"
import { Controller, useForm } from "react-hook-form"
import { GlassCard, GlassCardBody, GlowButton } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Progress } from "@/components/ui/progress"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import {
  type AigcBlueprintSlotTemplate,
  type AigcBrandProfile,
  type AigcChannelCode,
  type AigcProjectBlueprint,
  type AigcProjectType,
  type AigcProjectTypeCode,
  useAigcBrandProfiles,
  useAigcChannelSpecs,
  useAigcProjectBlueprints,
  useAigcProjectTypes,
  useMaterializeAigcProject
} from "@/lib/api/rest/ai/aigc"
import { useEntityAccess } from "@/lib/api/rest/user/permission"
import { paths } from "@/lib/constants/paths"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { usePermissionGuard } from "@/lib/hooks/use-permission-guard"
import { type NewProjectFormValues, newProjectSchema } from "./new-project-schema"
import { getChannelLabel, getObjectTypeConfig, getProjectTypeConfig } from "./project-type-config"

interface BrandProfileSelectProps {
  profiles: AigcBrandProfile[]
  value?: number
  onChange: (value?: number) => void
  disabled?: boolean
}

export function BrandProfileSelect({
  profiles,
  value,
  onChange,
  disabled = false
}: BrandProfileSelectProps) {
  return (
    <Select
      value={value === undefined ? "none" : String(value)}
      onValueChange={(next) => onChange(next && next !== "none" ? Number(next) : undefined)}
      disabled={disabled}
    >
      <SelectTrigger className="w-full sm:w-72">
        <SelectValue>
          {profiles.find((profile) => profile.id === value)?.name ?? "选择已发布品牌 / IP 资料"}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          <SelectItem value="none">暂不选择</SelectItem>
          {profiles.map((profile) => (
            <SelectItem key={profile.id} value={String(profile.id)}>
              {profile.name}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  )
}

interface ProjectTypePickerProps {
  types: AigcProjectType[]
  value?: AigcProjectTypeCode
  onChange: (value: AigcProjectTypeCode) => void
  showAssistantChoice?: boolean
}

export function ProjectTypePicker({
  types,
  value,
  onChange,
  showAssistantChoice = true
}: ProjectTypePickerProps) {
  return (
    <div className="flex flex-col gap-3">
      <div className="-mx-1 overflow-x-auto px-1 pb-2">
        <ToggleGroup
          value={value ? [value] : []}
          onValueChange={(values: string[]) => {
            const next = values.at(-1)
            if (next) onChange(next)
          }}
          variant="outline"
          className="flex w-max min-w-full justify-start gap-2"
        >
          {types.map((type) => {
            const config = getProjectTypeConfig(type)
            const Icon = config.icon
            return (
              <ToggleGroupItem
                key={type.code}
                value={type.code}
                aria-label={config.label}
                className="h-auto min-h-16 w-40 shrink-0 flex-col items-start gap-1.5 px-3 py-2.5 text-left"
              >
                <span className="flex w-full items-center justify-between">
                  <Icon />
                  {value === type.code ? <Check /> : null}
                </span>
                <span className="font-medium text-sm">{config.label}</span>
              </ToggleGroupItem>
            )
          })}
        </ToggleGroup>
      </div>
      {showAssistantChoice ? (
        <Link
          href="/studio/chat"
          className="flex w-fit items-center gap-2 rounded-lg px-2 py-1 text-muted-foreground text-sm transition-colors hover:bg-muted hover:text-foreground"
        >
          <Bot />
          不确定，让助手帮我选择
        </Link>
      ) : null}
    </div>
  )
}

interface ChannelPickerProps {
  channels: AigcChannelCode[]
  value: AigcChannelCode[]
  onChange: (value: AigcChannelCode[]) => void
}

export function ChannelPicker({ channels, value, onChange }: ChannelPickerProps) {
  return (
    <ToggleGroup
      value={value}
      onValueChange={onChange}
      variant="outline"
      size="sm"
      className="flex w-full flex-wrap justify-start"
    >
      {channels.map((channel) => (
        <ToggleGroupItem key={channel} value={channel} aria-label={getChannelLabel(channel)}>
          {getChannelLabel(channel)}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

interface ProjectBlueprintPickerProps {
  blueprints: AigcProjectBlueprint[]
  value?: number
  onChange: (value: number) => void
}

function ProjectBlueprintPicker({ blueprints, value, onChange }: ProjectBlueprintPickerProps) {
  return (
    <div className="-mx-1 overflow-x-auto px-1 pb-2">
      <ToggleGroup
        value={value === undefined ? [] : [String(value)]}
        onValueChange={(values: string[]) => {
          const next = Number(values.at(-1))
          if (Number.isFinite(next) && next > 0) onChange(next)
        }}
        variant="outline"
        className="flex w-max min-w-full justify-start gap-2"
      >
        {blueprints.map((blueprint) => (
          <ToggleGroupItem
            key={blueprint.id}
            value={String(blueprint.id)}
            aria-label={`选择项目模板 ${blueprint.name}`}
            className="h-auto min-h-28 w-64 shrink-0 flex-col items-start gap-2 px-4 py-3 text-left"
          >
            <span className="flex w-full items-center justify-between gap-2">
              <span className="truncate font-medium text-sm">{blueprint.name}</span>
              {value === blueprint.id ? <Check /> : null}
            </span>
            <span className="line-clamp-2 text-muted-foreground text-xs leading-5">
              {blueprint.description || "已发布项目模板"}
            </span>
            <span className="text-muted-foreground text-xs">v{blueprint.blueprintVersion}</span>
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  )
}

function createCoverIdempotencyKey(): string {
  return `project-cover-${globalThis.crypto.randomUUID()}`
}

/** 去除 displayNamePattern 中的 %02d 等 printf 序号占位符，预览阶段不展示具体序号。 */
function stripSlotNamePattern(pattern: string): string {
  return pattern.replace(/%0?\d*d/g, "").trim()
}

interface BlueprintActionStep {
  actionKey: string
  targetTemplateKey: string
  dependencyTemplateKeys: string[]
}

/** 从蓝图 actionSpec（后端未强类型的 JSON）中防御性解析生成流程步骤。 */
function parseBlueprintActions(
  actionSpec: Record<string, unknown> | undefined
): BlueprintActionStep[] {
  const actions = actionSpec?.actions
  if (!Array.isArray(actions)) return []
  return actions.flatMap((item) => {
    if (typeof item !== "object" || item === null) return []
    const record = item as Record<string, unknown>
    const actionKey = record.actionKey
    const targetTemplateKey = record.targetTemplateKey
    if (typeof actionKey !== "string" || typeof targetTemplateKey !== "string") return []
    const dependencyTemplateKeys = Array.isArray(record.dependencyTemplateKeys)
      ? record.dependencyTemplateKeys.filter((key): key is string => typeof key === "string")
      : []
    return [{ actionKey, targetTemplateKey, dependencyTemplateKeys }]
  })
}

/** 按 parentTemplateKey 构建产物层级并渲染可折叠树形结构。 */
function SlotProductTree({ templates }: { templates: AigcBlueprintSlotTemplate[] }) {
  const sorted = [...templates].toSorted(
    (left, right) => (left.orderNo ?? 0) - (right.orderNo ?? 0)
  )
  const roots = sorted.filter((template) => !template.parentTemplateKey)
  const childrenByParent = new Map<string, AigcBlueprintSlotTemplate[]>()
  for (const template of sorted) {
    if (!template.parentTemplateKey) continue
    const list = childrenByParent.get(template.parentTemplateKey) ?? []
    list.push(template)
    childrenByParent.set(template.parentTemplateKey, list)
  }

  function renderNode(template: AigcBlueprintSlotTemplate) {
    const children = childrenByParent.get(template.templateKey) ?? []
    const config = getObjectTypeConfig(
      template.objectType as Parameters<typeof getObjectTypeConfig>[0]
    )
    const Icon = config?.icon ?? ImageIcon
    const isRequired = (template.defaultContractRole ?? "REQUIRED") === "REQUIRED"

    const row = (
      <div className="flex items-center gap-2 py-1.5">
        <Icon className="size-4 shrink-0 text-muted-foreground" />
        <span className="flex min-w-0 shrink items-center gap-1 truncate text-sm">
          {(template.displayNamePattern && stripSlotNamePattern(template.displayNamePattern)) ||
            config?.label ||
            template.templateKey}
          {isRequired ? (
            <Star className="size-3.5 shrink-0 fill-amber-400 text-amber-400" aria-label="必需" />
          ) : null}
        </span>
        <span className="ml-auto shrink-0 text-muted-foreground text-xs">
          ×{template.defaultCount}
        </span>
      </div>
    )

    if (children.length === 0) {
      return <li key={template.templateKey}>{row}</li>
    }

    return (
      <li key={template.templateKey}>
        <Collapsible defaultOpen>
          <CollapsibleTrigger className="flex w-full items-center gap-1 text-left [&[data-panel-open]_svg[data-tree-caret]]:rotate-90">
            <ChevronRight
              data-tree-caret
              className="size-3.5 shrink-0 text-muted-foreground transition-transform"
            />
            <div className="min-w-0 flex-1">{row}</div>
          </CollapsibleTrigger>
          <CollapsibleContent>
            <ul className="ml-5 flex flex-col border-muted-foreground/20 border-l pl-3">
              {children.map((child) => renderNode(child))}
            </ul>
          </CollapsibleContent>
        </Collapsible>
      </li>
    )
  }

  if (roots.length === 0) {
    return <p className="text-muted-foreground text-sm">该项目模板没有预设产物。</p>
  }

  return <ul className="flex flex-col">{roots.map((template) => renderNode(template))}</ul>
}

/** 按顺序渲染生成流程步骤条。 */
function BlueprintActionSteps({
  steps,
  slotLabelByKey
}: {
  steps: BlueprintActionStep[]
  slotLabelByKey: Map<string, string>
}) {
  if (steps.length === 0) {
    return (
      <p className="rounded-lg border border-dashed p-3 text-muted-foreground text-sm">
        该项目模板没有预设生成流程。
      </p>
    )
  }

  return (
    <ol className="flex flex-col">
      {steps.map((step, index) => (
        <li key={step.actionKey} className="flex gap-3">
          <div className="flex flex-col items-center">
            <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-primary/15 font-medium text-primary text-xs">
              {index + 1}
            </span>
            {index < steps.length - 1 ? (
              <span className="w-px flex-1 bg-muted-foreground/20" />
            ) : null}
          </div>
          <div className="min-w-0 flex-1 pb-4">
            <p className="text-sm">
              {slotLabelByKey.get(step.targetTemplateKey) ?? step.targetTemplateKey}
            </p>
            <p className="text-muted-foreground text-xs">
              {step.dependencyTemplateKeys.length > 0
                ? `依赖：${step.dependencyTemplateKeys
                    .map((key) => slotLabelByKey.get(key) ?? key)
                    .join("、")}`
                : "无前置依赖"}
            </p>
          </div>
        </li>
      ))}
    </ol>
  )
}

export interface NewProjectLauncherProps {
  mode?: "compact" | "full"
  className?: string
  /** 从蓝图卡片预选进入时的蓝图 ID，跳过类型/蓝图选择步骤 */
  preselectedBlueprintId?: number
}

export function NewProjectLauncher({
  mode = "compact",
  className,
  preselectedBlueprintId
}: NewProjectLauncherProps) {
  const router = useRouter()
  const { data: projectAccess } = useEntityAccess("project")
  const { canCreate } = usePermissionGuard(projectAccess)
  const nameId = useId()
  const briefId = useId()
  const coverFileId = useId()
  const coverPromptId = useId()
  const projectDate = new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit" }).format(
    new Date()
  )
  const { data: typePage, isLoading: typesLoading } = useAigcProjectTypes()
  const { data: profilePage, isLoading: profilesLoading } = useAigcBrandProfiles()
  const { data: channelPage, isLoading: channelsLoading } = useAigcChannelSpecs({
    status: "published"
  })
  const materialize = useMaterializeAigcProject()
  const {
    upload: uploadCover,
    uploading: coverUploading,
    progress: coverUploadProgress
  } = useFileUpload({ maxWidth: 1920, maxHeight: 1080, quality: 0.9 })

  const methods = useForm<NewProjectFormValues>({
    resolver: zodResolver(newProjectSchema),
    defaultValues: {
      name: `新项目 · ${projectDate}`,
      projectTypeCode: "",
      blueprintVersionId: 0,
      brandProfileId: undefined,
      productionMode: "standard",
      channelCodes: [],
      documentVersionIds: [],
      coverMode: "AI_GENERATE",
      coverPrompt: "",
      brief: ""
    }
  })
  const { control, watch, setValue, handleSubmit, formState } = methods
  const nameEditedRef = { current: false }
  const nameEdited = nameEditedRef.current

  const projectTypeCode = watch("projectTypeCode")
  const blueprintVersionId = watch("blueprintVersionId")
  const brandProfileId = watch("brandProfileId")
  const channelCodes = watch("channelCodes")
  const coverMode = watch("coverMode")
  const coverUpload = watch("coverUpload")
  const coverPrompt = watch("coverPrompt")

  const allTypes = (typePage?.list ?? []).filter((type) => type.status === "published")
  const types = mode === "compact" ? allTypes.filter((type) => type.quickEntry) : allTypes
  const profiles = (profilePage?.list ?? []).filter(
    (profile) => profile.currentVersionId !== undefined
  )
  const channels = (channelPage?.list ?? []).filter((channel) => channel.status === "published")
  const selectedType = allTypes.find((type) => type.code === projectTypeCode)
  const { data: blueprintPage, isLoading: blueprintsLoading } = useAigcProjectBlueprints(
    {
      projectTypeCode: projectTypeCode || undefined,
      status: "published"
    },
    selectedType !== undefined
  )
  const blueprints = (blueprintPage?.list ?? []).filter(
    (blueprint) => blueprint.projectTypeCode === projectTypeCode
  )
  const selectedBlueprint =
    blueprints.find((blueprint) => blueprint.id === blueprintVersionId) ?? blueprints.at(0)
  const slotTemplates = selectedBlueprint?.slotTemplateSpec?.slotTemplates ?? []
  const slotLabelByKey = new Map(
    slotTemplates.map((template) => [
      template.templateKey,
      (template.displayNamePattern && stripSlotNamePattern(template.displayNamePattern)) ||
        template.templateKey
    ])
  )
  const blueprintActions = parseBlueprintActions(selectedBlueprint?.actionSpec)

  // 从蓝图卡片预选进入时，全量查询蓝图反查其所属项目类型，自动选中类型和蓝图两级。
  const { data: preselectedBlueprintPage } = useAigcProjectBlueprints(
    { status: "published" },
    preselectedBlueprintId !== undefined && !projectTypeCode
  )
  useEffect(() => {
    if (!preselectedBlueprintId || projectTypeCode) return
    const target = preselectedBlueprintPage?.list.find(
      (blueprint) => blueprint.id === preselectedBlueprintId
    )
    if (!target) return
    let type: AigcProjectType | undefined
    for (const item of allTypes) {
      if (item.code === target.projectTypeCode) {
        type = item
        break
      }
    }
    setValue("projectTypeCode", target.projectTypeCode)
    setValue("blueprintVersionId", target.id)
    setValue("productionMode", target.productionMode)
    setValue("channelCodes", type?.defaultChannels ?? [])
    if (!nameEdited) setValue("name", `${target.name} · ${projectDate}`)
  }, [
    allTypes,
    nameEdited,
    preselectedBlueprintId,
    preselectedBlueprintPage,
    projectDate,
    projectTypeCode,
    setValue
  ])

  function handleTypeChange(code: AigcProjectTypeCode) {
    const type = allTypes.find((item) => item.code === code)
    setValue("projectTypeCode", code)
    setValue("productionMode", type?.defaultProductionMode ?? "standard")
    setValue("channelCodes", type?.defaultChannels ?? [])
    setValue("blueprintVersionId", 0)
    if (!nameEditedRef.current && type) setValue("name", `${type.name} · ${projectDate}`)
  }

  function handleCoverModeChange(value: "UPLOAD" | "AI_GENERATE") {
    setValue("coverMode", value)
    if (value === "AI_GENERATE") {
      setValue("coverUpload", undefined)
    }
  }

  async function handleCoverFile(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith("image/")) return
    const result = await uploadCover(file)
    setValue("coverUpload", { fileId: result.fileId, url: result.url, name: result.name })
  }

  function onSubmit(values: NewProjectFormValues) {
    if (!canCreate || !selectedType || !selectedBlueprint || coverUploading) return
    const selectedProfile = profiles.find((profile) => profile.id === values.brandProfileId)
    materialize.mutate(
      {
        projectTypeCode: selectedType.code,
        name: values.name.trim(),
        blueprintVersionId: selectedBlueprint.id,
        briefJson: values.brief?.trim() || undefined,
        brandProfileVersionIds: selectedProfile?.currentVersionId
          ? [selectedProfile.currentVersionId]
          : [],
        channelSpecVersionIds: channels
          .filter((channel) => values.channelCodes.includes(channel.code))
          .map((channel) => channel.id),
        productionMode: values.productionMode,
        budgetTier: "MEDIUM",
        qualityTier: "STANDARD",
        slotOverrides: [],
        coverMode: values.coverMode,
        ...(values.coverMode === "UPLOAD" && values.coverUpload
          ? { coverFileId: values.coverUpload.fileId }
          : {}),
        ...(values.coverMode === "AI_GENERATE"
          ? {
              coverPrompt: values.coverPrompt?.trim() || undefined,
              coverIdempotencyKey: createCoverIdempotencyKey()
            }
          : {})
      },
      { onSuccess: (result) => router.push(paths.studio.project(result.project.id)) }
    )
  }

  return (
    <GlassCard glow="violet" className={className}>
      <GlassCardBody className="flex flex-col gap-6 p-5 sm:p-7">
        <h2 className="font-semibold text-xl">新建项目</h2>

        <form
          className="flex flex-col gap-6"
          onSubmit={handleSubmit(onSubmit)}
          noValidate
          autoComplete="off"
        >
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="flex flex-col gap-2 sm:w-[30%]">
              <span className="font-medium text-sm">
                项目封面 <span className="text-destructive">*</span>
              </span>
              <div className="relative flex aspect-video w-full items-center justify-center overflow-hidden rounded-xl border border-dashed bg-muted/30">
                {coverMode === "UPLOAD" && coverUpload ? (
                  <>
                    {/* biome-ignore lint/performance/noImgElement: 上传结果 URL 为即时预览地址 */}
                    <img
                      src={coverUpload.url}
                      alt="项目封面预览"
                      className="size-full object-cover"
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label="移除已上传封面"
                      className="absolute top-1 right-1 bg-background/80"
                      onClick={() => setValue("coverUpload", undefined)}
                    >
                      <X />
                    </Button>
                  </>
                ) : coverMode === "UPLOAD" ? (
                  <Button
                    type="button"
                    variant="ghost"
                    nativeButton={false}
                    // biome-ignore lint/a11y/noLabelWithoutControl: Button 通过 render 渲染为 label，触发文件选择
                    render={<label htmlFor={coverFileId} />}
                    className="flex size-full cursor-pointer flex-col items-center justify-center gap-1 rounded-none text-muted-foreground"
                    disabled={coverUploading}
                  >
                    <ImageIcon />
                    <span className="text-xs">点击上传</span>
                  </Button>
                ) : (
                  <div className="flex flex-col items-center gap-1 text-muted-foreground">
                    <Sparkles />
                    <span className="text-xs">AI 生成</span>
                  </div>
                )}
                <Input
                  id={coverFileId}
                  type="file"
                  accept="image/*"
                  className="sr-only"
                  disabled={coverUploading}
                  onChange={(event) => {
                    void handleCoverFile(event.target.files?.[0])
                    event.target.value = ""
                  }}
                />
              </div>
              <ToggleGroup
                value={[coverMode]}
                onValueChange={(values: string[]) => {
                  const next = values.at(-1)
                  if (next === "UPLOAD" || next === "AI_GENERATE") handleCoverModeChange(next)
                }}
                variant="outline"
                size="sm"
                className="flex w-full"
              >
                <ToggleGroupItem value="UPLOAD" aria-label="上传封面" className="flex-1">
                  <Upload />
                </ToggleGroupItem>
                <ToggleGroupItem value="AI_GENERATE" aria-label="AI 生成封面" className="flex-1">
                  <Sparkles />
                </ToggleGroupItem>
              </ToggleGroup>
              {coverUploading ? (
                <div className="flex w-full items-center gap-2 text-muted-foreground text-xs">
                  <Progress value={coverUploadProgress} className="flex-1" />
                  {coverUploadProgress}%
                </div>
              ) : null}
            </div>

            <div className="flex flex-1 flex-col gap-2">
              <label htmlFor={nameId} className="font-medium text-sm">
                项目名称 <span className="text-destructive">*</span>
              </label>
              <Controller
                name="name"
                control={control}
                render={({ field, fieldState: { error } }) => (
                  <>
                    <Input
                      id={nameId}
                      maxLength={200}
                      aria-invalid={!!error}
                      placeholder="输入项目名称"
                      {...field}
                      onChange={(event) => {
                        nameEditedRef.current = true
                        field.onChange(event)
                      }}
                    />
                    {error ? <p className="text-destructive text-xs">{error.message}</p> : null}
                  </>
                )}
              />
              {coverMode === "AI_GENERATE" ? (
                <div className="flex flex-1 flex-col gap-1">
                  <Label
                    htmlFor={coverPromptId}
                    className="font-normal text-muted-foreground text-xs"
                  >
                    封面描述（可选）
                  </Label>
                  <Textarea
                    id={coverPromptId}
                    value={coverPrompt}
                    onChange={(event) => setValue("coverPrompt", event.target.value)}
                    placeholder="例如：电影感、暖色调、突出主角与城市夜景"
                    className="min-h-16 resize-y"
                  />
                </div>
              ) : null}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <span className="font-medium text-sm">当前品牌 / IP</span>
            <div className="flex flex-wrap items-center gap-3">
              <BrandProfileSelect
                profiles={profiles}
                value={brandProfileId}
                onChange={(value) => setValue("brandProfileId", value)}
                disabled={profilesLoading}
              />
              {profiles.length === 0 && !profilesLoading ? (
                <Link href="/studio/brands" className="text-primary text-sm hover:underline">
                  <Plus /> 新建并发布品牌资料
                </Link>
              ) : null}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <span className="font-medium text-sm">
              项目类型 <span className="text-destructive">*</span>
            </span>
            <ProjectTypePicker
              types={types}
              value={projectTypeCode || undefined}
              onChange={handleTypeChange}
              showAssistantChoice={mode === "compact"}
            />
            {typesLoading ? (
              <p className="text-muted-foreground text-sm">正在加载项目类型…</p>
            ) : null}
            {formState.errors.projectTypeCode ? (
              <p className="text-destructive text-xs">{formState.errors.projectTypeCode.message}</p>
            ) : null}
          </div>

          {selectedType ? (
            <div className="flex flex-col gap-2">
              <span className="font-medium text-sm">
                项目模板 <span className="text-destructive">*</span>
              </span>
              {blueprintsLoading ? (
                <p className="text-muted-foreground text-sm">正在加载项目模板…</p>
              ) : blueprints.length > 0 ? (
                <ProjectBlueprintPicker
                  blueprints={blueprints}
                  value={selectedBlueprint?.id}
                  onChange={(value) => setValue("blueprintVersionId", value)}
                />
              ) : (
                <p className="text-destructive text-sm">
                  当前项目类型没有已发布项目模板，暂不能创建。
                </p>
              )}
              {formState.errors.blueprintVersionId ? (
                <p className="text-destructive text-xs">
                  {formState.errors.blueprintVersionId.message}
                </p>
              ) : null}
            </div>
          ) : null}

          {selectedBlueprint ? (
            <div className="flex flex-col gap-5">
              <div className="flex flex-col gap-2">
                <span className="font-medium text-sm">投放渠道</span>
                {channelsLoading ? (
                  <p className="text-muted-foreground text-sm">正在加载渠道…</p>
                ) : (
                  <ChannelPicker
                    channels={channels.map((channel) => channel.code)}
                    value={channelCodes}
                    onChange={(value) => setValue("channelCodes", value)}
                  />
                )}
              </div>

              <div className="grid gap-5 rounded-xl border bg-muted/20 p-4 lg:grid-cols-2">
                <div className="flex flex-col gap-3">
                  <div>
                    <p className="font-medium text-sm">产物</p>
                    <p className="text-muted-foreground text-xs">
                      按项目模板默认数量创建，创建后可在项目中按需添加。
                    </p>
                  </div>
                  <SlotProductTree templates={slotTemplates} />
                </div>

                <div className="flex flex-col gap-3">
                  <div>
                    <p className="font-medium text-sm">生成流程</p>
                    <p className="text-muted-foreground text-xs">
                      创建后按此流程逐步生成，可在项目详情执行每一步。
                    </p>
                  </div>
                  <BlueprintActionSteps steps={blueprintActions} slotLabelByKey={slotLabelByKey} />
                </div>
              </div>
            </div>
          ) : null}

          <div className="flex flex-col gap-2">
            <label htmlFor={briefId} className="font-medium text-sm">
              项目简介（可选）
            </label>
            <Controller
              name="brief"
              control={control}
              render={({ field }) => (
                <Textarea
                  id={briefId}
                  placeholder={
                    selectedType ? getProjectTypeConfig(selectedType).placeholder : "先选择项目类型"
                  }
                  className="min-h-28 resize-y"
                  {...field}
                  value={field.value ?? ""}
                />
              )}
            />
          </div>

          {!canCreate ? (
            <p className="text-amber-600 text-sm">当前账号没有创建项目的权限。</p>
          ) : null}
          <div className="flex justify-end">
            <GlowButton
              tone="violet"
              size="lg"
              type="submit"
              disabled={
                !canCreate ||
                !selectedType ||
                !selectedBlueprint ||
                coverUploading ||
                materialize.isPending
              }
            >
              <Sparkles />
              {materialize.isPending ? "创建中…" : "创建项目"}
            </GlowButton>
          </div>
        </form>
      </GlassCardBody>
    </GlassCard>
  )
}
