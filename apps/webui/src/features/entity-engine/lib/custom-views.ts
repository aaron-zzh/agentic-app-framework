/**
 * 声明式自定义视图辅助——内置视图 key 保护与按 scope 过滤
 * @author AaronZZH & Kiro
 */

import type { CustomViewDef, EntityDef } from "../types"

/** 内置视图 key；自定义视图不得占用 */
export const BUILTIN_VIEW_KEYS: ReadonlySet<string> = new Set([
  "list",
  "form",
  "kanban",
  "pivot",
  "calendar",
  "canvas"
])

/** 取实体指定 scope 的有效自定义视图（过滤掉占用内置 key 的声明）。 */
export function getCustomViews(
  entity: Pick<EntityDef, "customViews">,
  scope: CustomViewDef["scope"]
): CustomViewDef[] {
  return (entity.customViews ?? []).filter(
    (view) => view.scope === scope && !BUILTIN_VIEW_KEYS.has(view.key)
  )
}
