/**
 * 系统参数配置管理页
 * @author AaronZZH & Kiro
 */

"use client"

import { PageContainer } from "@/components/common/PageContainer"
import { TypographyH1 } from "@/components/ui/typography"
import { SystemConfigSettings } from "@/features/system-config/SystemConfigSettings"

export default function SystemConfigPage() {
  return (
    <PageContainer>
      <TypographyH1 className="mb-6">系统参数</TypographyH1>
      <SystemConfigSettings />
    </PageContainer>
  )
}
