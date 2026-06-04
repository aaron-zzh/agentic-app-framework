import type { AuthUser } from "@/lib/store/auth-store"

export const MOCK_AUTH_ACCESS_TOKEN = "mock-access-token"
export const MOCK_AUTH_REFRESH_TOKEN = "mock-refresh-token"

export const mockedUser: AuthUser = {
  id: "mock-admin",
  username: "mock-admin",
  email: "demo@xuejiai.com",
  nickname: "模拟管理员",
  avatar: "/logo.png"
}

export function isMockAuthEnabled(): boolean {
  return process.env.NODE_ENV === "development" && process.env.NEXT_PUBLIC_MOCK_AUTH === "true"
}
