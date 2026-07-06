/**
 * 工具-二维码生成 + 贴图
 * 本地生成二维码（自定义尺寸/颜色/风格）并下载；生成后可选把二维码贴到用户上传的底图指定位置合成下载。
 * 全程本地 Canvas 处理，不上传服务器。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { Download, Maximize2, QrCode as QrCodeIcon, Upload } from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { LottieIcon } from "@/components/animate"
import { GlassCard, GlassCardBody } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger } from "@/components/ui/select"
import { Slider } from "@/components/ui/slider"
import { Textarea } from "@/components/ui/textarea"
import { saveBlob } from "@/lib/utils/download-file"
import {
  clampLayerRect,
  compositeCanvasToBlob,
  compositeQrOntoImage,
  type QrLayerRect
} from "@/lib/utils/image-composite"
import {
  QR_CODE_MAX_TEXT_LENGTH,
  type QrCodeErrorLevel,
  type QrCodeStyle,
  qrCodeCanvasToBlob,
  renderQrCodeToCanvas,
  renderQrCodeToSvg
} from "@/lib/utils/qrcode-render"

const STYLE_OPTIONS: { value: QrCodeStyle; label: string }[] = [
  { value: "square", label: "经典方块" },
  { value: "dot", label: "圆点" },
  { value: "rounded", label: "圆角" }
]

const ERROR_LEVEL_OPTIONS: { value: QrCodeErrorLevel; label: string }[] = [
  { value: "L", label: "低（约 7%）" },
  { value: "M", label: "中（约 15%）" },
  { value: "Q", label: "较高（约 25%）" },
  { value: "H", label: "高（约 30%）" }
]

const SIZE_OPTIONS = [256, 512, 1024]

/** 贴图预览容器展示尺寸上限，超过按比例缩小显示（合成时仍按底图原始像素） */
const PASTE_PREVIEW_MAX_WIDTH = 360

/** 离屏二维码 Canvas 固定边长，贴图时按此基准缩放到目标大小 */
const OFFSCREEN_QR_SIZE = 512

