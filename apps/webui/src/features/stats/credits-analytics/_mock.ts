/**
 * 积分消耗统计仪表盘模拟数据
 * @author Kiro
 */

/** 概览统计 */
export const MOCK_CREDITS_OVERVIEW = {
  /** 本月充值 */
  recharged: 50000,
  /** 本月消耗 */
  consumed: 32480,
  /** 当前余额 */
  balance: 17520,
  /** 消耗变化率（vs 上月） */
  consumedPercent: 12.4,
  /** 充值变化率（vs 上月） */
  rechargedPercent: -3.2
}

/** 消耗趋势数据（近30天） */
export const MOCK_TREND_DAILY = Array.from({ length: 30 }, (_, i) => {
  const date = new Date(2026, 4, i + 1)
  return {
    time: `${date.getMonth() + 1}/${date.getDate()}`,
    value: Math.floor(800 + Math.random() * 1200)
  }
})

/** 月度趋势（近12个月，多系列） */
export const MOCK_TREND_MONTHLY = {
  categories: [
    "1月",
    "2月",
    "3月",
    "4月",
    "5月",
    "6月",
    "7月",
    "8月",
    "9月",
    "10月",
    "11月",
    "12月"
  ],
  series: [
    {
      name: "对话消耗",
      data: [12000, 9800, 15200, 18400, 13600, 21000, 19500, 22000, 17800, 24500, 28000, 32480]
    },
    {
      name: "图片生成",
      data: [3200, 2800, 4100, 5200, 3900, 6200, 5800, 7100, 5400, 8200, 9500, 11200]
    },
    {
      name: "语音合成",
      data: [1800, 1500, 2300, 2800, 2200, 3400, 3100, 3800, 2900, 4100, 4800, 5600]
    }
  ]
}

/** 按服务类型消耗分布 */
export const MOCK_BY_SERVICE = [
  { name: "LLM 对话", value: 16480 },
  { name: "图片生成", value: 6800 },
  { name: "语音合成", value: 3200 },
  { name: "知识库检索", value: 2400 },
  { name: "工作流执行", value: 1800 },
  { name: "其他", value: 1800 }
]

/** 按部门消耗分布 */
export const MOCK_BY_DEPT = [
  { name: "研发部", value: 12400 },
  { name: "产品部", value: 8200 },
  { name: "运营部", value: 5600 },
  { name: "市场部", value: 3800 },
  { name: "其他", value: 2480 }
]

/** 最近消耗记录 */
export type CreditRecord = {
  id: string
  user: string
  dept: string
  service: string
  amount: number
  model: string
  status: "成功" | "失败" | "处理中"
  time: string
}

export const MOCK_RECENT_RECORDS: CreditRecord[] = [
  {
    id: "1",
    user: "张三",
    dept: "研发部",
    service: "LLM 对话",
    model: "GPT-4o",
    amount: 240,
    status: "成功",
    time: "2026-06-04 23:10"
  },
  {
    id: "2",
    user: "李四",
    dept: "产品部",
    service: "图片生成",
    model: "DALL-E 3",
    amount: 480,
    status: "成功",
    time: "2026-06-04 22:55"
  },
  {
    id: "3",
    user: "王五",
    dept: "运营部",
    service: "语音合成",
    model: "TTS-1",
    amount: 120,
    status: "成功",
    time: "2026-06-04 22:42"
  },
  {
    id: "4",
    user: "赵六",
    dept: "研发部",
    service: "LLM 对话",
    model: "Claude 3.5",
    amount: 360,
    status: "处理中",
    time: "2026-06-04 22:31"
  },
  {
    id: "5",
    user: "孙七",
    dept: "市场部",
    service: "工作流执行",
    model: "GPT-4o-mini",
    amount: 80,
    status: "成功",
    time: "2026-06-04 22:18"
  },
  {
    id: "6",
    user: "周八",
    dept: "产品部",
    service: "知识库检索",
    model: "Embedding-3",
    amount: 60,
    status: "失败",
    time: "2026-06-04 22:05"
  },
  {
    id: "7",
    user: "吴九",
    dept: "研发部",
    service: "图片生成",
    model: "Flux-1",
    amount: 320,
    status: "成功",
    time: "2026-06-04 21:50"
  },
  {
    id: "8",
    user: "郑十",
    dept: "运营部",
    service: "LLM 对话",
    model: "GPT-4o",
    amount: 180,
    status: "成功",
    time: "2026-06-04 21:35"
  }
]
