/**
 * 点击热力图数据收集器
 * 记录点击位置和频率，生成热力图数据
 * @author AaronZZH & Kiro
 */

import type { UserAction } from "../types"

interface HeatmapPoint {
  /** 目标元素标识 */
  target: string
  /** 语义角色 */
  role: string
  /** 点击次数 */
  count: number
  /** 所属页面 */
  page: string
}

class HeatmapCollectorImpl {
  private points = new Map<string, HeatmapPoint>()

  /** 记录点击事件 */
  recordClick(action: UserAction): void {
    if (action.type !== "click") return

    const key = `${action.context.page}:${action.target}`
    const existing = this.points.get(key)

    if (existing) {
      existing.count++
    } else {
      this.points.set(key, {
        target: action.target,
        role: action.semantics.semanticRole,
        count: 1,
        page: action.context.page
      })
    }
  }

  /** 获取指定页面的热力图数据（按点击次数降序） */
  getHeatmap(page?: string): HeatmapPoint[] {
    const points = Array.from(this.points.values())
    const filtered = page ? points.filter((p) => p.page === page) : points
    return filtered.sort((a, b) => b.count - a.count)
  }

  /** 获取热点区域（点击次数 top N） */
  getHotspots(n = 10, page?: string): HeatmapPoint[] {
    return this.getHeatmap(page).slice(0, n)
  }

  /** 清空数据 */
  reset(): void {
    this.points.clear()
  }
}

/** 全局单例 */
export const HeatmapCollector = new HeatmapCollectorImpl()
