/**
 * 文档公开阅读页（SSR）
 *
 * 数据：访问 /api/public/docs/{id} 只读取已发布文档的精确不可变版本正文，不读取 currentVersionId 对应的未发布草稿。
 * 不缓存，取消发布或切换发布版本后立即生效，避免继续展示已撤回的正文。
 * 未发布或不存在的文档由公开接口统一返回 404。
 * 复用 <LegalDocLayout> 共享布局——保证项目内长文阅读视觉一致。
 *
 * @author AaronZZH & Kiro
 */
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { LegalDocLayout } from "@/components/common/LegalDocLayout"
import { buildApiUrl } from "@/lib/api/config"

interface PublicDocument {
  id: number
  documentVersionId: number
  title: string
  content: string
  docType: string
  updateTime: string
}

interface Props {
  params: Promise<{ id: string }>
}

async function fetchDoc(id: string): Promise<PublicDocument | null> {
  try {
    const res = await fetch(buildApiUrl(`/public/docs/${id}`), {
      cache: "no-store"
    })
    if (!res.ok) return null
    const json = (await res.json()) as { data: PublicDocument }
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

  if (!doc) notFound()

  return (
    <LegalDocLayout
      title={doc.title}
      content={doc.content ?? ""}
      category={doc.docType}
      updateTime={doc.updateTime}
    />
  )
}
