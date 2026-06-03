/**
 * 预定义 Section 组件——barrel export + 注册到 sectionComponents 注册表
 * @author AaronZZH & Kiro
 *
 * @example
 * ```ts
 * // 在应用入口导入以触发注册
 * import "@/features/page-engine/sections"
 * ```
 */

import type { ComponentType } from "react"

import { registerSectionType } from "../registry"
import type { SectionComponentProps } from "../types"

import { CTASection } from "./CTASection"
import { FAQSection } from "./FAQSection"
import { FeaturesSection } from "./FeaturesSection"
import { FooterSection } from "./FooterSection"
import { HeroSection } from "./HeroSection"
import { LogosSection } from "./LogosSection"
import { NavbarSection } from "./NavbarSection"
import { PricingSection } from "./PricingSection"
import { ShowcaseSection } from "./ShowcaseSection"
import { StatsSection } from "./StatsSection"
import { TestimonialsSection } from "./TestimonialsSection"
import { ZustandThreeSection } from "./ZustandThreeSection/ZustandThreeSection"

// ─── 注册所有预定义 Section ──────────────────────────────────────────────────

registerSectionType("navbar", {
  component: NavbarSection as ComponentType<SectionComponentProps>,
  label: "顶部导航",
  icon: "Menu"
})

registerSectionType("footer", {
  component: FooterSection as ComponentType<SectionComponentProps>,
  label: "页脚",
  icon: "PanelBottom"
})

registerSectionType("hero", {
  component: HeroSection as ComponentType<SectionComponentProps>,
  label: "Hero 横幅",
  icon: "Sparkles"
})

registerSectionType("features", {
  component: FeaturesSection as ComponentType<SectionComponentProps>,
  label: "功能亮点",
  icon: "LayoutGrid"
})

registerSectionType("pricing", {
  component: PricingSection as ComponentType<SectionComponentProps>,
  label: "定价方案",
  icon: "CreditCard"
})

registerSectionType("showcase", {
  component: ShowcaseSection as ComponentType<SectionComponentProps>,
  label: "产品展示",
  icon: "Monitor"
})

registerSectionType("zustand-three", {
  component: ZustandThreeSection as ComponentType<SectionComponentProps>,
  label: "Zustand + Three.js",
  icon: "Boxes"
})

registerSectionType("stats", {
  component: StatsSection as ComponentType<SectionComponentProps>,
  label: "数据统计",
  icon: "BarChart3"
})

registerSectionType("faq", {
  component: FAQSection as ComponentType<SectionComponentProps>,
  label: "常见问题",
  icon: "HelpCircle"
})

registerSectionType("cta", {
  component: CTASection as ComponentType<SectionComponentProps>,
  label: "行动号召",
  icon: "Megaphone"
})

registerSectionType("testimonials", {
  component: TestimonialsSection as ComponentType<SectionComponentProps>,
  label: "用户评价",
  icon: "MessageSquareQuote"
})

registerSectionType("logos", {
  component: LogosSection as ComponentType<SectionComponentProps>,
  label: "Logo 墙",
  icon: "Building2"
})

// ─── 导出组件 ────────────────────────────────────────────────────────────────

export { CTASection } from "./CTASection"
export { FAQSection } from "./FAQSection"
export { FeaturesSection } from "./FeaturesSection"
export { FooterSection } from "./FooterSection"
export { HeroSection } from "./HeroSection"
export { LogosSection } from "./LogosSection"
export { NavbarSection } from "./NavbarSection"
export { PricingSection } from "./PricingSection"
export { ShowcaseSection } from "./ShowcaseSection"
export { StatsSection } from "./StatsSection"
export { TestimonialsSection } from "./TestimonialsSection"
export { ZustandThreeSection } from "./ZustandThreeSection/ZustandThreeSection"
