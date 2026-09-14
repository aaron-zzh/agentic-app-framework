/**
 * 隐私政策精确发布版本公开页（SSR，不做 ISR）
 *
 * 数据：访问 /api/public/legal/privacy/versions/{documentVersionId} 取用户实际待同意的精确不可变版本。
 * 版本已不是当前发布版本时返回 404。
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
    const res = await fetch(buildApiUrl(`/public/legal/privacy/versions/${documentVersionId}`), {
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
    title: doc?.title ?? "隐私政策",
    description: `${APP.name} 隐私政策，说明我们如何收集、使用、存储与共享您的个人信息。`
  }
}

export default async function PrivacyVersionPage({ params }: Props) {
  const { documentVersionId } = await params
  const doc = await fetchLegalVersion(documentVersionId)

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
