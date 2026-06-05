/**
 * 文档公开阅读页（SSR）
 * 仅展示 publish=published 的文档，未发布文档返回 404。
 * @author AaronZZH & Kiro
 */
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
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
    <article className="mx-auto max-w-3xl px-6 py-12">
      <header className="mb-8">
        <h1 className="font-bold text-3xl">{doc.title}</h1>
        <p className="mt-2 text-muted-foreground text-sm">
          {doc.docType} · 更新于{" "}
          {new Date(doc.updateTime).toLocaleDateString("zh-CN", {
            year: "numeric",
            month: "long",
            day: "numeric"
          })}
        </p>
      </header>
      <div className="prose prose-neutral dark:prose-invert max-w-none">
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{doc.content ?? ""}</ReactMarkdown>
      </div>
    </article>
  )
}
