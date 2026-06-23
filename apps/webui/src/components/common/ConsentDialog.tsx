/**
 * 法律文档同意弹窗——登录成功后强制确认未同意的最新版本
 *
 * 触发场景：
 *   - 用户登录后，前端调用 /api/legal/consent/pending 获取待同意列表
 *   - items.length > 0 时展示本弹窗，勾选一个 checkbox 即同意所有
 *   - 用户点击"同意并继续" → 逐条 POST /api/legal/consent → 全部成功后回调 onAllConfirmed
 *   - 用户点击"不同意，退出登录" → onDecline，外部清理 token
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ScaleIcon } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog"
import { type LegalDocument, legalApi } from "@/lib/api/rest/legal"
import { notify } from "@/lib/notification"

export interface ConsentDialogProps {
  items: LegalDocument[]
  onAllConfirmed: () => void
  onDecline: () => void
}

const TYPE_HREF: Record<LegalDocument["type"], string> = {
  "legal-terms": "/terms",
  "legal-privacy": "/privacy"
}

export function ConsentDialog({ items, onAllConfirmed, onDecline }: ConsentDialogProps) {
  const [agreed, setAgreed] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit() {
    if (!agreed || submitting) return
    setSubmitting(true)
    try {
      for (const doc of items) {
        await legalApi.submit(doc.id)
      }
      onAllConfirmed()
    } catch (err) {
      notify.error(err instanceof Error ? err.message : "提交同意失败，请重试")
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={items.length > 0} modal onOpenChange={() => {}}>
      <DialogContent className="gap-0 p-0 sm:max-w-md" showCloseButton={false}>
        {/* Header */}
        <div className="flex items-start gap-4 border-b p-6">
          <div
            aria-hidden="true"
            className="flex size-11 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary ring-1 ring-primary/20"
          >
            <ScaleIcon className="size-5" strokeWidth={1.75} />
          </div>
          <div className="flex-1 space-y-1">
            <DialogTitle className="font-semibold text-base">请确认我们的政策更新</DialogTitle>
            <DialogDescription className="text-muted-foreground text-sm leading-relaxed">
              以下文档已更新，请阅读并同意后继续使用本服务。
            </DialogDescription>
          </div>
        </div>

        {/* Single checkbox */}
        <div className="flex items-center gap-2 pl-10 py-5">
          <Checkbox
            id="consent-all"
            checked={agreed}
            onCheckedChange={(c) => setAgreed(c === true)}
          />
          <label
            htmlFor="consent-all"
            className="cursor-pointer select-none text-muted-foreground text-xs leading-relaxed"
          >
            我已阅读并同意
            {items.map((doc, i) => (
              <span key={doc.id}>
                <Link
                  href={TYPE_HREF[doc.type]}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-primary hover:underline"
                  onClick={(e) => e.stopPropagation()}
                >
                  《{doc.title}》
                </Link>
                {i < items.length - 1 && "和"}
              </span>
            ))}
          </label>
        </div>

        {/* Footer */}
        <div className="flex justify-end gap-2 border-t bg-muted/30 px-6 py-4">
          <Button variant="ghost" size="sm" onClick={onDecline} disabled={submitting}>
            不同意，退出登录
          </Button>
          <Button size="sm" onClick={handleSubmit} disabled={!agreed || submitting}>
            {submitting ? "提交中..." : "同意并继续"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
