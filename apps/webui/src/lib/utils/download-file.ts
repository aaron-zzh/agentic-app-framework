import { toast } from "sonner"
import { API_BASE_URL } from "@/lib/api/config"
import { backendClient } from "@/lib/api/rest/backend-client"

/** 判断下载地址是否属于当前 AAF 后端 API，避免向外部地址发送认证信息 */
function isBackendApiUrl(url: string): boolean {
  if (typeof window === "undefined") return false

  try {
    const backendBaseUrl = new URL(API_BASE_URL, window.location.origin)
    const targetUrl = new URL(url, window.location.origin)
    const backendPath = backendBaseUrl.pathname.replace(/\/+$/, "")
    return (
      targetUrl.origin === backendBaseUrl.origin &&
      (targetUrl.pathname === backendPath || targetUrl.pathname.startsWith(`${backendPath}/`))
    )
  } catch {
    return false
  }
}

/** 获取下载文件；后端受保护资源复用统一认证客户端 */
async function fetchDownloadBlob(url: string): Promise<Blob> {
  if (isBackendApiUrl(url)) {
    const response = await backendClient.get<Blob>(url, {
      responseType: "blob",
      showError: false
    })
    return response.data
  }

  const response = await fetch(url)
  if (!response.ok) throw new Error(`下载失败: ${response.status}`)
  return response.blob()
}

/** 触发浏览器保存 blob 为文件，并释放临时 URL */
export function saveBlob(blob: Blob, filename: string): void {
  const a = document.createElement("a")
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}

/**
 * 下载远程文件到本地。
 * 通过 fetch 拉取 blob 后触发下载，避免同域大文件在新标签打开而非下载；
 * 跨域 fetch 失败（如 OSS 未配置 CORS）时降级为 window.open 直接打开。
 */
export async function downloadFile(url: string, filename: string): Promise<void> {
  try {
    const blob = await fetchDownloadBlob(url)
    saveBlob(blob, filename)
  } catch {
    if (!isBackendApiUrl(url)) window.open(url, "_blank")
  }
}

/** 带 toast 提示的下载，用于用户主动点击下载按钮的场景 */
export function downloadFileWithToast(url: string, filename: string): void {
  toast.promise(fetchDownloadBlob(url).then((blob) => saveBlob(blob, filename)), {
    loading: "下载中...",
    success: "下载完成",
    error: "下载失败"
  })
}
