"use client"

import { PageContainer } from "@/components/common/PageContainer"
import { TypographyH1 } from "@/components/ui/typography"

export default function BrokerageUsersPage() {
  return (
    <PageContainer>
      <TypographyH1 className="mb-6">分销员管理</TypographyH1>
      {/* TODO: 分销员列表，通过 /api/brokerage/users 查询 */}
    </PageContainer>
  )
}
