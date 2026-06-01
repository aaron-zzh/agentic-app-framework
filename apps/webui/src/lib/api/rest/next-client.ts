/**
 * Next.js Route Handler API 客户端。
 */

import { ApiClient } from "../api-client"
import type { ApiRequestConfig } from "../types"

export const nextApi = new ApiClient("/api")

export function nextRequest<T>(path: string, config: ApiRequestConfig = {}): Promise<T> {
  return nextApi.request<T>({
    url: path.startsWith("/api/") ? path.slice(4) : path,
    ...config
  })
}
