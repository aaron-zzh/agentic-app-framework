/**
 * 隐私政策公开页（SSR + ISR）
 *
 * 数据：访问 /api/public/legal/privacy 取最新已发布的"隐私政策"文档。
 * 缓存：ISR revalidate=300（5 分钟），后台发布后最多 5 分钟前端可见。
 * 渲染：复用 <LegalDocLayout> 共享布局（hero + 卡片 + TOC + 进度条）。
 *
 * @author AaronZZH & Kiro
 */
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { LegalDocLayout } from "@/components/common/LegalDocLayout"
import { buildApiUrl } from "@/lib/api/config"
import type { LegalDocument } from "@/lib/api/rest/legal"
import { APP } from "@/lib/config"

async function fetchLegal(): Promise<LegalDocument | null> {
  try {
    const res = await fetch(buildApiUrl("/public/legal/privacy"), {
      next: { revalidate: 300 }
    })
    if (!res.ok) return null
    const json = (await res.json()) as { data: LegalDocument }
    return json.data
  } catch {
    return null
  }
}

export async function generateMetadata(): Promise<Metadata> {
  const doc = await fetchLegal()
  return {
    title: doc?.title ?? "隐私政策",
    description: `${APP.name} 隐私政策，说明我们如何收集、使用、存储与共享您的个人信息。`
  }
}

export default async function PrivacyPage() {
  const doc = await fetchLegal()

  if (!doc) notFound()

  return (
    <LegalDocLayout
      title={doc.title}
      content={doc.content ?? ""}
      subtitle="我们重视您的隐私，以下说明将告诉您我们如何收集、使用、存储与共享您的个人信息。"
      category="隐私政策"
      version={doc.version}
      effectiveDate={doc.effectiveDate}
      updateTime={doc.updateTime}
    />
  )
}
