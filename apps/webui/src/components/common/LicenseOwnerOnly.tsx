"use client"

import { ExternalLink, ShieldAlert } from "lucide-react"
import type { ReactNode } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useLicenseStatus } from "@/lib/queries/use-license-status"

export function LicenseOwnerOnly({ children }: { children: ReactNode }) {
  const { data: license, isLoading } = useLicenseStatus()

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 w-full" />
      </div>
    )
  }

  if (!license?.owner) {
    return (
      <Card>
        <CardHeader>
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-md bg-muted text-muted-foreground">
              <ShieldAlert className="size-5" />
            </div>
            <div>
              <CardTitle>官方服务入口未启用</CardTitle>
              <CardDescription>此页面仅在官方 owner 授权的部署实例中展示。</CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <Button
            type="button"
            variant="outline"
            disabled={!license?.upgradeUrl}
            onClick={() =>
              license?.upgradeUrl &&
              window.open(license.upgradeUrl, "_blank", "noopener,noreferrer")
            }
          >
            打开官方站点
            <ExternalLink className="size-4" />
          </Button>
        </CardContent>
      </Card>
    )
  }

  return <>{children}</>
}
