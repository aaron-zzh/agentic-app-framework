/**
 * PageEngine 渲染器——遍历 PageDef.sections 并渲染对应组件
 * @author AaronZZH & Kiro
 */

"use client"

import { Component, type ReactNode } from "react"

import { cn } from "@/lib/utils/cn"

import { getSectionComponent } from "./registry"
import { SectionWrapper } from "./SectionWrapper"
import type { PageDef, SectionDef, SectionStyle } from "./types"

// ─── Section 级 ErrorBoundary ───────────────────────────────────────────────

interface ErrorBoundaryProps {
  sectionId: string
  children: ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

/** Section 级错误边界——单个 Section 崩溃不影响整页 */
// biome-ignore lint/style/useReactFunctionComponents: ErrorBoundary 必须用 class 组件
class SectionErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  override render() {
    if (this.state.error) {
      return (
        <div className="flex flex-col items-center justify-center gap-2 border border-destructive/30 bg-destructive/5 px-6 py-8">
          <p className="font-medium text-destructive text-sm">
            Section「{this.props.sectionId}」渲染失败
          </p>
          <p className="text-muted-foreground text-xs">{this.state.error.message}</p>
          <button
            type="button"
            onClick={() => this.setState({ error: null })}
            className="mt-2 rounded-md border px-3 py-1.5 text-xs hover:bg-accent"
          >
            重试
          </button>
        </div>
      )
    }
    return this.props.children
  }
}

// ─── Section 渲染 ────────────────────────────────────────────────────────────

function buildSectionStyle(style?: SectionStyle): string {
  const parts: string[] = []
  if (style?.fullWidth) {
    parts.push("w-full")
  } else {
    parts.push("mx-auto w-full")
  }
  if (style?.className) {
    parts.push(style.className)
  }
  return parts.join(" ")
}

function buildInlineStyle(style?: SectionStyle): React.CSSProperties | undefined {
  if (!style) return undefined
  const css: React.CSSProperties = {}
  if (style.padding) css.padding = style.padding
  if (style.backgroundColor) css.backgroundColor = style.backgroundColor
  if (style.maxWidth && !style.fullWidth) css.maxWidth = style.maxWidth
  return Object.keys(css).length > 0 ? css : undefined
}

function SectionRenderer({ section, darkMode }: { section: SectionDef; darkMode?: boolean }) {
  const entry = getSectionComponent(section.type)
  if (!entry) {
    return (
      <div className="border border-dashed border-muted-foreground/30 px-6 py-8 text-center text-muted-foreground text-sm">
        未注册的 Section 类型：{section.type}
      </div>
    )
  }

  const SectionComponent = entry.component

  // 有动画配置时使用 SectionWrapper
  if (section.style?.animation || darkMode) {
    return (
      <SectionWrapper id={section.id} style={section.style} darkMode={darkMode}>
        <SectionComponent data={section.props} style={section.style} />
      </SectionWrapper>
    )
  }

  return (
    <div className={buildSectionStyle(section.style)} style={buildInlineStyle(section.style)}>
      <SectionComponent data={section.props} style={section.style} />
    </div>
  )
}

// ─── PageEngine 主组件 ───────────────────────────────────────────────────────

interface PageEngineProps {
  page: PageDef
}

/** PageEngine——根据 PageDef 配置渲染完整页面 */
export function PageEngine({ page }: PageEngineProps) {
  const darkMode = page.theme?.darkMode === true

  return (
    <div className={cn("flex scroll-smooth flex-col", page.theme?.className, darkMode && "dark")}>
      {page.sections.map((section) => (
        <SectionErrorBoundary key={section.id} sectionId={section.id}>
          <SectionRenderer section={section} darkMode={darkMode} />
        </SectionErrorBoundary>
      ))}
    </div>
  )
}
