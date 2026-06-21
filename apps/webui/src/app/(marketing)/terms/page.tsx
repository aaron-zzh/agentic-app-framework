/**
 * 服务条款公开页（SSR + ISR）
 *
 * 数据：访问 /api/public/legal/terms 取最新已发布的"服务条款"文档。
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
    const res = await fetch(buildApiUrl("/public/legal/terms"), {
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
    title: doc?.title ?? "服务条款",
    description: `${APP.name} 服务条款，规定平台与用户的权利义务。`
  }
}

export default async function TermsPage() {
  const doc = await fetchLegal()

  if (!doc) notFound()

  return (
    <LegalDocLayout
      title={doc.title}
      content={doc.content ?? ""}
      subtitle="使用本服务即视为您已阅读、理解并接受以下条款全部内容。"
      category="服务条款"
      version={doc.version}
      effectiveDate={doc.effectiveDate}
      updateTime={doc.updateTime}
    />
  )
}
