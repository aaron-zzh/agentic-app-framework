/**
 * 组件展示索引页
 * 路由：/dev/components
 */

import Link from "next/link"
import { PageContainer } from "@/components/common/PageContainer"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"

const SECTIONS = [
  {
    title: "UI 基础组件",
    href: "/components/ui",
    description:
      "Button / Badge / Input / Select / Checkbox / Switch / Tabs / Tooltip / Progress / Skeleton"
  },
  {
    title: "表单组件",
    href: "/components/form",
    description:
      "Field / Form / FormView / Subtable / Signature / Money / Upload / Wizard / UnsavedGuard"
  },
  {
    title: "反馈组件",
    href: "/components/feedback",
    description: "Toast / Snackbar — 基于 sonner，支持 6 种位置、action 按钮、Promise"
  },
  {
    title: "编辑器",
    href: "/components/editor",
    description: "RichTextEditor（Lexical）— richField / chatter / minimal / document 四种 preset"
  }
]

export default function ComponentsIndexPage() {
  return (
    <PageContainer maxWidth="md">
      <div className="mb-8 space-y-2">
        <TypographyH1>组件展示</TypographyH1>
        <TypographyMuted>AAF 前端组件库，基于 shadcn/ui + Tailwind CSS。</TypographyMuted>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        {SECTIONS.map((s) => (
          <Link
            key={s.href}
            href={s.href}
            className="group rounded-xl border bg-background p-5 transition-colors hover:border-primary/50 hover:bg-accent/30"
          >
            <h2 className="font-semibold text-sm group-hover:text-primary">{s.title}</h2>
            <p className="mt-1.5 text-muted-foreground text-xs leading-relaxed">{s.description}</p>
          </Link>
        ))}
      </div>
    </PageContainer>
  )
}
