/**
 * API Key 管理页面——用户设置 > API Key
 */

"use client"

import { PageContainer } from "@/components/common/PageContainer"
import { ApiKeyList } from "@/features/settings/api-keys/ApiKeyList"
import { useAuthStore } from "@/lib/store/auth-store"

export default function ApiKeysPage() {
  // 当前登录用户 ID 作为上下文自动传递
  const userId = useAuthStore((s) => (s.user?.id ? Number(s.user.id) : undefined))

  return (
    <PageContainer>
      <ApiKeyList userId={userId ?? undefined} />
    </PageContainer>
  )
}
