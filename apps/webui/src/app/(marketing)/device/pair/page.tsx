/**
 * 设备自助绑定路由。
 * @author AaronZZH & Kiro
 */

import { Suspense } from "react"
import { AuthGuard } from "@/lib/auth/AuthGuard"
import { DevicePairingView } from "@/sections/device/view"

export default function DevicePairingPage() {
  return (
    <Suspense>
      <AuthGuard>
        <DevicePairingView />
      </AuthGuard>
    </Suspense>
  )
}
