/**
 * 阿里云 ESA AI 验证码 React Hook。
 *
 * 用法：
 * 1. 在页面渲染独立的 `<div id={elementId} />` 容器和 `<button id={buttonId} type={buttonType}>` 触发按钮。
 * 2. 调用 `useEsaCaptcha({ elementId, buttonId, onVerified })` 拿到 `enabled / buttonType / reset`。
 * 3. 在 `onVerified(captchaVerifyParam)` 回调中执行真正的业务请求，把参数透传给后端。
 * 4. 业务请求结束后（无论成败）调 `reset()` 失效旧 token，避免一次校验多次复用。
 *
 * 注意：每个 hook 实例对应一对独立的 elementId/buttonId，**不能跨实例复用**；
 * 同一页面有多个需要 ESA 的按钮时，给每个按钮分配唯一 id 即可。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useRef } from "react"

const CAPTCHA_ENABLED_DEFAULT = process.env.NEXT_PUBLIC_CAPTCHA_ENABLED === "true"
const CAPTCHA_SCENE_ID = process.env.NEXT_PUBLIC_CAPTCHA_SCENE_ID ?? ""

export interface UseEsaCaptchaOptions {
  /** 验证码容器 div 的 id（页面内唯一） */
  elementId: string
  /** 触发按钮的 id（页面内唯一），ESA SDK 自动绑定点击事件 */
  buttonId: string
  /** 验证通过回调，参数为后端校验所需的 captchaVerifyParam */
  onVerified: (captchaVerifyParam: string) => void
  /** 是否启用 ESA，默认读环境变量；本地调试可显式传 false */
  enabled?: boolean
}

export interface UseEsaCaptchaResult {
  /** 实际是否启用了 ESA（环境变量 + enabled 入参联合判断） */
  enabled: boolean
  /** 按钮 type：启用 ESA 时为 "button"（由 SDK 触发），否则为 "submit"（走表单原生提交） */
  buttonType: "submit" | "button"
  /** 业务请求结束后调一次，失效旧 token 并刷新滑块 */
  reset: () => void
}

export function useEsaCaptcha({
  elementId,
  buttonId,
  onVerified,
  enabled
}: UseEsaCaptchaOptions): UseEsaCaptchaResult {
  const active = enabled ?? CAPTCHA_ENABLED_DEFAULT

  // 用 ref 持久化最新回调，避免每次重渲染都重新初始化 SDK
  const onVerifiedRef = useRef(onVerified)
  onVerifiedRef.current = onVerified

  const instanceRef = useRef<{ refresh?: () => void } | null>(null)

  useEffect(() => {
    if (!active || typeof window === "undefined") return
    if (!window.initAliyunCaptcha) return

    let cancelled = false

    window.initAliyunCaptcha({
      SceneId: CAPTCHA_SCENE_ID,
      mode: "popup",
      element: `#${elementId}`,
      button: `#${buttonId}`,
      success: (captchaVerifyParam: string) => {
        if (cancelled) return
        onVerifiedRef.current(captchaVerifyParam)
      },
      fail: () => {},
      getInstance: (instance) => {
        instanceRef.current = instance as { refresh?: () => void }
      },
      server: ["captcha-esa-open.aliyuncs.com", "captcha-esa-open-b.aliyuncs.com"],
      slideStyle: { width: 360, height: 40 }
    })

    return () => {
      cancelled = true
    }
  }, [active, elementId, buttonId])

  const reset = useCallback(() => {
    instanceRef.current?.refresh?.()
  }, [])

  return {
    enabled: active,
    buttonType: active ? "button" : "submit",
    reset
  }
}
