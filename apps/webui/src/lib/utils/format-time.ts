/**
 * 格式化时间戳为相对时间或绝对时间
 * @param isoString ISO 8601 时间字符串
 * @returns 格式化后的时间字符串
 */
export function formatMessageTime(isoString: string): string {
  const now = new Date()
  const target = new Date(isoString)
  const diffMs = now.getTime() - target.getTime()
  const diffMin = Math.floor(diffMs / 60000)

  // 1分钟内：刚刚
  if (diffMin < 1) return "刚刚"

  // 1小时内：N分钟前
  if (diffMin < 60) return `${diffMin}分钟前`

  // 24小时内：N小时前
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}小时前`

  // 7天内：N天前
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 7) return `${diffDay}天前`

  // 超过7天：显示具体日期
  const month = target.getMonth() + 1
  const day = target.getDate()
  const hour = target.getHours().toString().padStart(2, "0")
  const minute = target.getMinutes().toString().padStart(2, "0")

  // 同年：月-日 时:分
  if (target.getFullYear() === now.getFullYear()) {
    return `${month}-${day} ${hour}:${minute}`
  }

  // 跨年：年-月-日 时:分
  return `${target.getFullYear()}-${month}-${day} ${hour}:${minute}`
}
