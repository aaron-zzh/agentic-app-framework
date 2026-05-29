/**
 * 看板统计——累积流图、周期时间、吞吐量、阻塞分析
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts"

interface KanbanStatsProps {
  /** 历史快照数据：每个时间点各状态的数量 */
  snapshots?: { date: string; [status: string]: string | number }[]
  /** 各记录的周期时间（天） */
  cycleTimes?: { id: string; days: number; status: string }[]
  /** 当前各列数据 */
  columnCounts: { value: string; label: string; count: number; color?: string }[]
  /** 阻塞记录 */
  blockedItems?: { id: string; title: string; days: number; status: string }[]
}

/** 看板统计面板 */
export function KanbanStats({
  snapshots,
  cycleTimes,
  columnCounts,
  blockedItems
}: KanbanStatsProps) {
  // 吞吐量：最后一列的数量变化
  const throughputData = useMemo(() => {
    if (!snapshots || snapshots.length < 2) return []
    const lastStatus = columnCounts[columnCounts.length - 1]?.value
    if (!lastStatus) return []
    return snapshots.slice(1).map((snap, i) => ({
      date: snap.date,
      throughput: (Number(snap[lastStatus]) || 0) - (Number(snapshots[i][lastStatus]) || 0)
    }))
  }, [snapshots, columnCounts])

  // 周期时间分布
  const cycleDistribution = useMemo(() => {
    if (!cycleTimes) return []
    const buckets = new Map<number, number>()
    for (const ct of cycleTimes) {
      const bucket = Math.floor(ct.days)
      buckets.set(bucket, (buckets.get(bucket) ?? 0) + 1)
    }
    return Array.from(buckets.entries())
      .sort((a, b) => a[0] - b[0])
      .map(([days, count]) => ({ days: `${days}天`, count }))
  }, [cycleTimes])

  return (
    <div className="grid grid-cols-1 gap-4 p-4 lg:grid-cols-2">
      {/* 累积流图 */}
      {snapshots && snapshots.length > 0 && (
        <div className="rounded-lg border p-4">
          <h3 className="mb-3 font-medium text-sm">累积流图</h3>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={snapshots}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              {columnCounts.map((col) => (
                <Area
                  key={col.value}
                  type="monotone"
                  dataKey={col.value}
                  name={col.label}
                  stackId="1"
                  fill={col.color ?? "#8884d8"}
                  stroke={col.color ?? "#8884d8"}
                  fillOpacity={0.6}
                />
              ))}
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* 周期时间分布 */}
      {cycleDistribution.length > 0 && (
        <div className="rounded-lg border p-4">
          <h3 className="mb-3 font-medium text-sm">周期时间分布</h3>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={cycleDistribution}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="days" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="count" name="数量" fill="#6366f1" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* 吞吐量 */}
      {throughputData.length > 0 && (
        <div className="rounded-lg border p-4">
          <h3 className="mb-3 font-medium text-sm">吞吐量</h3>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={throughputData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tick={{ fontSize: 11 }} />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip />
              <Bar dataKey="throughput" name="完成数" fill="#10b981" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* 阻塞分析 */}
      {blockedItems && blockedItems.length > 0 && (
        <div className="rounded-lg border p-4">
          <h3 className="mb-3 font-medium text-sm">阻塞分析</h3>
          <div className="max-h-[200px] space-y-2 overflow-y-auto">
            {blockedItems
              .sort((a, b) => b.days - a.days)
              .map((item) => (
                <div
                  key={item.id}
                  className="flex items-center justify-between rounded bg-muted/50 px-3 py-1.5 text-sm"
                >
                  <span className="truncate">{item.title}</span>
                  <span className="ml-2 shrink-0 text-destructive text-xs">
                    停滞 {item.days} 天
                  </span>
                </div>
              ))}
          </div>
        </div>
      )}
    </div>
  )
}
