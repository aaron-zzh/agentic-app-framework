"use client"

import { Download, ExternalLink, FileKey, Globe, ReceiptText } from "lucide-react"
import type { ReactNode } from "react"
import { LicenseOwnerOnly } from "@/components/common/LicenseOwnerOnly"
import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useLicenseStatus } from "@/lib/queries/use-license-status"

export default function OfficialPortalPage() {
  const { data: license } = useLicenseStatus()

  return (
    <PageContainer maxWidth="xl" className="space-y-6">
      <LicenseOwnerOnly>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="font-semibold text-2xl">官方客户门户</h1>
            <p className="text-muted-foreground text-sm">用于购买订阅、绑定实例与下载授权文件。</p>
          </div>
          <Badge variant="default">Owner</Badge>
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          <ActionCard
            icon={<Globe className="size-5" />}
            title="订阅购买"
            description="面向框架开发者的套餐购买、续费和升级入口。"
            action="打开官网"
            onClick={() =>
              license?.upgradeUrl &&
              window.open(license.upgradeUrl, "_blank", "noopener,noreferrer")
            }
          />
          <ActionCard
            icon={<FileKey className="size-5" />}
            title="授权文件"
            description="购买完成后签发 license.jwt，并引导用户放入本地实例。"
            action="进入签发"
          />
          <ActionCard
            icon={<ReceiptText className="size-5" />}
            title="订单账单"
            description="查看订阅订单、发票信息和续费状态。"
            action="查看账单"
          />
        </div>

        <Card>
          <CardHeader>
            <CardTitle>当前官方实例</CardTitle>
            <CardDescription>这个页面只在 license key 标记 `owner=true` 时可见。</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-3">
            <StatusItem label="版本" value={license?.tier ?? "free"} />
            <StatusItem label="授权主体" value={license?.userId ?? "未安装授权文件"} />
            <StatusItem
              label="到期时间"
              value={
                license?.expiresAt
                  ? new Date(license.expiresAt).toLocaleDateString("zh-CN")
                  : "未设置"
              }
            />
          </CardContent>
        </Card>
      </LicenseOwnerOnly>
    </PageContainer>
  )
}

function ActionCard({
  icon,
  title,
  description,
  action,
  onClick
}: {
  icon: ReactNode
  title: string
  description: string
  action: string
  onClick?: () => void
}) {
  return (
    <Card>
      <CardHeader>
        <div className="flex size-10 items-center justify-center rounded-md bg-primary/10 text-primary">
          {icon}
        </div>
        <CardTitle className="text-base">{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button type="button" variant="outline" className="w-full" onClick={onClick}>
          {action}
          {onClick ? <ExternalLink className="size-4" /> : <Download className="size-4" />}
        </Button>
      </CardContent>
    </Card>
  )
}

function StatusItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border p-3">
      <div className="text-muted-foreground text-xs">{label}</div>
      <div className="mt-2 truncate font-medium text-sm">{value}</div>
    </div>
  )
}
