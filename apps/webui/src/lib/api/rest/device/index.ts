/**
 * 设备自助绑定 REST 客户端。
 *
 * @example
 * ```ts
 * await deviceBindingApi.bind("ABCDEFGH")
 * ```
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

export interface DeviceBindingResult {
  id: number
  name: string
  serialNumber: string
  model: string | null
  status: string
}

export const deviceBindingApi = {
  bind: (pairCode: string) =>
    backendApi.post<DeviceBindingResult>("/device-bindings", { pairCode }, { showError: false })
}
