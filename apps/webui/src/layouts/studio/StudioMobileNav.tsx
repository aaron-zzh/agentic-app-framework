/**
 * Studio 移动端导航——汉堡按钮 + 左侧抽屉（Sheet）
 *
 * 仅在 <md 显示；桌面端使用常驻的 StudioSidebar。
 * 菜单项复用 StudioSidebar 的 SectionItem 与 useStudioSectionNav。
 */

"use client"

import { Gift, Menu, MessageCircle, Settings } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { Brand } from "@/components/brand/Brand"
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { STUDIO_NAV, type StudioSectionConfig } from "@/features/studio/nav-config"
import { useWechatQrImage } from "@/lib/api/rest/system"
import { cn } from "@/lib/utils/index"
import { SectionItem, useStudioSectionNav } from "./StudioSidebar"

const FOOTER_LINK_CLASS =
  "flex w-full items-center gap-2 rounded-lg px-3 py-2 font-medium text-xs transition-colors"

export function StudioMobileNav() {
  const [open, setOpen] = useState(false)
  const [qrVisible, setQrVisible] = useState(false)
  const { activeSection, handleOpen } = useStudioSectionNav()
  const { data: wechatQrData } = useWechatQrImage()
  const wechatQrUrl = wechatQrData || "https://picsum.photos/720"

  const close = () => setOpen(false)

  const onOpenSection = (config: StudioSectionConfig) => {
    handleOpen(config)
    close()
  }

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        className="inline-flex shrink-0 rounded-md p-1.5 text-muted-foreground hover:bg-foreground/6 md:hidden"
        aria-label="打开菜单"
        render={<button type="button" />}
      >
        <Menu className="size-5" />
      </SheetTrigger>
      <SheetContent side="left" showCloseButton={false} className="gap-0 p-0">
        <SheetTitle className="sr-only">导航菜单</SheetTitle>

        {/* 品牌区 */}
        <div className="flex h-14 shrink-0 items-center px-4">
          <Brand size="sm" href="/" className="text-foreground" />
        </div>

        {/* 功能分区列表 */}
        <nav className="flex flex-1 flex-col gap-1 overflow-y-auto px-2">
          {STUDIO_NAV.map((config) => (
            <SectionItem
              key={config.section}
              config={config}
              active={activeSection === config.section}
              collapsed={false}
              onOpen={() => onOpenSection(config)}
            />
          ))}
        </nav>

        {/* 底部：邀请 / 客服（点击展开二维码，无 hover）/ 设置 */}
        <div className="border-foreground/6 border-t p-2">
          <Link
            href="/studio/me/invite"
            onClick={close}
            className={cn(FOOTER_LINK_CLASS, "text-amber-400 hover:bg-amber-400/10")}
          >
            <Gift className="size-4 shrink-0" />
            <span className="flex-1 truncate">邀请赚积分 🎁</span>
          </Link>
          <button
            type="button"
            onClick={() => setQrVisible((v) => !v)}
            aria-expanded={qrVisible}
            className={cn(
              FOOTER_LINK_CLASS,
              "text-left text-muted-foreground hover:bg-foreground/[0.04] hover:text-foreground"
            )}
          >
            <MessageCircle className="size-4 shrink-0" />
            <span className="flex-1 truncate">微信客服</span>
          </button>
          {qrVisible && (
            <div className="mx-3 mb-1 rounded-lg border bg-background p-2">
              {/* biome-ignore lint/performance/noImgElement: 微信客服二维码 */}
              <img src={wechatQrUrl} alt="微信客服" className="mx-auto size-32 rounded" />
              <p className="mt-1 text-center text-[10px] text-muted-foreground">扫码联系客服</p>
            </div>
          )}
          <Link
            href="/studio/me"
            onClick={close}
            className={cn(
              FOOTER_LINK_CLASS,
              "text-muted-foreground hover:bg-foreground/[0.04] hover:text-foreground"
            )}
          >
            <Settings className="size-4 shrink-0" />
            <span className="flex-1 truncate">设置</span>
          </Link>
        </div>
      </SheetContent>
    </Sheet>
  )
}
