/**
 * ThemeSettings——主题设置面板（主题色 + 亮暗模式 + 布局模式）
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * <ThemeSettings />
 * ```
 */

"use client"

import { Monitor, Moon, Sun } from "lucide-react"
import { useTheme } from "next-themes"
import { useEffect } from "react"
import { type ThemeColor, useUIStore } from "@/lib/store/ui-store"
import { cn } from "@/lib/utils/cn"

const COLOR_PRESETS: { value: ThemeColor; label: string; color: string }[] = [
  { value: "default", label: "默认", color: "bg-zinc-900 dark:bg-zinc-100" },
  { value: "blue", label: "蓝色", color: "bg-blue-600" },
  { value: "green", label: "绿色", color: "bg-green-600" }
]

const MODE_OPTIONS = [
  { value: "light", label: "亮色", icon: Sun },
  { value: "dark", label: "暗色", icon: Moon },
  { value: "system", label: "系统", icon: Monitor }
] as const

/** 主题设置面板 */
export function ThemeSettings() {
  const { theme, setTheme } = useTheme()
  const { themeColor, setThemeColor, compactLayout, toggleCompactLayout } = useUIStore()

  // 同步 themeColor class 到 html 元素
  useEffect(() => {
    const html = document.documentElement
    html.classList.remove("theme-blue", "theme-green")
    if (themeColor !== "default") {
      html.classList.add(`theme-${themeColor}`)
    }
  }, [themeColor])

  return (
    <div className="space-y-6">
      {/* 主题色 */}
      <section>
        <h3 className="mb-3 font-medium text-sm">主题色</h3>
        <div className="flex gap-3">
          {COLOR_PRESETS.map((preset) => (
            <button
              key={preset.value}
              type="button"
              title={preset.label}
              onClick={() => setThemeColor(preset.value)}
              className={cn(
                "flex size-8 items-center justify-center rounded-full transition-all",
                themeColor === preset.value && "ring-2 ring-primary ring-offset-2"
              )}
            >
              <span className={cn("size-5 rounded-full", preset.color)} />
            </button>
          ))}
        </div>
      </section>

      {/* 亮暗模式 */}
      <section>
        <h3 className="mb-3 font-medium text-sm">外观模式</h3>
        <div className="flex gap-2">
          {MODE_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              type="button"
              onClick={() => setTheme(opt.value)}
              className={cn(
                "flex flex-1 flex-col items-center gap-1.5 rounded-lg border p-3 text-xs transition-colors",
                theme === opt.value ? "border-primary bg-primary/5 text-primary" : "hover:bg-accent"
              )}
            >
              <opt.icon className="size-5" />
              <span>{opt.label}</span>
            </button>
          ))}
        </div>
      </section>

      {/* 布局模式 */}
      <section>
        <h3 className="mb-3 font-medium text-sm">布局</h3>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => {
              if (!compactLayout) toggleCompactLayout()
            }}
            className={cn(
              "flex flex-1 flex-col items-center gap-1.5 rounded-lg border p-3 text-xs transition-colors",
              compactLayout ? "border-primary bg-primary/5 text-primary" : "hover:bg-accent"
            )}
          >
            <div className="flex h-6 w-10 items-center justify-center rounded border">
              <div className="h-4 w-6 rounded-sm bg-current opacity-30" />
            </div>
            <span>紧凑</span>
          </button>
          <button
            type="button"
            onClick={() => {
              if (compactLayout) toggleCompactLayout()
            }}
            className={cn(
              "flex flex-1 flex-col items-center gap-1.5 rounded-lg border p-3 text-xs transition-colors",
              !compactLayout ? "border-primary bg-primary/5 text-primary" : "hover:bg-accent"
            )}
          >
            <div className="flex h-6 w-10 items-center justify-center rounded border">
              <div className="h-4 w-9 rounded-sm bg-current opacity-30" />
            </div>
            <span>宽屏</span>
          </button>
        </div>
      </section>
    </div>
  )
}
