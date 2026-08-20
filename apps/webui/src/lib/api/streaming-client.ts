/**
 * 后端流式请求客户端：保留 fetch ReadableStream，并统一注入认证与租户上下文。
 *
 * @example
 * const response = await backendStreamFetch("/agui/run", {
 *   method: "POST",
 *   body: JSON.stringify(request)
 * })
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "./config"
import { getBackendRequestContextHeaders } from "./rest/backend-client"

/** 发起需要读取原始响应流的后端请求。调用方 Header 可覆盖公共上下文默认值。 */
export function backendStreamFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(getBackendRequestContextHeaders())
  new Headers(init.headers).forEach((value, name) => {
    headers.set(name, value)
  })

  return fetch(buildApiUrl(path), {
    ...init,
    headers,
    credentials: init.credentials ?? "include"
  })
}
