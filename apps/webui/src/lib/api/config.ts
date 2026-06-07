/**
 * 后端 API 地址规则。
 *
 * 前端不依赖 Next 代理，默认直连本地 Spring Boot： http://localhost:8080/api。
 * 部署环境通过 NEXT_PUBLIC_API_URL 覆盖；可配置到服务根地址或 /api 前缀地址。
 */
const DEFAULT_API_BASE_URL = "http://localhost:8080/api"

function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, "")
}

function normalizeBaseUrl(value: string): string {
  const base = trimTrailingSlash(value)
  return base.endsWith("/api") ? base : `${base}/api`
}

function normalizePath(path: string): string {
  if (/^https?:\/\//.test(path)) return path
  const withSlash = path.startsWith("/") ? path : `/${path}`
  return withSlash.startsWith("/api/") ? withSlash.slice(4) : withSlash
}

export const API_BASE_URL = normalizeBaseUrl(
  process.env.NEXT_PUBLIC_API_URL ?? DEFAULT_API_BASE_URL
)

export const API_ORIGIN = API_BASE_URL.endsWith("/api") ? API_BASE_URL.slice(0, -4) : API_BASE_URL

export function buildApiUrl(path: string): string {
  if (/^https?:\/\//.test(path)) return path
  return `${API_BASE_URL}${normalizePath(path)}`
}

/** 构建 WebSocket 地址（将 http/https 替换为 ws/wss） */
export function buildWsUrl(path: string): string {
  const wsOrigin = API_ORIGIN.replace(/^http/, "ws")
  return `${wsOrigin}${path.startsWith("/") ? path : `/${path}`}`
}
