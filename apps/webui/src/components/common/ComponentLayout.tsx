/**
 * ComponentLayout + ComponentBox——组件展示布局
 * @author AaronZZH & Kiro
 *
 * 参考 next-ts _examples/layout，用 Tailwind 实现等效布局：
 * - ComponentLayout：页面容器，接收 sectionData 自动渲染 Card 列表 + 右侧锚点导航
 * - ComponentBox：单个示例容器，带可选浮动标题标签
 *
 * @example
 * ```tsx
 * <ComponentLayout
 *   heading="Toast / Snackbar"
 *   links={[{ name: "shadcn/ui", href: "https://ui.shadcn.com/docs/components/sonner" }]}
 *   sectionData={[
 *     { name: "Status", component: <ComponentBox>...</ComponentBox> },
 *     { name: "With Action", component: <ComponentBox title="撤销">...</ComponentBox> },
 *   ]}
 * />
 * ```
 */

"use client"

import { useEffect, useRef, useState } from "react"
import { ExternalLink } from "lucide-react"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Separator } from "@/components/ui/separator"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { cn } from "@/lib/utils/cn"

// ─── ComponentBox ────────────────────────────────────────────────────────────

interface ComponentBoxProps {
  title?: string
  className?: string
  children: React.ReactNode
}

/**
 * 单个示例容器——带可选浮动标题标签
 * 内容居中排列，支持 flex-wrap
 */
export function ComponentBox({ title, className, children }: ComponentBoxProps) {
  return (
    <div className="relative w-full rounded-xl border bg-muted/30">
      {title && (
        <span className="absolute top-0 left-3 -translate-y-1/2 rounded-full border bg-background px-2 py-0.5 font-medium text-[11px] text-foreground">
          {title}
        </span>
      )}
      <div
        className={cn(
          "flex w-full flex-wrap items-center justify-center gap-3 px-6 py-8",
          className
        )}
      >
        {children}
      </div>
    </div>
  )
}

// ─── ComponentLayout ─────────────────────────────────────────────────────────

interface SectionData {
  name: string
  description?: string
  component: React.ReactNode
}

interface ComponentLayoutProps {
  heading: string
  description?: string
  links?: { name: string; href: string }[]
  sectionData: SectionData[]
}

/**
 * 组件展示页面容器
 * 左侧：标题 + 分区 Card 列表
 * 右侧：锚点导航（桌面端固定）
 */
export function ComponentLayout({
  heading,
  description,
  links,
  sectionData
}: ComponentLayoutProps) {
  const [activeId, setActiveId] = useState<string>("")
  const sectionRefs = useRef<Map<string, HTMLElement>>(new Map())

  // 滚动监听，高亮当前可见分区
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveId(entry.target.id)
            break
          }
        }
      },
      { rootMargin: "-20% 0px -60% 0px" }
    )
    for (const el of sectionRefs.current.values()) observer.observe(el)
    return () => observer.disconnect()
  }, [sectionData])

  const scrollTo = (id: string) => {
    const el = document.getElementById(id)
    if (el) el.scrollIntoView({ behavior: "smooth", block: "start" })
  }

  return (
    <div className="mx-auto flex max-w-5xl gap-8 px-6 py-8">
      {/* 主内容 */}
      <div className="min-w-0 flex-1 space-y-6">
        {/* Hero */}
        <div className="space-y-2">
          <TypographyH1>{heading}</TypographyH1>
          {description && <TypographyMuted>{description}</TypographyMuted>}
          {links && links.length > 0 && (
            <div className="flex flex-wrap gap-3 pt-1">
              {links.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1 text-muted-foreground text-xs hover:text-foreground"
                >
                  {link.name}
                  <ExternalLink className="size-3" />
                </a>
              ))}
            </div>
          )}
        </div>

        <Separator />

        {/* 分区列表 */}
        <div className="space-y-6">
          {sectionData.map((section) => {
            const id = toKebab(section.name)
            return (
              <section
                key={id}
                id={id}
                ref={(el) => {
                  if (el) sectionRefs.current.set(id, el)
                  else sectionRefs.current.delete(id)
                }}
                className="rounded-xl border bg-background"
              >
                <div className="border-b px-5 py-3">
                  <h2 className="font-semibold text-sm">{section.name}</h2>
                  {section.description && (
                    <p className="mt-0.5 text-muted-foreground text-xs">{section.description}</p>
                  )}
                </div>
                <div className="p-5">{section.component}</div>
              </section>
            )
          })}
        </div>
      </div>

      {/* 右侧锚点导航（桌面端） */}
      <aside className="hidden w-44 shrink-0 xl:block">
        <div className="sticky top-6">
          <p className="mb-3 font-medium text-muted-foreground text-xs uppercase tracking-wide">
            本页内容
          </p>
          <ScrollArea className="max-h-[calc(100vh-8rem)]">
            <nav className="space-y-1">
              {sectionData.map((section) => {
                const id = toKebab(section.name)
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => scrollTo(id)}
                    className={cn(
                      "block w-full rounded px-2 py-1 text-left text-xs transition-colors",
                      activeId === id
                        ? "bg-primary/10 font-medium text-primary"
                        : "text-muted-foreground hover:text-foreground"
                    )}
                  >
                    {section.name}
                  </button>
                )
              })}
            </nav>
          </ScrollArea>
        </div>
      </aside>
    </div>
  )
}

function toKebab(str: string) {
  return str
    .toLowerCase()
    .replace(/[\s_]+/g, "-")
    .replace(/[^\w-]/g, "")
}
