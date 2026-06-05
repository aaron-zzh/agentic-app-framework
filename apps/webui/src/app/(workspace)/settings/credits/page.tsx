/**
 * 积分详情页——余额公式（总额 = 会员积分 + 奖励积分 + 每周积分）+ tab 流水 + 可展开记录
 * 对标设计截图，模拟数据
 * @author Kiro
 */

"use client"

import { ChevronDown, ChevronUp, Gift } from "lucide-react"
import { useState } from "react"
import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { cn } from "@/lib/utils/cn"

// ─── 模拟数据 ────────────────────────────────────────────────────────────────

type TxType = "earn" | "spend"

interface TxRecord {
  id: string
  title: string
  time: string
  amount: number
  type: TxType
  /** 展开详情（子条目） */
  children?: { id: string; title: string; amount: number }[]
}

const MOCK_BALANCE = {
  total: 666,
  member: 466,
  reward: 0,
  weekly: 200
}

const MOCK_TRANSACTIONS: TxRecord[] = [
  {
    id: "1",
    title: "Weekly Credits refreshed",
    time: "2026.06.01 13:36",
    amount: 200,
    type: "earn"
  },
  {
    id: "2",
    title: "Weekly Credits expired",
    time: "2026.05.25 00:00",
    amount: -200,
    type: "spend"
  },
  {
    id: "3",
    title: "Weekly Credits refreshed",
    time: "2026.05.19 12:08",
    amount: 200,
    type: "earn"
  },
  {
    id: "4",
    title: "Weekly Credits expired",
    time: "2026.05.18 00:00",
    amount: -200,
    type: "spend"
  },
  {
    id: "5",
    title: "Weekly Credits refreshed",
    time: "2026.05.15 14:58",
    amount: 200,
    type: "earn"
  },
  {
    id: "6",
    title: "水墨奇幻次元觉醒",
    time: "2026.05.14 10:22",
    amount: -1089,
    type: "spend",
    children: [
      { id: "6-1", title: "图片生成 × 3", amount: -480 },
      { id: "6-2", title: "LLM 对话 × 12", amount: -360 },
      { id: "6-3", title: "语音合成 × 2", amount: -249 }
    ]
  },
  {
    id: "7",
    title: "Weekly Credits refreshed",
    time: "2026.05.08 09:00",
    amount: 200,
    type: "earn"
  },
  { id: "8", title: "新用户奖励积分", time: "2026.05.01 00:00", amount: 500, type: "earn" }
]

// ─── 余额公式区 ──────────────────────────────────────────────────────────────

function BalanceFormula() {
  return (
    <div className="grid grid-cols-4 items-end gap-2 py-6">
      <div>
        <p className="text-muted-foreground text-sm">积分余额</p>
        <p className="mt-1 font-bold text-3xl tabular-nums">{MOCK_BALANCE.total}</p>
      </div>

      <div className="flex flex-col items-center gap-1">
        <span className="text-muted-foreground text-xl">=</span>
        <div>
          <p className="flex items-center gap-1 text-muted-foreground text-sm">
            会员积分
            <InfoTip text="通过订阅会员获得" />
          </p>
          <p className="mt-1 font-bold text-3xl tabular-nums">{MOCK_BALANCE.member}</p>
        </div>
      </div>

      <div className="flex flex-col items-center gap-1">
        <span className="text-muted-foreground text-xl">+</span>
        <div>
          <p className="flex items-center gap-1 text-muted-foreground text-sm">
            奖励积分
            <InfoTip text="邀请好友或完成任务获得" />
          </p>
          <p className="mt-1 font-bold text-3xl tabular-nums">{MOCK_BALANCE.reward}</p>
        </div>
      </div>

      <div className="flex flex-col items-center gap-1">
        <span className="text-muted-foreground text-xl">+</span>
        <div>
          <p className="flex items-center gap-1 text-muted-foreground text-sm">
            每周积分
            <InfoTip text="每周自动刷新，到期清零" />
          </p>
          <p className="mt-1 font-bold text-3xl tabular-nums">{MOCK_BALANCE.weekly}</p>
        </div>
      </div>
    </div>
  )
}

