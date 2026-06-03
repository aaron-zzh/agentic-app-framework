/**
 * 匿名访客会话 ID 持久化
 * 存入 localStorage，刷新后可恢复聊天历史
 */

const GUEST_THREAD_KEY = "aaf-guest-thread-id"

/** 获取访客 threadId（不存在返回 null） */
export function getGuestThreadId(): string | null {
  if (typeof window === "undefined") return null
  return localStorage.getItem(GUEST_THREAD_KEY)
}

/** 确保访客 threadId 存在，不存在则生成并持久化 */
export function ensureGuestThreadId(): string {
  if (typeof window === "undefined") return crypto.randomUUID()
  let id = localStorage.getItem(GUEST_THREAD_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(GUEST_THREAD_KEY, id)
  }
  return id
}

/** 清除访客 threadId（登录后合并时使用） */
export function clearGuestThreadId(): void {
  if (typeof window !== "undefined") {
    localStorage.removeItem(GUEST_THREAD_KEY)
  }
}
