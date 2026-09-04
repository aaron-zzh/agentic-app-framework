/**
 * Content Studio 项目类型优先创建器。
 * @author AaronZZH & Kiro
 */

"use client"

import { Bot, Check, Clapperboard, ImageIcon, Plus, Sparkles, Upload, X } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useId, useState } from "react"
import { GlassCard, GlassCardBody, GlowButton } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
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
  type AigcBrandProfile,
  type AigcChannelCode,
  type AigcProductionMode,
  type AigcProjectBlueprint,
  type AigcProjectCoverMode,
  type AigcProjectType,
  type AigcProjectTypeCode,
  useAigcBrandProfiles,
  useAigcProjectBlueprints,
  useAigcProjectTypes,
  useMaterializeAigcProject
} from "@/lib/api/rest/ai/aigc"
import { paths } from "@/lib/constants/paths"
import { type UploadResult, useFileUpload } from "@/lib/hooks/use-file-upload"
import { getChannelLabel, getProjectTypeConfig } from "./project-type-config"

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

interface ProductionModePickerProps {
  value: AigcProductionMode
  onChange: (value: AigcProductionMode) => void
}

export function ProductionModePicker({ value, onChange }: ProductionModePickerProps) {
  return (
    <ToggleGroup
      value={[value]}
      onValueChange={(values: string[]) => {
        const next = values.at(-1)
        if (next === "standard" || next === "short_drama" || next === "motion_comic") {
          onChange(next)
        }
      }}
      variant="outline"
    >
      <ToggleGroupItem value="standard" aria-label="标准内容">
        <Sparkles /> 标准
      </ToggleGroupItem>
      <ToggleGroupItem value="short_drama" aria-label="短剧">
        <Clapperboard /> 短剧
      </ToggleGroupItem>
      <ToggleGroupItem value="motion_comic" aria-label="漫剧">
        <Sparkles /> 漫剧
      </ToggleGroupItem>
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
            aria-label={`选择蓝图 ${blueprint.name}`}
            className="h-auto min-h-28 w-64 shrink-0 flex-col items-start gap-2 px-4 py-3 text-left"
          >
            <span className="flex w-full items-center justify-between gap-2">
              <span className="truncate font-medium text-sm">{blueprint.name}</span>
              {value === blueprint.id ? <Check /> : null}
            </span>
            <span className="line-clamp-2 text-muted-foreground text-xs leading-5">
              {blueprint.description || "已发布项目蓝图"}
            </span>
            <span className="text-muted-foreground text-xs">v{blueprint.blueprintVersion}</span>
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  )
}

type ProjectCoverUpload = Pick<UploadResult, "fileId" | "url" | "name">

function createCoverIdempotencyKey(): string {
  return `project-cover-${globalThis.crypto.randomUUID()}`
}

export interface NewProjectLauncherProps {
  mode?: "compact" | "full"
  className?: string
}

