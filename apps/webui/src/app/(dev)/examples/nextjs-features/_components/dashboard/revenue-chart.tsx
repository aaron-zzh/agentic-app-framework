/**
 * 特性演示：connection() 强制动态渲染 + 长时间 Suspense
 *
 * next/server 的 connection() 标记此组件为动态（运行时渲染），
 * 演示 3 秒延时的骨架屏效果，对比快速加载的 CardWrapper。
 */

import { Calendar as CalendarIcon } from "lucide-react"
import { connection } from "next/server"

import { fetchRevenue, generateYAxis } from "../../_data/mock"

export default async function RevenueChart() {
  await connection() // 强制动态渲染，不走构建时缓存
  const revenue = await fetchRevenue() // 延时 3 秒
  const chartHeight = 200
  const { yAxisLabels, topLabel } = generateYAxis(revenue)

  return (
    <div className="w-full md:col-span-4">
      <h2 className="mb-4 font-bold text-slate-700 text-xl">Recent Revenue</h2>
      <div className="rounded-xl bg-gray-50 p-4">
        <div className="mt-0 grid grid-cols-12 items-end gap-2 rounded-md bg-white p-4 sm:grid-cols-13 md:gap-4">
          <div
            className="mb-6 hidden flex-col justify-between text-gray-400 text-xs sm:flex"
            style={{ height: `${chartHeight}px` }}
          >
            {yAxisLabels.map((label) => (
              <p key={label}>{label}</p>
            ))}
          </div>
          {revenue.map((month) => (
            <div key={month.month} className="flex flex-col items-center gap-2">
              <div
                className="w-full rounded-md bg-blue-300"
                style={{ height: `${(chartHeight / topLabel) * month.revenue}px` }}
              />
              <p className="-rotate-90 text-gray-400 text-xs sm:rotate-0">{month.month}</p>
            </div>
          ))}
        </div>
        <div className="flex items-center pt-3 pb-2">
          <CalendarIcon className="h-4 w-4 text-gray-500" />
          <h3 className="ml-2 text-gray-500 text-sm">Last 12 months</h3>
        </div>
      </div>
    </div>
  )
}
