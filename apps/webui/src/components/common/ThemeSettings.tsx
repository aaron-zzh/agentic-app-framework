/**
 * ThemeSettings——主题设置面板（卡片式布局）
 * 包含：亮暗模式、对比度、RTL、紧凑布局、导航布局/配色、主题色预设
 * @author AaronZZH & Kiro
 */

"use client"

import {
  Contrast,
  Info,
  Languages,
  Monitor,
  Moon,
  RotateCcw,
  Shrink,
  Sun
} from "lucide-react"
import { useTheme } from "next-themes"
import { useEffect } from "react"

import { type ThemeColor, useUIStore } from "@/lib/store/ui-store"
import { cn } from "@/lib/utils/cn"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"

/* ─── 主题色预设 ─────────────────────────────────────────── */

const COLOR_PRESETS: { value: ThemeColor; label: string; cssColor: string }[] = [
  { value: "default", label: "默认", cssColor: "oklch(0.205 0 0)" },
  { value: "blue", label: "智能蓝", cssColor: "oklch(0.55 0.2 250)" },
  { value: "purple", label: "知识紫", cssColor: "oklch(0.50 0.15 290)" },
  { value: "orange", label: "行动橙", cssColor: "oklch(0.62 0.18 55)" },
  { value: "green", label: "绿色", cssColor: "oklch(0.55 0.17 155)" },
  { value: "rose", label: "玫红", cssColor: "oklch(0.55 0.2 10)" },
  { value: "cyan", label: "青色", cssColor: "oklch(0.55 0.15 200)" }
]

/* ─── 开关卡片组件 ─────────────────────────────────────────── */

function ToggleCard({
  icon: Icon,
  label,
  checked,
  onChange,
  active,
  tooltip
}: {
  icon: React.ComponentType<{ className?: string }>
  label: string
  checked: boolean
  onChange: (v: boolean) => void
  active?: boolean
  tooltip?: string
}) {
  return (
    <div
      className={cn(
        "relative flex flex-col gap-3 rounded-xl border p-4 transition-colors",
        active ? "border-primary/30 bg-primary/5" : "bg-card"
      )}
    >
      <div className="flex items-center justify-between">
        <Icon className="size-5 text-muted-foreground" />
        <Switch checked={checked} onCheckedChange={onChange} />
      </div>
      <div className="flex items-center gap-1">
        <span className="font-medium text-sm">{label}</span>
        {tooltip && (
          <TooltipProvider delay={300}>
            <Tooltip>
              <TooltipTrigger render={<Info className="size-3.5 text-muted-foreground" />} />
              <TooltipContent side="bottom">
                <p className="text-xs">{tooltip}</p>
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        )}
      </div>
    </div>
  )
}

/* ─── 主组件 ─────────────────────────────────────────── */

