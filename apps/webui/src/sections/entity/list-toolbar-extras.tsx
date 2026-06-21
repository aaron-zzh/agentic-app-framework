/**
 * 实体列表工具栏的"额外操作"注册表
 *
 * <p>用于在 {@link import("./view/entity-list-view").EntityListView} 的面包屑右侧
 * 注入特定实体的自定义操作按钮（例如：兑换码模块需要的 "+ 生成兑换码" 走特殊端点）。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * export const listToolbarExtras: Record<string, () => ReactNode> = {
 *   "credit-redeem-code": () => <RedeemCodeGenerateButton />
 * }
 * }</pre>
 *
 * <p>选用注册表而非塞进 EntityDef，是因为 EntityDef 应保持序列化友好（未来由后端下发），
 * 而 React 组件无法序列化。
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import { RedeemCodeGenerateButton } from "@/features/billing/components/RedeemCodeGenerateButton"

/** 列表工具栏额外操作映射：实体 slug → 渲染函数 */
export const listToolbarExtras: Record<string, () => ReactNode> = {
  "credit-redeem-code": () => <RedeemCodeGenerateButton />
}

/** 获取指定实体的额外操作节点；不存在返回 null */
export function getListToolbarExtra(slug: string): ReactNode | null {
  const factory = listToolbarExtras[slug]
  return factory ? factory() : null
}
