/**
 * 访客匿名 ID 工具——localStorage 持久 UUID。
 *
 * 同一浏览器同一设备的访客在不同动作（chat 续聊、邮箱订阅、反馈、联系我们）之间共享。
 * 跨设备/清浏览器数据后会重置——这是匿名体系的固有限制。
 *
 * @author AaronZZH & Kiro
 */

const STORAGE_KEY = "aaf-anonymous-id"

/**
 * 获取或创建访客匿名 ID。
 *
 * SSR 环境（无 window）返回空字符串，调用方应在 useEffect 中使用以避免 hydration 不一致。
 */
export function getOrCreateAnonymousId(): string {
  if (typeof window === "undefined") return ""
  let id = window.localStorage.getItem(STORAGE_KEY)
  if (!id) {
    id = generateUuid()
    window.localStorage.setItem(STORAGE_KEY, id)
  }
  return id
}

/** 仅读取（不创建），用于条件判断。 */
export function readAnonymousId(): string | null {
  if (typeof window === "undefined") return null
  return window.localStorage.getItem(STORAGE_KEY)
}

/** 清除访客 ID（开发调试或访客转正时可能用到）。 */
export function clearAnonymousId(): void {
  if (typeof window === "undefined") return
  window.localStorage.removeItem(STORAGE_KEY)
}

/** 生成 UUID v4，优先用 crypto.randomUUID，回退到手写实现以兼容旧浏览器。 */
function generateUuid(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID()
  }
  // RFC 4122 v4 fallback
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === "x" ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
