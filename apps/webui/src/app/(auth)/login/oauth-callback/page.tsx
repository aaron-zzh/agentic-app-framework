/**
 * OAuth 回调页——处理第三方登录授权码
 * URL: /login/oauth-callback?provider=wecom&code=xxx&state=xxx
 */

"use client"

import { useRouter, useSearchParams } from "next/navigation"
import { Suspense, useEffect, useRef, useState } from "react"
import { authApi } from "@/lib/api/rest/user/auth"
import { paths } from "@/lib/constants/paths"
import { useAuthStore } from "@/lib/store/auth-store"
import { clearRefCode, readRefCode } from "@/lib/utils/ref-code"

function OAuthCallbackContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { setTokens, setUser } = useAuthStore()
  const [error, setError] = useState<string | null>(null)
  const processed = useRef(false)

  useEffect(() => {
    if (processed.current) return
    processed.current = true

    const provider = searchParams.get("provider")
    const code = searchParams.get("code")
    const state = searchParams.get("state")

    if (!provider || !code) {
      setError("缺少授权参数")
      return
    }

    // 验证 state 防止 CSRF
    const savedState = sessionStorage.getItem("oauth-state")
    if (savedState && state !== savedState) {
      setError("授权状态不匹配，请重新登录")
      return
    }
    sessionStorage.removeItem("oauth-state")

    authApi
      .oauthCallback(provider, code, readRefCode())
      .then(async (result) => {
        // OAuth 回调成功后清除 refCode（不区分新老用户：OAuth 老用户登录时无害，新用户已绑定）
        clearRefCode()
        setTokens(result.accessToken, result.refreshToken)
        const { user } = await authApi.me()
        setUser(user)
        router.push(paths.workspace.root)
      })
      .catch((err: Error) => {
        setError(err.message || "第三方登录失败，请重试")
      })
  }, [searchParams, router, setTokens, setUser])

  if (error) {
    return (
      <div className="w-full max-w-sm space-y-4 text-center">
        <h2 className="font-bold text-destructive text-xl">登录失败</h2>
        <p className="text-muted-foreground text-sm">{error}</p>
        <a href={paths.auth.login} className="text-primary text-sm hover:underline">
          返回登录
        </a>
      </div>
    )
  }

  return (
    <div className="w-full max-w-sm space-y-4 text-center">
      <h2 className="font-bold text-xl">正在登录...</h2>
      <p className="text-muted-foreground text-sm">正在处理第三方授权，请稍候</p>
    </div>
  )
}

export default function OAuthCallbackPage() {
  return (
    <Suspense
      fallback={
        <div className="w-full max-w-sm text-center">
          <p className="text-muted-foreground text-sm">加载中...</p>
        </div>
      }
    >
      <OAuthCallbackContent />
    </Suspense>
  )
}
