import type { Metadata, Viewport } from "next"
import Script from "next/script"
import { NextIntlClientProvider } from "next-intl"
import { getLocale, getMessages } from "next-intl/server"
import { NuqsAdapter } from "nuqs/adapters/next/app"

import { TooltipProvider } from "@/components/ui/tooltip"
import { FloatingAssistant } from "@/features/floating-assistant/FloatingAssistant"
import { geistMono, geistSans, notoSansSC } from "@/lib/fonts"
import { QueryProvider } from "@/providers/QueryProvider"
import { ServiceWorkerRegister } from "@/providers/ServiceWorkerRegister"
import { ThemeProvider } from "@/providers/ThemeProvider"
import { ToastProvider } from "@/providers/ToastProvider"

import "./global.css"

const APP_NAME = "AAF"
const APP_DEFAULT_TITLE = "AAF - Agentic App Framework"
const APP_TITLE_TEMPLATE = "%s - AAF"
const APP_DESCRIPTION = "多智能体应用开发框架"
const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://aaf.xuejiai.com"

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  applicationName: APP_NAME,
  title: {
    default: APP_DEFAULT_TITLE,
    template: APP_TITLE_TEMPLATE
  },
  description: APP_DESCRIPTION,
  manifest: "/manifest.json",
  keywords: ["AI", "多智能体", "工作流", "知识库", "Agentic App Framework", "AAF"],
  authors: [{ name: "AaronZZH" }],
  icons: {
    icon: "/favicon.ico",
    apple: "/logo/logo.png"
  },
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: APP_NAME
  },
  formatDetection: {
    telephone: false
  },
  openGraph: {
    type: "website",
    siteName: APP_NAME,
    title: {
      default: APP_DEFAULT_TITLE,
      template: APP_TITLE_TEMPLATE
    },
    description: APP_DESCRIPTION,
    url: SITE_URL,
    locale: "zh_CN",
    images: [
      {
        url: "/assets/images/ogimage.jpg",
        width: 1200,
        height: 630,
        alt: APP_DEFAULT_TITLE
      }
    ]
  },
  twitter: {
    card: "summary_large_image",
    title: {
      default: APP_DEFAULT_TITLE,
      template: APP_TITLE_TEMPLATE
    },
    description: APP_DESCRIPTION,
    images: ["/assets/images/ogimage.jpg"]
  }
}

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#ffffff" },
    { media: "(prefers-color-scheme: dark)", color: "#0f172a" }
  ]
}

export default async function RootLayout({ children }: { children: React.ReactNode }) {
  const locale = await getLocale()
  const messages = await getMessages()

  return (
    // suppressHydrationWarning: next-themes 在客户端注入 class/style 属性切换主题，
    // 服务端无法预知用户主题偏好，需抑制 hydration 不匹配警告（仅作用于此标签，不影响子元素）
    <html lang={locale} className="scroll-smooth" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} ${notoSansSC.variable} font-sans antialiased`}
      >
        {/* 阿里云 ESA AI 验证码：生产/测试环境启用，本地开发跳过 */}
        {process.env.NEXT_PUBLIC_CAPTCHA_ENABLED === "true" && (
          <>
            <Script id="aliyun-captcha-config" strategy="beforeInteractive">{`
              window.AliyunCaptchaConfig = {
                region: "${process.env.NEXT_PUBLIC_CAPTCHA_REGION ?? "cn"}",
                prefix: "${process.env.NEXT_PUBLIC_CAPTCHA_PREFIX ?? ""}",
              };
            `}</Script>
            <Script
              src="https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js"
              strategy="beforeInteractive"
            />
          </>
        )}
        <NextIntlClientProvider locale={locale} messages={messages}>
          <ThemeProvider>
            <QueryProvider>
              <TooltipProvider>
                <NuqsAdapter>{children}</NuqsAdapter>
                <ToastProvider />
                <FloatingAssistant />
              </TooltipProvider>
            </QueryProvider>
            <ServiceWorkerRegister />
          </ThemeProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  )
}
