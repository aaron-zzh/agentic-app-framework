import type { Metadata } from "next"
import { Geist, Geist_Mono } from "next/font/google"

import { QueryProvider } from "@/providers/QueryProvider"
import { ThemeProvider } from "@/providers/ThemeProvider"
import { ToastProvider } from "@/providers/ToastProvider"

import "./global.css"

const geistSans = Geist({
  variable: "--font-sans",
  subsets: ["latin"]
})

const geistMono = Geist_Mono({
  variable: "--font-mono",
  subsets: ["latin"]
})

export const metadata: Metadata = {
  title: "AAF",
  description: "Agentic App Framework"
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className={`${geistSans.variable} ${geistMono.variable} font-sans antialiased`}>
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
