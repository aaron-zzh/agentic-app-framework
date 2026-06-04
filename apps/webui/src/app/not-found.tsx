import type { Metadata } from "next"
import Link from "next/link"
import { NotFoundIllustration } from "@/components/illustrations/NotFoundIllustration"

export const metadata: Metadata = {
  title: "页面未找到",
  description: "抱歉，您访问的页面不存在。"
}

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-4">
      <NotFoundIllustration />
      <div className="text-center">
        <h1 className="font-bold text-2xl tracking-tight">页面未找到</h1>
        <p className="mt-2 max-w-sm text-muted-foreground text-sm">
          抱歉，您访问的页面不存在。请检查 URL 是否正确。
        </p>
      </div>
      <Link
        href="/dashboard"
        className="rounded-md bg-primary px-6 py-2 text-primary-foreground text-sm transition-colors hover:bg-primary/90"
      >
        返回工作台
      </Link>
    </div>
  )
}
