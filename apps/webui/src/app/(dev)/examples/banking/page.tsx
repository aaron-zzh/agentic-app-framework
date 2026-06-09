/**
 * Banking 仪表盘示例——用 shadcn 组件复刻 Minimal UI banking overview
 * @author AaronZZH & Kiro
 */

import { BankingBalanceStatistics } from "./_components/BankingBalanceStatistics"
import { BankingContacts } from "./_components/BankingContacts"
import { BankingCurrentBalance } from "./_components/BankingCurrentBalance"
import { BankingExpensesCategories } from "./_components/BankingExpensesCategories"
import { BankingInviteFriends } from "./_components/BankingInviteFriends"
import { BankingOverview } from "./_components/BankingOverview"
import { BankingQuickTransfer } from "./_components/BankingQuickTransfer"
import { BankingRecentTransitions } from "./_components/BankingRecentTransitions"
import {
  mockBalanceStatistics,
  mockContacts,
  mockCreditCards,
  mockExpensesCategories,
  mockTransactions
} from "./_mock"

export default function BankingExamplePage() {
  return (
    <div className="mx-auto max-w-7xl p-6">
      <h1 className="mb-1 font-bold text-2xl">Banking 示例</h1>
      <p className="mb-8 text-muted-foreground text-sm">shadcn + ECharts 复刻 banking 仪表盘</p>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* 左侧主内容区 */}
        <div className="flex flex-col gap-6 lg:col-span-2">
          <BankingOverview />

          <BankingBalanceStatistics
            title="Balance statistics"
            subheader="Statistics on balance over time"
            chart={mockBalanceStatistics}
          />

          <BankingExpensesCategories
            title="Expenses categories"
            chart={{ series: mockExpensesCategories }}
          />

          <BankingRecentTransitions title="Recent transitions" tableData={mockTransactions} />
        </div>

        {/* 右侧边栏 */}
        <div className="flex flex-col gap-6">
          <BankingCurrentBalance list={mockCreditCards} />

          <BankingQuickTransfer title="Quick transfer" list={mockContacts} />

          <BankingContacts title="Contacts" subheader="You have 122 contacts" list={mockContacts} />

          <BankingInviteFriends
            price="$50"
            title={`Invite friends\nand earn`}
            description="Praesent egestas tristique nibh. Duis lobortis massa imperdiet quam."
          />
        </div>
      </div>
    </div>
  )
}
