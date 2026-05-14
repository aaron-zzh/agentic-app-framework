import type { Metadata } from "next"

import { geistMono, geistSans, notoSansSC } from "@/lib/fonts"
import { QueryProvider } from "@/providers/QueryProvider"
import { ThemeProvider } from "@/providers/ThemeProvider"
import { ToastProvider } from "@/providers/ToastProvider"

import "./global.css"

export const metadata: Metadata = {
  title: "AAF",
  description: "Agentic App Framework"
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} ${notoSansSC.variable} font-sans antialiased`}
      >
        <ThemeProvider>
          <QueryProvider>
            {children}
            <ToastProvider />
          </QueryProvider>
        </ThemeProvider>
      </body>
    </html>
  )
}
