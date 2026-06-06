/**
 * AAF 共享 Tailwind 配置入口
 *
 * Tailwind v4 使用 CSS-first 配置，主题 token 定义在 global.css 的 @theme inline 中。
 * 本包导出 token 常量供 JS 侧引用（动态样式、主题计算等场景）。
 *
 * @author AaronZZH & Kiro
 */
export { fontFamily, layout, radius } from "./preset"
