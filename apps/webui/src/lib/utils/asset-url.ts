/**
 * 静态资源 URL 工具
 * 参考 kids-app url 工具，适配阿里云 OSS 图片处理
 *
 * 本地开发：NEXT_PUBLIC_ASSETS_URL 为空，走 /public 目录
 * 生产/OSS：NEXT_PUBLIC_ASSETS_URL=https://your-bucket.oss-cn-hangzhou.aliyuncs.com
 *
 * @example
 * import $url from "@/lib/utils/asset-url"
 * $url.cdn("/assets/models/DamagedHelmet.glb")
 * $url.thumb("/images/avatar.jpg", { width: 100, height: 100 })
 * $url.videoPoster("/videos/demo.mp4")
 */

const ASSETS_BASE = process.env.NEXT_PUBLIC_ASSETS_URL ?? ""

/**
 * 拼接 CDN/OSS 前缀
 * 已有 http 前缀的 URL 原样返回
 */
export function cdn(path: string, base = ASSETS_BASE): string {
  if (!path) return ""
  if (path.startsWith("http")) return path
  return `${base}${path}`
}

interface ThumbParams {
  width?: number
  height?: number
  /** 缩放模式：lfit（等比缩放）| mfit（等比填充）| fill（裁剪填充）| fixed（强制尺寸） */
  mode?: "lfit" | "mfit" | "fill" | "fixed"
  /** 压缩质量 1-100 */
  quality?: number
}

/**
 * 阿里云 OSS 图片缩略图
 * 本地环境（无 ASSETS_BASE）原样返回
 */
export function thumbUrl(path: string, params: ThumbParams = {}): string {
  const url = cdn(path)
  if (!ASSETS_BASE) return url

  const { width = 200, height = 200, mode = "lfit", quality = 90 } = params
  let suffix = `x-oss-process=image/resize,m_${mode},w_${width},h_${height}`
  if (quality > 0 && quality < 100) suffix += `/quality,q_${quality}`
  return `${url}?${suffix}`
}

/**
 * 对完整图片 URL 追加阿里云 OSS 图片处理参数（缩略图）。
 * 适用于后端直接返回的 ossUrl / CDN URL 等动态素材地址。
 *
 * 通过 NEXT_PUBLIC_OSS_THUMB_DOMAIN 配置允许追加参数的域名（逗号分隔）。
 * 未配置时对所有 http 图片 URL 追加（适合单一 CDN 环境）。
 *
 * - URL 已有查询参数：追加 `&x-oss-process=...`
 * - URL 无查询参数：追加 `?x-oss-process=...`
 * - 空值：返回空字符串
 */
const OSS_THUMB_DOMAINS = (process.env.NEXT_PUBLIC_OSS_THUMB_DOMAIN ?? "")
  .split(",")
  .filter(Boolean)

export function ossThumb(url: string | null | undefined, params: ThumbParams = {}): string {
  if (!url) return ""

  // 配置了域名白名单时，只对匹配的域名追加参数
  if (OSS_THUMB_DOMAINS.length > 0 && !OSS_THUMB_DOMAINS.some((d) => url.includes(d))) return url

  const { width = 300, height = 300, mode = "fill", quality = 90 } = params
  let process = `x-oss-process=image/resize,m_${mode},w_${width},h_${height}`
  if (quality > 0 && quality < 100) process += `/quality,q_${quality}`

  return url.includes("?") ? `${url}&${process}` : `${url}?${process}`
}

/**
 * 阿里云 OSS 视频封面截帧
 * @param path 视频路径
 * @param timeMs 截帧时间（毫秒），默认 1000ms
 */
export function videoPosterUrl(path: string, timeMs = 1000): string {
  const url = cdn(path)
  if (!ASSETS_BASE) return ""
  return `${url}?x-oss-process=video/snapshot,t_${timeMs},f_jpg,w_0,h_0`
}
