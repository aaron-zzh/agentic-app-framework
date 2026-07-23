/**
 * 列表排序协议：在 URL field:asc|desc 与 TanStack 排序状态之间转换
 */

import type { SortingState, Updater } from "@tanstack/react-table"

const SORT_ITEM_PATTERN = /^([A-Za-z][A-Za-z0-9_]*):(asc|desc)$/
const INVALID_SORT_MESSAGE = "排序格式非法，应为 field:asc|desc（多字段用逗号分隔）"

/** 将 URL 排序参数严格解析为 TanStack 排序状态。 */
export function parseSortParam(value?: string): SortingState {
  if (value === undefined) return []

  const fields = new Set<string>()
  return value.split(",").map((item) => {
    const match = SORT_ITEM_PATTERN.exec(item)
    if (!match || fields.has(match[1])) throw new Error(INVALID_SORT_MESSAGE)
    fields.add(match[1])
    return { id: match[1], desc: match[2] === "desc" }
  })
}

/** 将 TanStack 排序状态序列化为后端查询协议。 */
export function serializeSorting(sorting: SortingState): string | undefined {
  if (!sorting.length) return undefined

  const fields = new Set<string>()
  return sorting
    .map(({ id, desc }) => {
      if (!SORT_ITEM_PATTERN.test(`${id}:asc`) || fields.has(id)) {
        throw new Error(INVALID_SORT_MESSAGE)
      }
      fields.add(id)
      return `${id}:${desc ? "desc" : "asc"}`
    })
    .join(",")
}

/** 解析 TanStack 的值式或函数式排序更新。 */
export function resolveSortingUpdater(
  updater: Updater<SortingState>,
  previous: SortingState
): SortingState {
  return typeof updater === "function" ? updater(previous) : updater
}
