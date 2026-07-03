/**
 * 创作-AI 抠图
 *
 * 支持两种模式：
 *   - 人像高清抠图（SEGMENT_HD_BODY）：0.007 元/次
 *   - 通用图像分割（SEGMENT_COMMON_IMAGE）：0.002 元/次
 *
 * 完成后用拖动分割线（slider）对比原图 / 结果图。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useMutation } from "@tanstack/react-query"
import { Coins, Download, Scissors, Upload, Wand2 } from "lucide-react"
import { useCallback, useRef, useState } from "react"
import { toast } from "sonner"
import { GlassCard, GlowButton, NeonChip } from "@/components/studio"
import { Label } from "@/components/ui/label"
import { request } from "@/lib/api/rest/entity/crud"
import { type AigcTaskEvent, useAigcTaskStream } from "@/lib/hooks/use-aigc-task-stream"
import { useEstimateAigcCredits } from "@/lib/hooks/use-estimate-aigc-credits"
import { useFileUpload } from "@/lib/hooks/use-file-upload"
import { notify } from "@/lib/notification"
import { downloadFileWithToast } from "@/lib/utils"

// ─── 分割方式配置（含阿里云接口限制） ─────────────────────

const METHODS = [
  {
    value: "SEGMENT_HD_BODY",
    label: "人像高清抠图",
    desc: "专为人物照片优化，边缘细腻",
    accept: "image/jpeg,image/jpg,image/bmp,image/png",
    maxSizeMB: 40,
    formats: "JPG / JPEG / BMP / PNG（含透明图）",
    resolution: "32×32 ~ 6000×6000，最长边 ≤ 6000px"
  },
  {
    value: "SEGMENT_HD_COMMON_IMAGE",
    label: "通用高清分割",
    desc: "适用于任意物体背景去除，高清效果",
    accept: "image/jpeg,image/jpg,image/png,image/bmp",
    maxSizeMB: 40,
    formats: "PNG / JPEG / JPG / BMP",
    resolution: "32×32 ~ 10000×10000"
  }
] as const

type MethodValue = (typeof METHODS)[number]["value"]

// ─── 拖动分割线组件 ─────────────────────────────────────────

function ImageSplitViewer({ original, result }: { original: string; result: string }) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [split, setSplit] = useState(50) // 百分比
  const dragging = useRef(false)

  const updateSplit = useCallback((clientX: number) => {
    const rect = containerRef.current?.getBoundingClientRect()
    if (!rect) return
    const pct = Math.min(100, Math.max(0, ((clientX - rect.left) / rect.width) * 100))
    setSplit(pct)
  }, [])

  const onMouseDown = () => {
    dragging.current = true
    const onMove = (e: MouseEvent) => dragging.current && updateSplit(e.clientX)
    const onUp = () => {
      dragging.current = false
      window.removeEventListener("mousemove", onMove)
      window.removeEventListener("mouseup", onUp)
    }
    window.addEventListener("mousemove", onMove)
    window.addEventListener("mouseup", onUp)
  }

  const onTouchStart = () => {
    const onMove = (e: TouchEvent) => updateSplit(e.touches[0].clientX)
    const onEnd = () => {
      window.removeEventListener("touchmove", onMove)
      window.removeEventListener("touchend", onEnd)
    }
    window.addEventListener("touchmove", onMove)
    window.addEventListener("touchend", onEnd)
  }

  return (
    <div
      ref={containerRef}
      className="relative aspect-video w-full select-none overflow-hidden rounded-xl bg-[length:20px_20px] bg-[repeating-conic-gradient(#80808020_0%_25%,transparent_0%_50%)]"
    >
      {/* 结果图（底层，铺满） */}
      {/* biome-ignore lint/performance/noImgElement: 生成结果图，无固定尺寸 */}
      <img src={result} alt="结果图" className="absolute inset-0 size-full object-contain" />

      {/* 原图（顶层，裁剪右侧） */}
      <div
        className="absolute inset-0 overflow-hidden"
        style={{ clipPath: `inset(0 ${100 - split}% 0 0)` }}
      >
        {/* biome-ignore lint/performance/noImgElement: 生成结果图，无固定尺寸 */}
        <img src={original} alt="原图" className="absolute inset-0 size-full object-contain" />
        {/* 原图标签 */}
        <span className="absolute top-2 left-2 rounded bg-black/50 px-1.5 py-0.5 text-white text-xs">
          原图
        </span>
      </div>

      {/* 结果图标签 */}
      <span className="absolute top-2 right-2 rounded bg-primary/70 px-1.5 py-0.5 text-white text-xs">
        结果图
      </span>

      {/* 分割线 */}
      <div className="absolute inset-y-0 w-0.5 bg-primary" style={{ left: `${split}%` }} />

      {/* 拖动手柄 */}
      <button
        type="button"
        aria-label="拖动对比"
        className="absolute top-1/2 flex size-8 -translate-x-1/2 -translate-y-1/2 cursor-ew-resize items-center justify-center rounded-full bg-primary text-white shadow-lg"
        style={{ left: `${split}%` }}
        onMouseDown={onMouseDown}
        onTouchStart={onTouchStart}
      >
        ◀▶
      </button>
    </div>
  )
}

