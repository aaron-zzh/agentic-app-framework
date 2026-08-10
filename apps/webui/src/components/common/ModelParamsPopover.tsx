/**
 * 模型参数弹层——以紧凑摘要触发，按模型能力分组展示生成参数。
 *
 * 用于统一媒体 Composer 的图像与视频参数设置，不替代传统表单中的 ModelParamsBar。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown } from "lucide-react"
import { useId } from "react"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Switch } from "@/components/ui/switch"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import type { AiModelVO, ImageModeConfig } from "@/lib/api/rest/ai"
import { calcRatio } from "@/lib/api/rest/ai"
import type { GenerationParams } from "@/lib/hooks/use-generation-params"
import { cn } from "@/lib/utils"

interface ModelParamsPopoverProps {
  model: AiModelVO | undefined
  params: GenerationParams
  onChangeParams: (patch: Partial<GenerationParams>) => void
  isEditMode?: boolean
}

interface Option {
  value: string
  label: string
}

interface DimensionOption extends Option {
  width: number
  height: number
  ratio: string
  resolution: string
}

const DEFAULT_RATIOS = ["1:1", "9:16", "16:9", "4:3", "3:4"]
const QUALITY_LABELS: Record<string, string> = {
  auto: "自动画质",
  low: "低画质",
  medium: "标准画质",
  high: "高画质"
}
const BACKGROUND_LABELS: Record<string, string> = {
  auto: "自动",
  transparent: "透明",
  opaque: "不透明"
}
const MODERATION_LABELS: Record<string, string> = {
  auto: "自动",
  low: "宽松"
}

function uniqueOptions(options: Option[]): Option[] {
  return Array.from(new Map(options.map((option) => [option.value, option])).values())
}

function resolutionLabel(width: number, height: number): string {
  const longest = Math.max(width, height)
  if (longest >= 3000) return "4K"
  if (longest >= 1800) return "2K"
  return "1K"
}

function parseDimensions(value: string | undefined): [number, number] | null {
  if (!value || value === "auto") return null
  const [width, height] = value.split("x").map(Number)
  return width > 0 && height > 0 ? [width, height] : null
}

function dimensionOptions(sizes: (string | [number, number])[]): DimensionOption[] {
  return sizes.flatMap((size) => {
    if (!Array.isArray(size)) return []
    const [width, height] = size
    return [
      {
        value: `${width}x${height}`,
        label: `${width}×${height}`,
        width,
        height,
        ratio: calcRatio(width, height),
        resolution: resolutionLabel(width, height)
      }
    ]
  })
}

function choiceGridClass(count: number): string {
  if (count <= 3) return "grid-cols-3"
  if (count === 4) return "grid-cols-4"
  return "grid-cols-5"
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h3 className="font-medium text-muted-foreground text-xs">{label}</h3>
      {children}
    </section>
  )
}

function OptionGroup({
  label,
  value,
  options,
  onChange
}: {
  label: string
  value: string
  options: Option[]
  onChange: (value: string) => void
}) {
  if (options.length === 0) return null

  return (
    <ToggleGroup
      value={[value]}
      onValueChange={(values: string[]) => {
        const next = values.find((item) => item !== value) ?? values.at(-1)
        if (next) onChange(next)
      }}
      variant="outline"
      spacing={2}
      className={cn("grid w-full gap-2", choiceGridClass(options.length))}
    >
      {options.map((option) => (
        <ToggleGroupItem
          key={option.value}
          value={option.value}
          aria-label={`${label}：${option.label}`}
          className="h-9 w-full px-2 font-normal text-muted-foreground text-xs aria-pressed:border-foreground/70 aria-pressed:bg-foreground/10 aria-pressed:text-foreground"
        >
          {option.label}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

function RatioGlyph({ ratio, large = false }: { ratio: string; large?: boolean }) {
  const [widthPart, heightPart] = ratio.split(":").map(Number)
  const width = widthPart > 0 ? widthPart : 1
  const height = heightPart > 0 ? heightPart : 1
  const maxSide = large ? 18 : 14
  const glyphWidth = width >= height ? maxSide : Math.max(5, Math.round((maxSide * width) / height))
  const glyphHeight = height >= width ? maxSide : Math.max(5, Math.round((maxSide * height) / width))
  const viewSize = large ? 22 : 18

  return (
    <svg
      width={viewSize}
      height={viewSize}
      viewBox={`0 0 ${viewSize} ${viewSize}`}
      aria-hidden="true"
      className="shrink-0"
    >
      <rect
        x={(viewSize - glyphWidth) / 2 + 0.5}
        y={(viewSize - glyphHeight) / 2 + 0.5}
        width={glyphWidth - 1}
        height={glyphHeight - 1}
        rx="1.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
      />
    </svg>
  )
}

function RatioGroup({
  value,
  options,
  onChange
}: {
  value: string
  options: string[]
  onChange: (value: string) => void
}) {
  if (options.length === 0) return null

  return (
    <ToggleGroup
      value={[value]}
      onValueChange={(values: string[]) => {
        const next = values.find((item) => item !== value) ?? values.at(-1)
        if (next) onChange(next)
      }}
      variant="outline"
      spacing={2}
      className="grid w-full grid-cols-5 gap-2"
    >
      {options.map((ratio) => (
        <ToggleGroupItem
          key={ratio}
          value={ratio}
          aria-label={`画面比例：${ratio}`}
          className="h-16 w-full flex-col gap-1 px-1 font-normal text-muted-foreground text-xs aria-pressed:border-foreground/70 aria-pressed:bg-foreground/10 aria-pressed:text-foreground"
        >
          <RatioGlyph ratio={ratio} large />
          {ratio}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

function ImageParamsPanel({
  model,
  params,
  modeConfig,
  onChangeParams,
  promptExtendId
}: {
  model: AiModelVO
  params: GenerationParams
  modeConfig: ImageModeConfig | undefined
  onChangeParams: (patch: Partial<GenerationParams>) => void
  promptExtendId: string
}) {
  const config = model.imageConfig
  const fixedSizes = config?.mode === "fixed" ? (config.sizes as (string | [number, number])[]) : []
  const dimensions = dimensionOptions(fixedSizes)
  const parsedCurrent = parseDimensions(params.fixedSize)
  const fallbackDimension = dimensions[0]
  const currentRatio = parsedCurrent
    ? calcRatio(parsedCurrent[0], parsedCurrent[1])
    : (params.aspectRatio ?? fallbackDimension?.ratio ?? "1:1")
  const currentResolution =
    params.fixedSize === "auto"
      ? "auto"
      : parsedCurrent
        ? resolutionLabel(parsedCurrent[0], parsedCurrent[1])
        : (params.resolution ?? fallbackDimension?.resolution ?? "1K")

  const ratioSizes =
    config?.mode === "ratio"
      ? ((config.sizes ?? {}) as Record<string, [number, number][]>)
      : undefined
  const ratioOptions = ratioSizes
    ? Object.keys(ratioSizes)
    : dimensions.length > 0
      ? Array.from(new Set(dimensions.map((option) => option.ratio)))
      : DEFAULT_RATIOS

  const sizePresetOptions = (modeConfig?.sizePresets ?? []).map((value) => ({ value, label: value }))
  const resolutionOptions = (() => {
    if (sizePresetOptions.length > 0) return sizePresetOptions
    if (config?.mode === "fixed") {
      const options = uniqueOptions(
        dimensions
          .filter((option) => option.ratio === currentRatio)
          .map((option) => ({ value: option.resolution, label: option.resolution }))
      )
      return fixedSizes.includes("auto")
        ? [{ value: "auto", label: "自动" }, ...options]
        : options
    }
    if (ratioSizes) {
      return uniqueOptions(
        (ratioSizes[currentRatio] ?? []).map(([width, height]) => {
          const resolution = resolutionLabel(width, height)
          return { value: resolution, label: resolution }
        })
      )
    }
    return ["1K", "2K", "4K"].map((value) => ({ value, label: value }))
  })()

  const selectedResolution =
    sizePresetOptions.length > 0
      ? (params.sizePreset ?? sizePresetOptions[0]?.value ?? "")
      : config?.mode === "ratio"
        ? parsedCurrent
          ? resolutionLabel(parsedCurrent[0], parsedCurrent[1])
          : (resolutionOptions[0]?.value ?? "")
        : currentResolution

  const qualityOptions = (modeConfig?.quality ?? []).map((value) => ({
    value,
    label: QUALITY_LABELS[value] ?? value
  }))
  const countOptions = config
    ? Array.from({ length: modeConfig?.maxImages ?? 1 }, (_, index) => ({
        value: String(index + 1),
        label: `${index + 1}张`
      }))
    : [1, 2, 4].map((value) => ({ value: String(value), label: `${value}张` }))

  const changeRatio = (ratio: string) => {
    if (config?.mode === "fixed") {
      const currentTier = currentResolution === "auto" ? undefined : currentResolution
      const candidates = dimensions.filter((option) => option.ratio === ratio)
      const next = candidates.find((option) => option.resolution === currentTier) ?? candidates[0]
      onChangeParams({ aspectRatio: ratio, fixedSize: next?.value })
      return
    }
    if (ratioSizes) {
      const first = ratioSizes[ratio]?.[0]
      onChangeParams({
        aspectRatio: ratio,
        fixedSize: first ? `${first[0]}x${first[1]}` : undefined
      })
      return
    }
    onChangeParams({ aspectRatio: ratio })
  }

  const changeResolution = (value: string) => {
    if (sizePresetOptions.length > 0) {
      onChangeParams({ sizePreset: value })
      return
    }
    if (config?.mode === "fixed") {
      if (value === "auto") {
        onChangeParams({ fixedSize: "auto" })
        return
      }
      const next = dimensions.find(
        (option) => option.ratio === currentRatio && option.resolution === value
      )
      if (next) onChangeParams({ fixedSize: next.value })
      return
    }
    if (ratioSizes) {
      const next = (ratioSizes[currentRatio] ?? []).find(
        ([width, height]) => resolutionLabel(width, height) === value
      )
      if (next) onChangeParams({ fixedSize: `${next[0]}x${next[1]}` })
      return
    }
    onChangeParams({ resolution: value })
  }

  return (
    <div className="flex flex-col gap-4">
      {qualityOptions.length > 0 ? (
        <Section label="画质">
          <OptionGroup
            label="画质"
            value={params.quality ?? qualityOptions[0].value}
            options={qualityOptions}
            onChange={(quality) => onChangeParams({ quality })}
          />
        </Section>
      ) : null}

      {resolutionOptions.length > 0 ? (
        <Section label={sizePresetOptions.length > 0 ? "规格" : "清晰度"}>
          <OptionGroup
            label={sizePresetOptions.length > 0 ? "规格" : "清晰度"}
            value={selectedResolution}
            options={resolutionOptions}
            onChange={changeResolution}
          />
        </Section>
      ) : null}

      <Section label="比例">
        <RatioGroup value={currentRatio} options={ratioOptions} onChange={changeRatio} />
      </Section>

      {countOptions.length > 1 ? (
        <Section label="生成数量">
          <OptionGroup
            label="生成数量"
            value={String(params.imageCount ?? 1)}
            options={countOptions}
            onChange={(imageCount) => onChangeParams({ imageCount: Number(imageCount) })}
          />
        </Section>
      ) : null}

      {modeConfig?.format?.length ? (
        <Section label="格式">
          <OptionGroup
            label="格式"
            value={params.format ?? modeConfig.format[0]}
            options={modeConfig.format.map((value) => ({ value, label: value.toUpperCase() }))}
            onChange={(format) => onChangeParams({ format })}
          />
        </Section>
      ) : null}

      {modeConfig?.background?.length ? (
        <Section label="背景">
          <OptionGroup
            label="背景"
            value={params.background ?? modeConfig.background[0]}
            options={modeConfig.background.map((value) => ({
              value,
              label: BACKGROUND_LABELS[value] ?? value
            }))}
            onChange={(background) => onChangeParams({ background })}
          />
        </Section>
      ) : null}

      {modeConfig?.contentModeration?.length ? (
        <Section label="内容审核">
          <OptionGroup
            label="内容审核"
            value={params.contentModeration ?? modeConfig.contentModeration[0]}
            options={modeConfig.contentModeration.map((value) => ({
              value,
              label: MODERATION_LABELS[value] ?? value
            }))}
            onChange={(contentModeration) => onChangeParams({ contentModeration })}
          />
        </Section>
      ) : null}

      {modeConfig?.seed ? (
        <Section label="Seed">
          <Input
            type="number"
            min={0}
            max={2147483647}
            value={params.seed === 0 ? "" : (params.seed ?? "")}
            onChange={(event) =>
              onChangeParams({ seed: event.target.value ? Number(event.target.value) : 0 })
            }
            placeholder="随机"
            className="h-9 text-xs"
          />
        </Section>
      ) : null}

      {modeConfig?.promptExtend ? (
        <div className="flex items-center justify-between rounded-lg border border-foreground/8 px-3 py-2">
          <Label htmlFor={promptExtendId} className="cursor-pointer text-xs">
            智能改写提示词
          </Label>
          <Switch
            id={promptExtendId}
            checked={params.promptExtend ?? false}
            onCheckedChange={(promptExtend) => onChangeParams({ promptExtend })}
          />
        </div>
      ) : null}
    </div>
  )
}

function VideoParamsPanel({
  model,
  params,
  onChangeParams
}: {
  model: AiModelVO
  params: GenerationParams
  onChangeParams: (patch: Partial<GenerationParams>) => void
}) {
  const config = model.videoConfig
  const ratios = config?.ratios?.length ? config.ratios : []
  const resolutions = config?.resolutions?.length ? config.resolutions : []
  const durations = config?.durations?.length
    ? config.durations.map((duration) => `${duration}s`)
    : Array.from({ length: 14 }, (_, index) => `${index + 2}s`)

  return (
    <div className="flex flex-col gap-4">
      {resolutions.length > 0 ? (
        <Section label="清晰度">
          <OptionGroup
            label="清晰度"
            value={params.resolution ?? resolutions[0]}
            options={resolutions.map((value) => ({ value, label: value.toUpperCase() }))}
            onChange={(resolution) => onChangeParams({ resolution })}
          />
        </Section>
      ) : null}
      {ratios.length > 0 ? (
        <Section label="比例">
          <RatioGroup
            value={params.aspectRatio ?? ratios[0]}
            options={ratios}
            onChange={(aspectRatio) => onChangeParams({ aspectRatio })}
          />
        </Section>
      ) : null}
      <Section label="时长">
        <OptionGroup
          label="时长"
          value={params.videoDuration ?? durations[0]}
          options={durations.map((value) => ({
            value,
            label: `${value.replace("s", "")}秒`
          }))}
          onChange={(videoDuration) => onChangeParams({ videoDuration })}
        />
      </Section>
    </div>
  )
}

function getSummary(
  model: AiModelVO,
  params: GenerationParams,
  modeConfig: ImageModeConfig | undefined
): { ratio: string; text: string } {
  const isVideo = model.capabilities?.includes("VIDEO_GEN")
  if (isVideo) {
    const configuredRatio = model.videoConfig?.ratios?.[0]
    const ratio = params.aspectRatio ?? configuredRatio ?? "9:16"
    const resolution = model.videoConfig?.resolutions?.length
      ? (params.resolution ?? model.videoConfig.resolutions[0])
      : undefined
    const duration = params.videoDuration ?? `${model.videoConfig?.durations?.[0] ?? 5}s`
    return {
      ratio,
      text: [model.videoConfig?.ratios?.length ? ratio : undefined, resolution?.toUpperCase(), `${duration.replace("s", "")}秒`]
        .filter(Boolean)
        .join(" · ")
    }
  }

  const parsed = parseDimensions(params.fixedSize)
  const ratio = parsed
    ? calcRatio(parsed[0], parsed[1])
    : (params.aspectRatio ??
      (model.imageConfig?.mode === "ratio"
        ? Object.keys((model.imageConfig.sizes ?? {}) as Record<string, unknown>)[0]
        : undefined) ??
      "1:1")
  const size =
    params.fixedSize === "auto"
      ? "自动尺寸"
      : params.sizePreset ??
        (parsed ? resolutionLabel(parsed[0], parsed[1]) : params.resolution ?? undefined)
  const quality = modeConfig?.quality?.length
    ? QUALITY_LABELS[params.quality ?? modeConfig.quality[0]] ??
      (params.quality ?? modeConfig.quality[0])
    : undefined

  return {
    ratio,
    text: [ratio, quality, size, `${params.imageCount ?? 1}张`].filter(Boolean).join(" · ")
  }
}

/** 渲染参考设计风格的模型参数摘要与上弹设置面板。 */
export function ModelParamsPopover({
  model,
  params,
  onChangeParams,
  isEditMode = false
}: ModelParamsPopoverProps) {
  const promptExtendId = useId()
  if (!model) return null

  const isVideo = model.capabilities?.includes("VIDEO_GEN")
  const modeConfig = model.imageConfig
    ? isEditMode
      ? model.imageConfig.edit
      : model.imageConfig.generate
    : undefined
  const summary = getSummary(model, params, modeConfig)

  return (
    <Popover>
      <PopoverTrigger
        render={
          <button
            type="button"
            className="flex h-8 max-w-80 shrink-0 items-center gap-1.5 rounded-lg border border-foreground/8 px-2.5 text-muted-foreground text-xs transition-colors hover:bg-foreground/6 hover:text-foreground"
          />
        }
      >
        <RatioGlyph ratio={summary.ratio} />
        <span className="truncate">{summary.text}</span>
        <ChevronDown className="size-3 shrink-0" />
      </PopoverTrigger>
      <PopoverContent
        side="top"
        align="start"
        sideOffset={8}
        className="max-h-[min(70vh,38rem)] w-[min(380px,calc(100vw-2rem))] overflow-y-auto rounded-2xl border border-foreground/10 p-3.5 shadow-2xl"
      >
        {isVideo ? (
          <VideoParamsPanel model={model} params={params} onChangeParams={onChangeParams} />
        ) : (
          <ImageParamsPanel
            model={model}
            params={params}
            modeConfig={modeConfig}
            onChangeParams={onChangeParams}
            promptExtendId={promptExtendId}
          />
        )}
      </PopoverContent>
    </Popover>
  )
}
