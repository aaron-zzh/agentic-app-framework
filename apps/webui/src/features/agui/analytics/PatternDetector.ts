/**
 * 操作模式识别器
 * 检测高频操作序列、重复操作等行为模式
 * @author AaronZZH & Kiro
 */

import type { BehaviorPattern, UserAction } from "../types"

/** 滑动窗口大小 */
const WINDOW_SIZE = 5

/** 最小频率阈值（低于此值不视为模式） */
const MIN_FREQUENCY = 3

class PatternDetectorImpl {
  private actions: UserAction[] = []
  private patterns = new Map<string, BehaviorPattern>()

  /** 添加操作记录 */
  addAction(action: UserAction): void {
    this.actions.push(action)
    // 保留最近 1000 条
    if (this.actions.length > 1000) {
      this.actions = this.actions.slice(-1000)
    }
    this.detectPatterns()
  }

  /** 批量添加 */
  addActions(actions: UserAction[]): void {
    for (const action of actions) {
      this.actions.push(action)
    }
    if (this.actions.length > 1000) {
      this.actions = this.actions.slice(-1000)
    }
    this.detectPatterns()
  }

  /** 获取已识别的模式（按频率降序） */
  getPatterns(): BehaviorPattern[] {
    return Array.from(this.patterns.values())
      .filter((p) => p.frequency >= MIN_FREQUENCY)
      .sort((a, b) => b.frequency - a.frequency)
  }

  /** 检测重复操作（同一目标短时间内多次操作） */
  getRepeatedActions(windowMs = 10000): { target: string; count: number }[] {
    const now = Date.now()
    const recent = this.actions.filter((a) => now - a.timestamp < windowMs)
    const counts = new Map<string, number>()
    for (const a of recent) {
      const key = `${a.type}:${a.target}`
      counts.set(key, (counts.get(key) ?? 0) + 1)
    }
    return Array.from(counts.entries())
      .filter(([, count]) => count >= 3)
      .map(([target, count]) => ({ target, count }))
  }

  /** 滑动窗口检测序列模式 */
  private detectPatterns(): void {
    if (this.actions.length < WINDOW_SIZE) return

    // 提取最近的操作类型序列
    for (let size = 2; size <= WINDOW_SIZE; size++) {
      const sequences = new Map<string, { count: number; durations: number[] }>()

      for (let i = 0; i <= this.actions.length - size; i++) {
        const window = this.actions.slice(i, i + size)
        const key = window.map((a) => `${a.type}:${a.semantics.semanticRole}`).join(" → ")
        const duration = window[window.length - 1].timestamp - window[0].timestamp

        const existing = sequences.get(key) ?? { count: 0, durations: [] }
        existing.count++
        existing.durations.push(duration)
        sequences.set(key, existing)
      }

      for (const [key, data] of sequences) {
        if (data.count >= MIN_FREQUENCY) {
          const avgDuration =
            data.durations.reduce((s, d) => s + d, 0) / data.durations.length
          this.patterns.set(key, {
            id: key,
            sequence: key.split(" → "),
            frequency: data.count,
            avgDuration,
            lastSeen: Date.now()
          })
        }
      }
    }
  }
}

/** 全局单例 */
export const PatternDetector = new PatternDetectorImpl()