export function NewProjectLauncher({ mode = "compact", className }: NewProjectLauncherProps) {
  const router = useRouter()
  const nameId = useId()
  const briefId = useId()
  const coverFileId = useId()
  const coverPromptId = useId()
  const projectDate = new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit" }).format(
    new Date()
  )
  const { data: typePage, isLoading: typesLoading } = useAigcProjectTypes()
  const { data: profilePage, isLoading: profilesLoading } = useAigcBrandProfiles()
  const materialize = useMaterializeAigcProject()
  const {
    upload: uploadCover,
    uploading: coverUploading,
    progress: coverUploadProgress
  } = useFileUpload({ maxWidth: 1920, maxHeight: 1080, quality: 0.9 })
  const [name, setName] = useState(`新项目 · ${projectDate}`)
  const [nameEdited, setNameEdited] = useState(false)
  const [brandProfileId, setBrandProfileId] = useState<number>()
  const [projectTypeCode, setProjectTypeCode] = useState<AigcProjectTypeCode>()
  const [productionMode, setProductionMode] = useState<AigcProductionMode>("standard")
  const [blueprintVersionId, setBlueprintVersionId] = useState<number>()
  const [brief, setBrief] = useState("")
  const [coverMode, setCoverMode] = useState<AigcProjectCoverMode>("NONE")
  const [coverUpload, setCoverUpload] = useState<ProjectCoverUpload | null>(null)
  const [coverPrompt, setCoverPrompt] = useState("")
  const [coverIdempotencyKey, setCoverIdempotencyKey] = useState(createCoverIdempotencyKey)
  const [coverError, setCoverError] = useState<string | null>(null)

  const allTypes = (typePage?.list ?? []).filter((type) => type.status === "published")
  const types = mode === "compact" ? allTypes.filter((type) => type.quickEntry) : allTypes
  const profiles = (profilePage?.list ?? []).filter(
    (profile) => profile.currentVersionId !== undefined
  )
  const selectedType = allTypes.find((type) => type.code === projectTypeCode)
  const { data: blueprintPage, isLoading: blueprintsLoading } = useAigcProjectBlueprints(
    {
      projectTypeCode,
      productionMode,
      status: "published"
    },
    selectedType !== undefined
  )
  const blueprints = (blueprintPage?.list ?? []).filter(
    (blueprint) =>
      blueprint.projectTypeCode === projectTypeCode && blueprint.productionMode === productionMode
  )
  const selectedBlueprint =
    blueprints.find((blueprint) => blueprint.id === blueprintVersionId) ?? blueprints.at(0)

  function handleTypeChange(code: AigcProjectTypeCode) {
    const type = allTypes.find((item) => item.code === code)
    setProjectTypeCode(code)
    setProductionMode(type?.defaultProductionMode ?? "standard")
    setBlueprintVersionId(undefined)
    if (!nameEdited && type) setName(`${type.name} · ${projectDate}`)
  }

  function handleProductionModeChange(value: AigcProductionMode) {
    setProductionMode(value)
    setBlueprintVersionId(undefined)
  }

  function handleCoverModeChange(value: AigcProjectCoverMode) {
    if (value === "AI_GENERATE" && coverMode !== "AI_GENERATE") {
      setCoverIdempotencyKey(createCoverIdempotencyKey())
    }
    setCoverMode(value)
    setCoverError(null)
  }

  async function handleCoverFile(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith("image/")) {
      setCoverError("请选择图片文件")
      return
    }
    setCoverError(null)
    try {
      const result = await uploadCover(file)
      setCoverUpload({ fileId: result.fileId, url: result.url, name: result.name })
    } catch (error) {
      setCoverError(error instanceof Error ? error.message : "封面上传失败")
    }
  }

  function handleCreate() {
    const trimmedName = name.trim()
    if (
      !trimmedName ||
      !selectedType ||
      !selectedBlueprint ||
      coverUploading ||
      (coverMode === "UPLOAD" && !coverUpload)
    ) {
      return
    }
    const selectedProfile = profiles.find((profile) => profile.id === brandProfileId)
    materialize.mutate(
      {
        projectTypeCode: selectedType.code,
        name: trimmedName,
        blueprintVersionId: selectedBlueprint.id,
        briefJson: brief.trim() || undefined,
        brandProfileVersionIds: selectedProfile?.currentVersionId
          ? [selectedProfile.currentVersionId]
          : [],
        productionMode,
        coverMode,
        ...(coverMode === "UPLOAD" && coverUpload ? { coverFileId: coverUpload.fileId } : {}),
        ...(coverMode === "AI_GENERATE"
          ? {
              coverPrompt: coverPrompt.trim() || undefined,
              coverIdempotencyKey
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

        <div className="flex flex-col gap-2">
          <label htmlFor={nameId} className="font-medium text-sm">
            项目名称 <span className="text-destructive">*</span>
          </label>
          <Input
            id={nameId}
            required
            maxLength={200}
            value={name}
            onChange={(event) => {
              setName(event.target.value)
              setNameEdited(true)
            }}
            placeholder="输入项目名称"
          />
        </div>

        <div className="flex flex-col gap-2">
          <span className="font-medium text-sm">当前品牌 / IP</span>
          <div className="flex flex-wrap items-center gap-3">
            <BrandProfileSelect
              profiles={profiles}
              value={brandProfileId}
              onChange={setBrandProfileId}
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
          <span className="font-medium text-sm">项目类型</span>
          <ProjectTypePicker
            types={types}
            value={projectTypeCode}
            onChange={handleTypeChange}
            showAssistantChoice={mode === "compact"}
          />
          {typesLoading ? <p className="text-muted-foreground text-sm">正在加载项目类型…</p> : null}
        </div>

        {mode === "full" && projectTypeCode === "narrative_series" ? (
          <div className="flex flex-col gap-2">
            <span className="font-medium text-sm">生产模式</span>
            <ProductionModePicker value={productionMode} onChange={handleProductionModeChange} />
          </div>
        ) : null}

        {selectedType ? (
          <div className="flex flex-col gap-2">
            <span className="font-medium text-sm">蓝图模板</span>
            {blueprintsLoading ? (
              <p className="text-muted-foreground text-sm">正在加载蓝图模板…</p>
            ) : blueprints.length > 0 ? (
              <ProjectBlueprintPicker
                blueprints={blueprints}
                value={selectedBlueprint?.id}
                onChange={setBlueprintVersionId}
              />
            ) : (
              <p className="text-destructive text-sm">
                当前项目类型和生产模式没有已发布蓝图，暂不能创建。
              </p>
            )}
          </div>
        ) : null}

        <div className="flex flex-col gap-3">
          <span className="font-medium text-sm">项目封面</span>
          <ToggleGroup
            value={[coverMode]}
            onValueChange={(values: string[]) => {
              const next = values.at(-1)
              if (next === "NONE" || next === "UPLOAD" || next === "AI_GENERATE") {
                handleCoverModeChange(next)
              }
            }}
            variant="outline"
            className="flex w-full flex-wrap justify-start"
          >
            <ToggleGroupItem value="NONE" aria-label="不设置封面">
              不设置
            </ToggleGroupItem>
            <ToggleGroupItem value="UPLOAD" aria-label="上传封面">
              <Upload /> 上传
            </ToggleGroupItem>
            <ToggleGroupItem value="AI_GENERATE" aria-label="AI 生成封面">
              <Sparkles /> AI 生成
            </ToggleGroupItem>
          </ToggleGroup>

          {coverMode === "UPLOAD" ? (
            <div className="flex flex-col gap-3 rounded-xl border bg-muted/30 p-3">
              {coverUpload ? (
                <div className="flex items-center gap-3">
                  {/* biome-ignore lint/performance/noImgElement: 上传结果 URL 为即时预览地址 */}
                  <img
                    src={coverUpload.url}
                    alt="项目封面预览"
                    className="aspect-video w-28 rounded-lg object-cover"
                  />
                  <p className="min-w-0 flex-1 truncate text-sm">{coverUpload.name}</p>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label="移除已上传封面"
                    onClick={() => setCoverUpload(null)}
                  >
                    <X />
                  </Button>
                </div>
              ) : (
                <div className="flex items-center gap-3">
                  <ImageIcon className="text-muted-foreground" />
                  <Button
                    nativeButton={false}
                    // biome-ignore lint/a11y/noLabelWithoutControl: Button 通过 render 渲染为 label，文本内容由 children 提供
                    render={<label htmlFor={coverFileId} />}
                    variant="outline"
                    size="sm"
                    className="cursor-pointer"
                  >
                    选择图片
                  </Button>
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
              )}
              {coverUploading ? (
                <div className="flex items-center gap-3 text-muted-foreground text-xs">
                  <Progress value={coverUploadProgress} className="flex-1" />
                  {coverUploadProgress}%
                </div>
              ) : null}
            </div>
          ) : null}

          {coverMode === "AI_GENERATE" ? (
            <div className="flex flex-col gap-2 rounded-xl border bg-muted/30 p-3">
              <label htmlFor={coverPromptId} className="font-medium text-sm">
                封面描述（可选）
              </label>
              <Textarea
                id={coverPromptId}
                value={coverPrompt}
                onChange={(event) => setCoverPrompt(event.target.value)}
                placeholder="例如：电影感、暖色调、突出主角与城市夜景"
                className="min-h-20 resize-y"
              />
              <p className="text-muted-foreground text-xs">
                创建成功后异步生成，可在项目详情查看 PENDING / FAILED / READY 状态。
              </p>
            </div>
          ) : null}

          {coverError ? <p className="text-destructive text-sm">{coverError}</p> : null}
          {coverMode !== "NONE" ? (
            <p className="text-muted-foreground text-xs">
              封面仅作为项目级素材使用，不会自动保存到资产库。
            </p>
          ) : null}
        </div>

        <div className="flex flex-col gap-2">
          <label htmlFor={briefId} className="font-medium text-sm">
            补充说明（可选）
          </label>
          <Textarea
            id={briefId}
            value={brief}
            onChange={(event) => setBrief(event.target.value)}
            placeholder={
              selectedType ? getProjectTypeConfig(selectedType).placeholder : "先选择项目类型"
            }
            className="min-h-28 resize-y"
          />
        </div>

        <div className="flex justify-end">
          <GlowButton
            tone="violet"
            size="lg"
            disabled={
              !name.trim() ||
              !selectedType ||
              !selectedBlueprint ||
              coverUploading ||
              (coverMode === "UPLOAD" && !coverUpload) ||
              materialize.isPending
            }
            onClick={handleCreate}
          >
            <Sparkles />
            {materialize.isPending ? "正在物化…" : "创建项目"}
          </GlowButton>
        </div>
      </GlassCardBody>
    </GlassCard>
  )
}
