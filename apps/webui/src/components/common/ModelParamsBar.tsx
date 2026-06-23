/**
 * ModelParamsBar——根据模型配置动态渲染参数控件
 *
 * 配合 useGenerationParams 使用，自动根据 AiModelVO.imageConfig 决定显示哪些控件。
 * flex-wrap 超长自动换行。
 *
 * @example
 * const { params, onChangeParams } = useGenerationParams(currentModel)
 * <ModelParamsBar model={currentModel} params={params} onChangeParams={onChangeParams} />
 */
"use client"

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import type { AiModelVO, ImageConfig, ImageModeConfig } from "@/lib/api/rest/ai/ai-model"
import { calcRatio } from "@/lib/api/rest/ai/ai-model"
import type { GenerationParams } from "@/lib/hooks/use-generation-params"

interface ModelParamsBarProps {
  model: AiModelVO | undefined
  params: GenerationParams
  onChangeParams: (patch: Partial<GenerationParams>) => void
  /** 是否图像编辑模式（有参考图且模型支持 edit） */
  isEditMode?: boolean
}

/** 通用带标签 Select */
function P({
  label,
  value,
  options,
  onChange,
  labelMap,
  className = "h-8 w-full text-xs"
}: {
  label: string
  value: string
  options: string[]
  onChange: (v: string) => void
  labelMap?: Record<string, string>
  className?: string
}) {
  return (
    <Select value={value} onValueChange={(v) => v && onChange(v)}>
      <SelectTrigger className={className}>
        <span className="shrink-0 text-muted-foreground">{label}</span>
        <SelectValue>{labelMap?.[value] ?? value}</SelectValue>
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

/** 图像尺寸控件——fixed / ratio / sizePreset 三种模式 */
function SizeControl({
  cfg,
  modeConfig,
  params,
  onChange
}: {
  cfg: ImageConfig
  modeConfig: ImageModeConfig | undefined
  params: GenerationParams
  onChange: (patch: Partial<GenerationParams>) => void
}) {
  // sizePreset 优先
  if (modeConfig?.sizePresets?.length) {
    const presetEl = (
      <P
        key="preset"
        label="规格"
        value={params.sizePreset ?? modeConfig.sizePresets[0]}
        options={modeConfig.sizePresets}
        onChange={(v) => onChange({ sizePreset: v })}
        className="h-8 w-full text-xs"
      />
    )
    if (cfg.mode !== "ratio") return <>{presetEl}</>
    const ratios = Object.keys((cfg.sizes ?? {}) as Record<string, unknown>)
    return (
      <>
        <P
          label="比例"
          value={params.aspectRatio ?? ratios[0]}
          options={ratios}
          onChange={(v) => onChange({ aspectRatio: v })}
        />
        {presetEl}
      </>
    )
  }

  if (cfg.mode === "fixed") {
    const fixedSizes = cfg.sizes as [number, number][]
    return (
      <Select value={params.fixedSize ?? ""} onValueChange={(v) => v && onChange({ fixedSize: v })}>
        <SelectTrigger className="h-8 w-full text-xs">
          <span className="shrink-0 text-muted-foreground">尺寸</span>
          {params.fixedSize
            ? (() => {
                const [w, h] = params.fixedSize.split("x").map(Number)
                return (
                  <span className="truncate">
                    {calcRatio(w, h)} {w}×{h}
                  </span>
                )
              })()
            : null}
        </SelectTrigger>
        <SelectContent>
          {fixedSizes.map(([w, h]) => {
            const ratio = calcRatio(w, h)
            const ms = 14
            const rw = w >= h ? ms : Math.round((ms * w) / h)
            const rh = h >= w ? ms : Math.round((ms * h) / w)
            return (
              <SelectItem key={`${w}x${h}`} value={`${w}x${h}`}>
                <span className="flex items-center gap-1.5">
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 16 16"
                    aria-hidden="true"
                    className="shrink-0 text-muted-foreground"
                  >
                    <rect
                      x={(16 - rw) / 2 + 0.5}
                      y={(16 - rh) / 2 + 0.5}
                      width={rw - 1}
                      height={rh - 1}
                      rx="1"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="1.5"
                    />
                  </svg>
                  <span className="text-muted-foreground">{ratio}</span>
                  <span>
                    {w}×{h}
                  </span>
                </span>
              </SelectItem>
            )
          })}
        </SelectContent>
      </Select>
    )
  }

  // ratio 模式
  const ratioSizes = (cfg.sizes ?? {}) as Record<string, [number, number][]>
  const ratios = Object.keys(ratioSizes)
  const currentRatio = params.aspectRatio ?? ratios[0]
  const sizesForRatio = ratioSizes[currentRatio] ?? []
  const currentFixedSize = sizesForRatio.some(([w, h]) => `${w}x${h}` === params.fixedSize)
    ? (params.fixedSize ?? "")
    : sizesForRatio[0]
      ? `${sizesForRatio[0][0]}x${sizesForRatio[0][1]}`
      : ""

  return (
    <>
      <Select
        value={currentRatio}
        onValueChange={(v) => {
          if (!v) return
          const first = ratioSizes[v]?.[0]
          onChange({ aspectRatio: v, fixedSize: first ? `${first[0]}x${first[1]}` : undefined })
        }}
      >
        <SelectTrigger className="h-8 w-full text-xs">
          <span className="shrink-0 text-muted-foreground">比例</span>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {ratios.map((v) => (
            <SelectItem key={v} value={v}>
              {v}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {sizesForRatio.length > 0 && (
        <Select value={currentFixedSize} onValueChange={(v) => v && onChange({ fixedSize: v })}>
          <SelectTrigger className="h-8 w-full text-xs">
            <span className="shrink-0 text-muted-foreground">尺寸</span>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {sizesForRatio.map(([w, h]) => (
              <SelectItem key={`${w}x${h}`} value={`${w}x${h}`}>
                {w}×{h}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      )}
    </>
  )
}

export function ModelParamsBar({
  model,
  params,
  onChangeParams,
  isEditMode = false
}: ModelParamsBarProps) {
  if (!model) return null

  const cap = model.capabilities ?? ""
  const isVideo = cap.includes("VIDEO_GEN")
  const cfg = model.imageConfig
  const vcfg = model.videoConfig
  const modeConfig = cfg ? (isEditMode ? cfg.edit : cfg.generate) : undefined

  return (
    <div className="grid grid-cols-[repeat(auto-fill,minmax(110px,1fr))] gap-2">
      {/* 图像尺寸 */}
      {!isVideo && cfg && (
        <SizeControl cfg={cfg} modeConfig={modeConfig} params={params} onChange={onChangeParams} />
      )}

      {/* 无 imageConfig 降级：比例 + 数量 */}
      {!isVideo && !cfg && (
        <>
          <P
            label="比例"
            value={params.aspectRatio ?? "1:1"}
            options={["1:1", "9:16", "16:9", "4:3", "3:4"]}
            onChange={(v) => onChangeParams({ aspectRatio: v })}
          />
          <P
            label="张数"
            value={String(params.imageCount ?? 1)}
            options={["1", "2", "4"]}
            onChange={(v) => onChangeParams({ imageCount: Number(v) })}
            labelMap={{ "1": "1 张", "2": "2 张", "4": "4 张" }}
            className="h-8 w-full text-xs"
          />
        </>
      )}

      {/* 视频：清晰度 + 时长 */}
      {isVideo && (
        <>
          {vcfg?.resolutions && vcfg.resolutions.length > 0 && (
            <P
              label="清晰度"
              value={params.resolution ?? vcfg.resolutions[0]}
              options={vcfg.resolutions}
              onChange={(v) => onChangeParams({ resolution: v })}
              labelMap={{ "720p": "720P", "1080p": "1080P", "720P": "720P", "1080P": "1080P" }}
            />
          )}
          {(() => {
            const durationOpts =
              vcfg?.durations && vcfg.durations.length > 0
                ? vcfg.durations.map((d) => `${d}s`)
                : Array.from({ length: 14 }, (_, i) => `${i + 2}s`)
            const defaultDuration = durationOpts[0] ?? "5s"
            return (
              <P
                label="时长"
                value={
                  durationOpts.includes(params.videoDuration ?? "")
                    ? (params.videoDuration ?? defaultDuration)
                    : defaultDuration
                }
                options={durationOpts}
                onChange={(v) => onChangeParams({ videoDuration: v })}
                labelMap={Object.fromEntries(
                  durationOpts.map((o) => [o, `${o.replace("s", "")} 秒`])
                )}
                className="h-8 w-full text-xs"
              />
            )
          })()}
        </>
      )}

      {/* 张数 */}
      {!isVideo && (modeConfig?.maxImages ?? 1) > 1 && (
        <P
          label="张数"
          value={String(params.imageCount ?? 1)}
          options={Array.from({ length: modeConfig?.maxImages ?? 1 }, (_, i) => String(i + 1))}
          onChange={(v) => onChangeParams({ imageCount: Number(v) })}
          labelMap={Object.fromEntries(
            Array.from({ length: modeConfig?.maxImages ?? 1 }, (_, i) => [
              String(i + 1),
              `${i + 1} 张`
            ])
          )}
          className="h-8 w-full text-xs"
        />
      )}

      {/* 画质 */}
      {modeConfig?.quality?.length && (
        <P
          label="画质"
          value={params.quality ?? modeConfig.quality[0]}
          options={modeConfig.quality}
          onChange={(v) => onChangeParams({ quality: v })}
          labelMap={{ auto: "自动", low: "低", medium: "中", high: "高" }}
        />
      )}

      {/* 格式 */}
      {modeConfig?.format?.length && (
        <P
          label="格式"
          value={params.format ?? modeConfig.format[0]}
          options={modeConfig.format}
          onChange={(v) => onChangeParams({ format: v })}
          labelMap={Object.fromEntries(modeConfig.format.map((f) => [f, f.toUpperCase()]))}
          className="h-8 w-full text-xs"
        />
      )}

      {/* 背景 */}
      {modeConfig?.background?.length && (
        <P
          label="背景"
          value={params.background ?? modeConfig.background[0]}
          options={modeConfig.background}
          onChange={(v) => onChangeParams({ background: v })}
          labelMap={{ auto: "自动", transparent: "透明", opaque: "不透明" }}
          className="h-8 w-full text-xs"
        />
      )}

      {/* 内容审核 */}
      {modeConfig?.contentModeration?.length && (
        <P
          label="审核"
          value={params.contentModeration ?? modeConfig.contentModeration[0]}
          options={modeConfig.contentModeration}
          onChange={(v) => onChangeParams({ contentModeration: v })}
          labelMap={{ auto: "自动", low: "宽松" }}
        />
      )}

      {/* Seed */}
      {!isVideo && modeConfig?.seed && (
        <Input
          type="number"
          min={0}
          max={2147483647}
          value={params.seed === 0 ? "" : (params.seed ?? "")}
          onChange={(e) => onChangeParams({ seed: e.target.value ? Number(e.target.value) : 0 })}
          placeholder="Seed"
          className="h-8 w-full text-xs"
        />
      )}

      {/* 智能改写 */}
      {!isVideo && modeConfig?.promptExtend && (
        <div className="flex items-center gap-1.5">
          <Switch
            id="prompt-extend"
            checked={params.promptExtend ?? false}
            onCheckedChange={(v) => onChangeParams({ promptExtend: v })}
            className="h-4 w-7"
          />
          <Label htmlFor="prompt-extend" className="cursor-pointer text-muted-foreground text-xs">
            智能改写
          </Label>
        </div>
      )}
    </div>
  )
}
