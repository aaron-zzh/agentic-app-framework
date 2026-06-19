"use client"

/**
 * Qwen-OCR 演示页——通过 URL 调用 OCR 识别接口
 * 路由：/dev/examples/ocr
 */

import { useCallback, useEffect, useRef, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { type OcrRecognizeResult, ocrApi } from "@/lib/api/rest/ai/ocr"

const TASK_OPTIONS = [
  { value: "TEXT_RECOGNITION", label: "通用文字识别" },
  { value: "MULTI_LAN", label: "多语言识别" },
  { value: "KEY_INFORMATION_EXTRACTION", label: "信息抽取" },
  { value: "TABLE_PARSING", label: "表格解析" },
  { value: "DOCUMENT_PARSING", label: "文档解析" },
  { value: "FORMULA_RECOGNITION", label: "公式识别" },
  { value: "ADVANCED_RECOGNITION", label: "高精识别（含坐标）" }
]

/** 效果演示预设图像 */
const PRESETS = [
  {
    label: "默认测试",
    url: "https://help-static-aliyun-doc.aliyuncs.com/assets/img/zh-CN/5727078571/p1006591.png",
    task: "ADVANCED_RECOGNITION"
  },
  {
    label: "多语言识别",
    url: "https://help-static-aliyun-doc.aliyuncs.com/assets/img/zh-CN/5727078571/p1008252.png",
    task: "MULTI_LAN"
  },
  {
    label: "倾斜图像识别",
    url: "https://help-static-aliyun-doc.aliyuncs.com/assets/img/zh-CN/5727078571/p1006562.png",
    task: "TEXT_RECOGNITION"
  },
  {
    label: "火车票信息抽取",
    url: "https://img.alicdn.com/imgextra/i2/O1CN01ktT8451iQutqReELT_!!6000000004408-0-tps-689-487.jpg",
    task: "KEY_INFORMATION_EXTRACTION"
  }
]

export default function OcrExamplePage() {
  const [imageUrl, setImageUrl] = useState("")
  const [task, setTask] = useState("TEXT_RECOGNITION")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [result, setResult] = useState<OcrRecognizeResult | null>(null)
  const [previewUrl, setPreviewUrl] = useState("")
  const [imageSize, setImageSize] = useState<{ width: number; height: number } | null>(null)
  const [mousePos, setMousePos] = useState<{
    cx: number
    cy: number
    ix: number
    iy: number
  } | null>(null)
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const imgRef = useRef<HTMLImageElement>(null)

  /** 解析 ocrResult 并在 canvas 上绘制检测框（坐标基于原图尺寸） */
  const drawBoxes = useCallback((ocrResultJson: string | null) => {
    const canvas = canvasRef.current
    const img = imgRef.current
    if (!canvas || !img) return
    const ctx = canvas.getContext("2d")
    if (!ctx) return

    // canvas 尺寸 = img 容器渲染尺寸
    canvas.width = img.clientWidth
    canvas.height = img.clientHeight
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    if (!ocrResultJson) return

    try {
      const parsed = JSON.parse(ocrResultJson)
      let items: Array<{ rotate_rect?: number[]; location?: number[]; box?: number[][] }> = []
      // 优先用 words_info（location 是原图坐标，精确）
      if (Array.isArray(parsed?.words_info)) {
        items = parsed.words_info
      } else if (Array.isArray(parsed)) {
        // 后端精简格式：[{text, box}] 或高精识别原始数组
        items = parsed
      } else if (parsed?.processed_text) {
        const match = parsed.processed_text.match(/```json\s*([\s\S]*?)```/)
        if (match) items = JSON.parse(match[1])
      }
      if (items.length === 0) return

      // 坐标基于原图尺寸，按 object-contain 映射到容器
      const scale = Math.min(
        img.clientWidth / img.naturalWidth,
        img.clientHeight / img.naturalHeight
      )
      const offsetX = (img.clientWidth - img.naturalWidth * scale) / 2
      const offsetY = (img.clientHeight - img.naturalHeight * scale) / 2

      ctx.strokeStyle = "#ef4444"
      ctx.lineWidth = 1.5

      for (const item of items) {
        let corners: [number, number][]
        if (item.box) {
          corners = (item.box as number[][]).map(([x, y]) => [x, y])
        } else if (item.location) {
          const [x1, y1, x2, y2, x3, y3, x4, y4] = item.location
          corners = [
            [x1, y1],
            [x2, y2],
            [x3, y3],
            [x4, y4]
          ]
        } else continue

        ctx.beginPath()
        corners.forEach(([x, y], i) => {
          const sx = x * scale + offsetX
          const sy = y * scale + offsetY
          i === 0 ? ctx.moveTo(sx, sy) : ctx.lineTo(sx, sy)
        })
        ctx.closePath()
        ctx.stroke()
      }
    } catch {
      /* 格式不符跳过 */
    }
  }, [])

  useEffect(() => {
    if (result) drawBoxes(result.ocrResult)
  }, [result, drawBoxes])

  function applyPreset(preset: (typeof PRESETS)[number]) {
    setImageUrl(preset.url)
    setTask(preset.task)
    setPreviewUrl(preset.url)
    setImageSize(null)
    setResult(null)
    setError("")
  }

  async function handleRecognize() {
    if (!imageUrl.trim()) return
    setLoading(true)
    setError("")
    setResult(null)

    try {
      const data = await ocrApi.recognize({
        imageUrl: imageUrl.trim(),
        task,
        ...(imageSize && { imageWidth: imageSize.width, imageHeight: imageSize.height })
      })
      setResult(data)
      setPreviewUrl(imageUrl.trim())
    } catch (e) {
      setError(e instanceof Error ? e.message : "请求失败")
    } finally {
      setLoading(false)
    }
  }

  return (
    <PageContainer maxWidth="lg">
      <div className="mb-6 space-y-2">
        <TypographyH1>Qwen-OCR 文字识别</TypographyH1>
        <TypographyMuted>
          支持通用识别、多语言、信息抽取、表格/文档解析等任务，需配置 DASHSCOPE_API_KEY
        </TypographyMuted>
        <TypographyMuted className="text-xs">
          图像限制：BMP / JPEG / PNG / TIFF / WEBP / HEIC 格式，URL 方式单张 ≤ 20MB，宽高比 ≤
          200:1，分辨率建议 ≤ 8K。文字过小或分辨率低时可能产生幻觉。
        </TypographyMuted>
      </div>

      {/* 预设示例 */}
      <div className="mb-4 flex flex-wrap gap-2">
        {PRESETS.map((p) => (
          <Button key={p.label} variant="outline" size="sm" onClick={() => applyPreset(p)}>
            {p.label}
          </Button>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* 左：输入区 */}
        <Card>
          <CardHeader>
            <CardTitle>识别配置</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-1">
              <Label>图像 URL</Label>
              <Input
                placeholder="https://example.com/image.jpg"
                value={imageUrl}
                onChange={(e) => {
                  setImageUrl(e.target.value)
                  setPreviewUrl(e.target.value)
                  setImageSize(null)
                }}
                onKeyDown={(e) => e.key === "Enter" && handleRecognize()}
              />
            </div>

            <div className="space-y-1">
              <Label>识别任务</Label>
              <Select
                value={task}
                onValueChange={(v) => setTask(v ?? "TEXT_RECOGNITION")}
                items={TASK_OPTIONS}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TASK_OPTIONS.map((t) => (
                    <SelectItem key={t.value} value={t.value} label={t.label}>
                      {t.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <Button
              className="w-full"
              onClick={handleRecognize}
              disabled={loading || !imageUrl.trim()}
            >
              {loading ? "识别中..." : "开始识别"}
            </Button>

            {error && <p className="text-destructive text-sm">{error}</p>}

            {/* 图像预览 */}
            {previewUrl && (
              <div className="space-y-1">
                <Label>
                  图像预览
                  {imageSize && (
                    <span className="ml-2 font-normal text-muted-foreground text-xs">
                      {imageSize.width} × {imageSize.height}
                    </span>
                  )}
                </Label>
                {/* 预览图 + canvas 叠加检测框 */}
                <div
                  role="img"
                  aria-label="待识别图像预览"
                  className="relative max-h-64 w-full overflow-hidden rounded-md border"
                  onMouseMove={(e) => {
                    if (!imageSize) return
                    const img = imgRef.current
                    if (!img) return
                    const rect = e.currentTarget.getBoundingClientRect()
                    const cx = e.clientX - rect.left
                    const cy = e.clientY - rect.top
                    const scale = Math.min(
                      img.clientWidth / imageSize.width,
                      img.clientHeight / imageSize.height
                    )
                    const offsetX = (img.clientWidth - imageSize.width * scale) / 2
                    const offsetY = (img.clientHeight - imageSize.height * scale) / 2
                    const ix = Math.round((cx - offsetX) / scale)
                    const iy = Math.round((cy - offsetY) / scale)
                    setMousePos({ cx: Math.round(cx), cy: Math.round(cy), ix, iy })
                  }}
                  onMouseLeave={() => setMousePos(null)}
                >
                  {/* biome-ignore lint/performance/noImgElement: 动态外部 URL */}
                  <img
                    ref={imgRef}
                    src={previewUrl}
                    alt="待识别图像"
                    className="h-full w-full object-contain"
                    onLoad={(e) => {
                      const img = e.currentTarget
                      setImageSize({ width: img.naturalWidth, height: img.naturalHeight })
                      if (result) drawBoxes(result.ocrResult)
                    }}
                    onError={(e) => {
                      ;(e.currentTarget as HTMLImageElement).style.display = "none"
                      setImageSize(null)
                    }}
                  />
                  <canvas ref={canvasRef} className="pointer-events-none absolute inset-0" />
                  {mousePos && (
                    <div className="pointer-events-none absolute right-1 bottom-1 rounded bg-black/70 px-1.5 py-0.5 font-mono text-white text-xs">
                      容器({mousePos.cx},{mousePos.cy}) 图({mousePos.ix},{mousePos.iy})
                    </div>
                  )}
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        {/* 右：结果区 */}
        <Card>
          <CardHeader>
            <CardTitle>
              识别结果
              {result && (
                <span className="ml-2 font-normal text-muted-foreground text-xs">
                  {result.inputTokens + result.outputTokens} tokens
                </span>
              )}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {!result && !loading && (
              <p className="text-muted-foreground text-sm">识别结果将在此显示</p>
            )}

            {loading && (
              <div className="flex items-center gap-2 text-muted-foreground text-sm">
                <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
                识别中...
              </div>
            )}

            {result && (
              <div className="space-y-4">
                {/* 文本内容 */}
                {result.text && (
                  <div className="space-y-1">
                    <Label>文本内容</Label>
                    <pre className="max-h-64 overflow-auto whitespace-pre-wrap rounded-md bg-muted p-3 text-xs">
                      {result.text}
                    </pre>
                  </div>
                )}

                {/* 原始数据 */}
                {result.ocrResult && (
                  <div className="space-y-1">
                    <Label>原始数据</Label>
                    <pre className="max-h-40 overflow-auto whitespace-pre-wrap rounded-md bg-muted p-3 text-xs">
                      {result.ocrResult}
                    </pre>
                  </div>
                )}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </PageContainer>
  )
}
