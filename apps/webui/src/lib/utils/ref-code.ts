/**
 * 邀请码（refCode）持久化工具——sessionStorage 存活，关闭浏览器即清。
 *
 * <p>用户从邀请链接 `${origin}/?refCode=AAF-XXXXX` 进入站点时，落地的可能是首页或任意 marketing 页，
 * 等真正去到注册页可能已经离开当前 URL，所以必须先把 ?refCode= 写到 sessionStorage，注册时再读回。
 *
 * @author AaronZZH & Kiro
 */

const STORAGE_KEY = "aaf:refCode"

/** SSR 安全的 sessionStorage 访问（无 window 时直接返回 null） */
function getStorage(): Storage | null {
  if (typeof window === "undefined") return null
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

/**
 * 从当前 URL 捕获 ?refCode=...，存入 sessionStorage。
 *
 * <p>建议在根 layout 的客户端首次挂载时调用一次。已存在则跳过覆盖（避免后续刷新带空 refCode 把已存的覆盖掉）。
 */
export function captureRefCodeFromUrl(): void {
  const storage = getStorage()
  if (!storage) return
  if (typeof window === "undefined") return
  try {
    const url = new URL(window.location.href)
    const code = url.searchParams.get("refCode")
    if (!code) return
    const trimmed = code.trim()
    if (!trimmed) return
    // 仅当首次捕获或上次值不同 时写入
    const prev = storage.getItem(STORAGE_KEY)
    if (prev === trimmed) return
    storage.setItem(STORAGE_KEY, trimmed)
  } catch {
    // 隐身模式 / 跨域 storage 拒绝时静默
  }
}

/** 读取已捕获的邀请码，未捕获返回 undefined */
export function readRefCode(): string | undefined {
  const storage = getStorage()
  if (!storage) return undefined
  try {
    const code = storage.getItem(STORAGE_KEY)
    return code?.trim() ? code : undefined
  } catch {
    return undefined
  }
}

/** 清除邀请码（注册成功后调用，避免下一次注册再被关联） */
export function clearRefCode(): void {
  const storage = getStorage()
  if (!storage) return
  try {
    storage.removeItem(STORAGE_KEY)
  } catch {
    // 静默
  }
}
