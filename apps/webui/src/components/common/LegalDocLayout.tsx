/**
 * LegalDocLayout——法律/公开文档统一阅读布局
 *
 * 组成：
 *  - 顶部阅读进度条（ReadingProgress）
 *  - Hero：图标 + 大标题 + 版本 chip + 生效日期 badge + 一句副标题
 *  - 主体：左侧 article 卡片（prose 排版），右侧 sticky TOC（lg+ 显示）
 *  - 底部 Footer：最近更新时间 + 返回首页 / 打印按钮
 *
 * 设计原则：
 *  - 共享于 /terms /privacy /docs-public/[id]，避免三处重复样式
 *  - 移动端：TOC 隐藏，hero 紧凑，按钮全宽
 *  - 严肃风：使用 background 中性背景 + 主色点缀，不喧宾夺主
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowLeftIcon, CalendarDaysIcon, ClockIcon, FileTextIcon, PrinterIcon } from "lucide-react"
import Link from "next/link"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { ReadingProgress } from "@/components/common/ReadingProgress"
import { TableOfContents } from "@/components/common/TableOfContents"
import { Button } from "@/components/ui/button"
import { paths } from "@/lib/constants/paths"

export interface LegalDocLayoutProps {
  /** 文档标题 */
  title: string
  /** Markdown 正文 */
  content: string
  /** 一句话副标题/导语，可选 */
  subtitle?: string
  /** 版本号，可选 */
  version?: string | null
  /** 生效日期（ISO-8601 字符串），可选 */
  effectiveDate?: string | null
  /** 最近更新时间（ISO-8601 字符串） */
  updateTime?: string | null
  /** 文档类型/分类徽章文案，例如 "服务条款" / "guide" */
  category?: string
}

const ARTICLE_SELECTOR = "[data-legal-article]"

export function LegalDocLayout({
  title,
  content,
  subtitle,
  version,
  effectiveDate,
  updateTime,
  category
}: LegalDocLayoutProps) {
  return (
    <>
      <ReadingProgress />

      {/* Hero */}
      <section className="border-b bg-gradient-to-b from-muted/40 to-transparent">
        <div className="mx-auto max-w-(--layout-marketing-max-width) px-6 py-12 sm:py-16">
          <div className="flex flex-col gap-5">
            <div className="flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-full bg-primary/10 text-primary">
                <FileTextIcon className="size-5" strokeWidth={1.75} />
              </span>
              {category && (
                <span className="inline-flex items-center rounded-full bg-foreground/5 px-2.5 py-0.5 font-medium text-foreground/70 text-xs ring-1 ring-foreground/10">
                  {category}
                </span>
              )}
            </div>

            <h1 className="font-bold text-3xl tracking-tight sm:text-4xl">{title}</h1>

            {subtitle && (
              <p className="max-w-2xl text-muted-foreground text-sm sm:text-base">{subtitle}</p>
            )}

            {/* meta 行 */}
            <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-muted-foreground text-xs">
              {version && (
                <span className="inline-flex items-center gap-1.5">
                  <span className="font-mono text-[11px]">v{version.replace(/^v/i, "")}</span>
                </span>
              )}
              {effectiveDate && (
                <span className="inline-flex items-center gap-1.5">
                  <CalendarDaysIcon className="size-3.5" strokeWidth={1.75} />
                  生效日期 {effectiveDate}
                </span>
              )}
              {updateTime && (
                <span className="inline-flex items-center gap-1.5">
                  <ClockIcon className="size-3.5" strokeWidth={1.75} />
                  最近更新 {formatDate(updateTime)}
                </span>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* 主体 */}
      <section className="mx-auto max-w-(--layout-marketing-max-width) px-6 py-10 sm:py-14">
        <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_220px]">
          <article
            data-legal-article
            className="prose prose-neutral dark:prose-invert max-w-none prose-headings:scroll-mt-24 rounded-xl border bg-card p-6 shadow-sm sm:p-10"
          >
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
          </article>

          <aside className="hidden lg:block">
            <TableOfContents selector={ARTICLE_SELECTOR} contentKey={updateTime ?? title} />
          </aside>
        </div>

        {/* 底部 footer 行 */}
        <div className="mt-10 flex flex-col items-center justify-between gap-3 border-t pt-6 sm:flex-row">
          <p className="text-muted-foreground text-xs">
            如对本文档有疑问，请通过{" "}
            <Link href="/contact" className="text-primary hover:underline">
              联系我们
            </Link>{" "}
            反馈。
          </p>
          <div className="flex w-full gap-2 sm:w-auto">
            <Button
              variant="outline"
              size="sm"
              className="flex-1 sm:flex-initial"
              nativeButton={false}
              render={<Link href={paths.root} />}
            >
              <ArrowLeftIcon className="size-4" strokeWidth={1.75} />
              返回首页
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="flex-1 sm:flex-initial"
              onClick={() => window.print()}
            >
              <PrinterIcon className="size-4" strokeWidth={1.75} />
              打印
            </Button>
          </div>
        </div>
      </section>
    </>
  )
}

/** 格式化更新时间为本地化日期 */
function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString("zh-CN", {
      year: "numeric",
      month: "long",
      day: "numeric"
    })
  } catch {
    return iso
  }
}
