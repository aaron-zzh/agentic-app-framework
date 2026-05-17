/**
 * Dashboard——工作台首页
 * @author AaronZZH & Kiro
 */

import { entityRegistry } from "@/features/entity-engine"

export default function DashboardPage() {
  const entities = entityRegistry.getAll()

  return (
    <div className="p-6">
      <h1 className="mb-6 font-bold text-2xl">工作台</h1>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {entities.map((e) => (
          <a
            key={e.slug}
            href={`/workspace/${e.slug}`}
            className="flex flex-col gap-2 rounded-lg border p-4 transition-colors hover:bg-accent"
          >
            <span className="font-medium">{e.label}</span>
            <span className="text-muted-foreground text-sm">{e.apiPath}</span>
          </a>
        ))}
      </div>
    </div>
  )
}
