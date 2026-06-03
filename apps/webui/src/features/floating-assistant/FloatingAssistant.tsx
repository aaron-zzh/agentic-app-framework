"use client"

/**
 * FloatingAssistant——全局浮动 AI 助理（Odoo 风格）
 *
 * - 右下角浮动按钮，点击弹出聊天窗口
 * - 未登录时将 threadId 缓存到 localStorage，刷新后可恢复历史
 * - 基于 AssistantModalPrimitive（Radix Popover）+ AgUiChatProvider
 *
 * @author AaronZZH & Kiro
 */

import { AssistantModalPrimitive } from "@assistant-ui/react"
import { BotMessageSquare, ChevronDown } from "lucide-react"
import type { FC, RefObject } from "react"
import { LivechatPanel } from "@/features/livechat/LivechatPanel"
import { AgUiChatProvider } from "@/features/livechat/runtime/ag-ui-runtime"
import { useAuthStore } from "@/lib/store/auth-store"
import { ensureGuestThreadId } from "./guest-session"

export const FloatingAssistant: FC = () => {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  // 未登录时使用持久化的 guestThreadId，已登录时传 undefined（由 AgUiChatProvider 管理）
  const initialThreadId = isAuthenticated ? undefined : ensureGuestThreadId()

  return (
    <AgUiChatProvider initialThreadId={initialThreadId}>
      <AssistantModalPrimitive.Root>
        <AssistantModalPrimitive.Anchor className="fixed right-5 bottom-5 z-50 size-12">
          <AssistantModalPrimitive.Trigger asChild>
            <FloatingButton />
          </AssistantModalPrimitive.Trigger>
        </AssistantModalPrimitive.Anchor>

        <AssistantModalPrimitive.Content
          sideOffset={12}
          className="data-[state=open]:fade-in-0 data-[state=closed]:fade-out-0 data-[state=open]:zoom-in data-[state=closed]:zoom-out data-[state=open]:slide-in-from-bottom-1/2 data-[state=open]:slide-in-from-right-1/2 data-[state=closed]:slide-out-to-bottom-1/2 data-[state=closed]:slide-out-to-right-1/2 z-50 h-[560px] w-[380px] overflow-hidden rounded-xl border bg-background shadow-xl outline-none data-[state=closed]:animate-out data-[state=open]:animate-in"
        >
          <LivechatPanel />
        </AssistantModalPrimitive.Content>
      </AssistantModalPrimitive.Root>
    </AgUiChatProvider>
  )
}

type FloatingButtonProps = { "data-state"?: "open" | "closed" }

const FloatingButton = ({
  "data-state": state,
  ref,
  ...rest
}: FloatingButtonProps & { ref?: RefObject<HTMLButtonElement | null> }) => (
  <button
    ref={ref}
    type="button"
    aria-label={state === "open" ? "关闭助理" : "打开助理"}
    className="flex size-full items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform hover:scale-110 active:scale-90"
    {...rest}
  >
    <BotMessageSquare
      data-state={state}
      className="absolute size-6 transition-all data-[state=open]:rotate-90 data-[state=closed]:scale-100 data-[state=open]:scale-0"
    />
    <ChevronDown
      data-state={state}
      className="absolute size-6 transition-all data-[state=closed]:-rotate-90 data-[state=closed]:scale-0 data-[state=open]:scale-100"
    />
  </button>
)
FloatingButton.displayName = "FloatingButton"
