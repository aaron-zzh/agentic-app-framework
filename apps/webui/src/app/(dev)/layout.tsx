/**
 * Dev 路由布局。
 */

import { DevLayout } from "@/layouts/dev/DevLayout"

export default function Layout({ children }: { children: React.ReactNode }) {
  return <DevLayout>{children}</DevLayout>
}
