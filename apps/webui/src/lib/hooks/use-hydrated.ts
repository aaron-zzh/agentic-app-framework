/**
 * 客户端水合完成后返回 true，服务端渲染与水合阶段返回 false。
 *
 * 用于读取本地存储、当前时间等只在客户端可用的数据，避免与服务端渲染结果不一致。
 *
 * @example
 * const hydrated = useHydrated()
 * return hydrated ? <ClientOnlyView /> : <Skeleton />
 * @author AaronZZH & Kiro
 */

import { useSyncExternalStore } from "react"

const subscribeNothing = () => () => undefined

export function useHydrated(): boolean {
  return useSyncExternalStore(
    subscribeNothing,
    () => true,
    () => false
  )
}
