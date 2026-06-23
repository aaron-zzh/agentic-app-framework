/**
 * 产品首页——使用 PageEngine 渲染 aaf-landing 配置
 *
 * @author AaronZZH & Kiro
 */

import type { Metadata } from "next"

import { PageEngine } from "@/features/page-engine"
import { generatePageMetadata, PageJsonLd } from "@/features/page-engine/PageSEO"
import { aafLandingPageDef } from "@/features/page-engine/presets/aaf-landing"

export const metadata: Metadata = generatePageMetadata(aafLandingPageDef)

export default function HomePage() {
  return (
    <>
      <PageJsonLd page={aafLandingPageDef} />
      <PageEngine page={aafLandingPageDef} />
    </>
  )
}
