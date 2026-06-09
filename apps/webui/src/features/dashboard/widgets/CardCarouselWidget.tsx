/**
 * CardCarouselWidget——卡片余额轮播（支持显示/隐藏余额、编辑/删除菜单）
 */

"use client"

import { Eye, EyeOff, MoreVertical, Pencil, Trash2 } from "lucide-react"
import { useState } from "react"
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

export interface CardItem {
  id: string
  cardType: string
  balance: number
  cardHolder: string
  cardNumber: string
  cardValid: string
}

interface CardCarouselWidgetProps {
  list: CardItem[]
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value)
}

function SingleCard({ card }: { card: CardItem }) {
  const [showBalance, setShowBalance] = useState(true)

  return (
    <div className="relative overflow-hidden rounded-2xl bg-gradient-to-br from-gray-900 to-gray-700 p-6 text-white">
      <div className="absolute top-4 right-4">
        <DropdownMenu>
          <DropdownMenuTrigger className="flex h-8 w-8 items-center justify-center rounded-md text-white/60 hover:bg-white/10 hover:text-white">
            <MoreVertical className="h-4 w-4" />
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
      <div className="my-6 flex items-center justify-end gap-2">
        <span className="rounded bg-white px-1 py-0.5 font-bold text-gray-900 text-xs uppercase">
          {card.cardType}
        </span>
        <span className="font-medium">{card.cardNumber}</span>
      </div>
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

export function CardCarouselWidget({ list }: CardCarouselWidgetProps) {
  return (
    <div className="relative mb-2">
      <div className="absolute right-7 bottom-[-16px] left-7 h-10 rounded-2xl bg-gray-400/20" />
      <div className="absolute right-4 bottom-[-8px] left-4 h-10 rounded-2xl bg-gray-400/30" />
      <Carousel className="w-full">
        <CarouselContent>
          {list.map((card) => (
            <CarouselItem key={card.id}>
              <SingleCard card={card} />
            </CarouselItem>
          ))}
        </CarouselContent>
        <CarouselPrevious className="left-1 border-white/30 bg-white/10 text-white hover:bg-white/20 hover:text-white" />
        <CarouselNext className="right-1 border-white/30 bg-white/10 text-white hover:bg-white/20 hover:text-white" />
      </Carousel>
    </div>
  )
}
