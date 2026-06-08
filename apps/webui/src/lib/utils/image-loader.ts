/**
 * Next.js 自定义图片 Loader
 *
 * 【问题】默认 next/image 经服务端 /_next/image 代理回源，回源请求无 Referer，
 * 被 ESA/CDN 防盗链规则拦截返回 403。
 *
 * 【方案】配置此 loader 后，next/image 直接返回 CDN URL 给浏览器，
 * 浏览器直连 CDN 并携带 Referer，防盗链校验通过。
 * 额外收益：节省服务器 CPU 和带宽，图片优化由 OSS x-oss-process 接管。
 *
 * 【兼容性】本地图片（路径不含 http）原样返回，本地开发正常。
 *
 * 注意：修改此文件需重启开发服务器。
 */

interface LoaderParams {
  src: string
  width: number
  quality?: number
}

export default function cdnImageLoader({ src, width, quality }: LoaderParams): string {
  // 已有完整 URL（CDN 图片）直接返回，可附加 OSS 图片处理参数
  if (src.startsWith("http")) {
    const q = quality ?? 75
    // OSS 图片处理：按宽度缩放
    if (src.includes(".aliyuncs.com") || src.includes("aaronzzh.cn")) {
      return `${src}?x-oss-process=image/resize,m_lfit,w_${width}/quality,q_${q}`
    }
    return src
  }
  // 本地图片（无 NEXT_PUBLIC_ASSETS_URL）原样返回
  return src
}
