/**
 * PendingOverlay——生成/上传中的动态渐变蒙版，可选显示虚拟进度百分比
 * @author AaronZZH & Kiro
 */

"use client"

import { useFakeProgress } from "@/lib/hooks/use-fake-progress"

interface PendingOverlayProps {
  /** 底部标签文字 */
  label: string
  /** 是否显示虚拟进度百分比，默认 false */
  showProgress?: boolean
  /** 虚拟进度总时长（毫秒），默认 20000 */
  progressMs?: number
}

export function PendingOverlay({ label, showProgress = false, progressMs }: PendingOverlayProps) {
  const progress = useFakeProgress(progressMs)

  return (
    <div className="absolute inset-0 overflow-hidden rounded-[6px]">
      <div
        className="absolute inset-0"
        style={{
          background: "linear-gradient(135deg, #7c3aed, #db2777, #6366f1, #0ea5e9, #7c3aed)",
          backgroundSize: "300% 300%",
          animation: "gradientBreath 3s ease infinite",
          opacity: 0.7
        }}
      />
      <div
        className="absolute inset-0 animate-[shimmerDiag_4s_ease-in-out_infinite] opacity-0"
        style={{
          background:
            "linear-gradient(135deg, transparent 20%, rgba(255,255,255,0.25) 50%, transparent 80%)",
          backgroundSize: "400% 400%"
        }}
      />
      {showProgress && (
        <div className="absolute inset-0 flex items-center justify-center">
          <span className="font-normal text-sm text-white drop-shadow-[0_1px_4px_rgba(0,0,0,0.5)]">
            {progress}%
          </span>
        </div>
      )}
      <div className="absolute inset-x-0 bottom-2 flex justify-center">
        <span className="line-clamp-1 rounded-full bg-black/35 px-2 py-0.5 text-[10px] text-white/90 backdrop-blur-sm">
          {label}
        </span>
      </div>
    </div>
  )
}