// ─── 文件校验 ───────────────────────────────────────────────

function validateFile(file: File, method: MethodValue): string | null {
  const cfg = METHODS.find((m) => m.value === method)
  if (!cfg) return null
  const allowedMimes = cfg.accept.split(",")
  if (!allowedMimes.includes(file.type)) {
    return `当前模式仅支持 ${cfg.formats}`
  }
  if (file.size > cfg.maxSizeMB * 1024 * 1024) {
    return `图片大小不能超过 ${cfg.maxSizeMB} MB`
  }
  return null
}

function validateUrl(url: string): string | null {
  if (/[\u4e00-\u9fa5]/.test(url)) return "URL 中不能包含中文字符"
  return null
}

// ─── 主页面 ─────────────────────────────────────────────────

export default function MattingPage() {
  const [method, setMethod] = useState<MethodValue>("SEGMENT_HD_BODY")
  const [imageUrl, setImageUrl] = useState("")
  const [previewUrl, setPreviewUrl] = useState("")
  const [urlError, setUrlError] = useState<string | null>(null)
  const [tasks, setTasks] = useState<AigcTaskEvent[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { upload, uploading } = useFileUpload()

  const handleFile = async (file: File) => {
    const err = validateFile(file, method)
    if (err) {
      notify.error(err)
      return
    }
    setPreviewUrl(URL.createObjectURL(file))
    setImageUrl("")
    setUrlError(null)
    try {
      const result = await upload(file)
      setImageUrl(result.url)
    } catch {
      setPreviewUrl("")
    }
  }

  const handleUrlChange = (val: string) => {
    setImageUrl(val)
    setPreviewUrl(val)
    setUrlError(val ? validateUrl(val) : null)
  }

  const { mutate: submit, isPending } = useMutation({
    mutationFn: () =>
      request<number>("/aigc/tasks/submit", {
        method: "POST",
        body: JSON.stringify({
          type: "IMAGE_PROCESS",
          prompt: imageUrl,
          projectId: null,
          params: { imageUrl, method }
        })
      }),
    onSuccess: (taskId) => {
      setTasks((prev) => [
        {
          id: taskId,
          userId: 0,
          type: "IMAGE_PROCESS",
          prompt: imageUrl,
          status: "PENDING",
          createTime: new Date().toISOString(),
          updateTime: new Date().toISOString()
        },
        ...prev
      ])
      notify.success("抠图任务已提交")
    },
    onError: () => notify.error("提交失败")
  })

  useAigcTaskStream({
    onProgress: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "IMAGE_PROCESS") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
    }, []),
    onCompleted: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "IMAGE_PROCESS") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
      toast.success("抠图完成，素材已入库")
    }, []),
    onFailed: useCallback((task: AigcTaskEvent) => {
      if (task.type !== "IMAGE_PROCESS") return
      setTasks((prev) => prev.map((t) => (t.id === task.id ? task : t)))
      toast.error(task.errorMsg ?? "抠图失败")
    }, [])
  })

  const canSubmit = imageUrl.startsWith("http") && !isPending && !uploading && !urlError
  const selectedMethod = METHODS.find((m) => m.value === method) ?? METHODS[0]

  const { credits, sufficient } = useEstimateAigcCredits({
    type: "IMAGE_PROCESS",
    model: null,
    params: { method },
    enabled: true
  })

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      {/* 标题 */}
      <header className="space-y-1">
        <div className="flex items-center gap-2">
          <Scissors className="size-5 text-violet-400" />
          <h1 className="font-semibold text-xl">AI 抠图</h1>
        </div>
        <p className="text-muted-foreground text-sm">
          自动去除图片背景，支持人像高清与通用两种模式
        </p>
      </header>

      {/* Composer */}
      <GlassCard glow="violet">
        <div className="space-y-5 p-5">
          {/* 模式切换 */}
          <div className="space-y-2">
            <Label className="text-xs">分割模式</Label>
            <div className="grid grid-cols-2 gap-2">
              {METHODS.map((m) => (
                <button
                  key={m.value}
                  type="button"
                  onClick={() => setMethod(m.value)}
                  className={`rounded-xl border p-3 text-left transition-all ${
                    method === m.value
                      ? "border-primary/50 bg-primary/10"
                      : "border-foreground/8 bg-foreground/2 hover:bg-foreground/4"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-medium text-sm">{m.label}</span>
                  </div>
                  <p className="mt-0.5 text-muted-foreground text-xs">{m.desc}</p>
                </button>
              ))}
            </div>
          </div>

          {/* 图片 URL 输入 */}
          <div className="space-y-2">
            <Label className="text-xs">图片 URL</Label>
            <div className="flex gap-2">
              <input
                type="url"
                value={imageUrl.startsWith("blob:") ? "" : imageUrl}
                onChange={(e) => handleUrlChange(e.target.value)}
                placeholder="粘贴图片 URL（https://...）"
                className={`flex-1 rounded-lg border bg-foreground/2 px-3 py-2 text-sm outline-none focus:border-primary/50 ${
                  urlError ? "border-destructive" : "border-foreground/8"
                }`}
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="flex items-center gap-1.5 rounded-lg border border-foreground/8 px-3 py-2 text-muted-foreground text-sm hover:bg-foreground/[0.04]"
              >
                <Upload className="size-3.5" />
                本地
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept={selectedMethod.accept}
                className="hidden"
                onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])}
              />
            </div>
            {/* URL 校验错误 */}
            {urlError && <p className="text-destructive text-xs">{urlError}</p>}
            {uploading && <p className="text-muted-foreground text-xs">图片上传中，请稍候...</p>}
            {!uploading && imageUrl.startsWith("blob:") && (
              <p className="text-amber-400 text-xs">
                本地预览仅供参考，提交需先将图片上传至可访问的 URL
              </p>
            )}
            {/* 当前模式限制说明 */}
            <div className="space-y-0.5 rounded-lg border border-foreground/6 bg-foreground/2 px-3 py-2 text-muted-foreground text-xs">
              <p>
                格式：{selectedMethod.formats} 大小：≤ {selectedMethod.maxSizeMB} MB　分辨率：
                {selectedMethod.resolution}
              </p>
            </div>
          </div>

          {/* 预览 + 提交 */}
          {previewUrl && (
            <div className="overflow-hidden rounded-xl border border-foreground/6">
              {/* biome-ignore lint/performance/noImgElement: 用户上传预览图，无固定尺寸 */}
              <img
                src={previewUrl}
                alt="预览"
                className="max-h-48 w-full bg-[repeating-conic-gradient(#80808020_0%_25%,transparent_0%_50%)] bg-size-[20px_20px] object-contain"
              />
            </div>
          )}

          <div className="flex items-center justify-between border-foreground/6 border-t pt-3">
            <div className="flex items-center gap-3 text-muted-foreground text-xs">
              <span>
                当前模式：
                <NeonChip tone="violet" size="sm" className="ml-1">
                  {selectedMethod.label}
                </NeonChip>
              </span>
              {credits !== null && credits > 0 ? (
                <span className={`flex items-center gap-1 ${!sufficient ? "text-amber-400" : ""}`}>
                  <Coins className="size-3" />
                  {credits} 积分/次
                </span>
              ) : (
                <span className="opacity-40">费用以后台为准</span>
              )}
            </div>
            <GlowButton tone="violet" size="default" onClick={() => submit()} disabled={!canSubmit}>
              <Wand2 className="size-4" />
              {uploading ? "上传中..." : isPending ? "提交中..." : "开始抠图"}
            </GlowButton>
          </div>
        </div>
      </GlassCard>

      {/* 结果列表（含分割线对比） */}
      {tasks.length > 0 && (
        <GlassCard glow="violet" className="overflow-hidden">
          <div className="border-foreground/6 border-b px-4 py-3">
            <p className="font-medium text-sm">抠图结果</p>
          </div>
          <div className="divide-y divide-foreground/4">
            {tasks.map((task) => (
              <div key={task.id} className="space-y-3 p-4">
                <div className="flex items-center justify-between gap-2 text-sm">
                  <div className="flex items-center gap-2">
                    {task.status === "SUCCESS" ? (
                      <span className="text-emerald-400">✓ 完成</span>
                    ) : task.status === "FAIL" ? (
                      <span className="text-destructive">
                        ✕ 失败 {task.errorMsg && `· ${task.errorMsg.slice(0, 40)}`}
                      </span>
                    ) : (
                      <span className="animate-pulse text-muted-foreground">
                        {task.status === "RUNNING" ? "处理中..." : "排队中..."}
                      </span>
                    )}
                  </div>
                  {task.status === "SUCCESS" && task.ossUrl && (
                    <button
                      type="button"
                      className="flex items-center gap-1 rounded-md border border-foreground/8 px-2 py-1 text-muted-foreground text-xs hover:bg-foreground/[0.06] hover:text-foreground"
                      onClick={() => {
                        const url = task.ossUrl
                        if (!url) return
                        downloadFileWithToast(url, `matting-${task.id}.png`)
                      }}
                    >
                      <Download className="size-3" />
                      下载
                    </button>
                  )}
                </div>

                {task.status === "SUCCESS" && task.ossUrl && task.prompt && (
                  <ImageSplitViewer original={task.prompt} result={task.ossUrl} />
                )}
              </div>
            ))}
          </div>
        </GlassCard>
      )}
    </div>
  )
}
