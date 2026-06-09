/**
 * BankingCurrentBalance——银行卡余额轮播卡片（shadcn Carousel）
 */

"use client"

import { Eye, EyeOff, MoreVertical, Pencil, Trash2 } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import {
  Carousel,
  CarouselContent,
  CarouselItem,
  CarouselNext,
  CarouselPrevious
} from "@/components/ui/carousel"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"

interface CreditCard {
  id: string
  cardType: string
  balance: number
  cardHolder: string
  cardNumber: string
  cardValid: string
}

interface BankingCurrentBalanceProps {
  list: CreditCard[]
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value)
}

function CardItem({ card }: { card: CreditCard }) {
  const [showBalance, setShowBalance] = useState(true)

  return (
    <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-gray-900 to-gray-700 p-6 text-white">
      {/* 操作菜单 */}
      <div className="absolute top-4 right-4">
        <DropdownMenu>
          <DropdownMenuTrigger>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-white/60 hover:bg-white/10 hover:text-white"
            >
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem className="text-destructive">
              <Trash2 className="mr-2 h-4 w-4" /> Delete
            </DropdownMenuItem>
            <DropdownMenuItem>
              <Pencil className="mr-2 h-4 w-4" /> Edit
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* 余额 */}
      <p className="mb-1 text-sm text-white/50">Current balance</p>
      <div className="flex items-center gap-2">
        <span className="font-bold text-2xl">
          {showBalance ? formatCurrency(card.balance) : "••••••••"}
        </span>
        <button
          type="button"
          onClick={() => setShowBalance((v) => !v)}
          className="text-white/50 hover:text-white"
        >
          {showBalance ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
        </button>
      </div>

      {/* 卡号 + 类型 */}
      <div className="my-6 flex items-center justify-end gap-2">
        <span className="rounded bg-white px-1 py-0.5 font-bold text-gray-900 text-xs uppercase">
          {card.cardType}
        </span>
        <span className="font-medium">{card.cardNumber}</span>
      </div>

      {/* 持卡人 + 有效期 */}
      <div className="flex gap-10">
        <div>
          <p className="mb-1 text-white/50 text-xs">Card holder</p>
          <p className="font-medium">{card.cardHolder}</p>
        </div>
        <div>
          <p className="mb-1 text-white/50 text-xs">Expiration date</p>
          <p className="font-medium">{card.cardValid}</p>
        </div>
      </div>
    </div>
  )
}

export function BankingCurrentBalance({ list }: BankingCurrentBalanceProps) {
  return (
    <div className="relative mb-2">
      {/* 叠层阴影 */}
      <div className="absolute right-7 bottom-[-16px] left-7 h-10 rounded-2xl bg-gray-400/20" />
      <div className="absolute right-4 bottom-[-8px] left-4 h-10 rounded-2xl bg-gray-400/30" />

      <Carousel className="w-full">
        <CarouselContent>
          {list.map((card) => (
            <CarouselItem key={card.id}>
              <CardItem card={card} />
            </CarouselItem>
          ))}
        </CarouselContent>
        <CarouselPrevious className="left-1 border-white/30 bg-white/10 text-white hover:bg-white/20 hover:text-white" />
        <CarouselNext className="right-1 border-white/30 bg-white/10 text-white hover:bg-white/20 hover:text-white" />
      </Carousel>
    </div>
  )
}
