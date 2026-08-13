/**
 * 认证路由布局。
 *
 * @author AaronZZH & Kiro
 */

import { AuthLayout } from "@/layouts/auth/AuthLayout"

export default function Layout({ children }: { children: React.ReactNode }) {
  return <AuthLayout>{children}</AuthLayout>
}