export default function QrCodeToolPage() {
  const [text, setText] = useState("")
  const [size, setSize] = useState(512)
  const [style, setStyle] = useState<QrCodeStyle>("square")
  const [errorLevel, setErrorLevel] = useState<QrCodeErrorLevel>("M")
  const [foregroundColor, setForegroundColor] = useState("#000000")
  const [backgroundColor, setBackgroundColor] = useState("#ffffff")
  const [error, setError] = useState<string | null>(null)

  const previewCanvasRef = useRef<HTMLCanvasElement>(null)
  // 离屏 canvas：贴图区直接复用，避免用户在贴图区重复输入内容
  const offscreenCanvasRef = useRef<HTMLCanvasElement | null>(null)
  if (!offscreenCanvasRef.current && typeof document !== "undefined") {
    offscreenCanvasRef.current = document.createElement("canvas")
  }

  const render = useCallback(() => {
    const canvas = previewCanvasRef.current
    if (!canvas) return
    if (!text.trim()) {
      setError(null)
      canvas.width = size
      canvas.height = size
      const ctx = canvas.getContext("2d")
      ctx?.clearRect(0, 0, size, size)
      return
    }
    try {
      renderQrCodeToCanvas(canvas, {
        text,
        size,
        style,
        errorCorrectionLevel: errorLevel,
        foregroundColor,
        backgroundColor
      })
      if (offscreenCanvasRef.current) {
        renderQrCodeToCanvas(offscreenCanvasRef.current, {
          text,
          size: OFFSCREEN_QR_SIZE,
          style,
          // 贴图场景对容错要求更高，统一用高容错级别保证遮挡后仍可扫描
          errorCorrectionLevel: "H",
          foregroundColor,
          backgroundColor
        })
      }
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : "生成失败")
    }
  }, [text, size, style, errorLevel, foregroundColor, backgroundColor])

  useEffect(() => {
    render()
  }, [render])

  async function handleDownloadPng() {
    const canvas = previewCanvasRef.current
    if (!canvas || !text.trim()) return
    const blob = await qrCodeCanvasToBlob(canvas)
    saveBlob(blob, `qrcode-${Date.now()}.png`)
  }

  function handleDownloadSvg() {
    if (!text.trim()) return
    try {
      const svg = renderQrCodeToSvg({
        text,
        size,
        style,
        errorCorrectionLevel: errorLevel,
        foregroundColor,
        backgroundColor
      })
      const blob = new Blob([svg], { type: "image/svg+xml" })
      saveBlob(blob, `qrcode-${Date.now()}.svg`)
    } catch (e) {
      setError(e instanceof Error ? e.message : "生成失败")
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <header className="space-y-2">
        <h1 className="font-semibold text-xl">二维码工具</h1>
        <p className="text-muted-foreground text-sm">
          本地生成二维码并下载，生成后可选贴到指定图片位置合成下载。内容不会上传服务器。
        </p>
      </header>

      {/* 二维码生成 + 贴图，两栏同屏展示 */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* 左栏：参数配置 + 预览下载 */}
        <GlassCard>
          <GlassCardBody className="space-y-5">
            <div className="space-y-1.5">
              <label className="text-muted-foreground text-sm" htmlFor="qr-text">
                二维码内容
              </label>
              <Textarea
                id="qr-text"
                placeholder="输入网址或文本"
                value={text}
                maxLength={QR_CODE_MAX_TEXT_LENGTH}
                onChange={(e) => setText(e.target.value)}
                rows={4}
              />
              <p className="text-right text-muted-foreground text-xs">
                {text.length}/{QR_CODE_MAX_TEXT_LENGTH}
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <span className="text-muted-foreground text-sm">尺寸</span>
                <Select value={String(size)} onValueChange={(v) => v && setSize(Number(v))}>
                  <SelectTrigger className="w-full">
                    <span>
                      {size}×{size}px
                    </span>
                  </SelectTrigger>
                  <SelectContent>
                    {SIZE_OPTIONS.map((s) => (
                      <SelectItem key={s} value={String(s)}>
                        {s}×{s}px
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <span className="text-muted-foreground text-sm">风格</span>
                <Select value={style} onValueChange={(v) => v && setStyle(v as QrCodeStyle)}>
                  <SelectTrigger className="w-full">
                    <span>{STYLE_OPTIONS.find((s) => s.value === style)?.label}</span>
                  </SelectTrigger>
                  <SelectContent>
                    {STYLE_OPTIONS.map((s) => (
                      <SelectItem key={s.value} value={s.value}>
                        {s.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <span className="text-muted-foreground text-sm">容错级别</span>
                <Select
                  value={errorLevel}
                  onValueChange={(v) => v && setErrorLevel(v as QrCodeErrorLevel)}
                >
                  <SelectTrigger className="w-full">
                    <span>{ERROR_LEVEL_OPTIONS.find((o) => o.value === errorLevel)?.label}</span>
                  </SelectTrigger>
                  <SelectContent>
                    {ERROR_LEVEL_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <span className="text-muted-foreground text-sm">颜色</span>
                <div className="flex items-center gap-2">
                  <input
                    type="color"
                    value={foregroundColor}
                    onChange={(e) => setForegroundColor(e.target.value)}
                    className="h-9 w-9 cursor-pointer rounded border"
                    aria-label="前景色"
                    title="前景色"
                  />
                  <input
                    type="color"
                    value={backgroundColor}
                    onChange={(e) => setBackgroundColor(e.target.value)}
                    className="h-9 w-9 cursor-pointer rounded border"
                    aria-label="背景色"
                    title="背景色"
                  />
                </div>
              </div>
            </div>

            {error && <p className="text-destructive text-sm">{error}</p>}

            {/* 预览 + 下载（嵌入同一卡片下方） */}
            <div className="flex flex-col items-center gap-4 border-t pt-5">
              <div className="flex aspect-square w-full max-w-64 items-center justify-center rounded-lg border bg-white p-3">
                {text.trim() ? (
                  <canvas ref={previewCanvasRef} className="max-h-full max-w-full" />
                ) : (
                  <div className="flex flex-col items-center gap-2 text-muted-foreground">
                    <QrCodeIcon className="size-8" />
                    <p className="text-xs">输入内容后生成预览</p>
                  </div>
                )}
              </div>
              {text.trim() && (
                <p className="text-muted-foreground text-xs">
                  导出尺寸 {size}×{size}px（预览框已按比例缩放展示）
                </p>
              )}
              <div className="flex w-full gap-2">
                <Button className="flex-1" disabled={!text.trim()} onClick={handleDownloadPng}>
                  <Download className="size-4" />
                  下载 PNG
                </Button>
                <Button
                  className="flex-1"
                  variant="outline"
                  disabled={!text.trim()}
                  onClick={handleDownloadSvg}
                >
                  <Download className="size-4" />
                  下载 SVG
                </Button>
              </div>
            </div>
          </GlassCardBody>
        </GlassCard>

        {/* 右栏：贴到图片（可选） */}
        <PasteQrSection qrText={text} offscreenCanvasRef={offscreenCanvasRef} />
      </div>
    </div>
  )
}

// ─── 贴到图片（可选） ────────────────────────────────────────────────────────

function PasteQrSection({
  qrText,
  offscreenCanvasRef
}: {
  qrText: string
  offscreenCanvasRef: React.RefObject<HTMLCanvasElement | null>
}) {
  const [baseImageUrl, setBaseImageUrl] = useState<string | null>(null)
  const [baseImageEl, setBaseImageEl] = useState<HTMLImageElement | null>(null)
  const [viewport, setViewport] = useState({ width: 0, height: 0 })
  const [layer, setLayer] = useState<QrLayerRect>({ x: 0, y: 0, size: 100 })
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const dragStateRef = useRef<{ startX: number; startY: number; layer: QrLayerRect } | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const hasQr = qrText.trim().length > 0

  function handleUploadBase(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const displayWidth = Math.min(PASTE_PREVIEW_MAX_WIDTH, img.naturalWidth)
      const displayHeight = (img.naturalHeight / img.naturalWidth) * displayWidth
      setViewport({ width: displayWidth, height: displayHeight })
      // 默认贴在右下角，边长取容器宽度的 20%
      const defaultSize = displayWidth * 0.2
      setLayer({
        x: displayWidth - defaultSize - 12,
        y: displayHeight - defaultSize - 12,
        size: defaultSize
      })
      setBaseImageEl(img)
    }
    img.src = url
    setBaseImageUrl(url)
  }

  function handleDragStart(e: React.PointerEvent) {
    e.preventDefault()
    dragStateRef.current = { startX: e.clientX, startY: e.clientY, layer }
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  }

  function handleDragMove(e: React.PointerEvent) {
    const state = dragStateRef.current
    if (!state) return
    const dx = e.clientX - state.startX
    const dy = e.clientY - state.startY
    setLayer(
      clampLayerRect(
        { ...state.layer, x: state.layer.x + dx, y: state.layer.y + dy },
        viewport,
        24,
        Math.min(viewport.width, viewport.height)
      )
    )
  }

  function handleDragEnd() {
    dragStateRef.current = null
  }

  function handleResizeStart(e: React.PointerEvent) {
    e.preventDefault()
    e.stopPropagation()
    dragStateRef.current = { startX: e.clientX, startY: e.clientY, layer }
    ;(e.target as HTMLElement).setPointerCapture(e.pointerId)
  }

  function handleResizeMove(e: React.PointerEvent) {
    e.stopPropagation()
    const state = dragStateRef.current
    if (!state) return
    // 缩放锁定正方形：取对角线位移中较大的分量控制边长
    const dx = e.clientX - state.startX
    const dy = e.clientY - state.startY
    const delta = Math.max(dx, dy)
    setLayer(
      clampLayerRect(
        { ...state.layer, size: state.layer.size + delta },
        viewport,
        24,
        Math.min(viewport.width, viewport.height)
      )
    )
  }

  function handleSliderChange(v: number | readonly number[]) {
    const next = Array.isArray(v) ? v[0] : v
    if (next === undefined) return
    setLayer((prev) =>
      clampLayerRect(
        { ...prev, size: next },
        viewport,
        24,
        Math.min(viewport.width, viewport.height)
      )
    )
  }

  async function handleDownload() {
    const qrCanvas = offscreenCanvasRef.current
    if (!baseImageEl || !hasQr || !qrCanvas) return
    setDownloading(true)
    try {
      const canvas = compositeQrOntoImage(baseImageEl, qrCanvas, layer, viewport)
      const blob = await compositeCanvasToBlob(canvas)
      saveBlob(blob, `qrcode-poster-${Date.now()}.png`)
    } catch (e) {
      setError(e instanceof Error ? e.message : "合成失败")
    } finally {
      setDownloading(false)
    }
  }

  return (
    <GlassCard>
      <GlassCardBody className="space-y-4">
        <div className="flex items-center justify-between gap-3">
          <div className="space-y-1">
            <h2 className="font-medium text-base">贴到图片（可选）</h2>
            <p className="text-muted-foreground text-xs">
              {hasQr ? "上传底图，拖拽定位并调整大小后合成下载" : "请先在上方生成二维码"}
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="icon"
            disabled={!hasQr}
            onClick={() => fileInputRef.current?.click()}
            aria-label={baseImageUrl ? "更换底图" : "上传底图"}
            title={baseImageUrl ? "更换底图（本地处理，不上传）" : "上传底图（本地处理，不上传）"}
          >
            <Upload className="size-4" />
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            className="hidden"
            disabled={!hasQr}
            onChange={handleUploadBase}
          />
        </div>

        <div className="space-y-5">
          {/* 拖拽预览 */}
          <div className="flex items-center justify-center">
            {baseImageUrl ? (
              <div
                className="relative select-none overflow-hidden rounded-lg border"
                style={{ width: viewport.width, height: viewport.height }}
              >
                {/* biome-ignore lint/performance/noImgElement: 本地预览图，尺寸随上传图片动态变化 */}
                <img
                  src={baseImageUrl}
                  alt="底图预览"
                  draggable={false}
                  className="pointer-events-none block h-full w-full object-contain"
                />
                {hasQr && (
                  <div
                    className="absolute cursor-move touch-none border-2 border-primary bg-white/80"
                    style={{
                      left: layer.x,
                      top: layer.y,
                      width: layer.size,
                      height: layer.size
                    }}
                    onPointerDown={handleDragStart}
                    onPointerMove={handleDragMove}
                    onPointerUp={handleDragEnd}
                  >
                    <canvas
                      ref={(node) => {
                        if (node && offscreenCanvasRef.current) {
                          node.width = layer.size
                          node.height = layer.size
                          const ctx = node.getContext("2d")
                          ctx?.drawImage(offscreenCanvasRef.current, 0, 0, layer.size, layer.size)
                        }
                      }}
                      className="pointer-events-none h-full w-full"
                    />
                    <button
                      type="button"
                      className="absolute right-0 bottom-0 flex size-4 translate-x-1/3 translate-y-1/3 cursor-nwse-resize touch-none items-center justify-center rounded-full border-2 border-white bg-primary text-primary-foreground shadow-lg hover:bg-blue-500"
                      onPointerDown={handleResizeStart}
                      onPointerMove={handleResizeMove}
                      onPointerUp={handleDragEnd}
                      aria-label="拖拽缩放二维码"
                    >
                      <Maximize2 className="size-2.5 rotate-90" />
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex flex-col items-center gap-2 py-8 text-muted-foreground">
                <LottieIcon name="cat" width={140} height={140} loop />
                <p className="text-sm">
                  {hasQr ? "还没有底图，点击右上角上传吧" : "生成二维码后可上传底图"}
                </p>
              </div>
            )}
          </div>

          <div className="space-y-1.5">
            <span className="text-muted-foreground text-sm">二维码大小</span>
            <Slider
              value={[layer.size]}
              min={24}
              max={Math.max(24, Math.min(viewport.width, viewport.height))}
              step={1}
              onValueChange={handleSliderChange}
              disabled={!baseImageEl}
            />
          </div>

          {error && <p className="text-destructive text-sm">{error}</p>}

          <Button
            className="w-full"
            disabled={!baseImageEl || !hasQr || downloading}
            onClick={handleDownload}
          >
            <Download className="size-4" />
            {downloading ? "生成中..." : "合成并下载"}
          </Button>
        </div>
      </GlassCardBody>
    </GlassCard>
  )
}
