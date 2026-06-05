import type { Metadata } from "next"
import { CreditsAnalyticsView } from "@/features/stats/credits-analytics/view/credits-analytics-view"

export const metadata: Metadata = {
  title: "积分消耗统计"
}

export default function CreditsAnalyticsPage() {
  return <CreditsAnalyticsView />
}
