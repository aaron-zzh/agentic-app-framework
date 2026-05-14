/**
 * 认证布局——居中卡片（移动端）/ 左右分栏（桌面端）
 * @author AaronZZH & Kiro
 */

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen">
      {/* 左侧品牌区（桌面端可见） */}
      <div className="hidden flex-1 items-center justify-center bg-muted lg:flex">
        <div className="text-center">
          <h1 className="text-3xl font-bold">AAF</h1>
          <p className="mt-2 text-muted-foreground">AI 原生应用开发框架</p>
        </div>
      </div>
      {/* 右侧表单区 */}
      <div className="flex flex-1 items-center justify-center p-6 lg:flex-none lg:w-[var(--layout-auth-card-width)] lg:px-12">
        {children}
      </div>
    </div>
  )
}
