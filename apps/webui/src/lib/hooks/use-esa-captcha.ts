/**
 * 阿里云 ESA AI 验证码 React Hook。
 *
 * ## 推荐用法（先校验再弹验证码）
 *
 * 1. 渲染验证码容器 `<div id={elementId} />`
 * 2. 渲染隐藏触发按钮（SDK 绑定目标，用户不可见）：
 *    `<button id={buttonId} type="button" className="sr-only" tabIndex={-1} aria-hidden />`
 * 3. 渲染真实用户按钮，`onClick` 里先做表单/业务校验，通过后调 `captcha.trigger()`：
 *    ```tsx
 *    onClick={async () => {
 *      const valid = await methods.trigger("phone")
 *      if (!valid || !termsAgreed) return
 *      captcha.trigger()  // 校验通过才弹验证码
 *    }}
 *    ```
 * 4. 非验证码模式（`captcha.enabled = false`）时，`trigger()` 直接执行 `onVerified("")`，
 *    业务逻辑不需要分支处理。
 * 5. 在 `onVerified(captchaVerifyParam)` 回调中执行真正的业务请求，把参数透传给后端。
 * 6. 业务请求结束后（无论成败）调 `reset()` 失效旧 token，避免一次校验多次复用。
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
  /** 隐藏触发按钮的 id（SDK 绑定目标，用户不可见） */
  buttonId: string
  /** 验证通过回调，参数为后端校验所需的 captchaVerifyParam；非验证码模式时参数为空字符串 */
  onVerified: (captchaVerifyParam: string) => void
  /** 是否启用 ESA，默认读环境变量；本地调试可显式传 false */
  enabled?: boolean
}

export interface UseEsaCaptchaResult {
  /** 实际是否启用了 ESA（环境变量 + enabled 入参联合判断） */
  enabled: boolean
  /**
   * 触发验证码流程：
   * - 启用 ESA 时：程序化点击隐藏按钮，由 SDK 弹验证码
   * - 未启用 ESA 时：直接调用 onVerified("")，跳过验证码
   *
   * 在真实用户按钮的 onClick 里，先做表单校验，通过后调此方法。
   */
  trigger: () => void
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

    let cancelled = false
    let pollTimer: ReturnType<typeof setTimeout> | null = null

    function initCaptcha() {
      if (cancelled) return
      if (!window.initAliyunCaptcha) {
        // SDK 尚未加载（网络延迟或客户端导航），每 100ms 重试，最多等 10s
        pollTimer = setTimeout(initCaptcha, 100)
        return
      }

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
    }

    initCaptcha()

    return () => {
      cancelled = true
      if (pollTimer) clearTimeout(pollTimer)
    }
  }, [active, elementId, buttonId])

  const reset = useCallback(() => {
    instanceRef.current?.refresh?.()
  }, [])

  const trigger = useCallback(() => {
    if (active) {
      // 程序化点击隐藏按钮，让 SDK 弹验证码
      document.getElementById(buttonId)?.click()
    } else {
      // 非验证码模式，直接执行业务逻辑
      onVerifiedRef.current("")
    }
  }, [active, buttonId])

  return {
    enabled: active,
    trigger,
    reset
  }
}