/** 主题设置面板 */
export function ThemeSettings() {
  const { theme, setTheme } = useTheme()
  const { themeColor, setThemeColor, compactLayout, toggleCompactLayout } = useUIStore()

  // 同步 themeColor class 到 html 元素
  useEffect(() => {
    const html = document.documentElement
    html.classList.remove(
      "theme-blue",
      "theme-purple",
      "theme-orange",
      "theme-green",
      "theme-rose",
      "theme-cyan"
    )
    if (themeColor !== "default") {
      html.classList.add(`theme-${themeColor}`)
    }
  }, [themeColor])

  const isDark = theme === "dark"

  function handleReset() {
    setTheme("system")
    setThemeColor("default")
    if (compactLayout) toggleCompactLayout()
  }

  return (
    <div className="space-y-6">
      {/* 标题栏 */}
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-lg">设置</h2>
        <button
          type="button"
          onClick={handleReset}
          className="rounded-lg p-2 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          title="重置为默认"
        >
          <RotateCcw className="size-4" />
        </button>
      </div>

      {/* 开关卡片网格 */}
      <div className="grid grid-cols-2 gap-3">
        <ToggleCard
          icon={isDark ? Moon : Sun}
          label="深色模式"
          checked={isDark}
          onChange={(v) => setTheme(v ? "dark" : "light")}
          active={isDark}
        />
        <ToggleCard
          icon={Contrast}
          label="高对比"
          checked={false}
          onChange={() => {}}
          tooltip="即将推出"
        />
        <ToggleCard
          icon={Languages}
          label="RTL"
          checked={false}
          onChange={() => {}}
        />
        <ToggleCard
          icon={Shrink}
          label="紧凑"
          checked={compactLayout}
          onChange={() => toggleCompactLayout()}
          active={compactLayout}
          tooltip="减小间距和字号"
        />
      </div>

      {/* 导航 */}
      <section>
        <SectionLabel label="导航" />
        <div className="space-y-4 rounded-xl border bg-card p-4">
          {/* 布局 */}
          <div>
            <p className="mb-2 text-muted-foreground text-xs">布局</p>
            <div className="flex gap-2">
              <NavLayoutOption
                active={!compactLayout}
                onClick={() => { if (compactLayout) toggleCompactLayout() }}
              >
                {/* 宽侧边栏示意 */}
                <div className="flex h-10 w-14 items-center gap-0.5 rounded border p-1">
                  <div className="flex h-full w-4 flex-col gap-0.5">
                    <div className="size-1.5 rounded-full bg-primary" />
                    <div className="h-1 w-full rounded-sm bg-muted-foreground/30" />
                    <div className="h-1 w-full rounded-sm bg-muted-foreground/20" />
                  </div>
                  <div className="h-full flex-1 rounded-sm bg-muted/60" />
                </div>
              </NavLayoutOption>
              <NavLayoutOption
                active={compactLayout}
                onClick={() => { if (!compactLayout) toggleCompactLayout() }}
              >
                {/* 窄侧边栏示意 */}
                <div className="flex h-10 w-14 items-center gap-0.5 rounded border p-1">
                  <div className="flex h-full w-2 flex-col items-center gap-0.5">
                    <div className="size-1.5 rounded-full bg-muted-foreground/40" />
                    <div className="size-1 rounded-full bg-muted-foreground/20" />
                    <div className="size-1 rounded-full bg-muted-foreground/20" />
                  </div>
                  <div className="h-full flex-1 rounded-sm bg-muted/60" />
                </div>
              </NavLayoutOption>
            </div>
          </div>
        </div>
      </section>

      {/* 主题色预设 */}
      <section>
        <SectionLabel label="主题色" />
        <div className="rounded-xl border bg-card p-4">
          <div className="flex flex-wrap gap-3">
            {COLOR_PRESETS.map((preset) => (
              <button
                key={preset.value}
                type="button"
                title={preset.label}
                onClick={() => setThemeColor(preset.value)}
                className={cn(
                  "group relative flex size-10 items-center justify-center rounded-xl border-2 transition-all",
                  themeColor === preset.value
                    ? "border-primary shadow-sm"
                    : "border-transparent hover:border-border"
                )}
              >
                <span
                  className="size-6 rounded-lg shadow-sm"
                  style={{ backgroundColor: preset.cssColor }}
                />
                {themeColor === preset.value && (
                  <span className="absolute -top-1 -right-1 size-2.5 rounded-full border-2 border-background bg-primary" />
                )}
              </button>
            ))}
          </div>
          <div className="mt-3 flex flex-wrap gap-1.5">
            {COLOR_PRESETS.map((preset) => (
              <span
                key={preset.value}
                className={cn(
                  "rounded-md px-1.5 py-0.5 text-[10px]",
                  themeColor === preset.value
                    ? "bg-primary/10 font-medium text-primary"
                    : "text-muted-foreground"
                )}
              >
                {preset.label}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* 外观模式 */}
      <section>
        <SectionLabel label="外观模式" />
        <div className="grid grid-cols-3 gap-2 rounded-xl border bg-card p-4">
          <ModeButton
            icon={Sun}
            label="亮色"
            active={theme === "light"}
            onClick={() => setTheme("light")}
          />
          <ModeButton
            icon={Moon}
            label="暗色"
            active={theme === "dark"}
            onClick={() => setTheme("dark")}
          />
          <ModeButton
            icon={Monitor}
            label="系统"
            active={theme === "system"}
            onClick={() => setTheme("system")}
          />
        </div>
      </section>
    </div>
  )
}

/* ─── 子组件 ─────────────────────────────────────────── */

function SectionLabel({ label }: { label: string }) {
  return (
    <div className="mb-2 inline-flex items-center gap-1 rounded-md bg-foreground px-2 py-0.5">
      <span className="font-medium text-background text-xs">{label}</span>
    </div>
  )
}

function NavLayoutOption({
  active,
  onClick,
  children
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "rounded-lg border-2 p-1.5 transition-all",
        active ? "border-primary bg-primary/5" : "border-transparent hover:border-border"
      )}
    >
      {children}
    </button>
  )
}

function ModeButton({
  icon: Icon,
  label,
  active,
  onClick
}: {
  icon: React.ComponentType<{ className?: string }>
  label: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex flex-col items-center gap-1.5 rounded-lg border p-3 text-xs transition-colors",
        active ? "border-primary bg-primary/5 text-primary" : "hover:bg-accent"
      )}
    >
      <Icon className="size-4" />
      <span>{label}</span>
    </button>
  )
}
