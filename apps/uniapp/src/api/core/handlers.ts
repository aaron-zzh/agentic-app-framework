import type { Method } from 'alova'
import router from '@/router'

/** API 业务错误 */
export class ApiError extends Error {
  constructor(
    message: string,
    public code: number,
    public data?: unknown,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/** AAF 统一响应格式 */
interface ApiResponse<T = unknown> {
  code: number
  message?: string
  data: T
}

/** 处理 401：清除 token 并跳登录页 */
function handleUnauthorized(): void {
  useUserStore().logout()
  const toast = useGlobalToast()
  toast.error({ msg: '登录已过期，请重新登录', duration: 500 })
  setTimeout(() => router.replaceAll({ name: 'login' }), 500)
}

/** 成功响应处理：解包 { code, data, message } */
export async function handleAlovaResponse(
  response: UniApp.RequestSuccessCallbackResult | UniApp.UploadFileSuccessCallbackResult | UniApp.DownloadSuccessData,
): Promise<unknown> {
  const { statusCode, data } = response as UniNamespace.RequestSuccessCallbackResult

  if (statusCode === 401) {
    handleUnauthorized()
    throw new ApiError('登录已过期', 401, data)
  }

  if (statusCode >= 400) {
    throw new ApiError(`请求失败 (${statusCode})`, statusCode, data)
  }

  const json = data as ApiResponse
  // 业务错误码（非 0 视为失败）
  if (json.code !== 0 && json.code !== 200) {
    const msg = json.message ?? '请求失败'
    useGlobalToast().error(msg)
    throw new ApiError(msg, json.code, json.data)
  }

  // 解包：直接返回 data 字段
  return json.data
}

/** 错误响应处理 */
export function handleAlovaError(error: unknown, _method: Method): never {
  if (error instanceof ApiError && (error.code === 401)) {
    handleUnauthorized()
  }
  else if ((error as Error).name === 'NetworkError') {
    useGlobalToast().error('网络错误，请检查网络连接')
  }
  else if ((error as Error).name === 'TimeoutError') {
    useGlobalToast().error('请求超时，请重试')
  }
  else if (!(error instanceof ApiError)) {
    useGlobalToast().error('请求失败，请稍后重试')
  }

  throw error
}
