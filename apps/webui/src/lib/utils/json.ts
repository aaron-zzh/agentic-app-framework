/**
 * JSON 安全解析工具
 * @author AaronZZH & Kiro
 */

/**
 * 安全解析 JSON 字符串，解析失败时返回 fallback 值
 */
export function safeJsonParse<T>(str: string | null | undefined, fallback: T): T {
  if (!str) return fallback
  try {
    return JSON.parse(str) as T
  } catch {
    return fallback
  }
}
