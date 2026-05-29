/**
 * 工作区路由加载状态——路由切换时显示
 * @author AaronZZH & Kiro
 */

export default function WorkspaceLoading() {
  return (
    <div className="flex flex-1 flex-col">
      {/* 顶部进度条 */}
      <div className="h-0.5 w-full overflow-hidden bg-muted">
        <div className="h-full w-1/3 animate-pulse bg-primary" />
      </div>
      {/* 内容骨架 */}
      <div className="flex-1 space-y-4 p-4">
        <div className="h-8 w-48 animate-pulse rounded bg-muted" />
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="flex gap-4">
              <div className="h-8 flex-1 animate-pulse rounded bg-muted" />
              <div className="h-8 flex-1 animate-pulse rounded bg-muted" />
              <div className="h-8 flex-1 animate-pulse rounded bg-muted" />
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
