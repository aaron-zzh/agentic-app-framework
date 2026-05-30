/**
 * FieldSignature——手写签名字段（Canvas + 上传）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"

import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"

interface FieldSignatureProps {
  name: string
  label?: string
  value?: string // fileId 或 data URL
  onChange?: (value: string) => void
  disabled?: boolean
  uploadEndpoint?: string
  width?: number
  height?: number
}

export function FieldSignature({
  name,
  label,
  value,
  onChange,
  disabled,
  uploadEndpoint,
  width: _width = 400,
  height = 160
}: FieldSignatureProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [drawing, setDrawing] = useState(false)
  const [signed, setSigned] = useState(false)
  const lastPos = useRef<{ x: number; y: number } | null>(null)

  // 同步 canvas 绘图分辨率与实际显示尺寸，避免坐标偏移
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const rect = canvas.getBoundingClientRect()
    if (rect.width > 0) {
      canvas.width = rect.width
      canvas.height = height
    }
  }, [height])

  const getPos = (e: React.MouseEvent | React.TouchEvent, canvas: HTMLCanvasElement) => {
    const rect = canvas.getBoundingClientRect()
    const clientX = "touches" in e ? e.touches[0].clientX : e.clientX
    const clientY = "touches" in e ? e.touches[0].clientY : e.clientY
    return { x: clientX - rect.left, y: clientY - rect.top }
  }

  // biome-ignore lint/correctness/useExhaustiveDependencies: getPos 是纯函数，不需要加入依赖
  const startDraw = useCallback(
    (e: React.MouseEvent | React.TouchEvent) => {
      if (disabled) return
      const canvas = canvasRef.current
      if (!canvas) return
      setDrawing(true)
      lastPos.current = getPos(e, canvas)
    },
    [disabled]
  )

  // biome-ignore lint/correctness/useExhaustiveDependencies: getPos 是纯函数，不需要加入依赖
  const draw = useCallback(
    (e: React.MouseEvent | React.TouchEvent) => {
      if (!drawing) return
      const canvas = canvasRef.current
      const ctx = canvas?.getContext("2d")
      if (!canvas || !ctx || !lastPos.current) return
      const pos = getPos(e, canvas)
      ctx.beginPath()
      ctx.moveTo(lastPos.current.x, lastPos.current.y)
      ctx.lineTo(pos.x, pos.y)
      ctx.strokeStyle = "#000"
      ctx.lineWidth = 2
      ctx.lineCap = "round"
      ctx.stroke()
      lastPos.current = pos
      setSigned(true)
    },
    [drawing]
  )

  const stopDraw = useCallback(() => setDrawing(false), [])

  const clear = useCallback(() => {
    const canvas = canvasRef.current
    const ctx = canvas?.getContext("2d")
    if (!canvas || !ctx) return
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    setSigned(false)
    onChange?.("")
  }, [onChange])

  const confirm = useCallback(async () => {
    const canvas = canvasRef.current
    if (!canvas || !signed) return
    const dataUrl = canvas.toDataURL("image/png")

    if (uploadEndpoint) {
      // 上传到服务器
      const blob = await (await fetch(dataUrl)).blob()
      const form = new FormData()
      form.append("file", blob, "signature.png")
      try {
        const res = await fetch(uploadEndpoint, { method: "POST", body: form })
        const json = await res.json()
        onChange?.(json.data?.fileId ?? dataUrl)
      } catch {
        onChange?.(dataUrl)
      }
    } else {
      onChange?.(dataUrl)
    }
  }, [signed, uploadEndpoint, onChange])

  // 已有签名：显示图片
  if (value && !signed) {
    return (
      <div className="flex flex-col gap-1.5">
        {label && <Label>{label}</Label>}
        <div className="rounded-lg border p-2">
          {value.startsWith("data:") || value.startsWith("http") ? (
            // biome-ignore lint/performance/noImgElement: 签名为 data URL，next/image 不支持
            <img src={value} alt="签名" className="max-h-24 object-contain" />
          ) : (
            <p className="text-muted-foreground text-xs">签名已保存（ID: {value}）</p>
          )}
          {!disabled && (
            <Button type="button" variant="ghost" size="sm" className="mt-1" onClick={clear}>
              重新签名
            </Button>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-1.5">
      {label && <Label htmlFor={name}>{label}</Label>}
      <div className="rounded-lg border">
        <canvas
          ref={canvasRef}
          height={height}
          className="block w-full cursor-crosshair touch-none rounded-t-lg bg-white"
          onMouseDown={startDraw}
          onMouseMove={draw}
          onMouseUp={stopDraw}
          onMouseLeave={stopDraw}
          onTouchStart={startDraw}
          onTouchMove={draw}
          onTouchEnd={stopDraw}
        />
        <div className="flex items-center justify-between border-t px-3 py-1.5">
          <span className="text-muted-foreground text-xs">
            {signed ? "签名完成" : "请在上方手写签名"}
          </span>
          <div className="flex gap-2">
            <Button type="button" variant="ghost" size="sm" onClick={clear}>
              清除
            </Button>
            <Button type="button" size="sm" disabled={!signed} onClick={confirm}>
              确认签名
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
