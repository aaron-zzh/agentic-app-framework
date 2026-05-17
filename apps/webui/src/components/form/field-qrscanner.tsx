/**
 * FieldQRScanner——二维码/条码扫描字段（移动端摄像头）
 * @author AaronZZH & Kiro
 *
 * 移动端：输入框右侧显示扫码图标，点击调用摄像头
 * 桌面端：隐藏扫码图标
 * TODO: 集成 html5-qrcode 库（pnpm add html5-qrcode）
 */

"use client"

import { useCallback, useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

interface FieldQRScannerProps {
  name: string
  label?: string
  value?: string
  onChange?: (value: string) => void
  disabled?: boolean
  error?: string
  type?: "qrcode" | "barcode" | "both"
}

export function FieldQRScanner({
  name,
  label,
  value = "",
  onChange,
  disabled,
  error,
  type = "both"
}: FieldQRScannerProps) {
  const [isMobile, setIsMobile] = useState(false)
  const [scanning, setScanning] = useState(false)

  useEffect(() => {
    setIsMobile(/Mobi|Android|iPhone|iPad/i.test(navigator.userAgent))
  }, [])

  const startScan = useCallback(async () => {
    // TODO: 集成 html5-qrcode
    // import { Html5QrcodeScanner } from "html5-qrcode"
    // 当前降级：提示用户手动输入
    setScanning(true)
    const result = window.prompt("请输入条码/二维码内容（扫码功能待集成）")
    if (result) onChange?.(result)
    setScanning(false)
  }, [onChange])

  return (
    <div className="flex flex-col gap-1.5">
      {label && <Label htmlFor={name}>{label}</Label>}
      <div className="flex gap-2">
        <Input
          id={name}
          value={value}
          onChange={(e) => onChange?.(e.target.value)}
          disabled={disabled}
          placeholder="扫码或手动输入..."
          aria-invalid={!!error}
          className="flex-1"
        />
        {isMobile && (
          <Button
            type="button"
            variant="outline"
            size="icon"
            disabled={disabled || scanning}
            onClick={startScan}
            aria-label="扫码"
            title={`扫描${type === "qrcode" ? "二维码" : type === "barcode" ? "条码" : "二维码/条码"}`}
          >
            {scanning ? "…" : "📷"}
          </Button>
        )}
      </div>
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
