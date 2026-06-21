"use client"

import { PageContainer } from "@/components/common/PageContainer"
import { TypographyH1 } from "@/components/ui/typography"

export default function BrokerageWithdrawsPage() {
  return (
    <PageContainer>
      <TypographyH1 className="mb-6">提现审核</TypographyH1>
      {/* TODO: 提现申请列表，通过 /api/brokerage/withdraws 查询 */}
    </PageContainer>
  )
}
