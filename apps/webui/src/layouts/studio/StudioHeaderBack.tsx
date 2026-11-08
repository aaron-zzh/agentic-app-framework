/**
 * Studio 顶栏返回声明——供 Server Component 页面使用
 *
 * 客户端页面直接调用 useStudioHeader；服务端页面渲染本组件即可声明返回目标。
 */

"use client"

import { type StudioHeaderBack as StudioHeaderBackConfig, useStudioHeader } from "./header-store"

export function StudioHeaderBack(props: StudioHeaderBackConfig) {
  useStudioHeader({ back: props })
  return null
}
