/**
 * 极简 RFC 6902 JSON Patch 应用（仅支持 add/replace/remove 单层路径）
 *
 * 不引入 fast-json-patch：该库虽已作为 @ag-ui/client 的间接依赖出现在 node_modules，
 * 但 pnpm 严格隔离下不应直接 import 未在 package.json 声明的幽灵依赖；AAF 后端
 * AguiStateConverter 产出的 patch 路径固定为 "/subTasks/xxx" 或 "/kind" 这类单层
 * JSON Pointer（不含数组下标/多层嵌套），实现一个覆盖这一子集的最小函数足够，
 * 不需要完整 RFC 6902 实现的复杂度。
 *
 * @author AaronZZH & Kiro
 */

export interface JsonPatchOperation {
  op: "add" | "replace" | "remove"
  path: string
  value?: unknown
}

/** JSON Pointer（RFC 6901）转义还原：~1 → /，~0 → ~ */
function unescapePointerSegment(segment: string): string {
  return segment.replace(/~1/g, "/").replace(/~0/g, "~")
}

/** 按 JSON Pointer 路径解析出末级 key 所在的容器对象，不存在的中间层自动建空对象 */
function resolveContainer(
  root: Record<string, unknown>,
  segments: string[]
): Record<string, unknown> {
  let current = root
  for (const segment of segments.slice(0, -1)) {
    const next = current[segment]
    if (typeof next !== "object" || next === null) {
      const created: Record<string, unknown> = {}
      current[segment] = created
      current = created
    } else {
      current = next as Record<string, unknown>
    }
  }
  return current
}

/**
 * 对一份 content 应用一组 JSON Patch 增量，返回新对象（不修改入参，保持不可变数据流约定）
 */
export function applyJsonPatch(
  content: Record<string, unknown>,
  operations: JsonPatchOperation[]
): Record<string, unknown> {
  const next: Record<string, unknown> = structuredClone(content)
  for (const operation of operations) {
    const segments = operation.path.split("/").slice(1).map(unescapePointerSegment)
    if (segments.length === 0) continue
    const container = resolveContainer(next, segments)
    const key = segments.at(-1) as string
    if (operation.op === "remove") {
      delete container[key]
    } else {
      container[key] = operation.value
    }
  }
  return next
}
