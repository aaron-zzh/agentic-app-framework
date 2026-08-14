const SYSTEM_VALUE_LABELS: Record<string, string> = {
  $now: "当前时间",
  $todayStart: "今日开始",
  $tomorrowStart: "明日开始",
  $nowPlus3Days: "三天后"
}

/** 将筛选 DSL 值转换为仅供界面摘要使用的可读文本。 */
export function formatFilterValue(value: string): string {
  return SYSTEM_VALUE_LABELS[value] ?? value
}
