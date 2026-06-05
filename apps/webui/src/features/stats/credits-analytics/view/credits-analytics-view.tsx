/**
 * 积分消耗统计仪表盘视图——参考 banking 多区块布局
 * @author Kiro
 */

"use client"

import { CreditsByCategory } from "../credits-by-category"
import { CreditsStatCards } from "../credits-overview"
import { CreditsRecentRecords } from "../credits-recent-records"
import { CreditsTrend } from "../credits-trend"

export function CreditsAnalyticsView() {
  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="font-bold text-2xl tracking-tight">积分消耗统计</h1>
        <p className="text-muted-foreground">全公司 AI 服务积分使用情况总览</p>
      </div>

      {/* 顶部三张统计卡 */}
      <CreditsStatCards />

      {/* 主网格：左 8 右 4，参考 banking 布局 */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="space-y-6 lg:col-span-8">
          <CreditsTrend />
          <CreditsRecentRecords />
        </div>

        <div className="lg:col-span-4">
          <CreditsByCategory />
        </div>
      </div>
    </div>
  )
}
