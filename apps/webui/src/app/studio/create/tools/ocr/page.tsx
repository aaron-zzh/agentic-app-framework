/**
 * 工具-OCR 图片文字提取
 * @author AaronZZH & Kiro
 */

"use client"

import { Copy, FileText, Loader2, Upload } from "lucide-react"
import { useRef, useState } from "react"
import { GlassCard, GlassCardBody, GlowButton } from "@/components/studio"
import { ocrApi } from "@/lib/api/rest/ai/ocr"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { notify } from "@/lib/notification"

const TASK_OPTIONS = [
  { value: "TEXT_RECOGNITION", label: "通用文字识别" },
  { value: "MULTI_LAN", label: "多语言识别" },
  { value: "KEY_INFORMATION_EXTRACTION", label: "信息抽取" },
  { value: "TABLE_PARSING", label: "表格解析" },
  { value: "DOCUMENT_PARSING", label: "文档解析" },
  { value: "FORMULA_RECOGNITION", label: "公式识别" }
]

export default function OcrToolPage() {
  const [previewSrc, setPreviewSrc] = useState<string | null>(null)
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [task, setTask] = useState("TEXT_RECOGNITION")
  const [ocrResult, setOcrResult] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { upload, uploading } = useFileUpload()

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setPreviewSrc(URL.createObjectURL(file))
    setOcrResult(null)
    try {
      const uploaded = await upload(file)
      setImageUrl(uploaded.url)
    } catch {
      notify.error("图片上传失败")
    }
  }

  const handleRecognize = async () => {
    if (!imageUrl) return
    setIsLoading(true)
    try {
      const res = await ocrApi.recognize({ imageUrl, task })
      setOcrResult(res.text || res.ocrResult || "未识别到文字")
    } catch {
      notify.error("OCR 识别失败，请检查图片格式或网络")
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header className="flex items-center gap-2">
        <FileText className="size-5 text-cyan-400" />
        <h1 className="font-semibold text-xl">图片文字提取</h1>
      </header>

      <GlassCard glow="cyan">
        <GlassCardBody>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {/* 左：上传 + 任务选择 */}
            <div className="space-y-4">
              <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={handleFile}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploading || isLoading}
                className="flex min-h-[240px] w-full flex-col items-center justify-center gap-3 rounded-xl border-2 border-foreground/[0.12] border-dashed p-8 transition-colors hover:border-foreground/[0.24]"
              >
                {uploading ? (
                  <>
                    <Loader2 className="size-8 animate-spin text-cyan-400" />
                    <p className="text-muted-foreground text-sm">上传中...</p>
                  </>
                ) : previewSrc ? (
                  // biome-ignore lint/performance/noImgElement: blob URL 预览
                  <img src={previewSrc} alt="预览" className="max-h-56 rounded-lg object-contain" />
                ) : (
                  <>
                    <Upload className="size-8 text-muted-foreground/60" />
                    <p className="text-muted-foreground text-sm">点击上传图片</p>
                    <p className="text-muted-foreground text-xs">支持 JPG / PNG / WEBP，≤ 20MB</p>
                  </>
                )}
              </button>

              {imageUrl && (
                <div className="flex items-center gap-3">
                  <select
                    value={task}
                    onChange={(e) => setTask(e.target.value)}
                    className="flex-1 rounded-lg border bg-background px-3 py-2 text-sm"
                  >
                    {TASK_OPTIONS.map((t) => (
                      <option key={t.value} value={t.value}>
                        {t.label}
                      </option>
                    ))}
                  </select>
                  <GlowButton
                    tone="violet"
                    size="sm"
                    disabled={isLoading}
                    onClick={handleRecognize}
                  >
                    {isLoading ? <Loader2 className="size-4 animate-spin" /> : "识别"}
                  </GlowButton>
                </div>
              )}
            </div>

            {/* 右：结果 */}
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <p className="font-medium text-sm">提取结果</p>
                {ocrResult && (
                  <GlowButton
                    tone="ghost"
                    size="sm"
                    onClick={() => {
                      if (navigator.clipboard) {
                        navigator.clipboard
                          .writeText(ocrResult)
                          .then(() => notify.success("已复制"))
                      } else {
                        const el = document.createElement("textarea")
                        el.value = ocrResult
                        document.body.appendChild(el)
                        el.select()
                        document.execCommand("copy")
                        document.body.removeChild(el)
                        notify.success("已复制")
                      }
                    }}
                  >
                    <Copy className="size-3.5" />
                    复制
                  </GlowButton>
                )}
              </div>
              <div className="min-h-[240px] flex-1 overflow-y-auto whitespace-pre-wrap rounded-xl border border-foreground/6 bg-foreground/2 p-4 text-sm leading-6">
                {isLoading ? (
                  <div className="flex h-full items-center justify-center gap-2 text-muted-foreground">
                    <Loader2 className="size-4 animate-spin" />
                    识别中...
                  </div>
                ) : ocrResult ? (
                  ocrResult
                ) : (
                  <p className="text-muted-foreground text-sm">识别结果将在此显示</p>
                )}
              </div>
            </div>
          </div>
        </GlassCardBody>
      </GlassCard>
    </div>
  )
}
