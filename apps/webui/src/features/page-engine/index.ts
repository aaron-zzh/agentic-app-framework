/**
 * PageEngine 公开 API
 * @author AaronZZH & Kiro
 */

export type { AnimationType } from "./hooks/use-scroll-animation"
export { useScrollAnimation } from "./hooks/use-scroll-animation"
export { PageEngine } from "./PageEngine"
export { generatePageMetadata, PageJsonLd } from "./PageSEO"
export { getAllSectionTypes, getSectionComponent, registerSectionType } from "./registry"
export { SectionWrapper } from "./SectionWrapper"
// 导入 sections 触发注册
export * from "./sections"
export type {
  PageDef,
  PageDefRecord,
  PageMetadata,
  PageTheme,
  SectionComponentProps,
  SectionDef,
  SectionRegistryEntry,
  SectionStyle
} from "./types"
