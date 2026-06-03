import tunnel from "tunnel-rat"

/**
 * 创建一个隧道，用于在 React 组件树的不同位置之间传递元素
 *
 * 使用方式：
 * - <t.In>内容</t.In> - 发送内容到隧道
 * - <t.Out /> - 在其他地方渲染隧道内容
 *
 * 主要用于：
 * - 在 3D 场景中渲染 2D UI 元素
 * - 跨组件层级传递 JSX 而无需 props drilling
 * - 替代 React Portal 的更灵活方案
 */
export const t = tunnel()
