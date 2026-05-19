/**
 * Dashboard——工作台首页
 * @author AaronZZH & Kiro
 */

"use client"

import { PageContainer } from "@/components/common/PageContainer"
import { DashboardView } from "@/features/dashboard/DashboardView"

export default function DashboardPage() {
  return (
    <PageContainer disablePadding maxWidth={false}>
      <DashboardView />
    </PageContainer>
  )
}
