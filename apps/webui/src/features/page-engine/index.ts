/**
 * PageEngine 公开 API
 * @author AaronZZH & Kiro
 */

export { PageEngine } from "./PageEngine"
export { SectionWrapper } from "./SectionWrapper"
export { registerSectionType, getSectionComponent, getAllSectionTypes } from "./registry"
export { generatePageMetadata, PageJsonLd } from "./PageSEO"
export { useScrollAnimation } from "./hooks/use-scroll-animation"
export type {
  PageDef,
  PageDefRecord,
  PageMetadata,
  PageTheme,
  SectionDef,
  SectionStyle,
  SectionComponentProps,
  SectionRegistryEntry
} from "./types"
export type { AnimationType } from "./hooks/use-scroll-animation"

// 导入 sections 触发注册
export * from "./sections"
