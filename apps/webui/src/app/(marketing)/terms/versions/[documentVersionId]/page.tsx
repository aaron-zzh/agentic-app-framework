/**
 * 服务条款精确发布版本公开页（SSR，不做 ISR）
 *
 * 数据：访问 /api/public/legal/terms/versions/{documentVersionId} 取用户实际待同意的精确不可变版本。
 * 版本已不是当前发布版本时返回 404，跳转到最新版本页。
 * 渲染：复用 <LegalDocLayout> 共享布局。
 *
 * @author AaronZZH & Kiro
 */
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { LegalDocLayout } from "@/components/common/LegalDocLayout"
import { buildApiUrl } from "@/lib/api/config"
import type { LegalDocument } from "@/lib/api/rest/legal"
import { APP } from "@/lib/config"

interface Props {
  params: Promise<{ documentVersionId: string }>
}

async function fetchLegalVersion(documentVersionId: string): Promise<LegalDocument | null> {
  try {
    const res = await fetch(buildApiUrl(`/public/legal/terms/versions/${documentVersionId}`), {
      cache: "no-store"
    })
    if (!res.ok) return null
    const json = (await res.json()) as { data: LegalDocument }
    return json.data
  } catch {
    return null
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { documentVersionId } = await params
  const doc = await fetchLegalVersion(documentVersionId)
  return {
    title: doc?.title ?? "服务条款",
    description: `${APP.name} 服务条款，规定平台与用户的权利义务。`
  }
}

export default async function TermsVersionPage({ params }: Props) {
  const { documentVersionId } = await params
  const doc = await fetchLegalVersion(documentVersionId)

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
