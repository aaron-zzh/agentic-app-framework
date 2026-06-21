"use client"

import {
  BadgeCheck,
  ChevronDown,
  CircleHelp,
  Copy,
  CreditCard,
  Download,
  ExternalLink,
  RefreshCw,
  Sparkles
} from "lucide-react"
import type { ReactNode } from "react"
import { useEffect, useState } from "react"
import { toast } from "sonner"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { licenseApi } from "@/lib/api/rest/billing/license"
import { useLicenseStatus } from "@/lib/queries/use-license-status"
import { useAuthStore } from "@/lib/store/auth-store"
import { cn } from "@/lib/utils/cn"

export function LicensePlanBadge({ collapsed }: { collapsed: boolean }) {
  const [open, setOpen] = useState(false)
  const [mounted, setMounted] = useState(false)
  const { data: license } = useLicenseStatus()
  const isPremium = mounted && license ? license.tier !== "free" : false
  const tier = isPremium ? license?.tier || "Pro" : "体验版"

  useEffect(() => {
    setMounted(true)
  }, [])

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        title={collapsed ? `当前版本：${tier}` : undefined}
        className={cn(
          "mx-2 mb-3 flex items-center gap-2 rounded-md border bg-sidebar-accent/50 p-2 text-left text-sm transition-colors hover:bg-sidebar-accent",
          collapsed && "justify-center px-2"
        )}
      >
        <span
          className={cn(
            "flex size-7 shrink-0 items-center justify-center rounded-md",
            isPremium ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
          )}
        >
          {isPremium ? <BadgeCheck className="size-4" /> : <Sparkles className="size-4" />}
        </span>
        {!collapsed && (
          <>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-medium">框架版本</span>
              <span className="block truncate text-muted-foreground text-xs">管理订阅与额度</span>
            </span>
            <Badge variant={isPremium ? "default" : "secondary"}>{tier}</Badge>
          </>
        )}
      </button>
      <BillingPlanDialog open={open} onOpenChange={setOpen} />
    </>
  )
}

function BillingPlanDialog({
  open,
  onOpenChange
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const user = useAuthStore((s) => s.user)
  const { data: license, refetch, isFetching } = useLicenseStatus()
  const planLabel = license
    ? license.tier !== "free"
      ? license.tier || "Pro"
      : "免费版"
    : "免费版"

  async function downloadSourceCode() {
    const res = await fetch(licenseApi.sourceDownloadUrl(), {
      headers: licenseApi.sourceDownloadHeaders()
    })
    if (!res.ok) return
    const blob = await res.blob()
    const disposition = res.headers.get("Content-Disposition") ?? ""
    const filename = disposition.match(/filename="([^"]+)"/)?.[1] ?? "aaf-source.zip"
    downloadBlob(filename, blob)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100vh-3rem)] overflow-y-auto p-0 sm:max-w-3xl">
        <DialogHeader className="border-b p-5 pr-12">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-3">
              <div className="flex size-12 items-center justify-center rounded-full bg-primary/10 text-primary">
                <Sparkles className="size-6" />
              </div>
              <div className="min-w-0">
                <DialogTitle className="truncate text-lg">
                  {user?.email ?? user?.nickname ?? "AAF Developer"}
                </DialogTitle>
                <DialogDescription className="mt-1 flex items-center gap-1">
                  <span className="truncate">
                    License ID：{license?.userId ?? "未安装授权文件"}
                  </span>
                  <Copy className="size-3.5 shrink-0" />
                </DialogDescription>
              </div>
            </div>
            <Button
              type="button"
              variant="outline"
              className="text-primary"
              onClick={() => {
                const url = license?.features?.includes("official-console")
                  ? "/official/admin"
                  : "/"
                window.open(url, "_blank", "noopener,noreferrer")
              }}
            >
              <CreditCard className="size-4" />
              获取授权
              <ExternalLink className="size-4" />
            </Button>
          </div>
        </DialogHeader>

        <section className="border-b p-5">
          <div className="mb-5 flex items-center justify-between gap-3">
            <div>
              <h3 className="flex items-center gap-2 font-semibold text-xl">
                License Status
                <button
                  type="button"
                  onClick={() => refetch().then(() => toast.success("授权状态已刷新"))}
                  disabled={isFetching}
                  title="刷新授权状态"
                  className="text-muted-foreground hover:text-foreground disabled:opacity-50"
                >
                  <RefreshCw className={`size-4 ${isFetching ? "animate-spin" : ""}`} />
                </button>
              </h3>
              <p className="text-muted-foreground text-sm">授权状态由当前部署实例校验。</p>
            </div>
            <Badge variant="secondary" className="h-8 rounded-md px-3 text-sm">
              {planLabel}
            </Badge>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <StatusTile label="版本" value={planLabel} />
            <StatusTile label="授权用户" value={license?.userId ?? "未安装授权文件"} />
            <StatusTile
              label="授权标识"
              value={
                license?.tier && license.tier !== "free"
                  ? license.identityValid
                    ? "有效"
                    : "异常"
                  : "免费版"
              }
            />
          </div>
          {license?.features?.length ? (
            <div className="mt-4 flex flex-wrap gap-2">
              {license.features.map((feature) => (
                <Badge key={feature} variant="outline" className="rounded-md">
                  {feature}
                </Badge>
              ))}
            </div>
          ) : null}
        </section>

        <section className="border-b p-5">
          <div className="rounded-lg border p-4">
            <h3 className="font-medium">升级订阅</h3>
            <p className="mt-2 text-muted-foreground text-sm">
              购买或续费完成后，使用官方签发的授权文件解锁高级模块。
            </p>
            <Button
              type="button"
              className="mt-4"
              onClick={() => {
                const url = license?.features?.includes("official-console")
                  ? "/official/admin"
                  : "/"
                window.open(url, "_blank", "noopener,noreferrer")
              }}
            >
              <CreditCard className="size-4" />
              获取授权 / 升级版本
              <ExternalLink className="size-4" />
            </Button>
            {license?.features?.includes("source-download") ? (
              <Button type="button" variant="outline" className="mt-3" onClick={downloadSourceCode}>
                <Download className="size-4" />
                下载源码包
              </Button>
            ) : null}
          </div>
        </section>

        <section className="space-y-3 bg-muted/30 p-5">
          <InfoRow icon={<CircleHelp className="size-4" />} title="授权文件位置">
            {license?.licenseFileLocations?.join(" 或 ") ??
              "~/.aaf/license.jwt 或 ./config/license.jwt"}
          </InfoRow>
          <InfoRow icon={<ChevronDown className="size-4" />} title="Billing Support">
            当前实例只校验本地授权文件；订阅购买、续费和 license 签发统一由官方门户完成。
          </InfoRow>
        </section>
      </DialogContent>
    </Dialog>
  )
}

function downloadBlob(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

function StatusTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border p-3">
      <div className="text-muted-foreground text-xs">{label}</div>
      <div className="mt-2 truncate font-medium text-sm">{value}</div>
    </div>
  )
}

function InfoRow({
  icon,
  title,
  children
}: {
  icon: ReactNode
  title: string
  children: ReactNode
}) {
  return (
    <div className="rounded-lg border bg-background p-3">
      <div className="flex items-center gap-2 font-medium">
        {icon}
        <span>{title}</span>
      </div>
      <p className="mt-2 text-muted-foreground text-sm">{children}</p>
    </div>
  )
}
