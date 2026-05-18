/**
 * 时间工具函数
 * @author AaronZZH & Kiro
 */

/**
 * 将时间戳/日期字符串转为中文相对时间描述
 * @example formatTimeAgo("2026-05-18T10:00:00Z") → "3 小时前"
 */
export function formatTimeAgo(date: string | number | Date): string {
  const diff = Date.now() - new Date(date).getTime()
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) return "刚刚"
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months} 个月前`
  return `${Math.floor(months / 12)} 年前`
}
