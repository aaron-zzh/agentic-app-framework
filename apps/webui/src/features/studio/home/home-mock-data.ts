/**
 * AAF-125 示例数据，接口就绪后删除。
 *
 * "智能体运行统计"暂无后端接口（待 AAF-115 按日聚合的执行统计接口），
 * 这里提供与真实 Query hook 同形状的函数，接口就绪时只替换 hook，不改面板组件。
 * 中央画布的示例数据见 canvas/canvas-mock-data.ts。
 *
 * @example
 * const { data, isLoading, isError, refetch } = useAgentRunStatsMock()
 * @author AaronZZH & Kiro
 */

import { format, subDays } from "date-fns"
import { useMemo } from "react"

/** 每日智能体执行统计，date 为 yyyy-MM-dd */
export interface AgentRunStatPoint {
  date: string
  success: number
  failed: number
}

/** 与 TanStack Query 结果对齐的最小形状，便于替换为真实 hook */
export interface MockQueryResult<T> {
  data: T | undefined
  isLoading: boolean
  isError: boolean
  refetch: () => Promise<unknown>
}

/** 近 7 天成功/失败次数（按时间升序） */
const AGENT_RUN_COUNTS: ReadonlyArray<readonly [success: number, failed: number]> = [
  [12, 1],
  [18, 2],
  [9, 0],
  [21, 3],
  [16, 1],
  [24, 2],
  [11, 1]
]

const resolved = (): Promise<void> => Promise.resolve()

/** 示例：近 7 天智能体运行统计，日期以今天为最后一天 */
export function useAgentRunStatsMock(): MockQueryResult<AgentRunStatPoint[]> {
  const data = useMemo(() => {
    const today = new Date()
    const lastIndex = AGENT_RUN_COUNTS.length - 1
    return AGENT_RUN_COUNTS.map(([success, failed], index) => ({
      date: format(subDays(today, lastIndex - index), "yyyy-MM-dd"),
      success,
      failed
    }))
  }, [])
  return { data, isLoading: false, isError: false, refetch: resolved }
}
