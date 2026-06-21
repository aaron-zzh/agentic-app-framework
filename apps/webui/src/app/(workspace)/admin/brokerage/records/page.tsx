"use client"

import { PageContainer } from "@/components/common/PageContainer"
import { TypographyH1 } from "@/components/ui/typography"

export default function BrokerageRecordsPage() {
  return (
    <PageContainer>
      <TypographyH1 className="mb-6">佣金流水</TypographyH1>
      {/* TODO: 佣金流水列表，通过 /api/brokerage/records 查询 */}
    </PageContainer>
  )
}
