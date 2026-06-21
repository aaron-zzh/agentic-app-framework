import type { Metadata } from "next"
import Link from "next/link"
import { LottieIcon } from "@/components/animate/LottieIcon"

export const metadata: Metadata = {
  title: "页面未找到",
  description: "抱歉，您访问的页面不存在。"
}

export default function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center gap-6 p-10">
      <div className="flex flex-col items-center gap-3 rounded-xl bg-background py-4">
        <LottieIcon name="404" width={340} height={340} loop />
      </div>
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
