/**
 * 设备自助绑定页：当前登录用户确认后消费一次性配对码。
 * @author AaronZZH & Kiro
 */

"use client"

import { useSearchParams } from "next/navigation"
import { useState } from "react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle
} from "@/components/ui/card"
import { type DeviceBindingResult, deviceBindingApi } from "@/lib/api/rest/device"
import { useAuth } from "@/lib/auth/use-auth"
import { isAggregateScope, useOrgStore } from "@/lib/store/org-store"

type BindingState =
  | { status: "idle" }
  | { status: "submitting" }
  | { status: "success"; device: DeviceBindingResult }
  | { status: "error"; message: string }

export function DevicePairingView() {
  const searchParams = useSearchParams()
  const { user } = useAuth()
  const orgStatus = useOrgStore((state) => state.status)
  const currentScope = useOrgStore((state) => state.currentScope)
  const [bindingState, setBindingState] = useState<BindingState>({ status: "idle" })
  const pairCode = (searchParams.get("pairCode") ?? "").trim().toUpperCase()
  const pairCodeValid = /^[A-Z0-9]{8}$/.test(pairCode)
  const concreteWorkspace = orgStatus === "ready" && currentScope?.kind === "workspace"
  const aggregateScope = isAggregateScope(currentScope)

  async function bindDevice() {
    if (!pairCodeValid || !concreteWorkspace || bindingState.status === "submitting") return
    setBindingState({ status: "submitting" })
    try {
      const device = await deviceBindingApi.bind(pairCode)
      setBindingState({ status: "success", device })
    } catch (error) {
      setBindingState({
        status: "error",
        message: error instanceof Error ? error.message : "设备绑定失败，请重试"
      })
    }
  }

  if (bindingState.status === "success") {
    return (
      <div className="mx-auto flex w-full max-w-xl px-6 py-16 md:py-24">
        <Card className="w-full">
          <CardHeader>
            <CardTitle>设备绑定成功</CardTitle>
            <CardDescription>设备正在领取凭据并连接服务，请返回设备等待片刻。</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            <p className="font-medium">{bindingState.device.name}</p>
            <p className="text-muted-foreground text-sm">
              序列号：{bindingState.device.serialNumber}
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto flex w-full max-w-xl px-6 py-16 md:py-24">
      <Card className="w-full">
        <CardHeader>
          <CardTitle>绑定设备</CardTitle>
          <CardDescription>
            确认后，设备将归属于当前登录用户
            {user?.nickname || user?.username ? `“${user.nickname || user.username}”` : ""}。
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {pairCodeValid ? (
            <div className="rounded-lg bg-muted p-4 text-center">
              <p className="text-muted-foreground text-sm">设备配对码</p>
              <p className="mt-1 font-mono font-semibold text-2xl tracking-[0.2em]">{pairCode}</p>
            </div>
          ) : (
            <Alert variant="destructive">
              <AlertTitle>配对链接无效</AlertTitle>
              <AlertDescription>请重新扫描设备上显示的二维码。</AlertDescription>
            </Alert>
          )}

          {!concreteWorkspace && pairCodeValid && (
            <Alert variant="destructive">
              <AlertTitle>需要具体工作区</AlertTitle>
              <AlertDescription>
                {aggregateScope
                  ? "当前处于聚合查看范围，请先切换到具体工作区。"
                  : "正在恢复您的组织和工作区，请稍后重试。"}
              </AlertDescription>
            </Alert>
          )}

          {bindingState.status === "error" && (
            <Alert variant="destructive">
              <AlertTitle>绑定失败</AlertTitle>
              <AlertDescription>{bindingState.message}</AlertDescription>
            </Alert>
          )}
        </CardContent>
        <CardFooter className="justify-end">
          <Button
            disabled={!pairCodeValid || !concreteWorkspace || bindingState.status === "submitting"}
            onClick={bindDevice}
          >
            {bindingState.status === "submitting" ? "绑定中…" : "绑定到我的账号"}
          </Button>
        </CardFooter>
      </Card>
    </div>
  )
}
