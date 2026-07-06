import { toast } from "sonner"

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
    const res = await fetch(url)
    if (!res.ok) throw new Error(`下载失败: ${res.status}`)
    const blob = await res.blob()
    saveBlob(blob, filename)
  } catch {
    window.open(url, "_blank")
  }
}

/** 带 toast 提示的下载，用于用户主动点击下载按钮的场景 */
export function downloadFileWithToast(url: string, filename: string): void {
  toast.promise(
    fetch(url)
      .then((res) => {
        if (!res.ok) throw new Error(`下载失败: ${res.status}`)
        return res.blob()
      })
      .then((blob) => saveBlob(blob, filename)),
    { loading: "下载中...", success: "下载完成", error: "下载失败" }
  )
}
