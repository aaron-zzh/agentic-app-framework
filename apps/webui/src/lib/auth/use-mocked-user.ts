"use client"

import { useCallback } from "react"
import { useAuthStore } from "@/lib/store/auth-store"
import {
  isMockAuthEnabled,
  MOCK_AUTH_ACCESS_TOKEN,
  MOCK_AUTH_REFRESH_TOKEN,
  mockedUser
} from "./mock-user"

export function useMockedUser() {
  const setTokens = useAuthStore((s) => s.setTokens)
  const setUser = useAuthStore((s) => s.setUser)

  const loginAsMockedUser = useCallback(() => {
    if (!isMockAuthEnabled()) return false
    setTokens(MOCK_AUTH_ACCESS_TOKEN, MOCK_AUTH_REFRESH_TOKEN)
    setUser(mockedUser)
    return true
  }, [setTokens, setUser])

  return {
    enabled: isMockAuthEnabled(),
    user: mockedUser,
    loginAsMockedUser
  }
}
