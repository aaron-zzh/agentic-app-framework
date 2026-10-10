/**
 * 乐观锁版本辅助——以详情记录的 version 覆盖表单提交中的 expectedVersion
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```ts
 * const payload = withExpectedVersion(detail, values)
 * if (!payload) return // 版本非法，需刷新详情后重试
 * ```
 */

/**
 * 构造携带 expectedVersion 的更新载荷。
 * - 详情不含 version 字段：原样返回表单值（该实体未启用乐观锁）
 * - version 非法（非非负整数）：返回 undefined，调用方应拒绝提交
 */
export function withExpectedVersion(
  detail: Record<string, unknown> | null | undefined,
  values: Record<string, unknown>
): Record<string, unknown> | undefined {
  if (!detail || !("version" in detail)) return values
  const version = detail.version
  if (typeof version !== "number" || !Number.isInteger(version) || version < 0) return undefined
  return { ...values, expectedVersion: version }
}
