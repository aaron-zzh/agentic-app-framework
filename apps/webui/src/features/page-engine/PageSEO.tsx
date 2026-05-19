/**
 * PageSEO——根据 PageDef 自动生成 Next.js Metadata + JSON-LD 结构化数据
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * // 在 page.tsx 中使用
 * export const metadata = generatePageMetadata(aafLandingPageDef)
 *
 * // 在页面组件中渲染 JSON-LD
 * <PageJsonLd page={aafLandingPageDef} />
 * ```
 */

import type { Metadata } from "next"

import type { PageDef } from "./types"

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://aaf.xuejiai.com"

/** 根据 PageDef 生成 Next.js Metadata 对象 */
export function generatePageMetadata(page: PageDef): Metadata {
  const meta = page.metadata
  return {
    title: meta?.title ?? page.title,
    description: meta?.description,
    keywords: meta?.keywords,
    openGraph: {
      title: meta?.title ?? page.title,
      description: meta?.description,
      url: `${SITE_URL}/${page.slug === "home" ? "" : page.slug}`,
      siteName: "AAF",
      type: "website",
      ...(meta?.ogImage && { images: [{ url: meta.ogImage, width: 1200, height: 630 }] })
    },
    twitter: {
      card: "summary_large_image",
      title: meta?.title ?? page.title,
      description: meta?.description
    },
    alternates: {
      canonical: `${SITE_URL}/${page.slug === "home" ? "" : page.slug}`
    }
  }
}

/** JSON-LD 结构化数据——WebSite + SoftwareApplication */
interface PageJsonLdProps {
  page: PageDef
}

/** 渲染 JSON-LD script 标签 */
export function PageJsonLd({ page }: PageJsonLdProps) {
  const jsonLd = {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "WebSite",
        name: "AAF",
        url: SITE_URL,
        description: page.metadata?.description
      },
      {
        "@type": "SoftwareApplication",
        name: "AAF",
        applicationCategory: "DeveloperApplication",
        operatingSystem: "Web",
        description: page.metadata?.description,
        url: SITE_URL,
        offers: {
          "@type": "AggregateOffer",
          lowPrice: "0",
          priceCurrency: "CNY",
          offerCount: 3
        }
      }
    ]
  }

  return (
    <script
      type="application/ld+json"
      // biome-ignore lint/security/noDangerouslySetInnerHtml: JSON-LD 需要 dangerouslySetInnerHTML
      dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
    />
  )
}
