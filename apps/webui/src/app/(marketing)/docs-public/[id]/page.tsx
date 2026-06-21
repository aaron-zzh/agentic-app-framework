/**
 * 文档公开阅读页（SSR + ISR）
 *
 * 仅展示 publish=published 的文档，未发布文档返回 404。
 * 复用 <LegalDocLayout> 共享布局——保证项目内长文阅读视觉一致。
 *
 * @author AaronZZH & Kiro
 */
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { LegalDocLayout } from "@/components/common/LegalDocLayout"
import { buildApiUrl } from "@/lib/api/config"
import type { Document } from "@/lib/types/document"

interface Props {
  params: Promise<{ id: string }>
}

async function fetchDoc(id: string): Promise<Document | null> {
  try {
    const res = await fetch(buildApiUrl(`/docs/${id}`), {
      next: { revalidate: 60 } // ISR：60 秒重新验证
    })
    if (!res.ok) return null
    const json = (await res.json()) as { data: Document }
    return json.data
  } catch {
    return null
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { id } = await params
  const doc = await fetchDoc(id)
  return { title: doc?.title ?? "文档" }
}

export default async function DocPublicPage({ params }: Props) {
  const { id } = await params
  const doc = await fetchDoc(id)

  if (!doc || doc.publish !== "published") notFound()

  return (
    <LegalDocLayout
      title={doc.title}
      content={doc.content ?? ""}
      category={doc.docType}
      updateTime={doc.updateTime}
    />
  )
}
