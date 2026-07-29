/**
 * Content Studio 项目类型优先创建器。
 * @author AaronZZH & Kiro
 */

"use client"

import { Bot, Check, Clapperboard, Plus, Sparkles } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useId, useMemo, useState } from "react"
import { GlassCard, GlassCardBody, GlowButton } from "@/components/studio"
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
  type ContentBrandProfileVO,
  type ContentChannel,
  type ContentProductionMode,
  type ContentProjectTypeCode,
  type ContentProjectTypeVO,
  useContentBrandProfiles,
  useContentProjectTypes,
  useMaterializeContentProject
} from "@/lib/api/rest/content"
import { CHANNEL_LABELS, getProjectTypeConfig } from "./project-type-config"

interface BrandProfileSelectProps {
  profiles: ContentBrandProfileVO[]
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
          {profiles.find((profile) => profile.id === value)?.name ?? "选择品牌 / IP 资料"}
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
  types: ContentProjectTypeVO[]
  value?: ContentProjectTypeCode
  onChange: (value: ContentProjectTypeCode) => void
  showAssistantFallback?: boolean
}

export function ProjectTypePicker({
  types,
  value,
  onChange,
  showAssistantFallback = true
}: ProjectTypePickerProps) {
  return (
    <div className="flex flex-col gap-3">
      <ToggleGroup
        value={value ? [value] : []}
        onValueChange={(values: string[]) => {
          const next = values.at(-1)
          if (next) onChange(next as ContentProjectTypeCode)
        }}
        variant="outline"
        className="grid w-full grid-cols-2 gap-2 md:grid-cols-3"
      >
        {types.map((type) => {
          const config = getProjectTypeConfig(type)
          const Icon = config.icon
          return (
            <ToggleGroupItem
              key={type.code}
              value={type.code}
              aria-label={config.label}
              className="h-auto min-h-20 w-full flex-col items-start gap-2 px-4 py-3 text-left"
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
      {showAssistantFallback ? (
        <Link
          href="/studio/chat"
          className="flex w-fit items-center gap-2 rounded-lg px-2 py-1 text-muted-foreground text-sm transition-colors hover:bg-muted hover:text-foreground"
        >
          <Bot className="size-4" />
          不确定，让助手帮我选择
        </Link>
      ) : null}
    </div>
  )
}

interface ChannelPickerProps {
  channels: ContentChannel[]
  value: ContentChannel[]
  onChange: (value: ContentChannel[]) => void
}

export function ChannelPicker({ channels, value, onChange }: ChannelPickerProps) {
  return (
    <ToggleGroup
      value={value}
      onValueChange={(values: string[]) => onChange(values as ContentChannel[])}
      variant="outline"
      size="sm"
      className="flex w-full flex-wrap justify-start"
    >
      {channels.map((channel) => (
        <ToggleGroupItem key={channel} value={channel} aria-label={CHANNEL_LABELS[channel]}>
          {CHANNEL_LABELS[channel]}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

interface ProductionModePickerProps {
  value: ContentProductionMode
  onChange: (value: ContentProductionMode) => void
}

export function ProductionModePicker({ value, onChange }: ProductionModePickerProps) {
  return (
    <ToggleGroup
      value={[value]}
      onValueChange={(values: string[]) => {
        const next = values.at(-1)
        if (next) onChange(next as ContentProductionMode)
      }}
      variant="outline"
    >
      <ToggleGroupItem value="short_drama" aria-label="短剧">
        <Clapperboard /> 短剧
      </ToggleGroupItem>
      <ToggleGroupItem value="motion_comic" aria-label="漫剧">
        <Sparkles /> 漫剧
      </ToggleGroupItem>
    </ToggleGroup>
  )
}

export interface NewProjectLauncherProps {
  mode?: "compact" | "full"
  className?: string
}

export function NewProjectLauncher({ mode = "compact", className }: NewProjectLauncherProps) {
  const router = useRouter()
  const briefId = useId()
  const { data: typePage, isLoading: typesLoading } = useContentProjectTypes()
  const { data: profilePage, isLoading: profilesLoading } = useContentBrandProfiles()
  const materialize = useMaterializeContentProject()
  const [brandProfileId, setBrandProfileId] = useState<number>()
  const [projectTypeCode, setProjectTypeCode] = useState<ContentProjectTypeCode>()
  const [productionMode, setProductionMode] = useState<ContentProductionMode>("standard")
  const [channels, setChannels] = useState<ContentChannel[]>([])
  const [brief, setBrief] = useState("")

  const allTypes = typePage?.list ?? []
  const types = mode === "compact" ? allTypes.filter((type) => type.quickEntry) : allTypes
  const profiles = profilePage?.list ?? []
  const selectedType = allTypes.find((type) => type.code === projectTypeCode)
  const availableChannels = useMemo(() => {
    const defaults = selectedType?.defaultChannels ?? []
    const common: ContentChannel[] = [
      "xiaohongshu",
      "douyin",
      "wechat_channels",
      "wechat_mp",
      "offline_poster",
      "bilibili"
    ]
    return Array.from(new Set([...defaults, ...common]))
  }, [selectedType])

  function handleTypeChange(code: ContentProjectTypeCode) {
    const type = allTypes.find((item) => item.code === code)
    setProjectTypeCode(code)
    setChannels(type?.defaultChannels ?? [])
    setProductionMode(type?.defaultProductionMode ?? "standard")
  }

  function handleCreate() {
    if (!selectedType) return
    const date = new Intl.DateTimeFormat("zh-CN", { month: "2-digit", day: "2-digit" }).format(
      new Date()
    )
    materialize.mutate(
      {
        projectTypeCode: selectedType.code,
        name: `${selectedType.name} · ${date}`,
        brief: brief.trim() || undefined,
        primaryBrandProfileId: brandProfileId,
        channels,
        productionMode
      },
      { onSuccess: (project) => router.push(`/studio/projects/${project.id}`) }
    )
  }

  return (
    <GlassCard glow="violet" className={className}>
      <GlassCardBody className="flex flex-col gap-6 p-5 sm:p-7">
        <div className="flex flex-col gap-1">
          <h2 className="font-semibold text-xl">新建项目</h2>
          <p className="text-muted-foreground text-sm">选择业务目标，立即建立可编辑的项目骨架。</p>
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
                <Plus className="mr-1 inline size-4" />
                新建品牌资料
              </Link>
            ) : null}
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <span className="font-medium text-sm">这次想做什么？</span>
          <ProjectTypePicker
            types={types}
            value={projectTypeCode}
            onChange={handleTypeChange}
            showAssistantFallback={mode === "compact"}
          />
          {typesLoading ? <p className="text-muted-foreground text-sm">正在加载项目类型…</p> : null}
        </div>

        {mode === "full" && projectTypeCode === "narrative_series" ? (
          <div className="flex flex-col gap-2">
            <span className="font-medium text-sm">生产模式</span>
            <ProductionModePicker value={productionMode} onChange={setProductionMode} />
          </div>
        ) : null}

        <div className="flex flex-col gap-2">
          <span className="font-medium text-sm">投放渠道（可选）</span>
          <ChannelPicker channels={availableChannels} value={channels} onChange={setChannels} />
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
            disabled={!selectedType || materialize.isPending}
            onClick={handleCreate}
          >
            <Sparkles />
            {materialize.isPending ? "正在创建…" : "创建项目"}
          </GlowButton>
        </div>
      </GlassCardBody>
    </GlassCard>
  )
}
