/**
 * 动态营销页路由——根据 slug 加载 PageDef 并渲染
 * @author AaronZZH & Kiro
 */

import type { Metadata } from "next"
import { notFound } from "next/navigation"

import { PageEngine } from "@/features/page-engine"
import { pageDefApi } from "@/lib/api/page-def"

interface Props {
  params: Promise<{ slug: string[] }>
}

/** 生成页面元数据（SEO） */
export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params
  const path = slug.join("/")
  try {
    const record = await pageDefApi.getBySlug(path)
    const meta = record.config.metadata
    return {
      title: meta?.title ?? record.title,
      description: meta?.description,
      keywords: meta?.keywords
    }
  } catch {
    return { title: "页面未找到" }
  }
}

export default async function DynamicMarketingPage({ params }: Props) {
  const { slug } = await params
  const path = slug.join("/")

  let record: Awaited<ReturnType<typeof pageDefApi.getBySlug>>
  try {
    record = await pageDefApi.getBySlug(path)
  } catch {
    notFound()
  }

  return <PageEngine page={record.config} />
}
