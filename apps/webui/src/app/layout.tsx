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