function InfoTip({ text }: { text: string }) {
  return (
    <span
      className="inline-flex size-4 cursor-default items-center justify-center rounded-full border text-[10px] text-muted-foreground"
      title={text}
    >
      i
    </span>
  )
}

// ─── 流水条目 ────────────────────────────────────────────────────────────────

function TxRow({ tx }: { tx: TxRecord }) {
  const [expanded, setExpanded] = useState(false)
  const hasChildren = !!tx.children?.length
  const isEarn = tx.amount > 0

  return (
    <>
      <div className="flex items-center justify-between py-4">
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <p className="font-medium">{tx.title}</p>
            {hasChildren && (
              <button
                type="button"
                className="text-muted-foreground hover:text-foreground"
                onClick={() => setExpanded((v) => !v)}
                aria-label={expanded ? "收起" : "展开"}
              >
                {expanded ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
              </button>
            )}
          </div>
          <p className="mt-0.5 text-muted-foreground text-xs">{tx.time}</p>
        </div>
        <span
          className={cn(
            "font-semibold tabular-nums",
            isEarn ? "text-emerald-500" : "text-orange-400"
          )}
        >
          {isEarn ? `+ ${tx.amount}` : `- ${Math.abs(tx.amount)}`}
        </span>
      </div>

      {/* 展开子条目 */}
      {expanded && tx.children && (
        <div className="mb-2 ml-4 space-y-2 rounded-lg bg-muted/40 px-4 py-2">
          {tx.children.map((child) => (
            <div key={child.id} className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">{child.title}</span>
              <span className="text-orange-400 tabular-nums">- {Math.abs(child.amount)}</span>
            </div>
          ))}
        </div>
      )}

      <Separator />
    </>
  )
}

// ─── 兑换码对话框（简版） ────────────────────────────────────────────────────

function RedeemButton() {
  return (
    <Button
      variant="outline"
      size="sm"
      className="gap-1.5 border-amber-500 text-amber-500 hover:bg-amber-500/10"
    >
      <Gift className="size-4" />
      兑换码
    </Button>
  )
}

// ─── 主页面 ──────────────────────────────────────────────────────────────────

type TabValue = "all" | "spend" | "earn"

export default function CreditsPage() {
  const [tab, setTab] = useState<TabValue>("all")

  const filtered = MOCK_TRANSACTIONS.filter((tx) => {
    if (tab === "earn") return tx.type === "earn"
    if (tab === "spend") return tx.type === "spend"
    return true
  })

  return (
    <div className="mx-auto max-w-3xl px-8 py-6">
      {/* 标题行 */}
      <div className="flex items-center justify-between">
        <h1 className="font-semibold text-xl">积分详情</h1>
        <RedeemButton />
      </div>

      {/* 余额公式 */}
      <BalanceFormula />

      <Separator />

      {/* Tab */}
      <div className="py-4">
        <Tabs value={tab} onValueChange={(v) => setTab(v as TabValue)}>
          <TabsList className="w-full">
            <TabsTrigger value="all" className="flex-1">
              全部
            </TabsTrigger>
            <TabsTrigger value="spend" className="flex-1">
              已消耗
            </TabsTrigger>
            <TabsTrigger value="earn" className="flex-1">
              已获得
            </TabsTrigger>
          </TabsList>
        </Tabs>
      </div>

      {/* 流水列表 */}
      <div>
        {filtered.length === 0 ? (
          <p className="py-12 text-center text-muted-foreground text-sm">暂无记录</p>
        ) : (
          filtered.map((tx) => <TxRow key={tx.id} tx={tx} />)
        )}
      </div>
    </div>
  )
}
