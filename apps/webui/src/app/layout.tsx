import type { Metadata, Viewport } from "next"
import { NuqsAdapter } from "nuqs/adapters/next/app"

import { TooltipProvider } from "@/components/ui/tooltip"
import { geistMono, geistSans, notoSansSC } from "@/lib/fonts"
import { QueryProvider } from "@/providers/QueryProvider"
import { ServiceWorkerRegister } from "@/providers/ServiceWorkerRegister"
import { ThemeProvider } from "@/providers/ThemeProvider"
import { ToastProvider } from "@/providers/ToastProvider"

import "./global.css"

export const metadata: Metadata = {
  title: "AAF",
  description: "Agentic App Framework",
  manifest: "/manifest.json",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "AAF"
  }
}

export const viewport: Viewport = {
  themeColor: "#0f172a"
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // suppressHydrationWarning: next-themes 在客户端注入 class/style 属性切换主题，
    // 服务端无法预知用户主题偏好，需抑制 hydration 不匹配警告（仅作用于此标签，不影响子元素）
    <html lang="zh-CN" className="scroll-smooth" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} ${notoSansSC.variable} font-sans antialiased`}
      >
        <ThemeProvider>
          <QueryProvider>
            <TooltipProvider>
              <NuqsAdapter>{children}</NuqsAdapter>
              <ToastProvider />
            </TooltipProvider>
          </QueryProvider>
          <ServiceWorkerRegister />
        </ThemeProvider>
      </body>
    </html>
  )
}
