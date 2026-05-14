import { Geist, Geist_Mono, Merriweather, Montserrat, Noto_Sans_SC } from "next/font/google"

// —— 全局加载（layout.tsx 注入） ——

/** 英文主字体 */
export const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
  display: "swap"
})

/** 等宽字体 */
export const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
  display: "swap"
})

/** 中文字体 */
export const notoSansSC = Noto_Sans_SC({
  variable: "--font-noto-sans-sc",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  display: "swap"
})

// —— 按需加载（在需要的页面中引入 .variable 注入） ——

/** 标题装饰字体（英文） */
export const montserrat = Montserrat({
  variable: "--font-montserrat",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  display: "swap"
})

/** 衬线字体（英文，文章/引用） */
export const merriweather = Merriweather({
  variable: "--font-merriweather",
  subsets: ["latin"],
  weight: ["400", "700"],
  style: ["normal", "italic"],
  display: "swap"
})
