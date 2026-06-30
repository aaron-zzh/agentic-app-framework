/**
 * 登录页——密码登录 / 手机验证码登录（Tab 切换）+ 第三方 OAuth 入口（预留）
 * 生产/测试环境接入阿里云 ESA AI 验证码（NEXT_PUBLIC_CAPTCHA_ENABLED=true 时启用）。
 *
 * captcha 实例提升到 LoginContent，整个登录页只有一个 useEsaCaptcha 实例，
 * 消除多实例 SDK 绑定冲突（原 PasswordLoginPanel + PhoneLoginPanel 各持一个导致互相覆盖）。
 * 主按钮 onClick 先做表单/业务校验，通过后调 captcha.trigger()，实现先提示再弹验证码。
 * @author AaronZZH & Kiro
 */

"use client"

import Link from "next/link"
import { useRouter, useSearchParams } from "next/navigation"
import { Suspense, useCallback, useEffect, useRef, useState } from "react"
import { ConsentDialog } from "@/components/common/ConsentDialog"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { type LegalDocument, legalApi } from "@/lib/api/rest/legal"
import { authApi } from "@/lib/api/rest/user/auth"
import { clearAxiosAuth, setAxiosAuth } from "@/lib/auth/utils"
import { paths } from "@/lib/constants/paths"
import { useEsaCaptcha } from "@/lib/hooks/use-esa-captcha"
import { notify } from "@/lib/notification"
import { type AuthUser, useAuthStore } from "@/lib/store/auth-store"
import { LoginSuccessOverlay } from "./LoginSuccessOverlay"
import { PasswordLoginPanel, type PasswordLoginPanelRef } from "./PasswordLoginPanel"
import { PhoneLoginPanel, type PhoneLoginPanelRef } from "./PhoneLoginPanel"

function LoginContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { setTokens, setUser } = useAuthStore()
  const [successUser, setSuccessUser] = useState<string | null>(null)
  const [pendingConsent, setPendingConsent] = useState<LegalDocument[] | null>(null)
  const [activeTab, setActiveTab] = useState<"password" | "phone">("password")
  // 用 state 跟踪手机面板的步骤和服务条款状态，驱动按钮渲染
  const [phoneStep, setPhoneStep] = useState<"phone" | "code">("phone")
  const [phoneTermsAgreed, setPhoneTermsAgreed] = useState(false)
  // 主按钮 loading state
  const [isMainLoading, setIsMainLoading] = useState(false)

  const pendingAuthRef = useRef<{
    accessToken: string
    refreshToken: string
    user: AuthUser
    isNewUser: boolean
  } | null>(null)
  const passwordPanelRef = useRef<PasswordLoginPanelRef>(null)
  const phonePanelRef = useRef<PhoneLoginPanelRef>(null)

  const redirectTo = searchParams.get("redirect") || "/studio/welcome"

  // 处理后端 OAuth 重定向回来的 token 参数
  useEffect(() => {
    const accessToken = searchParams.get("accessToken")
    const refreshToken = searchParams.get("refreshToken")
    if (!accessToken || !refreshToken) return

    setTokens(accessToken, refreshToken)
    authApi.me().then(({ user }) => {
      setUser(user)
      router.replace(redirectTo)
    })
  }, [searchParams, setTokens, setUser, router, redirectTo])

  /**
   * 登录成功处理：
   * 先用临时 axios header 拉取用户信息并暂存，展示 LoginSuccessOverlay 动画；
   * 动画结束时（handleOverlayDone）再写入 store —— 避免 GuestGuard 监听到
   * isAuthenticated 立刻 router.replace，把动画抢跑卸载。
   *
   * 新用户首次通过手机号登录即注册时，isNewUser=true，动画结束后给出欢迎引导。
   *
   * 合规检查：me 之后立即查询 /legal/consent/pending；若有未同意的最新版本，
   * 先弹出 ConsentDialog 强制确认，确认后再继续动画 → 进入工作区。
   */
  async function handleLoginSuccess(
    accessToken: string,
    refreshToken: string,
    isNewUser?: boolean
  ) {
    setAxiosAuth(accessToken)
    const { user } = await authApi.me()
    pendingAuthRef.current = { accessToken, refreshToken, user, isNewUser: !!isNewUser }

    try {
      const pending = await legalApi.pending()
      if (pending.count > 0) {
        setPendingConsent(pending.items)
        return
      }
    } catch {
      // 接口异常不阻塞登录流程
    }

    setSuccessUser(user.nickname || user.username || "")
  }

  /** 用户在 ConsentDialog 中全部同意后回调 */
  const handleConsentConfirmed = useCallback(() => {
    setPendingConsent(null)
    const pending = pendingAuthRef.current
    if (!pending) return
    setSuccessUser(pending.user.nickname || pending.user.username || "")
  }, [])

  /** 用户在 ConsentDialog 中点"不同意，退出登录" */
  const handleConsentDecline = useCallback(() => {
    setPendingConsent(null)
    pendingAuthRef.current = null
    clearAxiosAuth()
    notify.info("您未同意必要条款，已取消本次登录")
  }, [])

  const handleOverlayDone = useCallback(() => {
    const pending = pendingAuthRef.current
    if (!pending) {
      router.push(redirectTo)
      return
    }
    pendingAuthRef.current = null
    setTokens(pending.accessToken, pending.refreshToken)
    setUser(pending.user)
    const dest = redirectTo.startsWith("/studio")
      ? `${redirectTo.split("?")[0]}?welcome=1`
      : redirectTo
    router.push(dest)
  }, [router, redirectTo, setTokens, setUser])

  // 验证码通过后，根据当前 tab 分发到对应面板
  const captcha = useEsaCaptcha({
    elementId: "login-captcha",
    buttonId: "login-hidden-btn",
    onVerified: (param) => {
      setIsMainLoading(true)
      const task =
        activeTab === "password"
          ? passwordPanelRef.current?.triggerSubmit(param)
          : phonePanelRef.current?.triggerSend(param)
      void Promise.resolve(task).finally(() => {
        captcha.reset()
        setIsMainLoading(false)
      })
    }
  })

  // 主按钮点击：先做表单/业务校验，通过后触发验证码（或直接提交）
  async function handleMainButtonClick() {
    if (activeTab === "password") {
      const valid = await passwordPanelRef.current?.validate()
      if (!valid) return
    } else {
      const valid = await phonePanelRef.current?.validate()
      if (!valid) return
      if (!phoneTermsAgreed) {
        notify.warning("请先阅读并同意服务条款与隐私政策")
        return
      }
    }
    captcha.trigger()
  }

  // 主按钮文字
  function getButtonLabel() {
    if (activeTab === "password") return isMainLoading ? "登录中..." : "登录"
    return isMainLoading ? "发送中..." : "发送验证码"
  }

  // 手机 tab 第一步才需要主按钮（第二步面板自管）
  const showMainButton = activeTab === "password" || phoneStep === "phone"

  return (
    <div className="w-full max-w-sm space-y-6">
      {pendingConsent && pendingConsent.length > 0 && (
        <ConsentDialog
          items={pendingConsent}
          onAllConfirmed={handleConsentConfirmed}
          onDecline={handleConsentDecline}
        />
      )}
      {successUser !== null ? (
        <LoginSuccessOverlay username={successUser} onDone={handleOverlayDone} />
      ) : (
        <>
          <div>
            <h2 className="font-bold text-2xl">登录</h2>
            <p className="mt-1 text-muted-foreground text-sm">欢迎使用，AI 时代快人一步</p>
          </div>

          <Tabs
            value={activeTab}
            onValueChange={(v) => {
              setActiveTab(v as "password" | "phone")
              setPhoneStep("phone")
            }}
          >
            <TabsList className="w-full">
              <TabsTrigger value="password" className="flex-1">
                密码登录
              </TabsTrigger>
              <TabsTrigger value="phone" className="flex-1">
                手机验证码
              </TabsTrigger>
            </TabsList>
            <TabsContent value="password" className="pt-4">
              <PasswordLoginPanel ref={passwordPanelRef} onSuccess={handleLoginSuccess} />
            </TabsContent>
            <TabsContent value="phone" className="pt-4">
              <PhoneLoginPanel
                ref={phonePanelRef}
                onSuccess={handleLoginSuccess}
                onStepChange={setPhoneStep}
                onTermsChange={setPhoneTermsAgreed}
              />
            </TabsContent>
          </Tabs>

          {/* 验证码容器 + 隐藏触发按钮（SDK 绑定目标，用户不可见） */}
          {captcha.enabled && <div id="login-captcha" />}
          <button
            id="login-hidden-btn"
            type="button"
            className="sr-only"
            tabIndex={-1}
            aria-hidden
          />

          {showMainButton && (
            <Button
              type="button"
              className="h-11 w-full dark:bg-zinc-700 dark:text-zinc-100 dark:hover:bg-zinc-600"
              disabled={isMainLoading || (activeTab === "phone" && !phoneTermsAgreed)}
              onClick={handleMainButtonClick}
            >
              {getButtonLabel()}
            </Button>
          )}

          {/* 第三方登录（暂时隐藏）
          <div className="space-y-3">
            <p className="text-center text-muted-foreground text-sm">其他登录方式</p>
            <div className="flex justify-center gap-4">
              <Button variant="outline" size="icon" onClick={() => handleOAuth("wechat")}>微信</Button>
              <Button variant="outline" size="icon" onClick={() => handleOAuth("wecom")}>企业微信</Button>
              <Button variant="outline" size="icon" onClick={() => handleOAuth("dingtalk")}>钉钉</Button>
            </div>
          </div>
          */}

          <p className="text-center text-muted-foreground text-sm">
            还没有账号？{" "}
            <Link href={paths.auth.register} className="text-primary hover:underline">
              立即注册
            </Link>
          </p>
        </>
      )}
    </div>
  )
}

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginContent />
    </Suspense>
  )
}
