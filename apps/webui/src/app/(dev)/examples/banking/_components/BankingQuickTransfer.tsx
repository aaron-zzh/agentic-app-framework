/**
 * BankingQuickTransfer——快速转账（联系人轮播 + 金额 Slider + 确认弹窗）
 */

"use client"

import { ChevronLeft, ChevronRight } from "lucide-react"
import { useState } from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Slider } from "@/components/ui/slider"
import { Textarea } from "@/components/ui/textarea"

interface Contact {
  id: string
  name: string
  email: string
  avatarUrl: string
}

interface BankingQuickTransferProps {
  title?: string
  list: Contact[]
}

const MIN = 0
const MAX = 1000
const STEP = 50
const DEFAULT = 200

function formatCurrency(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value)
}

export function BankingQuickTransfer({ title, list }: BankingQuickTransferProps) {
  const [offset, setOffset] = useState(0)
  const [selected, setSelected] = useState(0)
  const [amount, setAmount] = useState(DEFAULT)
  const [open, setOpen] = useState(false)

  const VISIBLE = 5
  const visibleList = list.slice(offset, offset + VISIBLE)
  const contact = list[selected]

  function handleAmountInput(e: React.ChangeEvent<HTMLInputElement>) {
    const v = Number(e.target.value.replace(/[^0-9.]/g, ""))
    setAmount(Math.min(MAX, v))
  }

  return (
    <>
      <div className="rounded-2xl border bg-muted/30 p-4">
        <div className="mb-1 font-semibold text-sm">{title}</div>

        {/* 联系人轮播 */}
        <div className="mb-2 text-muted-foreground text-xs">Recent</div>
        <div className="relative flex items-center justify-between gap-2 py-4">
          <button
            type="button"
            disabled={offset === 0}
            onClick={() => setOffset((o) => Math.max(0, o - 1))}
            className="text-muted-foreground hover:text-foreground disabled:opacity-30"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>

          <div className="flex flex-1 items-center justify-center gap-3">
            {visibleList.map((c, i) => {
              const globalIdx = offset + i
              const isSelected = globalIdx === selected
              return (
                <button
                  key={c.id}
                  type="button"
                  onClick={() => setSelected(globalIdx)}
                  className="flex flex-col items-center gap-1 transition-transform"
                  style={{
                    transform: isSelected ? "scale(1.25)" : "scale(1)",
                    opacity: isSelected ? 1 : 0.5
                  }}
                >
                  <Avatar className="h-10 w-10">
                    <AvatarImage src={c.avatarUrl} />
                    <AvatarFallback>{c.name.charAt(0)}</AvatarFallback>
                  </Avatar>
                </button>
              )
            })}
          </div>

          <button
            type="button"
            disabled={offset + VISIBLE >= list.length}
            onClick={() => setOffset((o) => Math.min(list.length - VISIBLE, o + 1))}
            className="text-muted-foreground hover:text-foreground disabled:opacity-30"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>

        {/* 金额输入 */}
        <p className="mb-2 text-center text-muted-foreground text-xs uppercase tracking-widest">
          Insert amount
        </p>
        <div className="mb-4 flex items-center justify-center">
          <span className="mr-1 translate-y-0.5 font-semibold text-muted-foreground">$</span>
          <input
            className="w-32 border-none bg-transparent text-center font-bold text-3xl outline-none"
            value={amount}
            onChange={handleAmountInput}
          />
        </div>

        <Slider
          min={MIN}
          max={MAX}
          step={STEP}
          value={[amount]}
          onValueChange={(v) => setAmount(Array.isArray(v) ? (v[0] ?? amount) : v)}
          className="mb-4"
        />

        <div className="mb-4 flex items-center justify-between text-sm">
          <span className="text-muted-foreground">Your balance</span>
          <span className="font-semibold">{formatCurrency(34212)}</span>
        </div>

        <Button className="w-full" disabled={amount === 0} onClick={() => setOpen(true)}>
          Transfer now
        </Button>
      </div>

      {/* 确认弹窗 */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>Transfer to</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {contact && (
              <div className="flex items-center gap-3">
                <Avatar className="h-12 w-12">
                  <AvatarImage src={contact.avatarUrl} />
                  <AvatarFallback>{contact.name.charAt(0)}</AvatarFallback>
                </Avatar>
                <div>
                  <p className="font-medium">{contact.name}</p>
                  <p className="text-muted-foreground text-sm">{contact.email}</p>
                </div>
              </div>
            )}
            <div className="flex items-center justify-center rounded-lg border p-3">
              <span className="mr-1 font-semibold text-muted-foreground">$</span>
              <Input
                className="w-24 border-none text-center font-bold text-2xl shadow-none"
                value={amount}
                onChange={handleAmountInput}
              />
            </div>
            <Textarea placeholder="Write a message..." rows={3} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button disabled={amount === 0} onClick={() => setOpen(false)}>
              Transfer
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
