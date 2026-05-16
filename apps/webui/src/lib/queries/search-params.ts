/**
 * URL 状态管理——基于 nuqs 的类型安全 URL 参数
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * const [params, setParams] = useEntitySearchParams()
 * // params.view / params.page / params.sort / params.search
 * ```
 */

import {
  createSearchParamsCache,
  parseAsInteger,
  parseAsString,
  parseAsStringLiteral
} from "nuqs/server"

/** 支持的视图类型 */
const viewTypes = ["list", "kanban", "form", "graph", "chart", "calendar"] as const

/** URL 参数解析器定义 */
export const searchParamsParsers = {
  view: parseAsStringLiteral(viewTypes).withDefault("list"),
  page: parseAsInteger.withDefault(1),
  pageSize: parseAsInteger.withDefault(20),
  sort: parseAsString,
  search: parseAsString
}

/** 服务端参数缓存（用于 Server Component） */
export const searchParamsCache = createSearchParamsCache(searchParamsParsers)
