"use client"

import { Copy, KeyRound, Settings2, Ticket, Users } from "lucide-react"
import type { ReactNode } from "react"
import { useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { LicenseOwnerOnly } from "@/components/common/LicenseOwnerOnly"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { licenseApi } from "@/lib/api/rest/billing/license"
import { useDeveloperPlans } from "@/lib/queries/use-developer-billing"
import { useIssueLicense, useLicenseStatus, useOfficialConsoleSummary } from "@/lib/queries/use-license-status"

const FEATURE_OPTIONS = [
  { code: "developer", label: "开发者商业化模块" },
  { code: "source-download", label: "源码包下载" },
  { code: "managed-gateway", label: "托管模型网关" },
  { code: "official-console", label: "官方服务控制台" }
] as const

export default function OfficialAdminPage() {
  const { data: license } = useLicenseStatus()
  const { data: plans = [], isLoading } = useDeveloperPlans(Boolean(license?.owner))
  const { data: summary } = useOfficialConsoleSummary(Boolean(license?.owner))
  const issueLicense = useIssueLicense()
  const [form, setForm] = useState({
    subject: "",
    tier: "premium",
    org: "",
    owner: false,
    features: ["developer", "source-download"],
    expiresAt: nextYearDateTimeLocal()
  })

  function handleIssue() {
    issueLicense.mutate({
      subject: form.subject,
      tier: form.tier,
      org: form.org,
      owner: form.owner,
      features: form.features,
      expiresAt: new Date(form.expiresAt).toISOString()
    })
  }

  async function copyToken() {
    if (!issueLicense.data?.token) return
    await navigator.clipboard.writeText(issueLicense.data.token)
  }

  function downloadLicense() {
    if (!issueLicense.data?.token) return
    downloadText("license.jwt", issueLicense.data.token, "application/jwt")
  }

  function toggleFeature(code: string, checked: boolean) {
    setForm((value) => ({
      ...value,
      features: checked
        ? Array.from(new Set([...value.features, code]))
        : value.features.filter((item) => item !== code)
    }))
  }

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
    <PageContainer maxWidth="xl" className="space-y-6">
      <LicenseOwnerOnly>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="font-semibold text-2xl">官方运营管理</h1>
            <p className="text-muted-foreground text-sm">管理开发者订阅、兑换码、授权签发与商业配置。</p>
          </div>
          <Badge variant="default">Owner Only</Badge>
        </div>

        <div className="grid gap-4 md:grid-cols-4">
          <MetricCard icon={<Users className="size-5" />} label="授权主体" value={summary?.ownerUserId ?? "-"} />
          <MetricCard icon={<Ticket className="size-5" />} label="启用模块" value={`${summary?.enabledModules.length ?? 0}`} />
          <MetricCard icon={<KeyRound className="size-5" />} label="授权等级" value={summary?.tier ?? "-"} />
          <MetricCard icon={<Settings2 className="size-5" />} label="套餐数量" value={`${plans.length}`} />
        </div>

        <Card>
          <CardHeader>
            <CardTitle>订阅套餐</CardTitle>
            <CardDescription>当前官方服务实例中的开发者订阅套餐。</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-2">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : (
              <div className="grid gap-3 md:grid-cols-2">
                {plans.map((plan) => (
                  <div key={plan.id} className="rounded-md border p-3">
                    <div className="flex items-center justify-between gap-3">
                      <div>
                        <div className="font-medium">{plan.name}</div>
                        <div className="text-muted-foreground text-xs">{plan.code}</div>
                      </div>
                      <Badge variant={plan.status === "ACTIVE" ? "default" : "secondary"}>
                        {plan.status}
                      </Badge>
                    </div>
                    <div className="mt-3 grid grid-cols-3 gap-2 text-sm">
                      <SmallValue label="价格" value={`¥${(plan.price / 100).toFixed(2)}`} />
                      <SmallValue label="天数" value={`${plan.durationDays}`} />
                      <SmallValue label="Token" value={new Intl.NumberFormat("zh-CN").format(plan.includedTokens)} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>签发 license.jwt</CardTitle>
            <CardDescription>生成给客户或官方实例使用的授权文件内容。</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-5 lg:grid-cols-[minmax(0,420px)_1fr]">
            <div className="space-y-4">
              <Field label="授权主体">
                <Input
                  value={form.subject}
                  onChange={(e) => setForm((v) => ({ ...v, subject: e.target.value }))}
                  placeholder="留空自动生成官方 user_id"
                />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="授权等级">
                  <Input
                    value={form.tier}
                    onChange={(e) => setForm((v) => ({ ...v, tier: e.target.value }))}
                    placeholder="premium"
                  />
                </Field>
                <Field label="组织">
                  <Input
                    value={form.org}
                    onChange={(e) => setForm((v) => ({ ...v, org: e.target.value }))}
                    placeholder="Acme Corp"
                  />
                </Field>
              </div>
              <Field label="高级模块 features">
                <div className="grid gap-2 rounded-md border p-3">
                  {FEATURE_OPTIONS.map((feature) => (
                    <label key={feature.code} className="flex items-center gap-3 text-sm">
                      <Checkbox
                        checked={form.features.includes(feature.code)}
                        onCheckedChange={(checked) => toggleFeature(feature.code, checked === true)}
                      />
                      <span className="min-w-0">
                        <span className="block font-medium">{feature.label}</span>
                        <span className="block text-muted-foreground text-xs">{feature.code}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </Field>
              <Field label="过期时间">
                <Input
                  type="datetime-local"
                  value={form.expiresAt}
                  onChange={(e) => setForm((v) => ({ ...v, expiresAt: e.target.value }))}
                />
              </Field>
              <div className="flex items-center justify-between rounded-md border p-3">
                <div>
                  <div className="font-medium text-sm">官方 owner 授权</div>
                  <div className="text-muted-foreground text-xs">仅给雪稽 AI 官方服务实例开启。</div>
                </div>
                <Switch
                  checked={form.owner}
                  onCheckedChange={(owner) => setForm((v) => ({ ...v, owner }))}
                  aria-label="官方 owner 授权"
                />
              </div>
              <Button type="button" onClick={handleIssue} disabled={issueLicense.isPending}>
                <KeyRound className="size-4" />
                {issueLicense.isPending ? "签发中" : "生成 license.jwt"}
              </Button>
            </div>

            <div className="space-y-3">
              <Textarea
                readOnly
                className="min-h-56 font-mono text-xs"
                value={issueLicense.data?.token ?? ""}
                placeholder="生成后的 license.jwt 内容会显示在这里"
              />
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground text-sm">
                  {issueLicense.error ? issueLicense.error.message : "将内容保存为 license.jwt 后放入授权目录。"}
                </span>
                <Button type="button" variant="outline" disabled={!issueLicense.data?.token} onClick={copyToken}>
                  <Copy className="size-4" />
                  复制
                </Button>
                <Button type="button" variant="outline" disabled={!issueLicense.data?.token} onClick={downloadLicense}>
                  下载密钥
                </Button>
              </div>
              <Button type="button" variant="outline" onClick={downloadSourceCode}>
                下载源码包
              </Button>
            </div>
          </CardContent>
        </Card>
      </LicenseOwnerOnly>
    </PageContainer>
  )
}

function downloadText(filename: string, content: string, mimeType: string) {
  downloadBlob(filename, new Blob([content], { type: mimeType }))
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

function nextYearDateTimeLocal() {
  const value = new Date()
  value.setFullYear(value.getFullYear() + 1)
  value.setMinutes(value.getMinutes() - value.getTimezoneOffset())
  return value.toISOString().slice(0, 16)
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-2">
      <Label>{label}</Label>
      {children}
    </div>
  )
}

function MetricCard({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-2">
          <CardDescription>{label}</CardDescription>
          <div className="text-muted-foreground">{icon}</div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="font-semibold text-xl">{value}</div>
      </CardContent>
    </Card>
  )
}

function SmallValue({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-muted-foreground text-xs">{label}</div>
      <div className="truncate font-medium">{value}</div>
    </div>
  )
}
